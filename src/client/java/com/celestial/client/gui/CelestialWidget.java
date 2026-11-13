package com.celestial.client.gui;

import com.celestial.client.Celestial;
import com.celestial.client.config.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Base class for all Celestial interface widgets. Custom-drawn glassmorphism
 * visuals with vanilla focus/click/sound plumbing inherited from AbstractWidget.
 */
public abstract class CelestialWidget extends AbstractWidget {
	protected CelestialWidget(int x, int y, int width, int height, Component message) {
		super(x, y, width, height, message);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		drawWidget(graphics, mouseX, mouseY, partialTick);
	}

	protected abstract void drawWidget(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick);

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		handleClick(event);
	}

	protected abstract void handleClick(MouseButtonEvent event);

	@Override
	public void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	@Override
	public void setX(int x) {
		super.setX(x);
		syncRect();
	}

	@Override
	public void setY(int y) {
		super.setY(y);
		syncRect();
	}

	@Override
	public void setWidth(int width) {
		super.setWidth(width);
		syncRect();
	}

	@Override
	public void setHeight(int height) {
		super.setHeight(height);
		syncRect();
	}

	@Override
	public void setSize(int width, int height) {
		super.setSize(width, height);
		syncRect();
	}

	private boolean syncingRect;

	private void syncRect() {
		// Vanilla setRectangle() itself calls back into setSize()/setX(),
		// so an unguarded sync recurses forever (StackOverflowError).
		if (syncingRect) {
			return;
		}
		syncingRect = true;
		try {
			setRectangle(getX(), getY(), getWidth(), getHeight());
		} catch (Exception ignored) {
		} finally {
			syncingRect = false;
		}
	}

	/**
	 * Click/hover hit-test straight from the live widget bounds. Vanilla's
	 * cached ScreenRectangle can go stale after programmatic moves, which
	 * made every button look alive but never fire.
	 */
	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return mouseX >= getX() && mouseX <= getX() + getWidth()
			&& mouseY >= getY() && mouseY <= getY() + getHeight();
	}

	// ---------- shared drawing helpers ----------

	protected static float anim() {
		return CelestialGui.contentAlpha();
	}

	protected static int accent() {
		return Theme.current.accent;
	}

	protected static int deep() {
		return Theme.current.deep();
	}

	/** ARGB color with alpha multiplied (0..1). */
	protected static int withAlpha(int argb, float mul) {
		int a = (int) (((argb >>> 24) & 0xFF) * mul);
		return (a << 24) | (argb & 0x00FFFFFF);
	}

	protected static int dim(int argb, float factor) {
		int r = (int) (((argb >> 16) & 0xFF) * factor);
		int g = (int) (((argb >> 8) & 0xFF) * factor);
		int b = (int) ((argb & 0xFF) * factor);
		return 0xFF000000 | (r << 16) | (g << 8) | b;
	}

	protected void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hovered) {
		float a = anim() * (float) Celestial.settings.opacity;
		g.fill(x, y, x + w, y + h, withAlpha(0xE0121216, a));
		if (hovered) {
			g.fill(x, y, x + w, y + h, withAlpha(accent() & 0x00FFFFFF | 0x22000000, a));
		}
		g.outline(x, y, x + w, y + h, withAlpha(hovered ? accent() : (accent() & 0x00FFFFFF | 0x55000000), a));
	}

	protected void text(GuiGraphicsExtractor g, String string, int x, int y, int color) {
		g.text(Minecraft.getInstance().font, string, x, y, withAlpha(color, anim()), true);
	}

	protected void centeredText(GuiGraphicsExtractor g, String string, int centerX, int y, int color) {
		g.centeredText(Minecraft.getInstance().font, string, centerX, y, withAlpha(color, anim()));
	}

	/** Flat rounded fill, no border. */
	protected static void roundedFill(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		g.fill(x + r, y, x + w - r, y + h, color);
		g.fill(x, y + r, x + r, y + h - r, color);
		g.fill(x + w - r, y + r, x + w, y + h - r, color);
	}

	/** Rounded fill with a segmented border (corners left open). */
	protected static void roundedPanel(GuiGraphicsExtractor g, int x, int y, int w, int h, int r,
		int fill, int border) {
		roundedFill(g, x, y, w, h, r, fill);
		if (w <= 0 || h <= 0) {
			return;
		}
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		g.fill(x + r, y, x + w - r, y + 1, border);
		g.fill(x + r, y + h - 1, x + w - r, y + h, border);
		g.fill(x, y + r, x + 1, y + h - r, border);
		g.fill(x + w - 1, y + r, x + w, y + h - r, border);
	}

	/**
	 * Tiny neon glyphs drawn from pixel patterns (no font glyphs needed,
	 * so they render identically on every system).
	 */
	protected static void drawIcon(GuiGraphicsExtractor g, String id, int x, int y, int size, int color) {
		String[] rows = iconRows(id);
		if (rows.length == 0) {
			return;
		}
		int widest = 1;
		for (String row : rows) {
			widest = Math.max(widest, row.length());
		}
		int px = Math.max(1, size / 8);
		int ox = x + (size - widest * px) / 2;
		int oy = y + (size - rows.length * px) / 2;
		int c = withAlpha(color, anim());
		for (int r = 0; r < rows.length; r++) {
			String row = rows[r];
			for (int col = 0; col < row.length(); col++) {
				if (row.charAt(col) == '#') {
					g.fill(ox + col * px, oy + r * px, ox + col * px + px, oy + r * px + px, c);
				}
			}
		}
	}

	private static String[] iconRows(String id) {
		switch (id) {
			case "home":
				return new String[]{"...##...", "..####..", ".######.", "########", ".##..##.", ".##..##."};
			case "cube":
				return new String[]{"..####..", ".##..##.", "########", ".##..##.", "..####.."};
			case "gear":
				return new String[]{"...##...", "...##...", ".######.", "###..###", "###..###", ".######.", "...##...", "...##..."};
			case "person":
				return new String[]{"...##...", "..####..", "...##...", "..####..", ".######.", ".##..##."};
			case "palette":
				return new String[]{"..####..", ".######.", "###.###.", ".######.", "..####.."};
			case "info":
				return new String[]{"..####..", "...##...", "...##...", "...##...", "...##...", "..####.."};
			case "grid":
				return new String[]{"##.##.##", "##.##.##", "........", "##.##.##", "##.##.##", "........", "##.##.##", "##.##.##"};
			case "monitor":
				return new String[]{"########", "#......#", "#......#", "#......#", "########", "...##...", "..####.."};
			case "bars":
				return new String[]{".......#", "......##", ".....###", "....####", "...#####"};
			case "cursor":
				return new String[]{"##......", "###.....", "####....", "#####...", "##.##...", "##..##..", ".....##."};
			case "cross":
				return new String[]{"...##...", "...##...", "..####..", "###..###", "###..###", "..####..", "...##...", "...##..."};
			case "diamond":
				return new String[]{"...##...", "..####..", "########", ".######.", "..####..", "...##..."};
			case "sun":
				return new String[]{"#..##..#", "...##...", ".######.", "...##...", "#..##..#"};
			case "eye":
				return new String[]{"..####..", ".##..##.", "##.##.##", ".##..##.", "..####.."};
			case "zoom":
				return new String[]{"..####..", ".##..##.", ".##..##.", "..####..", "....##..", ".....##."};
			case "fly":
				return new String[]{"##....##", "###..###", "########", ".######.", "...##..."};
			case "bolt":
				return new String[]{"....####", "...##...", "..##....", ".######.", "...##...", "..##....", ".##....."};
			case "dash":
				return new String[]{".....###", "...###..", "###.....", "...###..", ".....###"};
			case "heart":
				return new String[]{".##..##.", "########", "########", ".######.", "..####..", "...##..."};
			case "stairs":
				return new String[]{"......##", "....####", "..######", "########"};
			case "clock":
				return new String[]{"..####..", ".##..##.", ".##.###.", ".##..##.", "..####.."};
			case "box":
				return new String[]{".######.", ".##..##.", ".##..##.", ".######."};
			case "gauge":
				return new String[]{"..####..", ".##..##.", ".##.####", ".##.....", "..####.."};
			case "gamepad":
				return new String[]{".######.", "##.##.##", "########", ".######."};
			case "sliders":
				return new String[]{"########", "...##...", "########", ".##.....", "########", ".....##.", "########"};
			case "search":
				return new String[]{"..####..", ".##..##.", ".##..##.", "..####..", "....##..", ".....##."};
			default:
				return new String[]{"..####..", "..####..", "..####..", "..####.."};
		}
	}
}
