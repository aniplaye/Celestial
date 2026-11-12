package com.celestial.mixin;

import com.celestial.client.gui.CelestialGui;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla pause menu: pressing ESC in a world opens the
 * Celestial GUI instead. Only intercepts real key presses (window focused),
 * so auto-pause on alt-tab and all menu screens keep vanilla behavior.
 */
@Mixin(Minecraft.class)
public class PauseMenuMixin {
	@Inject(method = "pauseGame", at = @At("HEAD"), cancellable = true)
	private void celestial$replacePauseMenu(boolean pauseOnlyNarrator, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.level != null && mc.isWindowActive() && !CelestialGui.isOpen()) {
			mc.setScreenAndShow(new CelestialGui());
			ci.cancel();
		}
	}
}
