package com.celestial.client.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

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
		add(CpsModule::new);
		add(FpsModule::new);
		add(PingModule::new);
		add(CoordsModule::new);
		add(XRayModule::new);
		add(FullBrightModule::new);
		add(AntiAfkModule::new);
		add(FlyModule::new);
		add(SpeedModule::new);
		add(SprintModule::new);
		add(NoFallModule::new);
		add(StepModule::new);
		add(EspModule::new);
		add(ZoomModule::new);
		add(FpsBoostModule::new);
		add(FastPlaceModule::new);
		// First run: keep the HUD the settings describe (migrates showFps/
		// showPing/showCoords into the new counter modules). Returning players
		// keep whatever their profile saved, since load() overrides these.
		setInitial("fps", com.celestial.client.Celestial.settings.showFps);
		setInitial("ping", com.celestial.client.Celestial.settings.showPing);
		setInitial("coords", com.celestial.client.Celestial.settings.showCoords);
	}

	/** One broken module must never take down the whole menu. */
	private static void add(Supplier<Module> factory) {
		try {
			MODULES.add(factory.get());
		} catch (Exception e) {
			System.out.println("[Celestial] skipping broken module: " + e);
		}
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
