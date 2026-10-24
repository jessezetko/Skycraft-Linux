// SkyCraft+ : boats on Skyrim's water
package dev.skycraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Minecraft's boats (and rafts) on Skyrim's lakes, rivers and sea: placed on its surface
 * (BoatItemSkyrimWaterMixin), floating and paddled on it (BoatSkyrimWaterMixin). Skyrim's water is
 * only known in a grid round the player (SkyWater): a boat nobody's in that's out of that grid,
 * over nothing of Minecraft's, is held where it is (it would sink to Skyrim's lakebed otherwise),
 * till the player comes back by. Setting: boatsOnSkyrimWater in config/skycraft.properties.
 */
public final class SkyBoats {
	private SkyBoats() {
	}

	private static volatile boolean enabled = true;

	public static void init() {
		enabled = dev.skycraft.SkySettings.flag("boatsOnSkyrimWater", true);
		dev.skycraft.SkySettings.onChange(() -> enabled = dev.skycraft.SkySettings.flag("boatsOnSkyrimWater", true));
	}

	public static boolean enabled() {
		return enabled;
	}

	/** True if this boat should stay where it is this tick. */
	public static boolean hold(AbstractBoat boat) {
		Level level = boat.level();
		if (level.isClientSide() || !boat.getPassengers().isEmpty() || !enabled() || !SkyCollision.linked()
			|| !SkyMinecraftWorlds.skyrimsWorld(level.dimension())) {
			return false;
		}
		BlockPos at = boat.blockPosition();
		// On (or just over) Minecraft's own blocks or water: those hold it up anyway.
		for (int dy = 0; dy <= 1; dy++) {
			BlockPos q = at.below(dy);
			if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty() || !level.getFluidState(q).isEmpty()) {
				return false;
			}
		}
		return !SkyWater.covers(at.getX(), at.getZ());
	}

	/**
	 * A boat paddled into a ledge in Skyrim's river (where it drops a little, a small waterfall):
	 * lifted onto the water above, if that's no more than three blocks up and there's room.
	 * Minecraft's boats never step up (only what stands on the ground does); these got stuck at each.
	 * Run where the boat is driven (its driver's client).
	 */
	public static void climb(AbstractBoat boat) {
		if (!enabled() || !SkyWater.active() || !boat.horizontalCollision || boat.getControllingPassenger() == null || !boat.isLocalInstanceAuthoritative()) {
			return;
		}
		double yaw = Math.toRadians(boat.getYRot());
		double fx = -Math.sin(yaw), fz = Math.cos(yaw);
		// The water above the ledge, just past it or a little further (a fall's lip is often a block or two deep).
		for (double past : new double[] { 0.6, 1.2, 1.8 }) {
			double ahead = boat.getBbWidth() * 0.5 + past;
			double surface = SkyWater.surfaceAt((int) Math.floor(boat.getX() + fx * ahead), (int) Math.floor(boat.getZ() + fz * ahead));
			double rise = surface - boat.getY();
			if (Double.isNaN(surface) || rise <= 0.1 || rise > 3.0) {
				continue;
			}
			double step = past - 0.1;
			if (!boat.level().noCollision(boat, boat.getBoundingBox().move(fx * step, rise + 0.05, fz * step))) {
				continue;
			}
			boat.setPos(boat.getX() + fx * step, surface + 0.05, boat.getZ() + fz * step);
			return;
		}
	}

	/**
	 * The boat item's aim (BoatItem.use): Skyrim's water surface when it's nearer than whatever the
	 * aim hit (or the aim hit nothing), so the boat is put on the water, not on the lakebed.
	 */
	public static BlockHitResult aim(Player player, BlockHitResult hit) {
		if (!enabled() || !SkyWater.active()) {
			return hit;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		double reach = player.blockInteractionRange();
		double limit = hit.getType() == HitResult.Type.MISS ? reach : Math.min(reach, hit.getLocation().distanceTo(eye));
		BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
		for (double t = 0.0; t <= limit; t += 0.05) {
			Vec3 p = eye.add(look.scale(t));
			cell.set(p.x, p.y, p.z);
			double surface = SkyWater.surfaceAt(cell.getX(), cell.getZ());
			if (!Double.isNaN(surface) && p.y <= surface && p.y > surface - 1.5 && player.level().getBlockState(cell).isAir()) {
				Vec3 on = new Vec3(p.x, surface, p.z);
				return new BlockHitResult(on, Direction.UP, BlockPos.containing(on.x, surface - 0.01, on.z), false);
			}
		}
		return hit;
	}
}
