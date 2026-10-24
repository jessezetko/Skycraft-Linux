// SkyCraft+ : Skyrim weapons' own enchantments on Minecraft's mobs
package dev.skycraft.client;

import dev.skycraft.craft.SkyEnchanting;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * A Skyrim weapon's hit tells which of Skyrim's enchantments it carries (Proto.IN_SKYRIM_HIT c): what that
 * enchantment does is asked of Skyrim once (Proto.STORE_ENCHANT_INFO) and told to the server before each hit
 * with it (SkyEnchanting.SkyrimWeaponEnchant), which acts it out on the Minecraft mobs hit. The first hit
 * with one not yet known does without.
 */
public final class SkyHandEnchantsClient {
	private SkyHandEnchantsClient() {
	}

	private static final Map<Integer, String> KNOWN = new HashMap<>();   // enchantment -> its effects
	private static final Map<Integer, Integer> ASKED = new HashMap<>();  // store request -> enchantment

	/** A Skyrim melee hit with this enchantment (0: none) is about to go to the server. */
	static void hit(int enchantment) {
		if (enchantment == 0) {
			return;
		}
		String effects = KNOWN.get(enchantment);
		if (effects != null) {
			if (!effects.isEmpty() && ClientPlayNetworking.canSend(SkyEnchanting.SkyrimWeaponEnchant.TYPE)) {
				ClientPlayNetworking.send(new SkyEnchanting.SkyrimWeaponEnchant(enchantment, effects));
			}
			return;
		}
		if (!ASKED.containsValue(enchantment)) {
			int id = SkyLink.pushStoreRequest(Proto.STORE_ENCHANT_INFO, enchantment, 0, 0.0F, 0.0F, 0, 0, "");
			if (id != 0) {
				ASKED.put(id, enchantment);
			}
		}
	}

	/** SkyStorageClient hands on Skyrim's answers that aren't its own; true if it was this. */
	static boolean onStoreResult(SkyLink.StoreResult result) {
		Integer enchantment = ASKED.remove(result.id());
		if (enchantment == null) {
			return false;
		}
		String effects = result.status() == Proto.STORE_OK ? result.name() : "";
		KNOWN.put(enchantment, effects);
		dev.skycraft.SkyCraft.LOG.info("SkyCraft: Skyrim enchantment {} on Minecraft's mobs: {}", String.format("%08X", enchantment), effects.isEmpty() ? "nothing to act out" : effects);
		if (!effects.isEmpty() && ClientPlayNetworking.canSend(SkyEnchanting.SkyrimWeaponEnchant.TYPE)) {
			ClientPlayNetworking.send(new SkyEnchanting.SkyrimWeaponEnchant(enchantment, effects));
		}
		return true;
	}
}
