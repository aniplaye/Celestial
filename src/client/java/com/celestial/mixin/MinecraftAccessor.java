package com.celestial.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes Minecraft's private right-click delay for FastPlace.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("rightClickDelay")
	void celestial$setRightClickDelay(int value);
}
