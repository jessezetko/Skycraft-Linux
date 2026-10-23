// SkyCraft+ : boats on Skyrim's water
package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyBoats;
import dev.skycraft.world.SkyWater;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A boat finds Skyrim's water (lakes, rivers, the sea) where Minecraft has only air, as it finds
 * Minecraft's: it floats on it at Skyrim's surface, paddles, and goes under it as in Minecraft's
 * (its own water checks read the level's fluids itself, not through EntityFluidInteraction). One
 * left with nobody in it out of reach of Skyrim's water grid is held still, so it doesn't sink to
 * the lakebed when the player walks off. See {@link SkyWater}, {@link SkyBoats}.
 */
@Mixin(AbstractBoat.class)
public abstract class BoatSkyrimWaterMixin {
	@WrapOperation(
		method = { "checkInWater", "isUnderwater", "getWaterLevelAbove" },
		at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getFluidState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/material/FluidState;")
	)
	private FluidState skycraft$skyrimWater(Level level, BlockPos pos, Operation<FluidState> original) {
		FluidState state = original.call(level, pos);
		if (state.isEmpty() && SkyBoats.enabled() && SkyWater.active()) {
			FluidState water = SkyWater.fluidAt(level, pos);
			if (water != null) {
				return water;
			}
		}
		return state;
	}

	@WrapOperation(
		method = { "checkInWater", "isUnderwater", "getWaterLevelAbove" },
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/level/material/FluidState;getHeight(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"
		)
	)
	private float skycraft$skyrimWaterHeight(FluidState state, BlockGetter level, BlockPos pos, Operation<Float> original) {
		float height = SkyBoats.enabled() && SkyWater.active() ? SkyWater.substitutedHeight(level, pos) : -1.0F;
		return height >= 0.0F ? height : original.call(state, level, pos);
	}

	@Inject(method = "tick", at = @At("HEAD"), cancellable = true)
	private void skycraft$holdOutOfReach(CallbackInfo ci) {
		AbstractBoat boat = (AbstractBoat) (Object) this;
		if (SkyBoats.hold(boat)) {
			boat.setDeltaMovement(Vec3.ZERO);
			ci.cancel();
		}
	}
}
