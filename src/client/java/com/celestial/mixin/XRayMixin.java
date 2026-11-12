package com.celestial.mixin;

import com.celestial.client.module.modules.XRayModule;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * XRay: culls every block face except whitelisted ores/chests at mesh time.
 * Single injection point covers all block types (no per-block overrides to chase).
 */
@Mixin(net.minecraft.client.renderer.block.ModelBlockRenderer.class)
public class XRayMixin {
	@Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
	private void celestial$xrayCull(BlockAndTintGetter level, BlockState state, Direction direction, BlockPos pos,
			CallbackInfoReturnable<Boolean> cir) {
		if (XRayModule.shouldCull(state)) {
			cir.setReturnValue(false);
		}
	}
}
