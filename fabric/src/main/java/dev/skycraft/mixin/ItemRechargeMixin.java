// SkyCraft+ : enchanting across the games
package dev.skycraft.mixin;

import dev.skycraft.craft.SkyEnchanting;
import dev.skycraft.link.Proto;
import dev.skycraft.net.SkyShortcuts;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Skyrim soul gem (its shortcut) clicked onto a Minecraft weapon with a Skyrim enchantment in the inventory
 * recharges it, as a bundle takes what's clicked onto it, instead of the two swapping places (only a gem in the
 * off hand with the weapon in the main hand did before, and nobody found that). The client does it
 * (SkyEnchanting.clientRecharge: its Skyrim gives the gem up); the server only keeps the two where they are.
 */
@Mixin(Item.class)
public abstract class ItemRechargeMixin {
	@Inject(method = "overrideStackedOnOther", at = @At("HEAD"), cancellable = true)
	private void skycraft$recharge(ItemStack carried, Slot slot, ClickAction action, Player player, CallbackInfoReturnable<Boolean> cir) {
		if (!(slot.container instanceof Inventory) || !dev.skycraft.SkySettings.flag("minecraftEnchantsOnSkyrim", true)) {
			return;
		}
		SkyShortcuts.Ref ref = SkyShortcuts.ref(carried);
		SkyEnchanting.Enchant enchant = SkyEnchanting.enchant(slot.getItem());
		if (ref == null || ref.kind() != Proto.SKY_MISC || enchant == null || enchant.max() <= 0.0F) {
			return;
		}
		if (player.level().isClientSide()) {
			SkyEnchanting.clientRecharge.test(new SkyEnchanting.RechargeAsk(carried, slot.getContainerSlot()));
		}
		cir.setReturnValue(true);
	}
}
