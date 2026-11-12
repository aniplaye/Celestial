package com.celestial.client.module.modules;

import java.util.HashSet;
import java.util.Set;

import com.celestial.client.module.Category;
import com.celestial.client.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.celestial.client.KeyCodes;

/**
 * XRay: hides every block except ores/chests/spawners.
 * Rendering is filtered by {@code XRayMixin} at mesh time; toggling
 * re-meshes the world through the same path as vanilla F3+A.
 */
public class XRayModule extends Module {
	private static boolean xrayActive;
	private static final Set<Block> WHITELIST = new HashSet<>();

	static {
		WHITELIST.add(Blocks.DIAMOND_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_DIAMOND_ORE);
		WHITELIST.add(Blocks.GOLD_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_GOLD_ORE);
		WHITELIST.add(Blocks.IRON_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_IRON_ORE);
		WHITELIST.add(Blocks.COAL_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_COAL_ORE);
		WHITELIST.add(Blocks.REDSTONE_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_REDSTONE_ORE);
		WHITELIST.add(Blocks.LAPIS_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_LAPIS_ORE);
		WHITELIST.add(Blocks.EMERALD_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_EMERALD_ORE);
		WHITELIST.add(Blocks.COPPER_ORE);
		WHITELIST.add(Blocks.DEEPSLATE_COPPER_ORE);
		WHITELIST.add(Blocks.NETHER_GOLD_ORE);
		WHITELIST.add(Blocks.NETHER_QUARTZ_ORE);
		WHITELIST.add(Blocks.ANCIENT_DEBRIS);
		WHITELIST.add(Blocks.SPAWNER);
		WHITELIST.add(Blocks.TRIAL_SPAWNER);
		WHITELIST.add(Blocks.CHEST);
		WHITELIST.add(Blocks.ENDER_CHEST);
		WHITELIST.add(Blocks.TRAPPED_CHEST);
		WHITELIST.add(Blocks.BARREL);
		WHITELIST.add(Blocks.SHULKER_BOX);
	}

	public XRayModule() {
		super("xray", "XRay", "See ores through walls.", Category.RENDER, KeyCodes.KEY_X);
	}

	/** Called from the render mixin for every meshed face. */
	public static boolean shouldCull(BlockState state) {
		return xrayActive && !WHITELIST.contains(state.getBlock());
	}

	public static boolean isWhitelisted(Block block) {
		return WHITELIST.contains(block);
	}

	@Override
	protected void onEnable(Minecraft mc) {
		xrayActive = true;
		refreshChunks(mc);
	}

	@Override
	protected void onDisable(Minecraft mc) {
		xrayActive = false;
		refreshChunks(mc);
	}

	private static void refreshChunks(Minecraft mc) {
		if (mc.levelExtractor != null) {
			mc.levelExtractor.allChanged();
		}
	}
}
