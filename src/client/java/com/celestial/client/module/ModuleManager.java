package com.celestial.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.celestial.client.module.modules.AntiAfkModule;
import com.celestial.client.module.modules.CoordsModule;
import com.celestial.client.module.modules.CpsModule;
import com.celestial.client.module.modules.EspModule;
import com.celestial.client.module.modules.FastPlaceModule;
import com.celestial.client.module.modules.FlyModule;
import com.celestial.client.module.modules.FpsBoostModule;
import com.celestial.client.module.modules.FpsModule;
import com.celestial.client.module.modules.FullBrightModule;
import com.celestial.client.module.modules.NoFallModule;
import com.celestial.client.module.modules.PingModule;
import com.celestial.client.module.modules.SpeedModule;
import com.celestial.client.module.modules.SprintModule;
import com.celestial.client.module.modules.StepModule;
import com.celestial.client.module.modules.XRayModule;
import com.celestial.client.module.modules.ZoomModule;
import net.minecraft.client.Minecraft;

public final class ModuleManager {
	private static final List<Module> MODULES = new ArrayList<>();

	private ModuleManager() {
	}

	public static void init() {
		MODULES.clear();
		MODULES.add(new FpsModule());
		MODULES.add(new PingModule());
		MODULES.add(new CpsModule());
		MODULES.add(new CoordsModule());
		MODULES.add(new XRayModule());
		MODULES.add(new FullBrightModule());
		MODULES.add(new AntiAfkModule());
		MODULES.add(new FlyModule());
		MODULES.add(new SpeedModule());
		MODULES.add(new SprintModule());
		MODULES.add(new NoFallModule());
		MODULES.add(new StepModule());
		MODULES.add(new EspModule());
		MODULES.add(new ZoomModule());
		MODULES.add(new FpsBoostModule());
		MODULES.add(new FastPlaceModule());
		// First run: keep the HUD the settings describe (migrates showFps/
		// showPing/showCoords into the new counter modules). Returning players
		// keep whatever their profile saved, since load() overrides these.
		setInitial("fps", com.celestial.client.Celestial.settings.showFps);
		setInitial("ping", com.celestial.client.Celestial.settings.showPing);
		setInitial("coords", com.celestial.client.Celestial.settings.showCoords);
	}

	private static void setInitial(String id, boolean value) {
		Module module = byId(id);
		if (module != null) {
			module.setEnabled(value, false);
		}
	}

	public static List<Module> getModules() {
		return Collections.unmodifiableList(MODULES);
	}

	public static Module byId(String id) {
		for (Module module : MODULES) {
			if (module.getId().equals(id)) {
				return module;
			}
		}
		return null;
	}

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			return;
		}
		for (Module module : MODULES) {
			module.handleKey();
			if (module.isEnabled()) {
				module.onTick(mc);
			}
		}
	}
}
