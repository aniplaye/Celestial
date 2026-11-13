package com.celestial.client.module.modules;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;

/** HUD overlay module: shows server ping. Rendering lives in CelestialClient. */
public class PingModule extends Module {
	public PingModule() {
		super("ping", "Ping Counter", "Show your ping in-game.", Category.HUD, KeyCodes.KEY_N);
	}
}
