// SkyCraft+ : Skyrim's workbenches taken and placed
package dev.skycraft.client;

import dev.skycraft.craft.SkyWorkbenches;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
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
		SkyWorkbenches.clientPlace = (formId, foot, yaw) -> {
			if (!SkyClient.linked() || !dev.skycraft.SkySettings.flag("skyrimWorkbenchesTaken", true)) {
				return false;
			}
			SkyLink.pushEvent(Proto.EV_PLACE_WORKBENCH, formId, (float) foot.x, (float) foot.y, (float) foot.z, yaw, 0);
			return true;
		};
	}

	/**
	 * A cell came out (SkyDigClient): a workbench's is taken whole instead (true). With whatever mined it:
	 * asked for with a pickaxe, but one not taken for a pickaxe (what a Skyrim hand stands in as) came
	 * out as a crafting table, the workbench lost.
	 */
	static boolean mined(Minecraft minecraft, ItemStack tool, int material, double x, double y, double z) {
		if (material != Proto.DIG_WORKBENCH || minecraft.player == null || !dev.skycraft.SkySettings.flag("skyrimWorkbenchesTaken", true)) {
			return false;
		}
		ItemStack used = tool != null ? tool : minecraft.player.getMainHandItem();
		int id = SkyLink.pushStoreRequest(Proto.STORE_TAKE_WORKBENCH, 0, 0, (float) x, (float) y, Float.floatToRawIntBits((float) z), 0, "");
		dev.skycraft.SkyCraft.LOG.info("SkyCraft: a Skyrim workbench mined (with {}{}): asking Skyrim for it whole{}", used.isEmpty() ? "a bare hand" : used.getHoverName().getString(),
			tool != null ? ", a Skyrim weapon's stand-in" : "", id == 0 ? " (the request queue is full)" : "");
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
