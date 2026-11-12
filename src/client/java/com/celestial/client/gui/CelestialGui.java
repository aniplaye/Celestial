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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Celestial client interface. Opens on ESC (via PauseMenuMixin),
 * closes on ESC (vanilla), pauses singleplayer, never pauses multiplayer.
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
	private Runnable relayoutTask = () -> {
	};

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
		selectedProfile = Celestial.settings.activeProfile;
		buildTab();
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

	@Override
	public boolean isPauseScreen() {
		Minecraft mc = Minecraft.getInstance();
		return mc != null && mc.hasSingleplayerServer();
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	// ---------------- layout ----------------

	private double scale() {
		return Celestial.settings.guiScale;
	}

	private int winW() {
		return (int) Math.min(780 * scale(), this.width - 24);
	}

	private int winH() {
		return (int) Math.min(480 * scale(), this.height - 24);
	}

	private int winX() {
		return (this.width - winW()) / 2;
	}

	private int winY() {
		return (this.height - winH()) / 2;
	}

	private void track(CelestialWidget widget) {
		tabWidgets.add(widget);
		this.addRenderableWidget(widget);
	}

	private void trackBox(EditBox box) {
		this.addRenderableWidget(box);
	}

	private void clearTab() {
		for (CelestialWidget widget : tabWidgets) {
			this.removeWidget(widget);
		}
		tabWidgets.clear();
		moduleRows.clear();
		searchBox = null;
		profileBox = null;
		renameBox = null;
		hexBox = null;
		relayoutTask = () -> {
		};
	}

	private void switchTab(Tab next) {
		if (tab != next) {
			tab = next;
			tabStamp = System.currentTimeMillis();
			buildTab();
		}
	}

	private void buildTab() {
		clearTab();
		// Sidebar navigation (rebuilt per tab so selection highlights update).
		for (Tab value : Tab.values()) {
			NavButton button = new NavButton(value);
			track(button);
		}
		ActionButton disconnect = new DisconnectButton();
		track(disconnect);

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

	private void relayoutNav(Runnable content) {
		double s = scale();
		int x = winX();
		int y = winY();
		int sb = (int) (176 * s);
		int i = 0;
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof NavButton nav) {
				int by = y + (int) (64 * s) + i * (int) (34 * s);
				nav.setX(x + (int) (12 * s));
				nav.setY(by);
				nav.setSize(sb - (int) (24 * s), (int) (28 * s));
				i++;
			}
		}
		for (CelestialWidget widget : tabWidgets) {
			if (widget instanceof DisconnectButton) {
				widget.setX(x + (int) (12 * s));
				widget.setY(y + winH() - (int) (44 * s));
				widget.setSize(sb - (int) (24 * s), (int) (28 * s));
			}
		}
		content.run();
	}

	private void buildHome() {
		String[] quickIds = {"xray", "antiafk", "fullbright", "sprint", "fly"};
		for (String id : quickIds) {
			Module module = ModuleManager.byId(id);
			if (module != null) {
				track(new QuickPill(module));
			}
		}
		relayoutTask = () -> relayoutNav(() -> {
			double s = scale();
			int cx = winX() + (int) (176 * s);
			int cw = winW() - (int) (176 * s);
			int y = winY() + (int) (168 * s);
			int gap = (int) (8 * s);
			int w = (cw - (int) (40 * s) - gap * 4) / 5;
			int j = 0;
			for (CelestialWidget widget : tabWidgets) {
				if (widget instanceof QuickPill) {
					widget.setX(cx + (int) (20 * s) + j * (w + gap));
					widget.setY(y);
					widget.setSize(w, (int) (46 * s));
					j++;
				}
			}
		});
	}

	private void buildModules() {
		EditBox box = new EditBox(this.font, 0, 0, 100, 20, Component.literal("Search"));
		box.setHint(Component.literal("Search modules..."));
		box.setMaxLength(32);
		box.setTextColor(0xFFFFFFFF);
		if (!search.isEmpty()) {
			box.setValue(search);
		}
		box.setResponder(value -> {
			search = String.valueOf(value);
			rebuildModuleRows();
		});
		searchBox = box;
		trackBox(box);

		for (Category category : Category.values()) {
			track(new CategoryButton(category));
		}
		track(new AllButton());
		rebuildModuleRows();

		relayoutTask = () -> relayoutNav(() -> {
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
			for (CelestialWidget widget : tabWidgets) {
				if (widget instanceof CategoryButton || widget instanceof AllButton) {
					widget.setX(bx);
					widget.setY(winY() + (int) (88 * s));
					widget.setSize((int) (86 * s), (int) (22 * s));
					bx += (int) (92 * s);
				}
			}
			int ry = winY() + (int) (118 * s);
			for (ModuleRow row : moduleRows) {
				row.setX(px);
				row.setY(ry);
				row.setSize(pw, (int) (34 * s));
				ry += (int) (38 * s);
			}
		});
	}

	private void rebuildModuleRows() {
		for (ModuleRow row : moduleRows) {
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
			track(row);
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
		track(new ToggleRow("Show FPS", () -> settings.showFps, value -> {
			settings.showFps = value;
			Store.save(settings);
		}));
		track(new ToggleRow("Show Ping", () -> settings.showPing, value -> {
			settings.showPing = value;
			Store.save(settings);
		}));

		relayoutTask = () -> relayoutNav(() -> {
			double s = scale();
			int cx = winX() + (int) (176 * s);
			int pw = winW() - (int) (176 * s) - (int) (40 * s);
			int px = cx + (int) (20 * s);
			int ry = winY() + (int) (64 * s);
			for (CelestialWidget widget : tabWidgets) {
				if (widget instanceof StepperRow || widget instanceof ToggleRow) {
					widget.setX(px);
					widget.setY(ry);
					widget.setSize(pw, (int) (32 * s));
					ry += (int) (38 * s);
				}
			}
		});
	}

	private void buildProfiles() {
		EditBox box = new EditBox(this.font, 0, 0, 100, 20, Component.literal("Name"));
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
			}
		}));
		EditBox rename = new EditBox(this.font, 0, 0, 100, 20, Component.literal("Rename"));
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
			}
		}));

		relayoutTask = () -> relayoutNav(() -> {
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
			for (CelestialWidget widget : tabWidgets) {
				if (widget instanceof CreateButton || widget instanceof RenameButton) {
					widget.setX(px + pw - (int) (94 * s));
					widget.setY(top + (first ? 0 : (int) (30 * s)));
					widget.setSize((int) (94 * s), (int) (22 * s));
					first = false;
				}
			}
			java.util.Map<String, Integer> rowY = new java.util.HashMap<>();
			int ry = top + (int) (64 * s);
			for (CelestialWidget widget : tabWidgets) {
				if (widget instanceof ProfileRow row) {
					row.setX(px);
					row.setY(ry);
					row.setSize(pw, (int) (30 * s));
					rowY.put(row.name, ry);
					ry += (int) (34 * s);
				}
			}
			for (CelestialWidget widget : tabWidgets) {
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
		});
		refreshProfileRows();
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
				});
				delete.tag = "del:" + name;
				tabWidgets.add(delete);
				this.addRenderableWidget(delete);
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
		EditBox box = new EditBox(this.font, 0, 0, 100, 20, Component.literal("Hex"));
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

		relayoutTask = () -> relayoutNav(() -> {
			double s = scale();
			int cx = winX() + (int) (176 * s);
			int pw = winW() - (int) (176 * s) - (int) (40 * s);
			int px = cx + (int) (20 * s);
			int ry = winY() + (int) (64 * s);
			for (CelestialWidget widget : tabWidgets) {
				if (widget instanceof ThemeButton) {
					widget.setX(px);
					widget.setY(ry);
					widget.setSize(pw, (int) (30 * s));
					ry += (int) (34 * s);
				}
			}
			ry += (int) (6 * s);
			int sx = px;
			for (CelestialWidget widget : tabWidgets) {
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
			for (CelestialWidget widget : tabWidgets) {
				if (widget instanceof ApplyButton) {
					widget.setX(px + pw - (int) (94 * s));
					widget.setY(ry);
					widget.setSize((int) (94 * s), (int) (22 * s));
				}
			}
		});
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
		relayoutTask.run();
		drawBackdrop(graphics);
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
		graphics.text(this.font, "ESC to close", x + w - this.font.width("ESC to close") - (int) (14 * s),
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
					"9 modules across Render, Movement and Player,",
					"live themes, profiles, FPS/Ping HUD,",
					"and an ESC menu that respects singleplayer pause."
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
				graphics.text(this.font, "GUI Key: ESC (fixed)" + (extra.isEmpty() ? "" : " + " + extra), tx, ty,
					CelestialWidget.withAlpha(0xFFAAAAAA, a), true);
				graphics.text(this.font, "Extra key rebindable in Options > Controls > Celestial", tx,
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
				current.rebuildModuleRows();
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
				current.rebuildModuleRows();
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
		float a = anim();
		int track = on ? accent() : 0xFF3A3A40;
		g.fill(x, y, x + w, y + h, withAlpha(track & 0x00FFFFFF | (on ? 0xCC000000 : 0xFF000000), a));
		g.outline(x, y, x + w, y + h, withAlpha(on ? accent() : 0xFF55555F, a));
		int knob = h - 4;
		int travel = w - knob - 4;
		int from = on ? x + 2 : x + 2 + travel;
		int to = on ? x + 2 + travel : x + 2;
		int kx = (int) (from + (to - from) * t);
		g.fill(kx, y + 2, kx + knob, y + 2 + knob, withAlpha(0xFFFFFFFF, a));
	}
}
