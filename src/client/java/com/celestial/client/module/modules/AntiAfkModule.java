package com.celestial.client.module.modules;

import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;
import com.celestial.client.KeyCodes;

/**
 * AntiAFK: keeps you from being kicked for idling by jumping
 * and pulsing sneak every few minutes. Server-friendly packets only.
 */
public class AntiAfkModule extends Module {
	private static final int IDLE_TICKS = 3600;
	private static final int SNEAK_TICKS = 20;

	private int idleTimer;
	private int sneakTimer;

	public AntiAfkModule() {
		super("antiafk", "AntiAFK", "Avoid idle kicks: jump + sneak pulse.", Category.PLAYER, KeyCodes.KEY_K);
	}

	@Override
	protected void onEnable(Minecraft mc) {
		idleTimer = 0;
		sneakTimer = 0;
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (sneakTimer > 0) {
			sneakTimer--;
			if (sneakTimer == 0) {
				mc.options.keyShift.setDown(false);
			}
			return;
		}
		idleTimer++;
		if (idleTimer >= IDLE_TICKS) {
			idleTimer = 0;
			if (mc.player.onGround()) {
				mc.player.jumpFromGround();
			}
			mc.options.keyShift.setDown(true);
			sneakTimer = SNEAK_TICKS;
		}
	}

	@Override
	protected void onDisable(Minecraft mc) {
		idleTimer = 0;
		sneakTimer = 0;
		mc.options.keyShift.setDown(false);
	}
}
