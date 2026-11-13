package com.celestial.client.module.modules;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsPreset;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ParticleStatus;

public class FpsBoostModule extends Module {
	private GraphicsPreset previousGraphics;
	private CloudStatus previousClouds;
	private ParticleStatus previousParticles;
	private double previousEntityDistance = 1.0;

	public FpsBoostModule() {
		super("fpsboost", "FPSBoost", "Trades visuals for frames: fast graphics, no clouds, fewer particles.", Category.INTERFACE, KeyCodes.KEY_O);
	}

	@Override
	protected void onEnable(Minecraft mc) {
		previousGraphics = mc.options.graphicsPreset().get();
		previousClouds = mc.options.cloudStatus().get();
		previousParticles = mc.options.particles().get();
		previousEntityDistance = mc.options.entityDistanceScaling().get();
		apply(mc);
	}

	@Override
	protected void onDisable(Minecraft mc) {
		try {
			if (previousGraphics != null) {
				mc.options.graphicsPreset().set(previousGraphics);
			}
		} catch (Exception ignored) {
		}
		try {
			if (previousClouds != null) {
				mc.options.cloudStatus().set(previousClouds);
			}
		} catch (Exception ignored) {
		}
		try {
			if (previousParticles != null) {
				mc.options.particles().set(previousParticles);
			}
		} catch (Exception ignored) {
		}
		try {
			mc.options.entityDistanceScaling().set(previousEntityDistance);
		} catch (Exception ignored) {
		}
	}

	private static void apply(Minecraft mc) {
		try {
			mc.options.graphicsPreset().set(GraphicsPreset.FAST);
		} catch (Exception ignored) {
		}
		try {
			mc.options.cloudStatus().set(CloudStatus.OFF);
		} catch (Exception ignored) {
		}
		try {
			mc.options.particles().set(ParticleStatus.MINIMAL);
		} catch (Exception ignored) {
		}
		try {
			mc.options.entityDistanceScaling().set(0.5);
		} catch (Exception ignored) {
		}
	}
}
