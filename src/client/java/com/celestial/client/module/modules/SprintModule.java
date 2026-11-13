package com.celestial.client.module.modules;

import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;
import com.celestial.client.KeyCodes;

public class SprintModule extends Module {
	public SprintModule() {
		super("sprint", "Sprint", "Always sprint while moving forward.", Category.GAMEPLAY, KeyCodes.KEY_R);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player == null) {
			return;
		}
		if (mc.options.keyUp.isDown() && !mc.options.keyShift.isDown()) {
			mc.player.setSprinting(true);
		}
	}
}
