package com.celestial.client.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.celestial.client.module.ModuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Loads/saves settings + module states. Called on init, on every toggle,
 * and whenever a setting changes — so nothing is ever lost.
 */
public final class Store {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private Store() {
	}

	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("celestial");
	}

	public static Path settingsFile() {
		return dir().resolve("celestial.json");
	}

	public static Path profilesDir() {
		return dir().resolve("profiles");
	}

	public static void load(Settings settings) {
		try {
			Files.createDirectories(dir());
			Files.createDirectories(profilesDir());
		} catch (IOException ignored) {
		}
		Path file = settingsFile();
		if (Files.isRegularFile(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				Settings loaded = GSON.fromJson(reader, Settings.class);
				if (loaded != null) {
					settings.guiScale = clamp(loaded.guiScale, 0.75, 1.5);
					settings.opacity = clamp(loaded.opacity, 0.5, 1.0);
					settings.blurLayers = Math.max(0, Math.min(3, loaded.blurLayers));
					settings.animations = loaded.animations;
					settings.showFps = loaded.showFps;
					settings.showPing = loaded.showPing;
					settings.theme = loaded.theme != null ? loaded.theme : "pink";
					settings.customColor = loaded.customColor;
					settings.activeProfile = loaded.activeProfile != null ? loaded.activeProfile : "Default";
				}
			} catch (Exception ignored) {
			}
		}
		Theme.current = Theme.byId(settings.theme);
		if (settings.theme.equalsIgnoreCase("custom")) {
			Theme.current = new Theme("custom", "Custom", settings.customColor, settings.customColor);
		}
	}

	public static void save(Settings settings) {
		try {
			Files.createDirectories(dir());
			try (Writer writer = Files.newBufferedWriter(settingsFile(), StandardCharsets.UTF_8)) {
				GSON.toJson(settings, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public static void applyTheme(Settings settings) {
		if (settings.theme.equalsIgnoreCase("custom")) {
			Theme.current = new Theme("custom", "Custom", settings.customColor, settings.customColor);
		} else {
			Theme.current = Theme.byId(settings.theme);
		}
		save(settings);
	}

	private static double clamp(double value, double min, double max) {
		if (Double.isNaN(value)) {
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}

	/** Snapshot of enabled modules, stored inside each profile file. */
	public static class ModuleStates {
		public java.util.Map<String, Boolean> modules = new java.util.HashMap<>();
	}
}
