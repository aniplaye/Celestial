package com.celestial.mixin;

import com.celestial.client.Celestial;
import com.celestial.client.config.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brands the vanilla title screen so the "homepage" is visibly Celestial:
 * a top-left client pill and a bottom info bar. Vanilla buttons and
 * panorama are untouched, so Singleplayer/Multiplayer keep working.
 *
 * Targets Screen.extractRenderState (which definitely exists) and only
 * draws when the open screen is the TitleScreen, so a missing TitleScreen
 * override can never crash the game.
 */
@Mixin(Screen.class)
public class TitleScreenMixin {
	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void celestial$brand(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick,
		CallbackInfo ci) {
		if (!((Object) this instanceof TitleScreen)) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc == null) {
			return;
		}
		Font font = mc.font;
		if (font == null) {
			return;
		}
		int accent = Theme.current != null ? Theme.current.accent : 0xFFFF69B4;
		String pill = "\u25C6 CELESTIAL " + Celestial.VERSION;
		int pw = font.width(pill) + 20;
		graphics.fill(8, 8, 8 + pw, 28, 0xE0101014);
		graphics.outline(8, 8, 8 + pw, 28, 0x66444444 | (accent & 0x00FFFFFF));
		graphics.fill(8, 26, 8 + pw, 28, accent);
		graphics.text(font, pill, 18, 13, 0xFFFFFFFF, true);

		String left = "Celestial Client for Minecraft 26.3 (Fabric) - press ESC in-game for the menu";
		String right = "Build " + Celestial.BUILD_DATE;
		int w = mc.getWindow().getGuiScaledWidth();
		int h = mc.getWindow().getGuiScaledHeight();
		graphics.fill(0, h - 20, w, h, 0xC008080C);
		graphics.fill(0, h - 20, w, h - 19, accent);
		graphics.text(font, left, 8, h - 13, 0xFFBBBBBB, true);
		graphics.text(font, right, w - font.width(right) - 8, h - 13, accent, true);
	}
}
