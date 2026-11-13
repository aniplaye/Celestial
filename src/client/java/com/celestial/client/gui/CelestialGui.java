package com.celestial.client.gui;

import java.util.ArrayList;
import java.util.List;

import com.celestial.client.Celestial;
import com.celestial.client.config.Settings;
import com.celestial.client.config.Store;
import com.celestial.client.config.Theme;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import com.celestial.client.module.ModuleManager;
import com.celestial.client.profile.ProfileManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Celestial utility interface. Opens with the configurable Right Shift
 * keybind and behaves as a non-pausing overlay.
 *
 * Design: sidebar navigation, glassmorphism window, module rows with
 * toggle pills, steppers, profiles and themes.
 *
 * Reliability rules baked in from the old menu's failures:
 * - Every owned widget is tracked and removed EXPLICITLY on every
 *   rebuild (vanilla list helpers left stale widgets registered, which
 *   kept rendering and ate clicks).
 * - Layout runs on init/reposition, after every mutation, and once per
 *   frame before drawing, so positions can never go stale.
 * - Screen has no mouse/scroll callbacks in 26.3, so the module list
 *   scrolls with real Up/Down buttons.
 */
public class CelestialGui extends Screen {
	public enum Tab {
		HOME("Home"),
		MODULES("Modules"),
		SETTINGS("Settings"),
		PROFILES("Profiles"),
		THEMES("Themes"),
		ABOUT("About");

		public final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private static boolean open;
	private static long openStamp;
	private static long closedStamp;
	private static CelestialGui current;

	private Tab tab = Tab.HOME;
	private Category filter;
	private String search = "";
	private String selectedProfile;
	private int scrollFirst;
	private int scrollMax;

	private final List<CelestialWidget> ui = new ArrayList<>();
	private final List<ModuleRow> moduleRows = new ArrayList<>();
	private EditBox searchBox;
	private EditBox profileBox;
	private EditBox renameBox;
	private EditBox hexBox;

	public CelestialGui() {
		super(Component.literal("Celestial Client"));
	}

	public static boolean isOpen() {
		return open;
	}

	/**
	 * True right after the menu closed, so a still-queued Right Shift
	 * press that just closed the menu can't instantly reopen it.
	 */
	public static boolean justClosed() {
		return System.currentTimeMillis() - closedStamp < 400;
	}

	public static float contentAlpha() {
		if (!Celestial.settings.animations || current == null) {
			return 1.0f;
		}
		long age = System.currentTimeMillis() - openStamp;
		return Math.max(0.0f, Math.min(1.0f, age / 180.0f));
	}

	@Override
	public void added() {
		open = true;
		current = this;
		openStamp = System.currentTimeMillis();
		if (selectedProfile == null) {
			selectedProfile = Celestial.settings.activeProfile;
		}
	}

	@Override
	public void removed() {
		open = false;
		current = null;
		closedStamp = System.currentTimeMillis();
		safeSave();
	}

	@Override
	public void onClose() {
		safeSave();
	}

	private static void safeSave() {
		try {
			Store.save(Celestial.settings);
		} catch (Exception ignored) {
		}
		try {
			ProfileManager.save(Celestial.settings.activeProfile);
		} catch (Exception ignored) {
		}
	}

	@Override
	public void init() {
		try {
			buildTab();
		} catch (Exception e) {
			System.out.println("[Celestial] menu build failed: " + e);
		}
		layoutNow();
	}

	@Override
	public void repositionElements() {
		layoutNow();
	}

	@Override
	public boolean isPauseScreen() {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.hasSingleplayerServer();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	/** Right Shift also closes the menu (opening is handled by the tick handler). */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (com.celestial.client.CelestialClient.isGuiOpenKey(event)) {
			if (isAnyBoxFocused()) {
				return super.keyPressed(event);
			}
			Minecraft mc = Minecraft.getInstance();
			if (mc != null) {
				mc.setScreenAndShow(null);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	private boolean isAnyBoxFocused() {
		try {
			return (searchBox != null && searchBox.isFocused())
				|| (profileBox != null && profileBox.isFocused())
				|| (renameBox != null && renameBox.isFocused())
				|| (hexBox != null && hexBox.isFocused());
		} catch (Exception e) {
			return false;
		}
	}

	// ---------------- layout ----------------

	private double scale() {
		return Celestial.settings.guiScale;
	}

	private int winW() {
		return (int) Math.min(780 * scale(), Math.max(200, this.width - 24));
	}

	private int winH() {
		return (int) Math.min(480 * scale(), Math.max(160, this.height - 24));
	}

	private int winX() {
		return (this.width - winW()) / 2;
	}

	private int winY() {
		return (this.height - winH()) / 2;
	}

	private Font fontOrMinecraft() {
		if (this.font != null) {
			return this.font;
		}
		return Minecraft.getInstance().font;
	}

	private void track(CelestialWidget widget) {
		ui.add(widget);
		try {
			this.addRenderableWidget(widget);
		} catch (Exception e) {
			System.out.println("[Celestial] cannot track widget: " + e);
		}
	}

	private void trackBox(EditBox box) {
		try {
			this.addRenderableWidget(box);
		} catch (Exception e) {
			System.out.println("[Celestial] cannot track text box: " + e);
		}
	}

	/**
	 * Drops every owned widget, including the text boxes. Owned state is
	 * cleared FIRST so a throwing vanilla call can never corrupt the tab
	 * being built.
	 */
	private void clearTab() {
		List<CelestialWidget> oldUi = new ArrayList<>(ui);
		List<EditBox> oldBoxes = new ArrayList<>();
		if (searchBox != null) {
			oldBoxes.add(searchBox);
		}
		if (profileBox != null) {
			oldBoxes.add(profileBox);
		}
		if (renameBox != null) {
			oldBoxes.add(renameBox);
		}
		if (hexBox != null) {
			oldBoxes.add(hexBox);
		}
		ui.clear();
		moduleRows.clear();
		searchBox = null;
		profileBox = null;
		renameBox = null;
		hexBox = null;
		for (CelestialWidget widget : oldUi) {
			try {
				this.removeWidget(widget);
			} catch (Exception ignored) {
			}
		}
		for (EditBox box : oldBoxes) {
			try {
				this.removeWidget(box);
			} catch (Exception ignored) {
			}
		}
		try {
			this.clearWidgets();
		} catch (Exception ignored) {
		}
	}

	private void switchTab(Tab next) {
		if (tab != next) {
			tab = next;
			scrollFirst = 0;
			buildTab();
			layoutNow();
		}
	}

	private void buildTab() {
		clearTab();
		// Sidebar navigation (rebuilt per tab so selection highlights update).
		for (Tab value : Tab.values()) {
			track(new NavButton(value));
		}

		switch (tab) {
			case HOME:
				buildHome();
				break;
			case MODULES:
				buildModules();
				break;
			case SETTINGS:
				buildSettings();
				break;
			case PROFILES:
				buildProfiles();
				break;
			case THEMES:
				buildThemes();
				break;
			case ABOUT:
				break;
		}
	}

	private void layoutNow() {
		if (this.width <= 0 || this.height <= 0) {
			return;
		}
		try {
			relayoutNav();
			switch (tab) {
				case HOME:
					layoutHome();
					break;
				case MODULES:
					layoutModules();
					break;
				case SETTINGS:
					layoutSettings();
					break;
				case PROFILES:
					layoutProfiles();
					break;
				case THEMES:
					layoutThemes();
					break;
				case ABOUT:
					break;
			}
		} catch (Exception e) {
			System.out.println("[Celestial] menu layout failed: " + e);
		}
	}

	private void relayoutNav() {
		double s = scale();
		int x = winX();
		int y = winY();
		int sb = (int) (176 * s);
		int i = 0;
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof NavButton nav) {
				int by = y + (int) (64 * s) + i * (int) (34 * s);
				nav.setX(x + (int) (12 * s));
				nav.setY(by);
				nav.setSize(sb - (int) (24 * s), (int) (28 * s));
				i++;
			}
		}
	}

	private void buildHome() {
		String[] quickIds = {"fps", "ping", "cps", "fullbright"};
		for (String id : quickIds) {
			Module module = ModuleManager.byId(id);
			if (module != null) {
				track(new QuickPill(module));
			}
		}
	}

	private void layoutHome() {
		double s = scale();
		int cx = winX() + (int) (176 * s);
		int cw = winW() - (int) (176 * s);
		int y = winY() + (int) (168 * s);
		int gap = (int) (8 * s);
		int w = (cw - (int) (40 * s) - gap * 3) / 4;
		int j = 0;
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof QuickPill) {
				widget.setX(cx + (int) (20 * s) + j * (w + gap));
				widget.setY(y);
				widget.setSize(w, (int) (46 * s));
				j++;
			}
		}
	}

	private void buildModules() {
		EditBox box = new EditBox(fontOrMinecraft(), 0, 0, 100, 20, Component.literal("Search"));
		box.setHint(Component.literal("Search modules..."));
		box.setMaxLength(32);
		box.setTextColor(0xFFFFFFFF);
		if (!search.isEmpty()) {
			box.setValue(search);
		}
		box.setResponder(value -> {
			search = String.valueOf(value);
			scrollFirst = 0;
			rebuildModuleRows();
			layoutNow();
		});
		searchBox = box;
		trackBox(box);

		for (Category category : Category.values()) {
			track(new CategoryButton(category));
		}
		track(new AllButton());
		track(new ScrollButton(true));
		track(new ScrollButton(false));
		rebuildModuleRows();
	}

	private void rebuildModuleRows() {
		for (ModuleRow row : new ArrayList<>(moduleRows)) {
			try {
				this.removeWidget(row);
			} catch (Exception ignored) {
			}
			ui.remove(row);
		}
		moduleRows.clear();
		String query = search.trim().toLowerCase();
		for (Module module : ModuleManager.getModules()) {
			if (filter != null && module.getCategory() != filter) {
				continue;
			}
			if (!query.isEmpty() && !module.getName().toLowerCase().contains(query)
				&& !module.getDescription().toLowerCase().contains(query)) {
				continue;
			}
			ModuleRow row = new ModuleRow(module);
			moduleRows.add(row);
			ui.add(row);
			try {
				this.addRenderableWidget(row);
			} catch (Exception e) {
				System.out.println("[Celestial] cannot track module row: " + e);
			}
		}
		scrollFirst = Math.max(0, Math.min(scrollFirst, Math.max(0, moduleRows.size() - 1)));
	}

	private void layoutModules() {
		double s = scale();
		int cx = winX() + (int) (176 * s);
		int cw = winW() - (int) (176 * s);
		int px = cx + (int) (20 * s);
		int pw = cw - (int) (40 * s);
		if (searchBox != null) {
			searchBox.setX(px);
			searchBox.setY(winY() + (int) (58 * s));
			searchBox.setWidth(pw);
			searchBox.setHeight((int) (22 * s));
		}
		int bx = px;
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof CategoryButton || widget instanceof AllButton) {
				widget.setX(bx);
				widget.setY(winY() + (int) (88 * s));
				widget.setSize((int) (86 * s), (int) (22 * s));
				bx += (int) (92 * s);
			} else if (widget instanceof ScrollButton scrollBtn) {
				widget.setX(px + pw - (scrollBtn.up ? (int) (48 * s) : (int) (24 * s)));
				widget.setY(winY() + (int) (88 * s));
				widget.setSize((int) (20 * s), (int) (22 * s));
			}
		}
		int rowH = (int) (34 * s);
		int step = (int) (38 * s);
		int top = winY() + (int) (118 * s);
		int bottom = winY() + winH() - (int) (24 * s);
		int visible = Math.max(1, (bottom - top) / Math.max(1, step));
		scrollMax = Math.max(0, moduleRows.size() - visible);
		scrollFirst = Math.max(0, Math.min(scrollFirst, scrollMax));
		for (int i = 0; i < moduleRows.size(); i++) {
			ModuleRow row = moduleRows.get(i);
			int ry = top + (i - scrollFirst) * step;
			if (i < scrollFirst || ry + rowH > bottom + step / 2 || ry > bottom) {
				row.setX(-10000);
				row.setY(-10000);
				row.setSize(pw, rowH);
			} else {
				row.setX(px);
				row.setY(ry);
				row.setSize(pw, rowH);
			}
		}
	}

	private void buildSettings() {
		Settings settings = Celestial.settings;
		track(new StepperRow("GUI Scale", () -> fmtScale(settings.guiScale), () -> cycleScale(-1), () -> cycleScale(1)));
		track(new StepperRow("Panel Opacity", () -> Math.round(settings.opacity * 100) + "%",
			() -> {
				settings.opacity = round2(Math.max(0.5, settings.opacity - 0.05));
				Store.save(settings);
			},
			() -> {
				settings.opacity = round2(Math.min(1.0, settings.opacity + 0.05));
				Store.save(settings);
			}));
		track(new StepperRow("Blur Intensity", () -> String.valueOf(settings.blurLayers),
			() -> {
				settings.blurLayers = Math.max(0, settings.blurLayers - 1);
				Store.save(settings);
			},
			() -> {
				settings.blurLayers = Math.min(3, settings.blurLayers + 1);
				Store.save(settings);
			}));
		track(new ToggleRow("Animations", () -> settings.animations, value -> {
			settings.animations = value;
			Store.save(settings);
		}));
		// HUD counters are module-driven: toggling here flips the module,
		// which syncs the legacy show flags and the profile via onModulesChanged.
		track(new ToggleRow("Show FPS", () -> isOn("fps"), value -> setMod("fps", value)));
		track(new ToggleRow("Show Ping", () -> isOn("ping"), value -> setMod("ping", value)));
		track(new ToggleRow("Show Coords", () -> isOn("coords"), value -> setMod("coords", value)));
	}

	private void layoutSettings() {
		double s = scale();
		int cx = winX() + (int) (176 * s);
		int pw = winW() - (int) (176 * s) - (int) (40 * s);
		int px = cx + (int) (20 * s);
		int ry = winY() + (int) (64 * s);
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof StepperRow || widget instanceof ToggleRow) {
				widget.setX(px);
				widget.setY(ry);
				widget.setSize(pw, (int) (32 * s));
				ry += (int) (38 * s);
			}
		}
	}

	private void buildProfiles() {
		EditBox box = new EditBox(fontOrMinecraft(), 0, 0, 100, 20, Component.literal("Name"));
		box.setHint(Component.literal("New profile name..."));
		box.setMaxLength(24);
		box.setTextColor(0xFFFFFFFF);
		profileBox = box;
		trackBox(box);
		track(new CreateButton(() -> {
			if (profileBox != null && !profileBox.getValue().trim().isEmpty()) {
				ProfileManager.create(profileBox.getValue());
				profileBox.setValue("");
				refreshProfileRows();
				layoutNow();
			}
		}));
		EditBox rename = new EditBox(fontOrMinecraft(), 0, 0, 100, 20, Component.literal("Rename"));
		rename.setHint(Component.literal("Rename selected..."));
		rename.setMaxLength(24);
		rename.setTextColor(0xFFFFFFFF);
		renameBox = rename;
		trackBox(rename);
		track(new RenameButton(() -> {
			if (selectedProfile != null && renameBox != null && !renameBox.getValue().trim().isEmpty()) {
				ProfileManager.rename(selectedProfile, renameBox.getValue(), Celestial.settings);
				selectedProfile = Celestial.settings.activeProfile;
				renameBox.setValue("");
				refreshProfileRows();
				layoutNow();
			}
		}));
		refreshProfileRows();
	}

	private void layoutProfiles() {
		double s = scale();
		int cx = winX() + (int) (176 * s);
		int pw = winW() - (int) (176 * s) - (int) (40 * s);
		int px = cx + (int) (20 * s);
		int top = winY() + (int) (58 * s);
		if (profileBox != null) {
			profileBox.setX(px);
			profileBox.setY(top);
			profileBox.setWidth(pw - (int) (100 * s));
			profileBox.setHeight((int) (22 * s));
		}
		if (renameBox != null) {
			renameBox.setX(px);
			renameBox.setY(top + (int) (30 * s));
			renameBox.setWidth(pw - (int) (100 * s));
			renameBox.setHeight((int) (22 * s));
		}
		boolean first = true;
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof CreateButton || widget instanceof RenameButton) {
				widget.setX(px + pw - (int) (94 * s));
				widget.setY(top + (first ? 0 : (int) (30 * s)));
				widget.setSize((int) (94 * s), (int) (22 * s));
				first = false;
			}
		}
		java.util.Map<String, Integer> rowY = new java.util.HashMap<>();
		int ry = top + (int) (64 * s);
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof ProfileRow row) {
				row.setX(px);
				row.setY(ry);
				row.setSize(pw, (int) (30 * s));
				rowY.put(row.name, ry);
				ry += (int) (34 * s);
			}
		}
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof ActionButton button && button.tag != null) {
				Integer y = rowY.get(button.tag.substring(button.tag.indexOf(':') + 1));
				if (y == null) {
					continue;
				}
				if (button.tag.startsWith("del:")) {
					button.setX(px + pw - (int) (48 * s));
					button.setY(y + (int) (4 * s));
					button.setSize((int) (44 * s), (int) (22 * s));
				} else if (button.tag.startsWith("load:")) {
					button.setX(px + pw - (int) (104 * s));
					button.setY(y + (int) (4 * s));
					button.setSize((int) (52 * s), (int) (22 * s));
				}
			}
		}
	}

	private void refreshProfileRows() {
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			boolean remove = widget instanceof ProfileRow;
			if (!remove && widget instanceof ActionButton button && button.tag != null) {
				remove = button.tag.startsWith("load:") || button.tag.startsWith("del:");
			}
			if (remove) {
				try {
					this.removeWidget(widget);
				} catch (Exception ignored) {
				}
				ui.remove(widget);
			}
		}
		for (String name : ProfileManager.list()) {
			ProfileRow row = new ProfileRow(name);
			ui.add(row);
			try {
				this.addRenderableWidget(row);
			} catch (Exception e) {
				System.out.println("[Celestial] cannot track profile row: " + e);
			}
			ActionButton load = new ActionButton("Load", false, false, () -> {
				Celestial.setQuiet(true);
				try {
					ProfileManager.load(name, Celestial.settings);
				} finally {
					Celestial.setQuiet(false);
				}
				selectedProfile = name;
			});
			load.tag = "load:" + name;
			ui.add(load);
			try {
				this.addRenderableWidget(load);
			} catch (Exception e) {
				System.out.println("[Celestial] cannot track profile button: " + e);
			}
			if (!name.equalsIgnoreCase("Default")) {
				ActionButton delete = new ActionButton("Del", true, false, () -> {
					ProfileManager.delete(name, Celestial.settings);
					if (selectedProfile != null && selectedProfile.equalsIgnoreCase(name)) {
						selectedProfile = Celestial.settings.activeProfile;
					}
					refreshProfileRows();
					layoutNow();
				});
				delete.tag = "del:" + name;
				ui.add(delete);
				try {
					this.addRenderableWidget(delete);
				} catch (Exception e) {
					System.out.println("[Celestial] cannot track profile button: " + e);
				}
			}
		}
	}

	private void buildThemes() {
		for (Theme theme : Theme.PRESETS) {
			track(new ThemeButton(theme));
		}
		int[] swatches = {0xFF22D3EE, 0xFFFF9E4D, 0xFF9DFF57, 0xFFFFFFFF, 0xFFFFD166, 0xFF2DD4BF, 0xFFB388FF, 0xFFFF5C8A};
		for (int color : swatches) {
			track(new SwatchButton(color));
		}
		EditBox box = new EditBox(fontOrMinecraft(), 0, 0, 100, 20, Component.literal("Hex"));
		box.setHint(Component.literal("#RRGGBB..."));
		box.setMaxLength(9);
		box.setTextColor(0xFFFFFFFF);
		hexBox = box;
		trackBox(box);
		track(new ApplyButton(() -> {
			if (hexBox != null) {
				int color = parseHex(hexBox.getValue());
				if (color != -1) {
					Celestial.settings.theme = "custom";
					Celestial.settings.customColor = color;
					Store.applyTheme(Celestial.settings);
				}
			}
		}));
	}

	private void layoutThemes() {
		double s = scale();
		int cx = winX() + (int) (176 * s);
		int pw = winW() - (int) (176 * s) - (int) (40 * s);
		int px = cx + (int) (20 * s);
		int ry = winY() + (int) (64 * s);
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof ThemeButton) {
				widget.setX(px);
				widget.setY(ry);
				widget.setSize(pw, (int) (30 * s));
				ry += (int) (34 * s);
			}
		}
		ry += (int) (6 * s);
		int sx = px;
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof SwatchButton) {
				widget.setX(sx);
				widget.setY(ry);
				widget.setSize((int) (28 * s), (int) (28 * s));
				sx += (int) (34 * s);
			}
		}
		ry += (int) (36 * s);
		if (hexBox != null) {
			hexBox.setX(px);
			hexBox.setY(ry);
			hexBox.setWidth(pw - (int) (100 * s));
			hexBox.setHeight((int) (22 * s));
		}
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			if (widget instanceof ApplyButton) {
				widget.setX(px + pw - (int) (94 * s));
				widget.setY(ry);
				widget.setSize((int) (94 * s), (int) (22 * s));
			}
		}
	}

	// ---------------- actions ----------------

	private String fmtScale(double value) {
		return ((int) Math.round(value * 100)) + "%";
	}

	private void cycleScale(int direction) {
		double[] steps = {0.75, 1.0, 1.25, 1.5};
		double current = Celestial.settings.guiScale;
		int index = 0;
		for (int i = 0; i < steps.length; i++) {
			if (Math.abs(steps[i] - current) < 0.001) {
				index = i;
				break;
			}
		}
		index = (index + direction + steps.length) % steps.length;
		Celestial.settings.guiScale = steps[index];
		Store.save(Celestial.settings);
	}

	private static boolean isOn(String id) {
		try {
			Module module = ModuleManager.byId(id);
			return module != null && module.isEnabled();
		} catch (Exception e) {
			return false;
		}
	}

	private static void setMod(String id, boolean value) {
		try {
			Module module = ModuleManager.byId(id);
			if (module != null) {
				module.setEnabled(value);
			}
		} catch (Exception e) {
			System.out.println("[Celestial] cannot toggle " + id + ": " + e);
		}
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	private static int parseHex(String text) {
		if (text == null) {
			return -1;
		}
		String clean = text.trim().replace("#", "").replace("0x", "").replace("0X", "");
		if (clean.length() != 6 && clean.length() != 8) {
			return -1;
		}
		try {
			long value = Long.parseLong(clean, 16);
			return clean.length() == 6 ? (int) (0xFF000000L | value) : (int) value;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	// ---------------- frame ----------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		layoutNow();
		drawBackdrop(graphics);
		drawWindow(graphics);
		drawContent(graphics);
		for (Object child : new ArrayList<>(this.children())) {
			if (child instanceof Renderable renderable) {
				try {
					renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
				} catch (Exception ignored) {
				}
			}
		}
	}

	private void drawBackdrop(GuiGraphicsExtractor graphics) {
		float a = CelestialWidget.anim() * (float) Celestial.settings.opacity;
		graphics.fill(0, 0, this.width, this.height, CelestialWidget.withAlpha(0xAA050508, a * 0.55f));
	}

	private void drawWindow(GuiGraphicsExtractor graphics) {
		int x = winX();
		int y = winY();
		int w = winW();
		int h = winH();
		float a = CelestialWidget.anim() * (float) Celestial.settings.opacity;
		// Soft blur illusion: layered translucent fills.
		for (int i = Celestial.settings.blurLayers; i >= 1; i--) {
			int pad = i * 5;
			graphics.fill(x - pad, y - pad, x + w + pad, y + h + pad,
				CelestialWidget.withAlpha(0x18000000 | (Theme.current.accent & 0x00FFFFFF), a * 0.5f));
		}
		graphics.fill(x, y, x + w, y + h, CelestialWidget.withAlpha(0xE8101014, a));
		graphics.outline(x, y, x + w, y + h, CelestialWidget.withAlpha(accentBorder(), a));
		graphics.fill(x, y, x + w, y + 1, CelestialWidget.withAlpha(Theme.current.accent, a * 0.8f));

		// Sidebar divider + logo.
		double s = scale();
		int sb = (int) (176 * s);
		graphics.fill(x + sb, y + (int) (10 * s), x + sb + 1, y + h - (int) (10 * s),
			CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x33000000, a));
		long pulse = System.currentTimeMillis() % 2000;
		float glow = 0.55f + 0.35f * (float) Math.sin(pulse / 2000.0 * Math.PI * 2.0);
		if (!Celestial.settings.animations) {
			glow = 0.8f;
		}
		int diamond = (int) (10 * s);
		int dx = x + (int) (24 * s);
		int dy = y + (int) (26 * s);
		int accent = Theme.current.accent;
		graphics.fill(dx - diamond / 2, dy - 1, dx + diamond / 2, dy + 1, CelestialWidget.withAlpha(accent, a * glow));
		graphics.fill(dx - 1, dy - diamond / 2, dx + 1, dy + diamond / 2, CelestialWidget.withAlpha(accent, a * glow));
		graphics.text(this.font, "CELESTIAL", x + (int) (40 * s), y + (int) (20 * s),
			CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
		graphics.text(this.font, Celestial.VERSION, x + (int) (40 * s), y + (int) (32 * s),
			CelestialWidget.withAlpha(accent, a), true);

		// Content header.
		int cx = x + sb;
		graphics.text(this.font, tab.label.toUpperCase(), cx + (int) (20 * s), y + (int) (20 * s),
			CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
		graphics.fill(cx + (int) (20 * s), y + (int) (36 * s), cx + (int) (70 * s), y + (int) (37 * s),
			CelestialWidget.withAlpha(accent, a * 0.9f));
		graphics.text(this.font, "RIGHT SHIFT to close", x + w - this.font.width("RIGHT SHIFT to close") - (int) (14 * s),
			y + h - (int) (20 * s), CelestialWidget.withAlpha(0xFF888888, a), true);
	}

	private int accentBorder() {
		return Theme.current.accent & 0x00FFFFFF | 0x66000000;
	}

	private void drawContent(GuiGraphicsExtractor graphics) {
		double s = scale();
		int cx = winX() + (int) (176 * s);
		int y = winY();
		float a = CelestialWidget.anim();
		switch (tab) {
			case HOME: {
				int bx = cx + (int) (20 * s);
				int bw = winW() - (int) (176 * s) - (int) (40 * s);
				int by = y + (int) (58 * s);
				int bh = (int) (92 * s);
				graphics.fill(bx, by, bx + bw, by + bh, CelestialWidget.withAlpha(0xE016161C, a));
				graphics.outline(bx, by, bx + bw, by + bh,
					CelestialWidget.withAlpha(accentBorder(), a));
				graphics.fill(bx, by, bx + bw, by + 2, CelestialWidget.withAlpha(Theme.current.accent, a * 0.7f));
				graphics.text(this.font, "Welcome to Celestial", bx + (int) (16 * s), by + (int) (16 * s),
					CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				graphics.text(this.font, "Enhance your Minecraft experience.", bx + (int) (16 * s),
					by + (int) (34 * s), CelestialWidget.withAlpha(Theme.current.accent, a), true);
				graphics.text(this.font, "Quick Toggles", bx, by + bh + (int) (10 * s),
					CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				int iy = by + bh + (int) (76 * s);
				graphics.text(this.font, "Client Information", bx, iy, CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				graphics.text(this.font, "Version: " + Celestial.VERSION, bx, iy + (int) (16 * s),
					CelestialWidget.withAlpha(0xFFCCCCCC, a), true);
				graphics.text(this.font, "Build Date: " + Celestial.BUILD_DATE, bx, iy + (int) (30 * s),
					CelestialWidget.withAlpha(0xFFCCCCCC, a), true);
				graphics.text(this.font, "Developer: " + Celestial.DEVELOPER, bx, iy + (int) (44 * s),
					CelestialWidget.withAlpha(Theme.current.accent, a), true);
				break;
			}
			case ABOUT: {
				int tx = cx + (int) (20 * s);
				int ty = y + (int) (64 * s);
				String[] lines = {
					"Celestial Client " + Celestial.VERSION,
					"Build " + Celestial.BUILD_DATE + " by " + Celestial.DEVELOPER,
					"",
				};
				for (String line : lines) {
					graphics.text(this.font, line, tx, ty, CelestialWidget.withAlpha(0xFFCCCCCC, a), true);
					ty += (int) (16 * s);
				}
				String[] features = {
					"Sixteen utility modules across HUD, Visual,",
					"Gameplay and Interface.",
					"Live themes, profiles and a customizable utility HUD.",
					"Open or close the interface with Right Shift."
				};
				for (String line : features) {
					graphics.text(this.font, line, tx, ty, CelestialWidget.withAlpha(0xFF999999, a), true);
					ty += (int) (16 * s);
				}
				break;
			}
			case SETTINGS: {
				int tx = cx + (int) (20 * s);
				int ty = winY() + winH() - (int) (52 * s);
				String extra = keyName();
				graphics.text(this.font, "GUI Key: " + extra, tx, ty,
					CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				graphics.text(this.font, "Rebind the key in Options > Controls > Celestial", tx,
					ty + (int) (14 * s), CelestialWidget.withAlpha(0xFF777777, a), true);
				break;
			}
			case THEMES: {
				int tx = cx + (int) (20 * s);
				int ty = y + (int) (64 * s);
				graphics.text(this.font, "Preset", tx, ty - (int) (16 * s),
					CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				break;
			}
			default:
				break;
		}
	}

	private String keyName() {
		String extra = com.celestial.client.CelestialClient.extraKeyName();
		return extra == null ? "" : extra;
	}

	// ---------------- widgets ----------------

	private static class NavButton extends CelestialWidget {
		private final Tab target;

		NavButton(Tab target) {
			super(0, 0, 10, 10, Component.literal(target.label));
			this.target = target;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			CelestialGui gui = current;
			boolean selected = gui != null && gui.tab == target;
			float a = anim();
			if (selected) {
				g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
					withAlpha(accent() & 0x00FFFFFF | 0x33000000, a));
				g.fill(getX(), getY(), getX() + 3, getY() + getHeight(), withAlpha(accent(), a));
			} else if (isHovered()) {
				g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
					withAlpha(0x22FFFFFF, a));
			}
			text(g, target.label, getX() + 12, getY() + 9,
				withAlpha(selected ? accent() : 0xFFBBBBBB, a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.switchTab(target);
			}
		}
	}

	private static class ActionButton extends CelestialWidget {
		final Runnable action;
		final boolean danger;
		final boolean primary;
		String tag;

		ActionButton(String label, boolean danger, boolean primary, Runnable action) {
			super(0, 0, 10, 10, Component.literal(label));
			this.action = action;
			this.danger = danger;
			this.primary = primary;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			float a = anim();
			int base = danger ? 0xE01A1214 : 0xE0161620;
			g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), withAlpha(base, a));
			int border = danger ? 0xFFAA4444 : (isHovered() || primary ? accent() : (accent() & 0x00FFFFFF | 0x55000000));
			g.outline(getX(), getY(), getX() + getWidth(), getY() + getHeight(), withAlpha(border, a));
			centeredText(g, getMessage().getString(), getX() + getWidth() / 2, getY() + 7,
				withAlpha(danger ? 0xFFFF8888 : 0xFFFFFFFF, a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			action.run();
		}
	}

	private static class CreateButton extends ActionButton {
		CreateButton(Runnable action) {
			super("Create", false, true, action);
		}
	}

	private static class RenameButton extends ActionButton {
		RenameButton(Runnable action) {
			super("Rename", false, true, action);
		}
	}

	private static class ApplyButton extends ActionButton {
		ApplyButton(Runnable action) {
			super("Apply", false, true, action);
		}
	}

	private static class ScrollButton extends CelestialWidget {
		final boolean up;

		ScrollButton(boolean up) {
			super(0, 0, 10, 10, Component.literal(up ? "/\\" : "\\/"));
			this.up = up;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			float a = anim();
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered());
			centeredText(g, up ? "^" : "v", getX() + getWidth() / 2, getY() + 7,
				withAlpha(0xFFFFFFFF, a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				if (up) {
					current.scrollFirst = Math.max(0, current.scrollFirst - 1);
				} else {
					current.scrollFirst = Math.min(current.scrollMax, current.scrollFirst + 1);
				}
				current.layoutNow();
			}
		}
	}

	private static class CategoryButton extends CelestialWidget {
		private final Category category;

		CategoryButton(Category category) {
			super(0, 0, 10, 10, Component.literal(category.getLabel()));
			this.category = category;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			CelestialGui gui = current;
			boolean selected = gui != null && gui.filter == category;
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered() || selected);
			if (selected) {
				g.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight(),
					withAlpha(accent(), anim()));
			}
			centeredText(g, category.getLabel(), getX() + getWidth() / 2, getY() + 7,
				withAlpha(selected ? accent() : 0xFFBBBBBB, anim()));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.filter = category;
				current.scrollFirst = 0;
				current.rebuildModuleRows();
				current.layoutNow();
			}
		}
	}

	private static class AllButton extends CelestialWidget {
		AllButton() {
			super(0, 0, 10, 10, Component.literal("All"));
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			CelestialGui gui = current;
			boolean selected = gui == null || gui.filter == null;
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered() || selected);
			if (selected) {
				g.fill(getX(), getY() + getHeight() - 2, getX() + getWidth(), getY() + getHeight(),
					withAlpha(accent(), anim()));
			}
			centeredText(g, "All", getX() + getWidth() / 2, getY() + 7,
				withAlpha(selected ? accent() : 0xFFBBBBBB, anim()));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.filter = null;
				current.scrollFirst = 0;
				current.rebuildModuleRows();
				current.layoutNow();
			}
		}
	}

	private static class QuickPill extends CelestialWidget {
		private final Module module;
		private boolean lastState;
		private long lastChange;

		QuickPill(Module module) {
			super(0, 0, 10, 10, Component.literal(module.getName()));
			this.module = module;
			this.lastState = module.isEnabled();
			this.lastChange = System.currentTimeMillis();
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean on = module.isEnabled();
			if (on != lastState) {
				lastState = on;
				lastChange = System.currentTimeMillis();
			}
			float a = anim();
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered());
			text(g, module.getName(), getX() + 8, getY() + 6, withAlpha(0xFFFFFFFF, a));
			drawPill(g, getX() + getWidth() - 46, getY() + 8, 38, 14, on, animT());
		}

		private float animT() {
			if (!Celestial.settings.animations) {
				return 1.0f;
			}
			return Math.max(0.0f, Math.min(1.0f, (System.currentTimeMillis() - lastChange) / 120.0f));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			module.toggle();
		}
	}

	private static class ModuleRow extends CelestialWidget {
		private final Module module;
		private boolean lastState;
		private long lastChange;

		ModuleRow(Module module) {
			super(0, 0, 10, 10, Component.literal(module.getName()));
			this.module = module;
			this.lastState = module.isEnabled();
			this.lastChange = System.currentTimeMillis();
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean on = module.isEnabled();
			if (on != lastState) {
				lastState = on;
				lastChange = System.currentTimeMillis();
			}
			float a = anim();
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered());
			int tx = getX() + 10;
			text(g, module.getName(), tx, getY() + 5, withAlpha(on ? accent() : 0xFFFFFFFF, a));
			String desc = module.getDescription() + "  [" + keyLabel(module) + "]";
			text(g, desc, tx, getY() + 18, withAlpha(0xFF888888, a));
			float t = Celestial.settings.animations
				? Math.max(0.0f, Math.min(1.0f, (System.currentTimeMillis() - lastChange) / 120.0f))
				: 1.0f;
			drawPill(g, getX() + getWidth() - 52, getY() + 10, 42, 14, on, t);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			module.toggle();
		}

		private static String keyLabel(Module module) {
			try {
				return module.getKey().getTranslatedKeyMessage().getString();
			} catch (Exception e) {
				return "?";
			}
		}
	}

	private static class ToggleRow extends CelestialWidget {
		interface Getter {
			boolean get();
		}

		interface Setter {
			void set(boolean value);
		}

		private final String label;
		private final Getter getter;
		private final Setter setter;
		private boolean lastState;
		private long lastChange;

		ToggleRow(String label, Getter getter, Setter setter) {
			super(0, 0, 10, 10, Component.literal(label));
			this.label = label;
			this.getter = getter;
			this.setter = setter;
			this.lastState = getter.get();
			this.lastChange = System.currentTimeMillis();
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean on = getter.get();
			if (on != lastState) {
				lastState = on;
				lastChange = System.currentTimeMillis();
			}
			float a = anim();
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered());
			text(g, label, getX() + 10, getY() + 11, withAlpha(0xFFFFFFFF, a));
			float t = Celestial.settings.animations
				? Math.max(0.0f, Math.min(1.0f, (System.currentTimeMillis() - lastChange) / 120.0f))
				: 1.0f;
			drawPill(g, getX() + getWidth() - 52, getY() + 9, 42, 14, on, t);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			setter.set(!getter.get());
		}
	}

	private static class StepperRow extends CelestialWidget {
		interface Text {
			String get();
		}

		private final String label;
		private final Text text;
		private final Runnable minus;
		private final Runnable plus;

		StepperRow(String label, Text text, Runnable minus, Runnable plus) {
			super(0, 0, 10, 10, Component.literal(label));
			this.label = label;
			this.text = text;
			this.minus = minus;
			this.plus = plus;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			float a = anim();
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered());
			text(g, label, getX() + 10, getY() + 11, withAlpha(0xFFFFFFFF, a));
			int right = getX() + getWidth() - 8;
			int plusX = right - 30;
			int minusX = right - 104;
			g.fill(plusX, getY() + 6, right, getY() + getHeight() - 6,
				withAlpha(accent() & 0x00FFFFFF | 0x33000000, a));
			g.fill(minusX, getY() + 6, minusX + 30, getY() + getHeight() - 6,
				withAlpha(0x22FFFFFF, a));
			text(g, "+", plusX + 12, getY() + 10, withAlpha(0xFFFFFFFF, a));
			text(g, "-", minusX + 12, getY() + 10, withAlpha(0xFFFFFFFF, a));
			centeredText(g, text.get(), right - 52, getY() + 11, withAlpha(accent(), a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			int right = getX() + getWidth() - 8;
			if (event.x() >= right - 30) {
				plus.run();
			} else if (event.x() >= right - 104) {
				minus.run();
			}
		}
	}

	private static class ThemeButton extends CelestialWidget {
		private final Theme theme;

		ThemeButton(Theme theme) {
			super(0, 0, 10, 10, Component.literal(theme.label));
			this.theme = theme;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			CelestialGui gui = current;
			boolean selected = gui != null && Celestial.settings.theme.equalsIgnoreCase(theme.id)
				&& Theme.current == theme;
			float a = anim();
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered() || selected);
			int preview = getX() + 10;
			g.fill(preview, getY() + 8, preview + 14, getY() + 22, withAlpha(theme.accent | 0xFF000000, a));
			g.outline(preview, getY() + 8, preview + 14, getY() + 22, withAlpha(0x88FFFFFF, a));
			text(g, theme.label, preview + 22, getY() + 11, withAlpha(selected ? accent() : 0xFFFFFFFF, a));
			if (selected) {
				text(g, "Active", getX() + getWidth() - 50, getY() + 11, withAlpha(accent(), a));
			}
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			Celestial.settings.theme = theme.id;
			Store.applyTheme(Celestial.settings);
		}
	}

	private static class SwatchButton extends CelestialWidget {
		private final int color;

		SwatchButton(int color) {
			super(0, 0, 10, 10, Component.literal("color"));
			this.color = color;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			float a = anim();
			g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
				withAlpha(color | 0xFF000000, a));
			g.outline(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
				withAlpha(isHovered() ? 0xFFFFFFFF : (accent() & 0x00FFFFFF | 0x55000000), a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			Celestial.settings.theme = "custom";
			Celestial.settings.customColor = color | 0xFF000000;
			Store.applyTheme(Celestial.settings);
		}
	}

	private class ProfileRow extends CelestialWidget {
		private final String name;

		ProfileRow(String name) {
			super(0, 0, 10, 10, Component.literal(name));
			this.name = name;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			boolean selected = name.equals(selectedProfile);
			boolean active = name.equals(Celestial.settings.activeProfile);
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered() || selected);
			if (selected) {
				g.fill(getX(), getY(), getX() + 3, getY() + getHeight(), withAlpha(accent(), anim()));
			}
			String label = name + (active ? "  \u25CF" : "");
			text(g, label, getX() + 10, getY() + 10, withAlpha(active ? accent() : 0xFFFFFFFF, anim()));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			selectedProfile = name;
		}
	}

	/** Shared toggle-pill drawing. */
	private static void drawPill(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on, float t) {
		float a = CelestialWidget.anim();
		int track = on ? CelestialWidget.accent() : 0xFF3A3A40;
		g.fill(x, y, x + w, y + h, CelestialWidget.withAlpha(track & 0x00FFFFFF | (on ? 0xCC000000 : 0xFF000000), a));
		g.outline(x, y, x + w, y + h, CelestialWidget.withAlpha(on ? CelestialWidget.accent() : 0xFF55555F, a));
		int knob = h - 4;
		int travel = w - knob - 4;
		int from = on ? x + 2 : x + 2 + travel;
		int to = on ? x + 2 + travel : x + 2;
		int kx = (int) (from + (to - from) * t);
		g.fill(kx, y + 2, kx + 2 + knob, y + 2 + knob, CelestialWidget.withAlpha(0xFFFFFFFF, a));
	}
}
