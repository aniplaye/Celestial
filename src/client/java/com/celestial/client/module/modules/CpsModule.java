package com.celestial.client.module.modules;

import java.util.ArrayDeque;
import java.util.Deque;

import com.celestial.client.KeyCodes;
import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * HUD overlay module: clicks per second, left and right separately.
 * Uses two mouse-button key mappings (GLFW 0 = left, 1 = right).
 * KeyMapping queues every press, so counting in the client tick stays
 * accurate even though ticks run at 20 Hz. Presses made while any
 * screen is open (including our own menu) are drained, not counted.
 */
public class CpsModule extends Module {
	private final KeyMapping leftClick;
	private final KeyMapping rightClick;
	private final Deque<Long> leftStamps = new ArrayDeque<>();
	private final Deque<Long> rightStamps = new ArrayDeque<>();
	private static int leftCps;
	private static int rightCps;

	public CpsModule() {
		super("cps", "CPS Counter", "Display your clicks per second.", Category.HUD, KeyCodes.KEY_U);
		leftClick = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.celestial.cps_left",
			InputConstants.Type.MOUSE, 0, KeyMapping.Category.MISC));
		rightClick = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.celestial.cps_right",
			InputConstants.Type.MOUSE, 1, KeyMapping.Category.MISC));
	}

	public static int getLeftCps() {
		return leftCps;
	}

	public static int getRightCps() {
		return rightCps;
	}

	@Override
	public void onTick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (mc.screen != null) {
			drain(leftClick);
			drain(rightClick);
		} else {
			while (leftClick.consumeClick()) {
				leftStamps.addLast(now);
			}
			while (rightClick.consumeClick()) {
				rightStamps.addLast(now);
			}
		}
		prune(leftStamps, now);
		prune(rightStamps, now);
		leftCps = leftStamps.size();
		rightCps = rightStamps.size();
	}

	private static void drain(KeyMapping key) {
		while (key.consumeClick()) {
		}
	}

	private static void prune(Deque<Long> stamps, long now) {
		while (!stamps.isEmpty() && now - stamps.peekFirst() > 1000) {
			stamps.removeFirst();
		}
	}
}
