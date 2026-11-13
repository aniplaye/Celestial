package com.celestial.client.module.modules;

import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import com.celestial.client.KeyCodes;

public class FlyModule extends Module {
	public FlyModule() {
		super("fly", "Fly", "Creative-style flight. Space up, Shift down.", Category.GAMEPLAY, KeyCodes.KEY_G);
	}

	@Override
	public void onTick(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.getAbilities().setFlyingSpeed(0.12f);
		player.onUpdateAbilities();

		Vec3 motion = player.getDeltaMovement();
		if (mc.options.keyJump.isDown()) {
			player.setDeltaMovement(motion.x, 0.5, motion.z);
		} else if (mc.options.keyShift.isDown()) {
			player.setDeltaMovement(motion.x, -0.5, motion.z);
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		mc.player.getAbilities().flying = false;
		if (!mc.player.getAbilities().instabuild) {
			mc.player.getAbilities().mayfly = false;
		}
		mc.player.getAbilities().setFlyingSpeed(0.05f);
		mc.player.onUpdateAbilities();
	}
}
