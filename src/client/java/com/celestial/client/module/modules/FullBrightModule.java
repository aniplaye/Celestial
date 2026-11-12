package com.celestial.client.module.modules;

import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;
import com.celestial.client.KeyCodes;

public class FullBrightModule extends Module {
	private double previousGamma = 1.0;

	public FullBrightModule() {
		super("fullbright", "FullBright", "Maximum brightness everywhere.", Category.RENDER, KeyCodes.KEY_P);
	}

	@Override
	protected void onEnable(Minecraft mc) {
		previousGamma = mc.options.gamma().get();
		mc.options.gamma().set(16.0);
	}

	@Override
	protected void onDisable(Minecraft mc) {
		mc.options.gamma().set(previousGamma);
	}
}
