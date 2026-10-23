// SkyCraft+ : boats on Skyrim's water
package dev.skycraft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skycraft.world.SkyBoats;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A boat aimed at Skyrim's water is put on its surface, not on the lakebed under it (SkyBoats). */
@Mixin(BoatItem.class)
public abstract class BoatItemSkyrimWaterMixin {
	@WrapOperation(
		method = "use",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/item/BoatItem;getPlayerPOVHitResult(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/ClipContext$Fluid;)Lnet/minecraft/world/phys/BlockHitResult;"
		)
	)
	private BlockHitResult skycraft$onSkyrimWater(Level level, Player player, ClipContext.Fluid fluid, Operation<BlockHitResult> original) {
		return SkyBoats.aim(player, original.call(level, player, fluid));
	}
}
