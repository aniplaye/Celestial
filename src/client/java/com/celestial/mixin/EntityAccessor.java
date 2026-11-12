package com.celestial.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes Entity's protected glowing-flag setter for EntityESP.
 */
@Mixin(Entity.class)
public interface EntityAccessor {
	@Invoker("setSharedFlag")
	void celestial$setSharedFlag(int flag, boolean value);
}
