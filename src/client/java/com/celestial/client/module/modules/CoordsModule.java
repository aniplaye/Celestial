package com.celestial.client.module.modules;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;

/** HUD overlay module: shows XYZ coordinates. Rendering lives in CelestialClient. */
public class CoordsModule extends Module {
	public CoordsModule() {
		super("coords", "Coords", "Show your coordinates in-game.", Category.HUD, KeyCodes.KEY_I);
	}
}
