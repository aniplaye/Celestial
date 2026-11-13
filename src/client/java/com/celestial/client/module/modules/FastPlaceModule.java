package com.celestial.client.module.modules;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;

public class FastPlaceModule extends Module {
	public FastPlaceModule() {
		super("fastplace", "FastPlace", "Remove the right-click placement delay.", Category.PLAYER, KeyCodes.KEY_F);
	}

	@Override
	public void onTick(Minecraft mc) {
		mc.rightClickDelay = 0;
	}

	@Override
	protected void onDisable(Minecraft mc) {
		mc.rightClickDelay = 4;
	}
}
