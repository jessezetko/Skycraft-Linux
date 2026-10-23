// SkyCraft+ : Skyrim's workbenches taken and placed
package dev.skycraft.client;

import dev.skycraft.craft.SkyWorkbenches;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * The player's side of Skyrim's workbenches as things to carry (SkyWorkbenches): a workbench's part
 * (Proto.DIG_WORKBENCH) mined with a pickaxe asks this player's Skyrim to give it up whole; its
 * answer becomes the item. Placing the item tells Skyrim where it stands.
 */
public final class SkyWorkbenchesClient {
	private SkyWorkbenchesClient() {
	}

	private static final Set<Integer> ASKED = new HashSet<>();

	public static void init() {
		SkyWorkbenches.clientPlace = (formId, pos, yaw) -> {
			if (!SkyClient.linked() || !dev.skycraft.SkySettings.flag("skyrimWorkbenchesTaken", true)) {
				return false;
			}
			SkyLink.pushEvent(Proto.EV_PLACE_WORKBENCH, formId, pos.getX() + 0.5f, pos.getY(), pos.getZ() + 0.5f, yaw, 0);
			return true;
		};
	}

	/** A cell came out (SkyDigClient): a workbench's, with a pickaxe, is taken whole instead (true). */
	static boolean mined(Minecraft minecraft, ItemStack tool, int material, double x, double y, double z) {
		if (material != Proto.DIG_WORKBENCH || minecraft.player == null || !dev.skycraft.SkySettings.flag("skyrimWorkbenchesTaken", true)) {
			return false;
		}
		ItemStack used = tool != null ? tool : minecraft.player.getMainHandItem();
		if (!used.is(ItemTags.PICKAXES)) {
			return false;
		}
		int id = SkyLink.pushStoreRequest(Proto.STORE_TAKE_WORKBENCH, 0, 0, (float) x, (float) y, Float.floatToRawIntBits((float) z), 0, "");
		if (id == 0) {
			return false;
		}
		ASKED.add(id);
		return true;
	}

	/** SkyStorageClient hands on Skyrim's answers that aren't its own; true if it was this. */
	static boolean onStoreResult(SkyLink.StoreResult result) {
		if (!ASKED.remove(result.id())) {
			return false;
		}
		if (result.status() == Proto.STORE_OK && result.formId() != 0 && ClientPlayNetworking.canSend(SkyWorkbenches.Taken.TYPE)) {
			ClientPlayNetworking.send(new SkyWorkbenches.Taken(result.formId(), result.name(), result.kind()));
		}
		return true;
	}
}
