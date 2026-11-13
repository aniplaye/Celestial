package com.celestial.client;

import com.celestial.client.config.Theme;
import com.celestial.client.gui.CelestialGui;
import com.celestial.client.module.ModuleManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.Identifier;

public class CelestialClient implements ClientModInitializer {
	private static KeyMapping extraGuiKey;

	public static String extraKeyName() {
		try {
			if (extraGuiKey == null || extraGuiKey.isUnbound()) {
				return "NONE";
			}
			return extraGuiKey.getTranslatedKeyMessage().getString();
		} catch (Exception e) {
			return "?";
		}
	}

	@Override
	public void onInitializeClient() {
		Celestial.init();

		// Optional extra opener (ESC always works via mixin). Unbound by default.
		extraGuiKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.celestial.gui_extra",
			InputConstants.Type.KEYBOARD,
			-1,
			KeyMapping.Category.MISC));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.level == null) {
				return;
			}
			while (extraGuiKey.consumeClick()) {
				CelestialGui.open(client);
			}
			ModuleManager.tick(client);
		});

		HudElementRegistry.addLast(Identifier.withDefaultNamespace("celestial_hud"), (graphics, deltaTracker) -> {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.level == null) {
				return;
			}
			boolean showFps = Celestial.settings.showFps;
			boolean showPing = Celestial.settings.showPing;
			boolean showCoords = Celestial.settings.showCoords;
			if (!showFps && !showPing && !showCoords) {
				return;
			}
			StringBuilder text = new StringBuilder();
			if (showFps) {
				text.append("FPS: ").append(mc.getFps());
			}
			if (showFps && showPing) {
				text.append(" | ");
			}
			if (showPing) {
				text.append("Ping: ").append(pingText(mc));
			}
			String line = text.toString();
			int width = mc.font.width(line);
			int right = mc.getWindow().getGuiScaledWidth() - 6;
			graphics.fill(right - width - 8, 3, right + 2, 17, 0x80000000);
			graphics.fill(right - width - 8, 16, right + 2, 17, Theme.current.accent);
			graphics.text(mc.font, line, right - width - 4, 6, Theme.current.accent, true);
			if (showCoords && mc.player != null) {
				String pos = "XYZ: " + (int) Math.floor(mc.player.getX()) + " / " + (int) Math.floor(mc.player.getY()) + " / " + (int) Math.floor(mc.player.getZ());
				int width2 = mc.font.width(pos);
				graphics.fill(right - width2 - 8, 19, right + 2, 33, 0x80000000);
				graphics.text(mc.font, pos, right - width2 - 4, 22, 0xFFFFFFFF, true);
			}
		});
	}

	private static String pingText(Minecraft mc) {
		try {
			if (mc.getConnection() != null && mc.player != null) {
				PlayerInfo info = mc.getConnection().getPlayerInfo(mc.getGameProfile().id());
				if (info != null) {
					return info.getLatency() + "ms";
				}
			}
		} catch (Exception ignored) {
		}
		return "--";
	}
}
