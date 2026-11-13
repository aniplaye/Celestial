package com.celestial.client.module.modules;

import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import com.celestial.mixin.EntityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import com.celestial.client.KeyCodes;

/**
 * Entity ESP: outlines players, mobs and animals through walls
 * using the vanilla glowing effect (team-color style).
 */
public class EspModule extends Module {
	private static final int GLOWING_FLAG = 6;

	public EspModule() {
		super("esp", "EntityESP", "Outline entities through walls.", Category.VISUAL, KeyCodes.KEY_J);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.level == null) {
			return;
		}
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity != mc.player && entity instanceof LivingEntity) {
				((EntityAccessor) entity).celestial$setSharedFlag(GLOWING_FLAG, true);
			}
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		clearGlow(mc);
	}

	private static void clearGlow(Minecraft mc) {
		if (mc.level == null) {
			return;
		}
		LocalPlayer self = mc.player;
		for (Entity entity : mc.level.entitiesForRendering()) {
			if (entity != self && entity instanceof LivingEntity) {
				((EntityAccessor) entity).celestial$setSharedFlag(GLOWING_FLAG, false);
			}
		}
	}
}
