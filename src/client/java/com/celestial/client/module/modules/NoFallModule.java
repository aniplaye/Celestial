package com.celestial.client.module.modules;

import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;
import com.celestial.client.KeyCodes;

public class NoFallModule extends Module {
	public NoFallModule() {
		super("nofall", "NoFall", "Never take fall damage.", Category.GAMEPLAY, KeyCodes.KEY_B);
	}

	@Override
	public void onTick(Minecraft mc) {
		if (mc.player != null) {
			mc.player.resetFallDistance();
		}
	}
}
