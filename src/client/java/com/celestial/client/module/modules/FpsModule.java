package com.celestial.client.module.modules;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;

/** HUD overlay module: shows FPS. Rendering lives in CelestialClient. */
public class FpsModule extends Module {
	public FpsModule() {
		super("fps", "FPS Counter", "Display your FPS in-game.", Category.HUD, KeyCodes.KEY_H);
	}
}
