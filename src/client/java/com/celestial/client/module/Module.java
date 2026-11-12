package com.celestial.client.module;

import com.celestial.client.Celestial;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Base class for all Celestial modules. Each module gets a rebindable key
 * (Options &gt; Controls &gt; Celestial) and ticks every client tick while enabled.
 */
public abstract class Module {
	private final String id;
	private final String name;
	private final String description;
	private final Category category;
	private final KeyMapping key;
	private boolean enabled;

	protected Module(String id, String name, String description, Category category, int defaultKey) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.category = category;
		this.key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.celestial." + id,
			InputConstants.Type.KEYBOARD,
			defaultKey,
			KeyMapping.Category.MISC));
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public Category getCategory() {
		return category;
	}

	public KeyMapping getKey() {
		return key;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean value) {
		setEnabled(value, true);
	}

	public void setEnabled(boolean value, boolean announce) {
		if (this.enabled == value) {
			return;
		}
		this.enabled = value;
		Minecraft mc = Minecraft.getInstance();
		if (value) {
			onEnable(mc);
		} else {
			onDisable(mc);
		}
		if (announce && mc.player != null) {
			mc.player.sendSystemMessage(Component.literal((value ? "\u00A7d[+] " : "\u00A78[-] ") + "\u00A7f" + name));
		}
		Celestial.onModulesChanged();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	/** Consume queued key presses for this module's bind. Called every tick. */
	public void handleKey() {
		while (key.consumeClick()) {
			toggle();
		}
	}

	protected void onEnable(Minecraft mc) {
	}

	protected void onDisable(Minecraft mc) {
	}

	public void onTick(Minecraft mc) {
	}
}
