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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Celestial in-game menu, rewritten for reliability on 26.3.
 *
 * Lessons baked in from the old version's failures:
 * - Vanilla's widget-list helpers are not trustworthy here
 *   (stale widgets survived tab switches, kept rendering and ate
 *   clicks). Every widget this screen owns is tracked in {@link #ui}
 *   plus the four text boxes, removed EXPLICITLY on every rebuild,
 *   and only owned widgets are ever drawn.
 * - Owned state is cleared BEFORE any vanilla call that could throw,
 *   so a failing vanilla method can never corrupt the next tab.
 * - Screen has no mouse/scroll callbacks in 26.3 (verified against the
 *   client jar), so module-list scrolling is done with real Up/Down
 *   buttons, not a dead mouseScrolled override.
 *
 * Opened with ESC (pause replacement, PauseMenuMixin) or Right Shift
 * (rebindable, CelestialClient). Right Shift or ESC closes.
 */
public class CelestialGui extends Screen {
	public enum Tab {
		HOME("Home", "home"),
		MODULES("Modules", "cube"),
		SETTINGS("Settings", "gear"),
		PROFILES("Profiles", "person"),
		THEMES("Themes", "palette"),
		ABOUT("About", "info");

		public final String label;
		public final String icon;

		Tab(String label, String icon) {
			this.label = label;
			this.icon = icon;
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
	private double scroll;

	private final List<CelestialWidget> ui = new ArrayList<>();
	private final List<ModuleCard> cards = new ArrayList<>();
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

	/** Module-list scrolling (Screen has no scroll callback in 26.3). */
	private void scrollBy(double amount) {
		scroll = clampScroll(scroll + amount);
		layoutNow();
	}

	// ---------------- geometry ----------------

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

	private int pillY() {
		return winY() + (int) (86 * scale());
	}

	private int pillH() {
		return (int) (26 * scale());
	}

	private int gridTop() {
		return pillY() + pillH() + (int) (12 * scale());
	}

	private int gridBottom() {
		return winY() + winH() - (int) (26 * scale());
	}

	private int cardGap() {
		return (int) (10 * scale());
	}

	private int cardH() {
		return (int) (64 * scale());
	}

	private int cardW() {
		int pw = contentW() - (int) (36 * scale());
		return (pw - cardGap()) / 2;
	}

	private double clampScroll(double value) {
		int visible = Math.max(1, gridBottom() - gridTop());
		int rows = (cards.size() + 1) / 2;
		int total = Math.max(0, rows * (cardH() + cardGap()) - cardGap());
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

	// ---------------- tab lifecycle ----------------

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
	 * Drops every owned widget. Owned state is cleared FIRST so a
	 * throwing vanilla call can never corrupt the tab being built.
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
		cards.clear();
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
		try {
			double s = scale();
			int x = winX();
			int y = winY();
			int sb = sideW();
			int i = 0;
			for (CelestialWidget widget : new ArrayList<>(ui)) {
				if (widget instanceof NavButton) {
					widget.setX(x + (int) (10 * s));
					widget.setY(y + (int) (88 * s) + i * (int) (34 * s));
					widget.setSize(sb - (int) (20 * s), (int) (30 * s));
					i++;
				} else if (widget instanceof DisconnectButton) {
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
		} catch (Exception e) {
			System.out.println("[Celestial] menu layout failed: " + e);
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

	private int[] homeGeom() {
		double s = scale();
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
		int leftW = Math.max(40, (contentW() - pad * 2 - gap) / 2);
		int leftX = contentX() + pad;
		return new int[]{contentX() + pad, contentW() - pad * 2, hy, hh, cardY, cardH, leftX, leftW,
				leftX + leftW + gap};
	}

	private void layoutHome() {
		double s = scale();
		int[] geom = homeGeom();
		int cardY = geom[4];
		int cardH = geom[5];
		int leftX = geom[6];
		int leftW = geom[7];
		int rows = 0;
		for (CelestialWidget widget : ui) {
			if (widget instanceof HomeToggleRow) {
				rows++;
			}
		}
		if (rows == 0) {
			return;
		}
		int rowH = Math.max((int) (24 * s), (cardH - (int) (34 * s)) / rows);
		int ry = cardY + (int) (28 * s);
		for (CelestialWidget widget : ui) {
			if (widget instanceof HomeToggleRow) {
				widget.setX(leftX + (int) (6 * s));
				widget.setY(ry);
				widget.setSize(leftW - (int) (12 * s), rowH);
				ry += rowH + (int) (2 * s);
			}
		}
	}

	// ---------------- MODULES ----------------

	private void buildModules() {
		Font f = fontOrMinecraft();
		EditBox box = new EditBox(f, 0, 0, 100, 22, Component.literal("Search"));
		box.setHint(Component.literal("Search modules..."));
		box.setMaxLength(32);
		box.setTextColor(0xFFFFFFFF);
		box.setBordered(false);
		if (!search.isEmpty()) {
			box.setValue(search);
		}
		box.setResponder(value -> {
			search = String.valueOf(value);
			scroll = 0;
			rebuildCards();
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
		rebuildCards();
	}

	private void rebuildCards() {
		for (ModuleCard card : new ArrayList<>(cards)) {
			try {
				this.removeWidget(card);
			} catch (Exception ignored) {
			}
			ui.remove(card);
		}
		cards.clear();
		String query = search.trim().toLowerCase();
		for (Module module : ModuleManager.getModules()) {
			if (filter != null && module.getCategory() != filter) {
				continue;
			}
			if (!query.isEmpty() && !module.getName().toLowerCase().contains(query)
				&& !module.getDescription().toLowerCase().contains(query)) {
				continue;
			}
			ModuleCard card = new ModuleCard(module);
			cards.add(card);
			ui.add(card);
			try {
				this.addRenderableWidget(card);
			} catch (Exception e) {
				System.out.println("[Celestial] cannot track card: " + e);
			}
		}
		scroll = clampScroll(scroll);
	}

	private int searchPad() {
		return (int) (26 * scale());
	}

	private void layoutModules() {
		double s = scale();
		int px = contentX() + (int) (18 * s);
		int pw = contentW() - (int) (36 * s);
		if (searchBox != null) {
			int sw = Math.min((int) (230 * s), Math.max(80, pw / 2));
			searchBox.setX(px + pw - sw + searchPad());
			searchBox.setY(winY() + (int) (15 * s));
			searchBox.setWidth(Math.max(20, sw - searchPad() - (int) (8 * s)));
			searchBox.setHeight((int) (22 * s));
		}
		int gap = (int) (8 * s);
		int pillW = (pw - gap * 6) / 7;
		int bx = px;
		for (CelestialWidget widget : ui) {
			if (widget instanceof CategoryButton || widget instanceof AllButton) {
				widget.setX(bx);
				widget.setY(pillY());
				widget.setSize(pillW, pillH());
				bx += pillW + gap;
			} else if (widget instanceof ScrollButton scrollBtn) {
				widget.setX(px + pw - (scrollBtn.up ? (int) (48 * s) : (int) (24 * s)));
				widget.setY(winY() + (int) (15 * s));
				widget.setSize((int) (20 * s), (int) (22 * s));
			}
		}
		int colW = cardW();
		int ch = cardH();
		int step = ch + cardGap();
		for (int i = 0; i < cards.size(); i++) {
			ModuleCard card = cards.get(i);
			int col = i % 2;
			int gridRow = i / 2;
			int ry = gridTop() - (int) scroll + gridRow * step;
			if (ry + ch < gridTop() || ry > gridBottom()) {
				card.setX(-10000);
				card.setY(-10000);
				card.setSize(colW, ch);
			} else {
				card.setX(px + col * (colW + cardGap()));
				card.setY(ry);
				card.setSize(colW, ch);
			}
		}
	}

	// ---------------- SETTINGS ----------------

	private void buildSettings() {
		Settings settings = Celestial.settings;
		track(new StepperRow("GUI Scale", () -> fmtScale(settings.guiScale), () -> cycleScale(-1),
			() -> cycleScale(1)));
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
		track(new ToggleRow("Show FPS", () -> hudOn("fps"), value -> setHud("fps", value)));
		track(new ToggleRow("Show Ping", () -> hudOn("ping"), value -> setHud("ping", value)));
		track(new ToggleRow("Show Coords", () -> hudOn("coords"), value -> setHud("coords", value)));
	}

	private void layoutSettings() {
		double s = scale();
		int px = contentX() + (int) (18 * s);
		int pw = contentW() - (int) (36 * s);
		int ry = winY() + (int) (60 * s);
		for (CelestialWidget widget : ui) {
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
		for (CelestialWidget widget : ui) {
			if (widget instanceof CreateButton || widget instanceof RenameButton) {
				widget.setX(px + pw - (int) (94 * s));
				widget.setY(top + (first ? 0 : (int) (28 * s)));
				widget.setSize((int) (94 * s), (int) (20 * s));
				first = false;
			}
		}
		java.util.Map<String, Integer> rowY = new java.util.HashMap<>();
		int ry = top + (int) (62 * s);
		for (CelestialWidget widget : ui) {
			if (widget instanceof ProfileRow row) {
				row.setX(px);
				row.setY(ry);
				row.setSize(pw, (int) (28 * s));
				rowY.put(row.name, ry);
				ry += (int) (32 * s);
			}
		}
		for (CelestialWidget widget : ui) {
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
			} catch (Exception ignored) {
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
			} catch (Exception ignored) {
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
				} catch (Exception ignored) {
				}
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
		for (CelestialWidget widget : ui) {
			if (widget instanceof ThemeButton) {
				widget.setX(px);
				widget.setY(ry);
				widget.setSize(pw, (int) (28 * s));
				ry += (int) (32 * s);
			}
		}
		ry += (int) (4 * s);
		int sx = px;
		for (CelestialWidget widget : ui) {
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
		for (CelestialWidget widget : ui) {
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
		safeSave();
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
		Store.save(settings());
		layoutNow();
	}

	private Settings settings() {
		return Celestial.settings;
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

	private static boolean hudOn(String id) {
		Module module = ModuleManager.byId(id);
		return module != null && module.isEnabled();
	}

	private static void setHud(String id, boolean value) {
		Module module = ModuleManager.byId(id);
		if (module != null) {
			module.setEnabled(value);
		}
	}

	private static String categoryIcon(Category category) {
		if (category == null) {
			return "grid";
		}
		switch (category) {
			case HUD:
				return "monitor";
			case VISUAL:
				return "eye";
			case GAMEPLAY:
				return "gamepad";
			case INTERFACE:
				return "sliders";
			default:
				return "cube";
		}
	}

	private static String moduleIcon(Module module) {
		if (module == null) {
			return "cube";
		}
		switch (module.getId()) {
			case "fps":
				return "monitor";
			case "ping":
				return "bars";
			case "cps":
				return "cursor";
			case "coords":
				return "cross";
			case "xray":
				return "diamond";
			case "fullbright":
				return "sun";
			case "esp":
				return "eye";
			case "zoom":
				return "zoom";
			case "fly":
				return "fly";
			case "speed":
				return "bolt";
			case "sprint":
				return "dash";
			case "nofall":
				return "heart";
			case "step":
				return "stairs";
			case "antiafk":
				return "clock";
			case "fastplace":
				return "box";
			case "fpsboost":
				return "gauge";
			default:
				return "cube";
		}
	}

	/** Splits a card description into at most two lines that fit maxW. */
	private static String[] wrap2(Font f, String text, int maxW) {
		if (text == null) {
			return new String[]{null, null};
		}
		if (maxW <= 10 || f.width(text) <= maxW) {
			return new String[]{text, null};
		}
		String line1 = "";
		StringBuilder rest = new StringBuilder();
		boolean overflow = false;
		for (String word : text.split(" ")) {
			if (!overflow) {
				String candidate = line1.isEmpty() ? word : line1 + " " + word;
				if (f.width(candidate) <= maxW) {
					line1 = candidate;
					continue;
				}
				overflow = true;
			}
			if (rest.length() > 0) {
				rest.append(' ');
			}
			rest.append(word);
		}
		String line2 = rest.toString();
		while (line2.length() > 1 && f.width(line2 + "…") > maxW) {
			line2 = line2.substring(0, line2.length() - 1).trim();
		}
		if (line2.length() < rest.length()) {
			line2 = line2 + "…";
		}
		return new String[]{line1.isEmpty() ? null : line1, line2.isEmpty() ? null : line2};
	}

	private String keyName() {
		String extra = com.celestial.client.CelestialClient.extraKeyName();
		return extra == null ? "" : extra;
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

	// ---------------- frame ----------------

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		layoutNow();
		try {
			drawBackdrop(graphics);
		} catch (Exception e) {
			System.out.println("[Celestial] backdrop failed: " + e);
		}
		try {
			drawHudPill(graphics);
		} catch (Exception e) {
			System.out.println("[Celestial] HUD pill failed: " + e);
		}
		try {
			drawWindow(graphics);
		} catch (Exception e) {
			System.out.println("[Celestial] window failed: " + e);
		}
		try {
			drawContent(graphics);
		} catch (Exception e) {
			System.out.println("[Celestial] content failed: " + e);
		}
		for (CelestialWidget widget : new ArrayList<>(ui)) {
			try {
				widget.extractRenderState(graphics, mouseX, mouseY, partialTick);
			} catch (Exception e) {
				System.out.println("[Celestial] widget failed: " + e);
			}
		}
		try {
			if (searchBox != null) {
				searchBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}
			if (profileBox != null) {
				profileBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}
			if (renameBox != null) {
				renameBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}
			if (hexBox != null) {
				hexBox.extractRenderState(graphics, mouseX, mouseY, partialTick);
			}
		} catch (Exception e) {
			System.out.println("[Celestial] text box failed: " + e);
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
		CelestialWidget.roundedPanel(graphics, px, py, pw, 18, 9,
			CelestialWidget.withAlpha(0xE0101014, a),
			CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x66000000, a));
		graphics.text(f, line, px + 10, py + 5, CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
	}

	private void centered(GuiGraphicsExtractor graphics, Font f, String string, int centerX, int y,
		int color) {
		graphics.text(f, string, centerX - f.width(string) / 2, y, color, true);
	}

	private void drawWindow(GuiGraphicsExtractor graphics) {
		int x = winX();
		int y = winY();
		int w = winW();
		int h = winH();
		float a = CelestialWidget.anim() * (float) Celestial.settings.opacity;
		int rr = (int) (12 * scale());
		for (int i = Celestial.settings.blurLayers; i >= 1; i--) {
			int pad = i * 5;
			CelestialWidget.roundedFill(graphics, x - pad, y - pad, w + pad * 2, h + pad * 2, rr + pad,
				CelestialWidget.withAlpha(0x18000000 | (Theme.current.accent & 0x00FFFFFF), a * 0.5f));
		}
		CelestialWidget.roundedPanel(graphics, x, y, w, h, rr,
			CelestialWidget.withAlpha(0xE8101016, a), CelestialWidget.withAlpha(accentBorder(), a));

		double s = scale();
		int sb = sideW();
		graphics.fill(x + sb, y + (int) (92 * s), x + sb + 1, y + h - (int) (12 * s),
			CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x2A000000, a));
		Font lf = fontOrMinecraft();
		int logoCx = x + sb / 2;
		drawSparkle(graphics, logoCx, y + (int) (28 * s), (int) (10 * s), a);
		centered(graphics, lf, "Celestial", logoCx, y + (int) (46 * s),
			CelestialWidget.withAlpha(0xFFFFFFFF, a));
		centered(graphics, lf, "Client", logoCx, y + (int) (58 * s),
			CelestialWidget.withAlpha(0xFF999999, a));

		int cx = x + sb;
		Font hf = fontOrMinecraft();
		graphics.text(hf, tab.label, cx + (int) (18 * s), y + (int) (16 * s),
			CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
		String sub = switch (tab) {
			case HOME -> "Welcome to Celestial Client.";
			case MODULES -> "Customize your client with useful features.";
			case SETTINGS -> "Tune the client to your liking.";
			case PROFILES -> "Save and load module presets.";
			case THEMES -> "Make it yours.";
			case ABOUT -> "About this client.";
		};
		graphics.text(hf, sub, cx + (int) (18 * s), y + (int) (30 * s),
			CelestialWidget.withAlpha(0xFF999999, a), true);
		graphics.text(hf, "Right Shift to close", x + w - hf.width("Right Shift to close") - (int) (14 * s),
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
		int pad = (int) (18 * s);
		int y = winY();
		float a = CelestialWidget.anim();
		Font f = fontOrMinecraft();
		switch (tab) {
			case MODULES: {
				if (searchBox != null) {
					int spad = searchPad();
					int bx = searchBox.getX() - spad;
					int by = searchBox.getY() - (int) (4 * s);
					int bw = searchBox.getWidth() + spad + (int) (8 * s);
					int bh = searchBox.getHeight() + (int) (8 * s);
					CelestialWidget.roundedPanel(graphics, bx, by, bw, bh, 11,
						CelestialWidget.withAlpha(0xE0161620, a),
						CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x3A000000, a));
					int iconSz = Math.max(10, (int) (14 * s));
					CelestialWidget.drawIcon(graphics, "search", bx + (int) (8 * s),
						by + (bh - iconSz) / 2, iconSz, 0xFF888888);
				}
				break;
			}
			case HOME: {
				int[] geom = homeGeom();
				int hx = geom[0];
				int hw = geom[1];
				int hy = geom[2];
				int hh = geom[3];
				graphics.fill(hx, hy, hx + hw, hy + hh, CelestialWidget.withAlpha(0xE016161C, a));
				graphics.outline(hx, hy, hx + hw, hy + hh, CelestialWidget.withAlpha(accentBorder(), a));
				graphics.fill(hx, hy, hx + hw, hy + 2, CelestialWidget.withAlpha(Theme.current.accent, a * 0.8f));
				int bigR = Math.max((int) (8 * s), hh / 3);
				drawSparkle(graphics, hx + hw - (int) (64 * s), hy + hh / 2, bigR, a * 0.9f);
				drawSparkle(graphics, hx + hw - (int) (36 * s), hy + Math.max((int) (14 * s), hh / 4),
					(int) (7 * s), a * 0.7f);
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
				graphics.text(f, "Quick Toggles", leftX, cardY - (int) (14 * s),
					CelestialWidget.withAlpha(0xFFBBBBBB, a), true);
				graphics.text(f, "Client Info", rightX, cardY - (int) (14 * s),
					CelestialWidget.withAlpha(0xFFBBBBBB, a), true);
				graphics.fill(leftX, cardY, leftX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(0xE0121216, a));
				graphics.outline(leftX, cardY, leftX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(accentBorder(), a));
				graphics.fill(rightX, cardY, rightX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(0xE0121216, a));
				graphics.outline(rightX, cardY, rightX + leftW, cardY + cardH,
					CelestialWidget.withAlpha(accentBorder(), a));
				int ix = rightX + (int) (10 * s);
				int iy = cardY + (int) (10 * s);
				graphics.text(f, "Version", ix, iy, CelestialWidget.withAlpha(0xFF999999, a), true);
				String ver = Celestial.VERSION.startsWith("v") ? Celestial.VERSION : "v" + Celestial.VERSION;
				graphics.text(f, ver, rightX + leftW - f.width(ver) - (int) (10 * s), iy,
					CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				iy += (int) (18 * s);
				graphics.text(f, "Build Date", ix, iy, CelestialWidget.withAlpha(0xFF999999, a), true);
				graphics.text(f, Celestial.BUILD_DATE, rightX + leftW - f.width(Celestial.BUILD_DATE) - (int) (10 * s),
					iy, CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				iy += (int) (18 * s);
				int nh = (int) (26 * s);
				int ny = cardY + cardH - nh - (int) (8 * s);
				if (iy + 10 <= ny) {
					graphics.text(f, "Developer", ix, iy, CelestialWidget.withAlpha(0xFF999999, a), true);
					graphics.text(f, Celestial.DEVELOPER,
						rightX + leftW - f.width(Celestial.DEVELOPER) - (int) (10 * s), iy,
						CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				}
				graphics.fill(rightX + (int) (6 * s), ny, rightX + leftW - (int) (6 * s), ny + nh,
					CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x2A000000, a));
				graphics.outline(rightX + (int) (6 * s), ny, rightX + leftW - (int) (6 * s), ny + nh,
					CelestialWidget.withAlpha(Theme.current.accent & 0x00FFFFFF | 0x55000000, a));
				drawSparkle(graphics, rightX + (int) (18 * s), ny + nh / 2, (int) (5 * s), a);
				graphics.text(f, "Enjoy your time in Minecraft!", rightX + (int) (30 * s), ny + (nh - 8) / 2,
					CelestialWidget.withAlpha(0xFFFFFFFF, a), true);
				break;
			}
			case ABOUT: {
				int tx = cx + pad;
				int ty = y + (int) (60 * s);
				String[] lines = {
					"Celestial Client " + Celestial.VERSION,
					"Build " + Celestial.BUILD_DATE + " by " + Celestial.DEVELOPER,
					""
				};
				for (String line : lines) {
					graphics.text(f, line, tx, ty, CelestialWidget.withAlpha(0xFFCCCCCC, a), true);
					ty += (int) (16 * s);
				}
				String[] features = {
					"16 modules across HUD, Visual, Gameplay and Interface,",
					"live themes, counter overlays,",
					"and a Right Shift menu that respects singleplayer pause."
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
				graphics.text(f, "Menu Key: " + (extra.isEmpty() ? "Right Shift" : extra), tx, ty,
					CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				graphics.text(f, "Rebindable in Options > Controls > Celestial", tx,
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
			int r = Math.min(9, getHeight() / 2);
			if (selected) {
				roundedFill(g, getX(), getY(), getWidth(), getHeight(), r,
					withAlpha(accent() & 0x00FFFFFF | 0x5C000000, a));
			} else if (isHovered()) {
				roundedFill(g, getX(), getY(), getWidth(), getHeight(), r,
					withAlpha(0x1AFFFFFF, a));
			}
			int iconSz = Math.max(12, getHeight() - 14);
			drawIcon(g, target.icon, getX() + 10, getY() + (getHeight() - iconSz) / 2, iconSz,
				selected ? 0xFFFFFFFF : 0xFF999999);
			text(g, target.label, getX() + 10 + iconSz + 6, getY() + (getHeight() - 8) / 2,
				withAlpha(selected ? 0xFFFFFFFF : 0xFFBBBBBB, a));
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
			float a = anim();
			int r = getHeight() / 2;
			if (selected) {
				roundedFill(g, getX(), getY(), getWidth(), getHeight(), r, withAlpha(accent(), a));
			} else {
				roundedPanel(g, getX(), getY(), getWidth(), getHeight(), r,
					withAlpha(0xE0161620, a),
					withAlpha(isHovered() ? accent() : (accent() & 0x00FFFFFF | 0x3A000000), a));
			}
			Font f = Minecraft.getInstance().font;
			String label = category.getLabel();
			int iconSz = Math.max(10, getHeight() - 14);
			int total = iconSz + 4 + f.width(label);
			int sx = getX() + Math.max(2, (getWidth() - total) / 2);
			int iy = getY() + (getHeight() - iconSz) / 2;
			drawIcon(g, CelestialGui.categoryIcon(category), sx, iy, iconSz,
				selected ? 0xFFFFFFFF : 0xFFBBBBBB);
			text(g, label, sx + iconSz + 4, getY() + (getHeight() - 8) / 2,
				withAlpha(selected ? 0xFFFFFFFF : 0xFFBBBBBB, a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.filter = category;
				current.scroll = 0;
				current.rebuildCards();
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
			float a = anim();
			int r = getHeight() / 2;
			if (selected) {
				roundedFill(g, getX(), getY(), getWidth(), getHeight(), r, withAlpha(accent(), a));
			} else {
				roundedPanel(g, getX(), getY(), getWidth(), getHeight(), r,
					withAlpha(0xE0161620, a),
					withAlpha(isHovered() ? accent() : (accent() & 0x00FFFFFF | 0x3A000000), a));
			}
			Font f = Minecraft.getInstance().font;
			int iconSz = Math.max(10, getHeight() - 14);
			int total = iconSz + 4 + f.width("All");
			int sx = getX() + Math.max(2, (getWidth() - total) / 2);
			int iy = getY() + (getHeight() - iconSz) / 2;
			drawIcon(g, "grid", sx, iy, iconSz, selected ? 0xFFFFFFFF : 0xFFBBBBBB);
			text(g, "All", sx + iconSz + 4, getY() + (getHeight() - 8) / 2,
				withAlpha(selected ? 0xFFFFFFFF : 0xFFBBBBBB, a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.filter = null;
				current.scroll = 0;
				current.rebuildCards();
				current.layoutNow();
			}
		}
	}

	/** List scroller: Screen has no scroll callback in 26.3, so these are real buttons. */
	private static class ScrollButton extends CelestialWidget {
		final boolean up;

		ScrollButton(boolean up) {
			super(0, 0, 10, 10, Component.literal(up ? "^" : "v"));
			this.up = up;
		}

		@Override
		protected void drawWidget(GuiGraphicsExtractor g, int mx, int my, float pt) {
			float a = anim();
			roundedPanel(g, getX(), getY(), getWidth(), getHeight(), 6,
				withAlpha(0xE0161620, a),
				withAlpha(isHovered() ? accent() : (accent() & 0x00FFFFFF | 0x3A000000), a));
			centeredText(g, up ? "^" : "v", getX() + getWidth() / 2, getY() + 6,
				withAlpha(0xFFFFFFFF, a));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			if (current != null) {
				current.scrollBy(up ? -90 : 90);
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

	/** Mockup-style module card: icon + name + wrapped description + iOS toggle. */
	private static class ModuleCard extends CelestialWidget {
		private final Module module;
		private boolean lastState;
		private long lastChange;

		ModuleCard(Module module) {
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
			int r = Math.min(10, getHeight() / 4);
			roundedPanel(g, getX(), getY(), getWidth(), getHeight(), r,
				withAlpha(on ? 0xE01A1218 : 0xE0121216, a),
				withAlpha(accent() & 0x00FFFFFF | (on ? 0x77000000 : 0x3A000000), a));
			int iconSz = Math.max(14, getHeight() / 3);
			drawIcon(g, CelestialGui.moduleIcon(module), getX() + 12,
				getY() + (getHeight() - iconSz) / 2, iconSz, on ? accent() : 0xFF80808C);
			int tx = getX() + 12 + iconSz + 8;
			text(g, module.getName(), tx, getY() + 8, withAlpha(0xFFFFFFFF, a));
			Font f = Minecraft.getInstance().font;
			int maxW = getX() + getWidth() - 52 - tx;
			String[] lines = CelestialGui.wrap2(f, module.getDescription(), maxW);
			if (lines[0] != null) {
				text(g, lines[0], tx, getY() + 22, withAlpha(0xFF8A8A92, a));
			}
			if (lines[1] != null) {
				text(g, lines[1], tx, getY() + 33, withAlpha(0xFF8A8A92, a));
			}
			float t = Celestial.settings.animations
				? Math.max(0.0f, Math.min(1.0f, (System.currentTimeMillis() - lastChange) / 120.0f))
				: 1.0f;
			drawPill(g, getX() + getWidth() - 48, getY() + (getHeight() - 18) / 2, 36, 18, on, t);
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			module.toggle();
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
			roundedPanel(g, getX(), getY(), getWidth(), getHeight(), 8,
				withAlpha(0xE0121216, a),
				withAlpha(accent() & 0x00FFFFFF | 0x3A000000, a));
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
			roundedPanel(g, getX(), getY(), getWidth(), getHeight(), 8,
				withAlpha(0xE0121216, a),
				withAlpha(accent() & 0x00FFFFFF | 0x3A000000, a));
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
			boolean selected = Celestial.settings.theme.equalsIgnoreCase(theme.id)
				&& Theme.current == theme;
			float a = anim();
			roundedPanel(g, getX(), getY(), getWidth(), getHeight(), 8,
				withAlpha(0xE0121216, a),
				withAlpha(isHovered() || selected ? accent() : (accent() & 0x00FFFFFF | 0x3A000000), a));
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
			roundedPanel(g, getX(), getY(), getWidth(), getHeight(), 8,
				withAlpha(0xE0121216, anim()),
				withAlpha(accent() & 0x00FFFFFF | 0x3A000000, anim()));
			if (selected) {
				g.fill(getX(), getY(), getX() + 3, getY() + getHeight(), withAlpha(accent(), anim()));
			}
			String label = name + (active ? "  ●" : "");
			text(g, label, getX() + 10, getY() + 9, withAlpha(active ? accent() : 0xFFFFFFFF, anim()));
		}

		@Override
		protected void handleClick(MouseButtonEvent event) {
			selectedProfile = name;
		}
	}

	/** Shared iOS-style toggle-pill drawing. */
	private static void drawPill(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean on, float t) {
		float a = CelestialWidget.anim();
		int track = on ? CelestialWidget.accent() : 0xFF3A3A40;
		int base = track & 0x00FFFFFF | (on ? 0xDD000000 : 0xFF000000);
		CelestialWidget.roundedFill(g, x, y, w, h, h / 2, CelestialWidget.withAlpha(base, a));
		int knob = h - 6;
		int travel = w - knob - 6;
		int from = on ? x + 3 : x + 3 + travel;
		int to = on ? x + 3 + travel : x + 3;
		int kx = (int) (from + (to - from) * t);
		CelestialWidget.roundedFill(g, kx, y + 3, knob, knob, knob / 2,
			CelestialWidget.withAlpha(0xFFFFFFFF, a));
	}
}
