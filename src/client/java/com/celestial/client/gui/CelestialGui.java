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
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Celestial in-game menu (press ESC). Glassmorphism layout:
 * left sidebar navigation, content cards on the right, FPS/Ping pill
 * top-right, hero banner on Home.
 *
 * Widgets are built in init() (NOT added()) because vanilla clears all
 * widgets during screen setup — building earlier meant everything was
 * wiped and the menu rendered empty. Layout runs every frame so tab
 * switches and window resizes reposition immediately.
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
	private static CelestialGui current;

	private Tab tab = Tab.HOME;
	private Category filter;
	private String search = "";
	private String selectedProfile;
	private long tabStamp;
	private double scroll;

	private final List<CelestialWidget> tabWidgets = new ArrayList<>();
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

	public static void open(Minecraft mc) {
		if (!open) {
			mc.setScreenAndShow(new CelestialGui());
		}
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
		tabStamp = openStamp;
		if (selectedProfile == null) {
			selectedProfile = Celestial.settings.activeProfile;
		}
	}

	@Override
	public void removed() {
		open = false;
		current = null;
		Store.save(Celestial.settings);
		ProfileManager.save(Celestial.settings.activeProfile);
	}

	@Override
	public void onClose() {
		Store.save(Celestial.settings);
		ProfileManager.save(Celestial.settings.activeProfile);
	}

	/** Vanilla calls this after width/height/font are valid. Build here. */
	@Override
	public void init() {
		buildTab();
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

	/** Scroll the module list. No @Override: keeps compiling even if the
	 *  Screen hierarchy routes scroll events differently. */
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		if (tab == Tab.MODULES) {
			int top = listTop();
			int bottom = listBottom();
			if (x >= contentX() && x <= contentX() + contentW() && y >= top && y <= bottom) {
				scroll = clampScroll(scroll - dy * 18.0);
				layoutNow();
				return true;
			}
		}
		return false;
	}

	// ---------------- layout ----------------

	private double scale() {
		return Celestial.settings.guiScale;
	}

	private int winW() {
		return (int) Math.min(780 * scale(), Math.max(200, this.width - 20));
	}

	private int winH() {
		return (int) Math.min(470 * scale(), Math.max(160, this.height - 20));
	}

	private int winX() {
		return (this.width - winW()) / 2;
	}

	private int winY() {
		return (this.height - winH()) / 2;
	}

	private int sideW() {
		return (int) (152 * scale());
	}

	private int contentX() {
		return winX() + sideW();
	}

	private int contentW() {
		return winW() - sideW();
	}

	private int listTop() {
		return winY() + (int) (118 * scale());
	}

	private int listBottom() {
		return winY() + winH() - (int) (32 * scale());
	}

	private double clampScroll(double value) {
		int visible = Math.max(1, listBottom() - listTop());
		int rowH = (int) (44 * scale());
		int total = moduleRows.size() * rowH;
		double max = Math.max(0, total - visible);
		if (value < 0) {
			return 0;
		}
		if (value > max) {
			return max;
		}
		return value;
	}

	private Font fontOrMinecraft() {
		if (this.font != null) {
			return this.font;
		}
		return Minecraft.getInstance().font;
	}

	private void track(CelestialWidget widget) {
		tabWidgets.add(widget);
		this.addRenderableWidget(widget);
	}

	private void trackBox(EditBox box) {
		this.addRenderableWidget(box);
	}

	private void clearTab() {
		this.clearWidgets();
		tabWidgets.clear();
		moduleRows.clear();
		searchBox = null;
		profileBox = null;
		renameBox = null;
		hexBox = null;
	}

	private void switchTab(Tab next) {
		if (tab != next) {
			tab = next;
			tabStamp = System.currentTimeMillis();
			scroll = 0;
			buildTab();
			layoutNow();
		}
	}

	private void buildTab() {
		clearTab();
		for (Tab value : Tab.values()) {
			track(new NavButton(value));
		}
		track(new DisconnectButton());

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
		double s = scale();
		int x = winX();
		int y = winY();
		int sb = sideW();
		// Sidebar nav.
		int i = 0;
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof NavButton) {
				int by = y + (int) (62 * s) + i * (int) (32 * s);
				widget.setX(x + (int) (10 * s));
				widget.setY(by);
				widget.setSize(sb - (int) (20 * s), (int) (26 * s));
				i++;
			}
		}
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof DisconnectButton) {
				widget.setX(x + (int) (10 * s));
				widget.setY(y + winH() - (int) (38 * s));
				widget.setSize(sb - (int) (20 * s), (int) (26 * s));
			}
		}
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
	}

	// ---------------- HOME ----------------

	private void buildHome() {
		String[] quickIds = {"fullbright", "esp", "nofall", "antiafk", "fly"};
		for (String id : quickIds) {
			Module module = ModuleManager.byId(id);
			if (module != null) {
				track(new HomeToggleRow(module));
			}
		}
	}

	private void layoutHome() {
		double s = scale();
		int cx = contentX();
		int cw = contentW();
		int pad = (int) (18 * s);
		int[] geom = homeGeom();
		int cardY = geom[4];
		int cardH = geom[5];
		int leftX = geom[6];
		int leftW = geom[7];
		int gap = (int) (10 * s);
		int rows = 0;
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof HomeToggleRow) {
				rows++;
			}
		}
		if (rows == 0) {
			return;
		}
		int rowH = Math.max((int) (24 * s), (cardH - (int) (34 * s)) / Math.max(1, rows));
		int ry = cardY + (int) (28 * s);
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof HomeToggleRow) {
				widget.setX(leftX + (int) (6 * s));
				widget.setY(ry);
				widget.setSize(leftW - (int) (12 * s), rowH);
				ry += rowH + (int) (2 * s);
			}
		}
	}

	/**
	 * Shared Home geometry: hx, hw, hy, hh, cardY, cardH, leftX, leftW, rightX.
	 * Shrinks the hero banner on short windows so cards always fit.
	 */
	private int[] homeGeom() {
		double s = scale();
		int cx = contentX();
		int cw = contentW();
		int pad = (int) (18 * s);
		int avail = winY() + winH() - (int) (30 * s);
		int hy = winY() + (int) (52 * s);
		int hh = (int) (96 * s);
		int minCard = (int) (84 * s);
		int labelRoom = (int) (24 * s);
		if (hy + hh + labelRoom + minCard > avail) {
			hh = Math.max((int) (48 * s), avail - minCard - hy - labelRoom);
		}
		int cardY = hy + hh + labelRoom;
		int cardH = Math.max(minCard, avail - cardY);
		int gap = (int) (10 * s);
		int leftW = Math.max(40, (cw - pad * 2 - gap) / 2);
		int leftX = cx + pad;
		return new int[]{cx + pad, cw - pad * 2, hy, hh, cardY, cardH, leftX, leftW, leftX + leftW + gap};
	}

	// ---------------- MODULES ----------------

	private void buildModules() {
		Font f = fontOrMinecraft();
		EditBox box = new EditBox(f, 0, 0, 100, 20, Component.literal("Search"));
		box.setHint(Component.literal("Search modules..."));
		box.setMaxLength(32);
		box.setTextColor(0xFFFFFFFF);
		if (!search.isEmpty()) {
			box.setValue(search);
		}
		box.setResponder(value -> {
			search = String.valueOf(value);
			scroll = 0;
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
			this.removeWidget(row);
			tabWidgets.remove(row);
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
			tabWidgets.add(row);
			this.addRenderableWidget(row);
		}
		scroll = clampScroll(scroll);
	}

	private void layoutModules() {
		double s = scale();
		int cx = contentX();
		int cw = contentW();
		int px = cx + (int) (18 * s);
		int pw = cw - (int) (36 * s);
		if (searchBox != null) {
			searchBox.setX(px);
			searchBox.setY(winY() + (int) (56 * s));
			searchBox.setWidth(Math.max(40, pw - (int) (50 * s)));
			searchBox.setHeight((int) (20 * s));
		}
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof ScrollButton scroll) {
				if (scroll.up) {
					widget.setX(px + pw - (int) (48 * s));
				} else {
					widget.setX(px + pw - (int) (24 * s));
				}
				widget.setY(winY() + (int) (56 * s));
				widget.setSize((int) (20 * s), (int) (20 * s));
			}
		}
		int gap = Math.max(3, (int) (5 * s));
		int pillW = Math.max((int) (52 * s), (pw - gap * 3) / 4);
		int step = pillW + gap;
		int bx = px;
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof CategoryButton || widget instanceof AllButton) {
				widget.setX(bx);
				widget.setY(winY() + (int) (84 * s));
				widget.setSize(pillW, (int) (20 * s));
				bx += step;
			}
		}
		int rowH = (int) (44 * scale());
		int ry = listTop() - (int) scroll;
		for (ModuleRow row : moduleRows) {
			if (ry + rowH < listTop() || ry > listBottom()) {
				// Park off-screen so clipped rows never overlap the footer.
				row.setX(-10000);
				row.setY(-10000);
				row.setSize(pw, rowH);
			} else {
				row.setX(px);
				row.setY(ry);
				row.setSize(pw, rowH);
			}
			ry += rowH + (int) (6 * s);
		}
	}

	// ---------------- SETTINGS ----------------

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
		track(new StepperRow("Blur Layers", () -> String.valueOf(settings.blurLayers),
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
		track(new ToggleRow("Show FPS", () -> settings.showFps, value -> {
			settings.showFps = value;
			Store.save(settings);
		}));
		track(new ToggleRow("Show Ping", () -> settings.showPing, value -> {
			settings.showPing = value;
			Store.save(settings);
		}));
		track(new ToggleRow("Show Coords", () -> settings.showCoords, value -> {
			settings.showCoords = value;
			Store.save(settings);
		}));
	}

	private void layoutSettings() {
		double s = scale();
		int px = contentX() + (int) (18 * s);
		int pw = contentW() - (int) (36 * s);
		int ry = winY() + (int) (60 * s);
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof StepperRow || widget instanceof ToggleRow) {
				widget.setX(px);
				widget.setY(ry);
				widget.setSize(pw, (int) (30 * s));
				ry += (int) (36 * s);
			}
		}
	}

	// ---------------- PROFILES ----------------

	private void buildProfiles() {
		Font f = fontOrMinecraft();
		EditBox box = new EditBox(f, 0, 0, 100, 20, Component.literal("Name"));
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
		EditBox rename = new EditBox(f, 0, 0, 100, 20, Component.literal("Rename"));
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
		int px = contentX() + (int) (18 * s);
		int pw = contentW() - (int) (36 * s);
		int top = winY() + (int) (58 * s);
		if (profileBox != null) {
			profileBox.setX(px);
			profileBox.setY(top);
			profileBox.setWidth(pw - (int) (100 * s));
			profileBox.setHeight((int) (20 * s));
		}
		if (renameBox != null) {
			renameBox.setX(px);
			renameBox.setY(top + (int) (28 * s));
			renameBox.setWidth(pw - (int) (100 * s));
			renameBox.setHeight((int) (20 * s));
		}
		boolean first = true;
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof CreateButton || widget instanceof RenameButton) {
				widget.setX(px + pw - (int) (94 * s));
				widget.setY(top + (first ? 0 : (int) (28 * s)));
				widget.setSize((int) (94 * s), (int) (20 * s));
				first = false;
			}
		}
		java.util.Map<String, Integer> rowY = new java.util.HashMap<>();
		int ry = top + (int) (62 * s);
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof ProfileRow row) {
				row.setX(px);
				row.setY(ry);
				row.setSize(pw, (int) (28 * s));
				rowY.put(row.name, ry);
				ry += (int) (32 * s);
			}
		}
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof ActionButton button && button.tag != null) {
				Integer yy = rowY.get(button.tag.substring(button.tag.indexOf(':') + 1));
				if (yy == null) {
					continue;
				}
				if (button.tag.startsWith("del:")) {
					button.setX(px + pw - (int) (48 * s));
					button.setY(yy + (int) (4 * s));
					button.setSize((int) (44 * s), (int) (20 * s));
				} else if (button.tag.startsWith("load:")) {
					button.setX(px + pw - (int) (104 * s));
					button.setY(yy + (int) (4 * s));
					button.setSize((int) (52 * s), (int) (20 * s));
				}
			}
		}
	}

	private void refreshProfileRows() {
		for (CelestialWidget widget : new ArrayList<>(tabWidgets)) {
			boolean remove = widget instanceof ProfileRow;
			if (!remove && widget instanceof ActionButton button && button.tag != null) {
				remove = button.tag.startsWith("load:") || button.tag.startsWith("del:");
			}
			if (remove) {
				this.removeWidget(widget);
				tabWidgets.remove(widget);
			}
		}
		for (String name : ProfileManager.list()) {
			ProfileRow row = new ProfileRow(name);
			tabWidgets.add(row);
			this.addRenderableWidget(row);
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
			tabWidgets.add(load);
			this.addRenderableWidget(load);
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
				tabWidgets.add(delete);
				this.addRenderableWidget(delete);
			}
		}
	}

	// ---------------- THEMES ----------------

	private void buildThemes() {
		for (Theme theme : Theme.PRESETS) {
			track(new ThemeButton(theme));
		}
		int[] swatches = {0xFF22D3EE, 0xFFFF9E4D, 0xFF9DFF57, 0xFFFFFFFF, 0xFFFFD166, 0xFF2DD4BF, 0xFFB388FF, 0xFFFF5C8A};
		for (int color : swatches) {
			track(new SwatchButton(color));
		}
		Font f = fontOrMinecraft();
		EditBox box = new EditBox(f, 0, 0, 100, 20, Component.literal("Hex"));
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
		int px = contentX() + (int) (18 * s);
		int pw = contentW() - (int) (36 * s);
		int ry = winY() + (int) (60 * s);
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof ThemeButton) {
				widget.setX(px);
				widget.setY(ry);
				widget.setSize(pw, (int) (28 * s));
				ry += (int) (32 * s);
			}
		}
		ry += (int) (4 * s);
		int sx = px;
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof SwatchButton) {
				widget.setX(sx);
				widget.setY(ry);
				widget.setSize((int) (26 * s), (int) (26 * s));
				sx += (int) (32 * s);
			}
		}
		ry += (int) (34 * s);
		if (hexBox != null) {
			hexBox.setX(px);
			hexBox.setY(ry);
			hexBox.setWidth(pw - (int) (100 * s));
			hexBox.setHeight((int) (20 * s));
		}
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof ApplyButton) {
				widget.setX(px + pw - (int) (94 * s));
				widget.setY(ry);
				widget.setSize((int) (94 * s), (int) (20 * s));
			}
		}
	}

	// ---------------- actions ----------------

	private void disconnect() {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		Store.save(Celestial.settings);
		ProfileManager.save(Celestial.settings.activeProfile);
		if (mc.isLocalServer()) {
			mc.disconnectWithSavingScreen();
		} else {
			mc.disconnectWithProgressScreen();
		}
	}

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
		layoutNow();
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
		drawHudPill(graphics);
		drawWindow(graphics);
		drawContent(graphics);
		for (Object child : this.children()) {
			if (child instanceof Renderable renderable) {
				renderable.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}
		}
	}

	private void drawBackdrop(GuiGraphicsExtractor graphics) {
		float a = CelestialWidget.anim() * (float) Celestial.settings.opacity;
		int dim = CelestialWidget.withAlpha(0xCC050508, Math.min(1.0f, a * 0.75f + 0.25f));
		graphics.fill(0, 0, this.width, this.height, dim);
	}

	private void drawHudPill(GuiGraphicsExtractor graphics) {
		boolean fps = Celestial.settings.showFps;
		boolean ping = Celestial.settings.showPing;
		if (!fps && !ping) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		StringBuilder text = new StringBuilder();
		if (fps) {
			text.append("FPS: ").append(mc != null ? mc.getFps() : "--");
		}
		if (fps && ping) {
			text.append("   |   ");
		}
		if (ping) {
			text.append("Ping: ").append(pingText(mc));
		}
		String line = text.toString();
		Font f = fontOrMinecraft();
		int tw = f.width(line);
		int pw = tw + 20;
		int px = this.width - pw - 8;
		int py = 8;
		float a = CelestialWidget.anim();
		graphics.fill(px, py, px + pw, py + 18, CelestialWidget.withAlpha(0xE0101014, a));
		graphics.outline(px, py, px + pw, py + 18,
			CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x66000000, a));
		graphics.fill(px + 4, py + 16, px + pw - 4, py + 17,
			CelestialWidget.withAlpha(Theme.current.accent, a * 0.9f));
		graphics.text(f, line, px + 10, py + 5, CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
	}

	private void drawWindow(GuiGraphicsExtractor graphics) {
		int x = winX();
		int y = winY();
		int w = winW();
		int h = winH();
		float a = CelestialWidget.anim() * (float) Celestial.settings.opacity;
		for (int i = Celestial.settings.blurLayers; i >= 1; i--) {
			int pad = i * 5;
			graphics.fill(x - pad, y - pad, x + w + pad, y + h + pad,
				CelestialWidget.withAlpha(0x18000000 | (Theme.current.accent & 0x00FFFFFF), a * 0.5f));
		}
		graphics.fill(x, y, x + w, y + h, CelestialWidget.withAlpha(0xE8101014, a));
		graphics.outline(x, y, x + w, y + h, CelestialWidget.withAlpha(accentBorder(), a));
		graphics.fill(x, y, x + w, y + 1, CelestialWidget.withAlpha(Theme.current.accent, a * 0.8f));

		double s = scale();
		int sb = sideW();
		graphics.fill(x + sb, y + (int) (10 * s), x + sb + 1, y + h - (int) (10 * s),
			CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x33000000, a));
		// Sidebar logo: sparkle + name.
		drawSparkle(graphics, x + (int) (24 * s), y + (int) (26 * s), (int) (9 * s), a);
		graphics.text(this.font != null ? this.font : Minecraft.getInstance().font,
			"Celestial Client", x + (int) (40 * s), y + (int) (20 * s),
			CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
		graphics.text(this.font != null ? this.font : Minecraft.getInstance().font,
			"v" + Celestial.VERSION.replace("v", ""), x + (int) (40 * s), y + (int) (32 * s),
			CelestialWidget.withAlpha(Theme.current.accent, a), true);

		// Content header.
		int cx = x + sb;
		Font hf = this.font != null ? this.font : Minecraft.getInstance().font;
		graphics.text(hf,
			tab.label, cx + (int) (18 * s), y + (int) (16 * s),
			CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
		String sub = switch (tab) {
			case HOME -> "Welcome to Celestial Client.";
			case MODULES -> "Toggle and configure every module.";
			case SETTINGS -> "Tune the client to your liking.";
			case PROFILES -> "Save and load module presets.";
			case THEMES -> "Make it yours.";
			case ABOUT -> "About this client.";
		};
		graphics.text(this.font != null ? this.font : Minecraft.getInstance().font,
			sub, cx + (int) (18 * s), y + (int) (30 * s),
			CelestialWidget.withAlpha(0xFF999999, a), true);
		graphics.text(hf,
			"ESC to close", x + w - hf.width("ESC to close") - (int) (14 * s),
			y + h - (int) (20 * s), CelestialWidget.withAlpha(0xFF888888, a), true);
	}

	private void drawSparkle(GuiGraphicsExtractor g, int cx, int cy, int r, float a) {
		int accent = Theme.current.accent;
		long pulse = System.currentTimeMillis() % 2000;
		float glow = 0.6f + 0.3f * (float) Math.sin(pulse / 2000.0 * Math.PI * 2.0);
		if (!Celestial.settings.animations) {
			glow = 0.85f;
		}
		g.fill(cx - r, cy - 1, cx + r, cy + 1, CelestialWidget.withAlpha(accent, a * glow));
		g.fill(cx - 1, cy - r, cx + 1, cy + r, CelestialWidget.withAlpha(accent, a * glow));
		int c = Math.max(2, r / 3);
		g.fill(cx - c, cy - c, cx + c, cy + c, CelestialWidget.withAlpha(0xFFFFFFFF, a * glow));
	}

	private int accentBorder() {
		return Theme.current.accent & 0x00FFFFFF | 0x66000000;
	}

	private void drawContent(GuiGraphicsExtractor graphics) {
		double s = scale();
		int cx = contentX();
		int cw = contentW();
		int pad = (int) (18 * s);
		int y = winY();
		float a = CelestialWidget.anim();
		Font f = this.font != null ? this.font : Minecraft.getInstance().font;
		switch (tab) {
			case HOME: {
				int[] geom = homeGeom();
				int hx = geom[0];
				int hw = geom[1];
				int hy = geom[2];
				int hh = geom[3];
				// Hero banner.
				graphics.fill(hx, hy, hx + hw, hy + hh, CelestialWidget.withAlpha(0xE016161C, a));
				graphics.outline(hx, hy, hx + hw, hy + hh, CelestialWidget.withAlpha(accentBorder(), a));
				graphics.fill(hx, hy, hx + hw, hy + 2, CelestialWidget.withAlpha(Theme.current.accent, a * 0.8f));
				// Faint giant sparkle on the right side.
				int bigR = Math.max((int) (8 * s), hh / 3);
				drawSparkle(graphics, hx + hw - (int) (64 * s), hy + hh / 2, bigR, a * 0.9f);
				drawSparkle(graphics, hx + hw - (int) (36 * s), hy + Math.max((int) (14 * s), hh / 4), (int) (7 * s), a * 0.7f);
				graphics.text(f, "Celestial Client", hx + (int) (16 * s), hy + (int) (24 * s),
					CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				graphics.text(f, "Enhance your Minecraft experience.", hx + (int) (16 * s),
					hy + (int) (44 * s), CelestialWidget.withAlpha(Theme.current.accent, a), true);
				if (hh >= (int) (72 * s)) {
					graphics.text(f, "Press a toggle or open Modules for everything.", hx + (int) (16 * s),
						hy + (int) (62 * s), CelestialWidget.withAlpha(0xFF888888, a), true);
				}

				int cardY = geom[4];
				int cardH = geom[5];
				int leftX = geom[6];
				int leftW = geom[7];
				int rightX = geom[8];
				// Section labels.
				graphics.text(f, "Quick Toggles", leftX, cardY - (int) (14 * s),
					CelestialWidget.withAlpha(0xFFBBBBBB, a), true);
				graphics.text(f, "Client Info", rightX, cardY - (int) (14 * s),
					CelestialWidget.withAlpha(0xFFBBBBBB, a), true);
				// Cards.
				graphics.fill(leftX, cardY, leftX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(0xE0121216, a));
				graphics.outline(leftX, cardY, leftX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(accentBorder(), a));
				graphics.fill(rightX, cardY, rightX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(0xE0121216, a));
				graphics.outline(rightX, cardY, rightX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(accentBorder(), a));
				// Right card rows.
				int ix = rightX + (int) (10 * s);
				int iy = cardY + (int) (10 * s);
				graphics.text(f, "Version", ix, iy, CelestialWidget.withAlpha(0xFF999999, a), true);
				String ver = Celestial.VERSION.startsWith("v") ? Celestial.VERSION : "v" + Celestial.VERSION;
				graphics.text(f, ver, rightX + leftW - f.width(ver) - (int) (10 * s), iy,
					CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				iy += (int) (18 * s);
				graphics.text(f, "Build Date", ix, iy, CelestialWidget.withAlpha(0xFF999999, a), true);
				graphics.text(f, Celestial.BUILD_DATE, rightX + leftW - f.width(Celestial.BUILD_DATE) - (int) (10 * s), iy,
					CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				iy += (int) (18 * s);
				int nh = (int) (26 * s);
				int ny = cardY + cardH - nh - (int) (8 * s);
				if (iy + 10 <= ny) {
					graphics.text(f, "Developer", ix, iy, CelestialWidget.withAlpha(0xFF999999, a), true);
					graphics.text(f, Celestial.DEVELOPER, rightX + leftW - f.width(Celestial.DEVELOPER) - (int) (10 * s), iy,
						CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				}
				// Footer note.
				graphics.fill(rightX + (int) (6 * s), ny, rightX + leftW - (int) (6 * s), ny + nh,
					CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x2A000000, a));
				graphics.outline(rightX + (int) (6 * s), ny, rightX + leftW - (int) (6 * s), ny + nh,
					CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x55000000, a));
				drawSparkle(graphics, rightX + (int) (18 * s), ny + nh / 2, (int) (5 * s), a);
				graphics.text(f, "Enjoy your time in Minecraft!",
					rightX + (int) (30 * s), ny + (nh - 8) / 2,
					CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				break;
			}
			case ABOUT: {
				int tx = cx + pad;
				int ty = y + (int) (60 * s);
				String[] lines = {
					"Celestial Client " + Celestial.VERSION,
					"Build " + Celestial.BUILD_DATE + " by " + Celestial.DEVELOPER,
					"",
				};
				for (String line : lines) {
					graphics.text(f, line, tx, ty, CelestialWidget.withAlpha(0xFFCCCCCC, a), true);
					ty += (int) (16 * s);
				}
				String[] features = {
					"12 modules across Render, Movement and Player,",
					"live themes, profiles, FPS/Ping/Coords HUD,",
					"and an ESC menu that respects singleplayer pause."
				};
				for (String line : features) {
					graphics.text(f, line, tx, ty, CelestialWidget.withAlpha(0xFF999999, a), true);
					ty += (int) (16 * s);
				}
				break;
			}
			case SETTINGS: {
				int tx = cx + pad;
				int ty = winY() + winH() - (int) (52 * s);
				String extra = keyName();
				graphics.text(f, "GUI Key: ESC (fixed)" + (extra.isEmpty() ? "" : " + " + extra), tx, ty,
					CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				graphics.text(f, "Extra key rebindable in Options > Controls > Celestial", tx,
					ty + (int) (14 * s), CelestialWidget.withAlpha(0xFF777777, a), true);
				break;
			}
			case THEMES: {
				int tx = cx + pad;
				int ty = y + (int) (60 * s);
				graphics.text(f, "Preset", tx, ty - (int) (14 * s),
					CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				break;
			}
			default:
				break;
		}
	}

	private String pingText(Minecraft mc) {
		try {
			if (mc != null && mc.getConnection() != null && mc.player != null) {
				var info = mc.getConnection().getPlayerInfo(mc.getGameProfile().id());
				if (info != null) {
					return info.getLatency() + "ms";
				}
			}
		} catch (Exception ignored) {
		}
		return "--";
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
			if (selected) {
				text(g, "◆ " + target.label, getX() + 10, getY() + 8,
					withAlpha(accent(), a));
			} else {
				text(g, target.label, getX() + 12, getY() + 8,
					withAlpha(0xFFBBBBBB, a));
			}
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.switchTab(target);
			}
		}
	}

	private static class DisconnectButton extends ActionButton {
		DisconnectButton() {
			super("Disconnect", true, false, () -> {
				if (current != null) {
					current.disconnect();
				}
			});
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
			centeredText(g, getMessage().getString(), getX() + getWidth() / 2, getY() + 6,
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
			centeredText(g, category.getLabel(), getX() + getWidth() / 2, getY() + 6,
				withAlpha(selected ? accent() : 0xFFBBBBBB, anim()));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.filter = category;
				current.scroll = 0;
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
			centeredText(g, "All", getX() + getWidth() / 2, getY() + 6,
				withAlpha(selected ? accent() : 0xFFBBBBBB, anim()));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.filter = null;
				current.scroll = 0;
				current.rebuildModuleRows();
				current.layoutNow();
			}
		}
	}

	/** Small list scroller (mouse wheel over the list also works). */
	private static class ScrollButton extends CelestialWidget {
		final boolean up;

		ScrollButton(boolean up) {
			super(0, 0, 10, 10, Component.literal(up ? "^" : "v"));
			this.up = up;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			float a = anim();
			panel(g, getX(), getY(), getWidth(), getHeight(), isHovered());
			centeredText(g, up ? "^" : "v", getX() + getWidth() / 2, getY() + 5,
				withAlpha(0xFFFFFFFF, a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.scroll = current.clampScroll(current.scroll + (up ? -80 : 80));
				current.layoutNow();
			}
		}
	}

	/** Compact name + toggle pill row used in the Home "Quick Toggles" card. */
	private static class HomeToggleRow extends CelestialWidget {
		private final Module module;
		private boolean lastState;
		private long lastChange;

		HomeToggleRow(Module module) {
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
			if (isHovered()) {
				g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(),
					withAlpha(0x18FFFFFF, a));
			}
			text(g, module.getName(), getX() + 2, getY() + (getHeight() - 8) / 2, withAlpha(0xFFFFFFFF, a));
			float t = Celestial.settings.animations
				? Math.max(0.0f, Math.min(1.0f, (System.currentTimeMillis() - lastChange) / 120.0f))
				: 1.0f;
			int pw = 34;
			int ph = 13;
			drawPill(g, getX() + getWidth() - pw, getY() + (getHeight() - ph) / 2, pw, ph, on, t);
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
			text(g, module.getName(), tx, getY() + 6, withAlpha(on ? accent() : 0xFFFFFFFF, a));
			String desc = module.getDescription() + "  [" + keyLabel(module) + "]";
			text(g, desc, tx, getY() + getHeight() - 16, withAlpha(0xFF888888, a));
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
			text(g, label, getX() + 10, getY() + 10, withAlpha(0xFFFFFFFF, a));
			float t = Celestial.settings.animations
				? Math.max(0.0f, Math.min(1.0f, (System.currentTimeMillis() - lastChange) / 120.0f))
				: 1.0f;
			drawPill(g, getX() + getWidth() - 52, getY() + 8, 42, 14, on, t);
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
			text(g, label, getX() + 10, getY() + 10, withAlpha(0xFFFFFFFF, a));
			int right = getX() + getWidth() - 8;
			int plusX = right - 30;
			int minusX = right - 104;
			g.fill(plusX, getY() + 6, right, getY() + getHeight() - 6,
				withAlpha(accent() & 0x00FFFFFF | 0x33000000, a));
			g.fill(minusX, getY() + 6, minusX + 30, getY() + getHeight() - 6,
				withAlpha(0x22FFFFFF, a));
			text(g, "+", plusX + 12, getY() + 9, withAlpha(0xFFFFFFFF, a));
			text(g, "-", minusX + 12, getY() + 9, withAlpha(0xFFFFFFFF, a));
			centeredText(g, text.get(), right - 52, getY() + 10, withAlpha(accent(), a));
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
			g.fill(preview, getY() + 7, preview + 14, getY() + 21, withAlpha(theme.accent | 0xFF000000, a));
			g.outline(preview, getY() + 7, preview + 14, getY() + 21, withAlpha(0x88FFFFFF, a));
			text(g, theme.label, preview + 22, getY() + 10, withAlpha(selected ? accent() : 0xFFFFFFFF, a));
			if (selected) {
				text(g, "Active", getX() + getWidth() - 50, getY() + 10, withAlpha(accent(), a));
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
			text(g, label, getX() + 10, getY() + 9, withAlpha(active ? accent() : 0xFFFFFFFF, anim()));
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
		g.fill(kx, y + 2, kx + knob, y + 2 + knob, CelestialWidget.withAlpha(0xFFFFFFFF, a));
	}
}
