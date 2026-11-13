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

	private void syncRect() {
		try {
			setRectangle(getX(), getY(), getWidth(), getHeight());
		} catch (Exception ignored) {
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
}
