package com.celestial.client;

import com.celestial.client.config.Settings;
import com.celestial.client.config.Store;
import com.celestial.client.module.ModuleManager;
import com.celestial.client.profile.ProfileManager;

/** Global client state: settings, persistence glue, bulk-load guard. */
public final class Celestial {
	public static final Settings settings = new Settings();
	public static final String VERSION = "v1.0";
	public static final String BUILD_DATE = "2026.11.12";
	public static final String DEVELOPER = "Vawue";

	private static boolean quiet;

	private Celestial() {
	}

	public static void init() {
		Store.load(settings);
		ModuleManager.init();
		ProfileManager.ensureDefault(settings);
		setQuiet(true);
		try {
			ProfileManager.load(settings.activeProfile, settings);
		} finally {
			setQuiet(false);
		}
	}

	public static void onModulesChanged() {
		if (!quiet) {
			ProfileManager.save(settings.activeProfile);
		}
	}

	public static void setQuiet(boolean value) {
		quiet = value;
	}
}
