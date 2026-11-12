package com.celestial;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CelestialMod implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("celestial");

	@Override
	public void onInitialize() {
		LOGGER.info("Celestial loaded (common entrypoint)");
	}
}
