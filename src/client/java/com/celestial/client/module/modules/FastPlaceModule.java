package com.celestial.client.module.modules;

import com.celestial.client.KeyCodes;
import com.celestial.mixin.MinecraftAccessor;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;

public class FastPlaceModule extends Module {
	public FastPlaceModule() {
		super("fastplace", "FastPlace", "Remove the right-click placement delay.", Category.GAMEPLAY, KeyCodes.KEY_F);
	}

	@Override
	public void onTick(Minecraft mc) {
		((MinecraftAccessor) mc).celestial$setRightClickDelay(0);
	}

	@Override
	protected void onDisable(Minecraft mc) {
		((MinecraftAccessor) mc).celestial$setRightClickDelay(4);
	}
}
