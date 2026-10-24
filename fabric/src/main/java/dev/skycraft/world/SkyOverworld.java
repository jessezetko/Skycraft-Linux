// SkyCraft+ : the Overworld Portal
package dev.skycraft.world;

import dev.skycraft.SkyCraft;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * The Overworld Portal: a frame of Minecraft dirt (shaped as a Nether portal's), lit with flint and
 * steel, leads to Minecraft's own overworld (skycraft:overworld, generated as a normal world: its
 * biomes, structures and mobs). There Skyrim's player waits in an empty cell as in the Nether
 * (Proto.DIM_OVERWORLD: daylight, Skyrim's animals and travellers by day and its enemies at night,
 * its guards about villages); Skyrim's inventory, magic and shouts work as anywhere. A return
 * portal is built where the player comes out; going back through it leads to where they left
 * Skyrim's world (SkyMinecraftWorlds). Setting: overworldPortal.
 */
public final class SkyOverworld extends NetherPortalBlock {
	public static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "overworld"));
	public static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "overworld_portal"));
	public static final Block PORTAL = Registry.register(BuiltInRegistries.BLOCK, KEY, new SkyOverworld(BlockBehaviour.Properties.of().noCollision()
		.strength(-1.0F).sound(SoundType.GLASS).lightLevel(state -> 11).setId(KEY)));
	private static final int MAX = 21;

	/** Players standing in one of Minecraft's villages in the overworld (Proto.MC_IN_VILLAGE), checked each second. */
	private static final Set<UUID> IN_VILLAGE = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private SkyOverworld(BlockBehaviour.Properties properties) {
		super(properties);
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			var stack = player.getItemInHand(hand);
			if (!stack.is(Items.FLINT_AND_STEEL) || !frame(level.getBlockState(hit.getBlockPos())) || !dev.skycraft.SkySettings.flag("overworldPortal", true)) {
				return InteractionResult.PASS;
			}
			if (level.dimension() == Level.NETHER || level.dimension() == Level.END) {
				return InteractionResult.PASS;
			}
			BlockPos inside = hit.getBlockPos().relative(hit.getDirection());
			if (!light(level, inside)) {
				return InteractionResult.PASS;
			}
			if (!level.isClientSide()) {
				level.playSound(null, inside, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
				stack.hurtAndBreak(1, player, hand.asEquipmentSlot());
				SkyCraft.LOG.info("SkyCraft: an Overworld Portal lit at {}", inside.toShortString());
			}
			return InteractionResult.SUCCESS;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 != 0) {
				return;
			}
			ServerLevel level = server.getLevel(OVERWORLD);
			IN_VILLAGE.clear();
			if (level == null) {
				return;
			}
			for (ServerPlayer player : level.players()) {
				if (level.structureManager().getStructureWithPieceAt(player.blockPosition(), StructureTags.VILLAGE).isValid()) {
					IN_VILLAGE.add(player.getUUID());
				}
			}
		});
	}

	public static boolean inVillage(UUID player) {
		return IN_VILLAGE.contains(player);
	}

	/** Minecraft dirt, as the frame is made of. */
	private static boolean frame(BlockState state) {
		return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.GRASS_BLOCK);
	}

	private static boolean open(BlockState state) {
		return state.isAir() || state.is(Blocks.FIRE);
	}

	/** Fills the dirt frame round this empty spot (either way it faces) with the portal; false if there's none. */
	private static boolean light(Level level, BlockPos inside) {
		if (!open(level.getBlockState(inside))) {
			return false;
		}
		for (Direction.Axis axis : new Direction.Axis[] { Direction.Axis.X, Direction.Axis.Z }) {
			Direction right = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
			BlockPos bottom = inside;
			for (int n = 0; n < MAX && open(level.getBlockState(bottom.below())); n++) {
				bottom = bottom.below();
			}
			if (!frame(level.getBlockState(bottom.below()))) {
				continue;
			}
			BlockPos left = bottom;
			for (int n = 0; n < MAX && open(level.getBlockState(left.relative(right.getOpposite()))); n++) {
				left = left.relative(right.getOpposite());
			}
			if (!frame(level.getBlockState(left.relative(right.getOpposite())))) {
				continue;
			}
			int width = 0;
			while (width <= MAX && open(level.getBlockState(left.relative(right, width))) && frame(level.getBlockState(left.relative(right, width).below()))) {
				width++;
			}
			if (width < 2 || width > MAX || !frame(level.getBlockState(left.relative(right, width)))) {
				continue;
			}
			int height = 0;
			rows:
			while (height <= MAX) {
				for (int i = 0; i < width; i++) {
					if (!open(level.getBlockState(left.relative(right, i).above(height)))) {
						break rows;
					}
				}
				if (!frame(level.getBlockState(left.relative(right.getOpposite()).above(height))) || !frame(level.getBlockState(left.relative(right, width).above(height)))) {
					break;
				}
				height++;
			}
			if (height < 3 || height > MAX) {
				continue;
			}
			boolean top = true;
			for (int i = 0; i < width && top; i++) {
				top = frame(level.getBlockState(left.relative(right, i).above(height)));
			}
			if (!top) {
				continue;
			}
			if (!level.isClientSide()) {
				BlockState portal = PORTAL.defaultBlockState().setValue(AXIS, axis);
				for (int i = 0; i < width; i++) {
					for (int h = 0; h < height; h++) {
						level.setBlock(left.relative(right, i).above(h), portal, Block.UPDATE_CLIENTS);
					}
				}
			}
			return true;
		}
		return false;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction, BlockPos neighbourPos,
		BlockState neighbour, RandomSource random) {
		// Its frame broken (or the portal next to it gone): it goes, as a Nether portal does.
		Direction.Axis axis = state.getValue(AXIS);
		if (direction.getAxis() != axis && direction.getAxis() != Direction.Axis.Y) {
			return state;
		}
		return neighbour.is(this) || frame(neighbour) ? state : Blocks.AIR.defaultBlockState();
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		// (No zombified piglins.)
	}

	@Override
	public TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
		if (!(entity instanceof ServerPlayer player)) {
			return null;
		}
		if (level.dimension() == OVERWORLD) {
			// Back to where they left Skyrim's world: into the portal they left by, else where it stood.
			ServerLevel back = SkyMinecraftWorlds.leftLevel(player);
			TeleportTransition there = back == null ? null : SkyMinecraftWorlds.intoOverworldPortalLeftBy(player, back);
			if (there == null && back != null) {
				there = SkyMinecraftWorlds.whereTheyLeft(player, back);
			}
			if (there == null && back != null) {
				BlockPos spawn = back.getRespawnData().pos();
				there = new TeleportTransition(back, Vec3.atBottomCenterOf(spawn), Vec3.ZERO, player.getYRot(), 0.0F, TeleportTransition.DO_NOTHING);
			}
			return there;
		}
		ServerLevel overworld = level.getServer().getLevel(OVERWORLD);
		if (overworld == null) {
			return null;
		}
		// The same x and z, on the ground there; a portal back is made beside the player if there's none near.
		int x = pos.getX(), z = pos.getZ();
		overworld.getChunk(x >> 4, z >> 4);
		int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		BlockPos ground = new BlockPos(x, Math.max(y, overworld.getMinY() + 2), z);
		BlockPos portal = findPortal(overworld, ground);
		if (portal == null) {
			buildPortal(overworld, ground);
			portal = ground;
		}
		Vec3 spot = Vec3.atBottomCenterOf(portal).add(0.5, 0.0, 1.5);
		return new TeleportTransition(overworld, spot, Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
	}

	private static BlockPos findPortal(ServerLevel level, BlockPos near) {
		for (BlockPos q : BlockPos.betweenClosed(near.offset(-16, -8, -16), near.offset(16, 8, 16))) {
			if (level.getBlockState(q).is(PORTAL)) {
				return q.immutable();
			}
		}
		return null;
	}

	/** A dirt frame four wide and five high (two by three of portal), facing along x, its floor at ground's level. */
	private static void buildPortal(ServerLevel level, BlockPos ground) {
		BlockState dirt = Blocks.DIRT.defaultBlockState();
		BlockState portal = PORTAL.defaultBlockState().setValue(AXIS, Direction.Axis.X);
		for (int dx = -1; dx <= 2; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				boolean edge = dx == -1 || dx == 2 || dy == -1 || dy == 3;
				level.setBlock(ground.offset(dx, dy, 0), edge ? dirt : portal, Block.UPDATE_CLIENTS);
			}
			// Somewhere to stand in front of it, and the way clear.
			level.setBlock(ground.offset(dx, -1, 1), dirt, Block.UPDATE_CLIENTS);
			for (int dy = 0; dy <= 2; dy++) {
				level.setBlock(ground.offset(dx, dy, 1), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		SkyCraft.LOG.info("SkyCraft: an Overworld Portal made at {} in Minecraft's overworld", ground.toShortString());
	}
}
