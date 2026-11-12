package com.celestial.client.profile;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.celestial.client.config.Settings;
import com.celestial.client.config.Store;
import com.celestial.client.module.Module;
import com.celestial.client.module.ModuleManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Named profiles: each stores which modules are enabled.
 * Files live in config/celestial/profiles/&lt;name&gt;.json.
 */
public final class ProfileManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ProfileManager() {
	}

	public static List<String> list() {
		List<String> names = new ArrayList<>();
		try {
			Files.createDirectories(Store.profilesDir());
			try (Stream<Path> stream = Files.list(Store.profilesDir())) {
				stream.filter(path -> path.toString().endsWith(".json")).forEach(path -> {
					String file = path.getFileName().toString();
					names.add(file.substring(0, file.length() - 5));
				});
			}
		} catch (IOException ignored) {
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	public static void ensureDefault(Settings settings) {
		if (!list().contains("Default")) {
			save("Default");
		}
		if (!list().contains(settings.activeProfile)) {
			settings.activeProfile = "Default";
			Store.save(settings);
		}
	}

	public static void save(String name) {
		Store.ModuleStates states = new Store.ModuleStates();
		for (Module module : ModuleManager.getModules()) {
			states.modules.put(module.getId(), module.isEnabled());
		}
		try {
			Files.createDirectories(Store.profilesDir());
			try (Writer writer = Files.newBufferedWriter(profileFile(name), StandardCharsets.UTF_8)) {
				GSON.toJson(states, writer);
			}
		} catch (IOException ignored) {
		}
	}

	public static void load(String name, Settings settings) {
		Path file = profileFile(name);
		if (!Files.isRegularFile(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Store.ModuleStates states = GSON.fromJson(reader, Store.ModuleStates.class);
			if (states != null && states.modules != null) {
				for (Module module : ModuleManager.getModules()) {
					Boolean enabled = states.modules.get(module.getId());
					if (enabled != null) {
						module.setEnabled(enabled, false);
					}
				}
			}
		} catch (Exception ignored) {
		}
		settings.activeProfile = name;
		Store.save(settings);
	}

	public static void create(String name) {
		String clean = cleanName(name);
		if (clean.isEmpty() || list().contains(clean)) {
			return;
		}
		save(clean);
	}

	public static void delete(String name, Settings settings) {
		if (name.equalsIgnoreCase("Default")) {
			return;
		}
		try {
			Files.deleteIfExists(profileFile(name));
		} catch (IOException ignored) {
		}
		if (settings.activeProfile.equalsIgnoreCase(name)) {
			settings.activeProfile = "Default";
			Store.save(settings);
		}
	}

	public static void rename(String from, String to, Settings settings) {
		String clean = cleanName(to);
		if (clean.isEmpty() || from.equalsIgnoreCase("Default") || list().contains(clean)) {
			return;
		}
		try {
			Files.move(profileFile(from), profileFile(clean));
		} catch (IOException ignored) {
			return;
		}
		if (settings.activeProfile.equalsIgnoreCase(from)) {
			settings.activeProfile = clean;
			Store.save(settings);
		}
	}

	private static Path profileFile(String name) {
		return Store.profilesDir().resolve(cleanName(name) + ".json");
	}

	private static String cleanName(String name) {
		if (name == null) {
			return "";
		}
		String clean = name.trim().replaceAll("[^A-Za-z0-9 _-]", "");
		return clean.length() > 24 ? clean.substring(0, 24) : clean;
	}
}
