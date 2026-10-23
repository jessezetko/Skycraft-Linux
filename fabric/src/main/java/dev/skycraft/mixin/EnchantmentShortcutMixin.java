// SkyCraft+ : enchanting across the games
package dev.skycraft.mixin;

import dev.skycraft.net.SkyShortcuts;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A Skyrim weapon's or armor's shortcut (paper, drawn as its stand-in) takes the enchantments its
 * stand-in would: an enchanted book at Minecraft's anvil puts them on (setting minecraftEnchantsOnSkyrim).
 */
@Mixin(Enchantment.class)
public abstract class EnchantmentShortcutMixin {
	@Inject(method = { "canEnchant", "isSupportedItem" }, at = @At("HEAD"), cancellable = true)
	private void skycraft$shortcut(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		var standIn = SkyShortcuts.standIn(stack);
		if (standIn != null && standIn != net.minecraft.world.item.Items.PAPER && dev.skycraft.SkySettings.flag("minecraftEnchantsOnSkyrim", true)) {
			Enchantment self = (Enchantment) (Object) this;
			ItemStack as = new ItemStack(standIn);
			cir.setReturnValue(self.isSupportedItem(as));
		}
	}
}
