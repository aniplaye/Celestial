package com.celestial.client.module.modules;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;

public class ZoomModule extends Module {
	private int previousFov = 70;

	public ZoomModule() {
		super("zoom", "Zoom", "Narrow the field of view for a closer look.", Category.RENDER, KeyCodes.KEY_C);
	}

	@Override
	protected void onEnable(Minecraft mc) {
		previousFov = mc.options.fov().get();
		mc.options.fov().set(30);
	}

	@Override
	protected void onDisable(Minecraft mc) {
		mc.options.fov().set(previousFov);
	}
}
