// SkyCraft+ : Skyrim's workbenches taken and placed
package dev.skycraft.craft;

import dev.skycraft.SkyCraft;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Skyrim's workbenches as things to carry: one mined (with a pickaxe, or anything: SkyWorkbenchesClient) is
 * taken out of Skyrim's world whole (its client asks Skyrim, Proto.STORE_TAKE_WORKBENCH) and comes as
 * this item; used on a block it stands there in Skyrim again (Proto.EV_PLACE_WORKBENCH), kept in
 * the save, and works as ever. Each can also be made at Minecraft's crafting table (data/skycraft/
 * recipe/skyrim_*.json). The item keeps the furniture's FormID ("skycraft_workbench") and its name.
 * Setting: skyrimWorkbenchesTaken.
 */
public final class SkyWorkbenches extends Item {
	public static final ResourceKey<Item> KEY = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "skyrim_workbench"));
	public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, KEY, new SkyWorkbenches(new Item.Properties().setId(KEY).stacksTo(1)));
	private static final String TAG = "skycraft_workbench";

	// What each looks like in Minecraft's hand: its Minecraft counterpart (by furniture, else by bench kind).
	private static final Map<Integer, String> LOOKS = Map.ofEntries(
		Map.entry(0x000CAE0B, "blast_furnace"), Map.entry(0x000BF9E1, "blast_furnace"), Map.entry(0x000BBCF1, "blast_furnace"),
		Map.entry(0x0001A2AD, "anvil"), Map.entry(0x0009C6CE, "furnace"), Map.entry(0x000D932F, "smithing_table"),
		Map.entry(0x0006E9C2, "grindstone"), Map.entry(0x000727A1, "loom"), Map.entry(0x00068ADB, "campfire"),
		Map.entry(0x001010B3, "cauldron"), Map.entry(0x00104110, "cauldron"), Map.entry(0x00108230, "cauldron"),
		Map.entry(0x000BAD0D, "enchanting_table"), Map.entry(0x000D5501, "enchanting_table"), Map.entry(0x000E7AEA, "enchanting_table"),
		Map.entry(0x000BAD0C, "brewing_stand"), Map.entry(0x000D54FF, "brewing_stand"));
	private static final String[] KIND_LOOKS = { "crafting_table", "blast_furnace", "grindstone", "enchanting_table", "enchanting_table", "brewing_stand", "brewing_stand", "smithing_table" };

	/** The client's side (SkyWorkbenchesClient): tells its Skyrim to stand the workbench there. */
	public static volatile PlaceHook clientPlace = (formId, foot, yaw) -> false;

	@FunctionalInterface
	public interface PlaceHook {
		/** foot: where it stands (on a top face, the point the click landed on: Skyrim's ground runs through its cell). */
		boolean place(int formId, net.minecraft.world.phys.Vec3 foot, float yaw);
	}

	private SkyWorkbenches(Item.Properties properties) {
		super(properties);
	}

	/** Client -> server: Skyrim gave up this workbench to the player (it was mined): it's theirs to carry. */
	public record Taken(int formId, String name, int kind) implements CustomPacketPayload {
		public static final Type<Taken> TYPE = new Type<>(Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "workbench_taken"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Taken> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, Taken::formId,
			ByteBufCodecs.STRING_UTF8, Taken::name,
			ByteBufCodecs.INT, Taken::kind,
			Taken::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Taken.TYPE, Taken.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Taken.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			context.server().execute(() -> {
				ItemStack stack = make(payload.formId(), payload.name(), payload.kind());
				if (!player.getInventory().add(stack)) {
					player.spawnAtLocation(player.level(), stack);
				}
				SkyCraft.LOG.info("SkyCraft: {} took Skyrim's {} ({})", player.getName().getString(), payload.name(), Integer.toHexString(payload.formId()));
			});
		});
		net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(net.minecraft.world.item.CreativeModeTabs.FUNCTIONAL_BLOCKS)
			.register(output -> {
				output.accept(make(0x000CAE0B, "Blacksmith Forge", 1));
				output.accept(make(0x0001A2AD, "Anvil", 1));
				output.accept(make(0x000D932F, "Workbench", 7));
				output.accept(make(0x0006E9C2, "Grindstone", 2));
				output.accept(make(0x0009C6CE, "Smelter", 1));
				output.accept(make(0x000727A1, "Tanning Rack", 1));
				output.accept(make(0x00068ADB, "Cooking Pot", 1));
				output.accept(make(0x000BAD0D, "Arcane Enchanter", 3));
				output.accept(make(0x000BAD0C, "Alchemy Lab", 5));
			});
	}

	/** The item for a Skyrim workbench: its furniture, its name and its bench kind (BenchType). */
	public static ItemStack make(int formId, String name, int kind) {
		ItemStack stack = new ItemStack(ITEM);
		CompoundTag tag = new CompoundTag();
		tag.putInt(TAG, formId);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.literal(name.isEmpty() ? "Skyrim Workbench" : name));
		String look = LOOKS.getOrDefault(formId, KIND_LOOKS[Math.clamp(kind, 0, KIND_LOOKS.length - 1)]);
		stack.set(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace(look));
		return stack;
	}

	/** The furniture a Skyrim workbench item stands for, or 0. */
	public static int formId(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : data.copyTag().getIntOr(TAG, 0);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		int formId = formId(context.getItemInHand());
		if (formId == 0 || context.getPlayer() == null) {
			return InteractionResult.PASS;
		}
		BlockPos at = context.getClickedPos().relative(context.getClickedFace());
		if (context.getLevel().isClientSide()) {
			// On a top face, where the click landed: Skyrim's ground lies inside its cell (stood on the cell above, it
			// floated), and the crosshair isn't the cell's middle (it stood off from it).
			var click = context.getClickLocation();
			var foot = context.getClickedFace() == net.minecraft.core.Direction.UP ? new net.minecraft.world.phys.Vec3(click.x, Math.min(click.y, at.getY()), click.z)
				: net.minecraft.world.phys.Vec3.atBottomCenterOf(at);
			return clientPlace.place(formId, foot, context.getPlayer().getYRot()) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		if (!context.getPlayer().getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
