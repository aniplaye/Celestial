package com.celestial.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Gui's private current-screen field (26.3 moved it from
 * Minecraft to Gui). Lets the menu toggle leave other screens
 * (chat, inventory, …) alone instead of opening over them.
 */
@Mixin(Gui.class)
public interface GuiAccessor {
	@Accessor("screen")
	Screen celestial$getScreen();
}
