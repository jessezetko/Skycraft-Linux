// SkyCraft+ : enchanting across the games
package dev.skycraft.craft;

import dev.skycraft.SkyCraft;
import dev.skycraft.combat.SkyrimActorEntity;
import dev.skycraft.link.Proto;
import dev.skycraft.link.SkyLink;
import dev.skycraft.net.SkyNet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Skyrim enchantments on Minecraft weapons and armor (enchanted at Skyrim's enchanter: SkyLending,
 * with a soul gem, as Skyrim's own). The enchantment's effects ride on the Minecraft item: a weapon's
 * hit on one of Skyrim's people or creatures is the enchantment itself, cast by the player; on a
 * Minecraft mob each effect is acted out the Minecraft way (fire sets it alight, frost slows it,
 * absorb heals the player, paralysis holds it still...). A weapon's hits use up its charge as
 * Skyrim's do; a filled soul gem used with it in hand (SkyEnchantingClient) charges it again. Armor
 * gives its effects while worn (fortify health more hearts, waterbreathing, fire resistance...).
 * The enchantment exists only in the Skyrim save it was made in: in another, a hit on Skyrim's
 * people is acted out as on a mob.
 */
public final class SkyEnchanting {
	private SkyEnchanting() {
	}

	private static final String TAG = "skycraft_enchant";

	/** A Skyrim enchantment: its form (player-made, FF...), its effects ("kind magnitude seconds;..."), charge, most charge, and cost a hit. */
	public record Enchant(int formId, String effects, float charge, float max, float cost) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Enchant> CODEC = StreamCodec.of((buf, e) -> {
			buf.writeInt(e.formId);
			buf.writeUtf(e.effects, 128);
			buf.writeFloat(e.charge);
			buf.writeFloat(e.max);
			buf.writeFloat(e.cost);
		}, buf -> new Enchant(buf.readInt(), buf.readUtf(128), buf.readFloat(), buf.readFloat(), buf.readFloat()));

		Enchant withCharge(float now) {
			return new Enchant(this.formId, this.effects, Math.max(0.0F, Math.min(now, this.max)), this.max, this.cost);
		}

		/** Each effect: kind, magnitude, seconds. */
		public List<Effect> list() {
			List<Effect> out = new ArrayList<>();
			for (String part : this.effects.split(";")) {
				String[] bits = part.trim().split(" ");
				if (bits.length >= 2) {
					try {
						out.add(new Effect(bits[0], Float.parseFloat(bits[1]), bits.length > 2 ? Integer.parseInt(bits[2]) : 0));
					} catch (NumberFormatException ignored) {
					}
				}
			}
			return out;
		}
	}

	public record Effect(String kind, float magnitude, int seconds) {
		/** How it reads on the item. */
		String describe() {
			String what = switch (this.kind) {
				case "fire" -> "Fire Damage";
				case "frost" -> "Frost Damage";
				case "shock" -> "Shock Damage";
				case "absorb_health" -> "Absorb Health";
				case "absorb_stamina" -> "Absorb Stamina";
				case "absorb_magicka" -> "Absorb Magicka";
				case "paralyze" -> "Paralyze";
				case "fear" -> "Fear";
				case "frenzy" -> "Frenzy";
				case "calm" -> "Calm";
				case "soultrap" -> "Soul Trap";
				case "banish" -> "Banish";
				case "turn" -> "Turn Undead";
				case "stamina" -> "Stamina Damage";
				case "magicka" -> "Magicka Damage";
				case "fortify_health" -> "Fortify Health";
				case "fortify_stamina" -> "Fortify Stamina";
				case "fortify_magicka" -> "Fortify Magicka";
				case "regen" -> "Fortify Healing";
				case "resist_fire" -> "Resist Fire";
				case "resist_frost" -> "Resist Frost";
				case "resist_shock" -> "Resist Shock";
				case "resist_magic" -> "Resist Magic";
				case "water_breathing" -> "Waterbreathing";
				case "speed" -> "Fortify Speed";
				case "fortify_attack" -> "Fortify Attack";
				case "fortify_armor" -> "Fortify Armor";
				case "sneak" -> "Muffle";
				case "carry" -> "Fortify Carry Weight";
				default -> this.kind.substring(0, 1).toUpperCase(Locale.ROOT) + this.kind.substring(1).replace('_', ' ');
			};
			return what + (this.magnitude > 0.0F ? " " + Math.round(this.magnitude) : "") + (this.seconds > 0 ? " for " + this.seconds + " s" : "");
		}
	}

	public static @Nullable Enchant enchant(ItemStack stack) {
		CustomData data = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag().getCompoundOrEmpty(TAG);
		if (tag.isEmpty()) {
			return null;
		}
		return new Enchant(tag.getIntOr("id", 0), tag.getStringOr("effects", ""), tag.getFloatOr("charge", 0.0F), tag.getFloatOr("max", 0.0F), tag.getFloatOr("cost", 0.0F));
	}

	/** The Minecraft item takes the Skyrim enchantment on: kept in it, shown in its tooltip, with the glint. */
	public static void apply(ItemStack stack, Enchant enchant) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
			CompoundTag tag = new CompoundTag();
			tag.putInt("id", enchant.formId());
			tag.putString("effects", enchant.effects());
			tag.putFloat("charge", enchant.charge());
			tag.putFloat("max", enchant.max());
			tag.putFloat("cost", enchant.cost());
			root.put(TAG, tag);
		});
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		List<Component> lines = new ArrayList<>();
		for (Effect effect : enchant.list()) {
			lines.add(Component.literal(effect.describe()).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.AQUA)));
		}
		if (enchant.max() > 0.0F && !worn(stack)) {
			lines.add(Component.literal("Charge " + Math.round(enchant.charge()) + " / " + Math.round(enchant.max())).withStyle(st -> st.withItalic(false)
				.withColor(ChatFormatting.GRAY)));
		}
		lines.add(Component.literal("Skyrim enchantment").withStyle(st -> st.withItalic(false).withColor(0x808080)));
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	private static boolean worn(ItemStack stack) {
		var equippable = stack.get(DataComponents.EQUIPPABLE);
		return equippable != null && equippable.slot().isArmor();
	}

	/** A filled soul gem's soul (1 petty .. 5 grand): the charge it gives, as Skyrim's. */
	public static float soulCharge(int soul) {
		return switch (soul) {
			case 1 -> 250.0F;
			case 2 -> 500.0F;
			case 3 -> 1000.0F;
			case 4 -> 2000.0F;
			default -> soul >= 5 ? 3000.0F : 0.0F;
		};
	}

	/** Server: a soul gem's charge into the main-hand weapon's Skyrim enchantment. */
	public static void recharge(ServerPlayer player, float charge) {
		ItemStack weapon = player.getMainHandItem();
		Enchant enchant = enchant(weapon);
		if (enchant == null || charge <= 0.0F) {
			return;
		}
		apply(weapon, enchant.withCharge(enchant.charge() + charge));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.sendOverlayMessage(Component.literal(weapon.getHoverName().getString() + " recharged"));
	}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer server) || !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}
			ItemStack weapon = player.getMainHandItem();
			Enchant enchant = enchant(weapon);
			if (enchant == null || worn(weapon)) {
				return InteractionResult.PASS;
			}
			if (enchant.max() > 0.0F && enchant.charge() < enchant.cost()) {
				return InteractionResult.PASS;  // out of charge (a soul gem charges it)
			}
			if (enchant.max() > 0.0F && enchant.cost() > 0.0F) {
				apply(weapon, enchant.withCharge(enchant.charge() - enchant.cost()));
			}
			// After the weapon's own hit (which makes the target briefly unhurtable).
			server.level().getServer().execute(() -> strike(server, target, enchant));
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(SkyEnchanting::wornTick);
		// SkyCraft+ : a Skyrim-enchanted bow's, crossbow's or trident's shot carries the enchantment (a shot's worth of charge).
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer player && arrow.tickCount == 0) {
				shot(player, arrow);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			Enchant enchant = source.getDirectEntity() == null ? null : SHOTS.remove(source.getDirectEntity());
			if (enchant != null && source.getEntity() instanceof ServerPlayer player && entity.isAlive()) {
				player.level().getServer().execute(() -> strike(player, entity, enchant));
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> trapped(entity));
		ServerTickEvents.END_SERVER_TICK.register(SkyEnchanting::spellTick);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(SkyrimWeaponEnchant.TYPE, SkyrimWeaponEnchant.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(SkyrimWeaponEnchant.TYPE, (payload, context) -> {
			if (payload.formId() != 0 && SKYRIM_WEAPON_EFFECTS.size() < 4096) {
				SKYRIM_WEAPON_EFFECTS.put(payload.formId(), payload.effects());
			}
		});
	}

	/**
	 * SkyCraft+ : Skyrim weapons' own enchantments on Minecraft's mobs. Client -> server: what a Skyrim enchantment does
	 * (its effects, "kind magnitude seconds;...", Skyrim's answer to Proto.STORE_ENCHANT_INFO), sent before a hit with it.
	 */
	public record SkyrimWeaponEnchant(int formId, String effects) implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
		public static final Type<SkyrimWeaponEnchant> TYPE = new Type<>(net.minecraft.resources.Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "skyrim_weapon_enchant"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SkyrimWeaponEnchant> CODEC = StreamCodec.of((buf, e) -> {
			buf.writeInt(e.formId);
			buf.writeUtf(e.effects, 128);
		}, buf -> new SkyrimWeaponEnchant(buf.readInt(), buf.readUtf(128)));

		@Override
		public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Each Skyrim enchantment's effects, as its players' clients told them (SkyrimWeaponEnchant). */
	private static final Map<Integer, String> SKYRIM_WEAPON_EFFECTS = new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * A Skyrim weapon whose own enchantment (formId, 0: none or out of charge) hit these Minecraft mobs (SkyCombat.skyrimMelee):
	 * acted out on each as a Minecraft weapon's would be, and one hit's charge goes from it in the host's Skyrim. The Nordic
	 * Sword of Fear did nothing to a zombie but knock it back.
	 */
	public static void skyrimWeaponHit(ServerPlayer player, List<LivingEntity> hit, int formId, boolean left) {
		String effects = formId == 0 ? null : SKYRIM_WEAPON_EFFECTS.get(formId);
		if (effects == null || effects.isEmpty() || hit.isEmpty()) {
			return;
		}
		Enchant enchant = new Enchant(formId, effects, 0.0F, 0.0F, 0.0F);
		List<LivingEntity> targets = List.copyOf(hit);
		// After the weapon's own hit (which makes the target briefly unhurtable).
		player.level().getServer().execute(() -> {
			for (LivingEntity target : targets) {
				strike(player, target, enchant);
			}
		});
		if (SkyNet.isHost(player) && SkyLink.active()) {
			SkyLink.pushEvent(Proto.EV_SPEND_CHARGE, 0, 0.0F, 0.0F, 0.0F, 0.0F, left ? 1 : 0);
		}
	}

	/** The enchantment's hit on a target: Skyrim's own on Skyrim's people (the host's), acted out the Minecraft way on the rest. */
	private static void strike(ServerPlayer player, LivingEntity target, Enchant enchant) {
		if (!target.isAlive()) {
			return;
		}
		if (target instanceof SkyrimActorEntity proxy && SkyNet.isHost(player) && SkyLink.active() && enchant.formId() != 0) {
			SkyLink.pushEvent(Proto.EV_ENCHANT_HIT, proxy.formId(), 0.0F, 0.0F, 0.0F, 0.0F, enchant.formId());  // Skyrim's own enchantment on Skyrim's own
		} else {
			for (Effect effect : enchant.list()) {
				actOut(player, target, effect);
			}
		}
	}

	/** Shots in flight from a Skyrim-enchanted weapon, with what they carry (forgotten once they hit, or with the shot). */
	private static final Map<Entity, Enchant> SHOTS = new WeakHashMap<>();

	private static void shot(ServerPlayer player, AbstractArrow arrow) {
		ItemStack weapon = arrow instanceof ThrownTrident ? arrow.getPickupItemStackOrigin() : null;
		if (weapon == null || enchant(weapon) == null) {
			ItemStack fired = arrow.getWeaponItem();
			weapon = fired != null && ItemStack.isSameItem(player.getMainHandItem(), fired) ? player.getMainHandItem()
				: fired != null && ItemStack.isSameItem(player.getOffhandItem(), fired) ? player.getOffhandItem() : null;
		}
		Enchant enchant = weapon == null ? null : enchant(weapon);
		if (enchant == null || worn(weapon) || enchant.max() > 0.0F && enchant.charge() < enchant.cost()) {
			return;
		}
		if (enchant.max() > 0.0F && enchant.cost() > 0.0F) {
			apply(weapon, enchant.withCharge(enchant.charge() - enchant.cost()));
		}
		SHOTS.put(arrow, enchant);
	}

	/** A weapon's effect, the Minecraft way. Skyrim's numbers are a tenth of a Minecraft heart's worth or so (a mob has 20 health, a bandit 100). */
	private static void actOut(ServerPlayer player, LivingEntity target, Effect effect) {
		ServerLevel level = (ServerLevel) target.level();
		float damage = effect.magnitude() * 0.2F;
		int ticks = Math.max(20, effect.seconds() * 20);
		switch (effect.kind()) {
			case "fire" -> {
				target.igniteForSeconds(Math.max(3.0F, effect.seconds()));
				hurt(player, target, level.damageSources().onFire(), damage);
			}
			case "frost" -> {
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 1), player);
				target.setTicksFrozen(Math.max(target.getTicksFrozen(), 140));
				hurt(player, target, level.damageSources().freeze(), damage);
			}
			case "shock" -> hurt(player, target, level.damageSources().lightningBolt(), damage * 1.25F);
			case "damage", "magicka" -> hurt(player, target, level.damageSources().indirectMagic(player, player), damage);
			case "stamina" -> {
				// SkyCraft+ : tired out: slower, and its blows weaker
				hurt(player, target, level.damageSources().indirectMagic(player, player), damage * 0.5F);
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 0), player);
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
			}
			case "absorb_health" -> {
				hurt(player, target, level.damageSources().indirectMagic(player, player), damage);
				player.heal(damage);
			}
			case "absorb_stamina", "absorb_magicka" -> {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), player);
				player.getFoodData().eat(1, 0.1F);
			}
			// SkyCraft+ : the spells that last (fear, calm, frenzy, paralysis, soul trap) hold the mob in spellTick.
			// Skyrim's "up to level N" has no Minecraft match: a mob counts as up to level N if its most health is N x 4 or less
			// (a zombie's 20 is level 5); a magnitude of 0 has no cap.
			case "paralyze" -> {
				if (target instanceof Mob mob) {
					hold(mob, "paralyze", Math.max(40, Math.min(ticks, 200)), player);
				}
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, Math.max(40, Math.min(ticks, 200)), 9), player);
			}
			case "fear" -> {
				if (target instanceof Mob mob && upTo(mob, effect)) {
					hold(mob, "fear", ticks, player);
				}
			}
			case "calm" -> {
				if (target instanceof Mob mob && upTo(mob, effect)) {
					hold(mob, "calm", ticks, player);
				}
			}
			case "frenzy" -> {
				if (target instanceof Mob mob && upTo(mob, effect)) {
					hold(mob, "frenzy", ticks, player);
					target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0), player);
				}
			}
			case "turn" -> {
				if (undead(target)) {
					if (target instanceof Mob mob && upTo(mob, effect)) {
						hold(mob, "fear", ticks, player);  // turned: it runs from the player
					} else {
						hurt(player, target, level.damageSources().indirectMagic(player, player), Math.max(4.0F, damage * 2.0F));
					}
				}
			}
			case "banish" -> {
				// Skyrim's sends summoned things back where they came from: undead (Minecraft's nearest) up to its level are
				// sent away, a boss never; the rest take a hard hit.
				if (undead(target)) {
					if (target instanceof Mob mob && upTo(mob, effect) && !(mob instanceof net.minecraft.world.entity.boss.wither.WitherBoss)
						&& mob.getMaxHealth() < 100.0F) {
						level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(), 20, 0.3, 0.5, 0.3, 0.02);
						level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
						mob.discard();
					} else {
						hurt(player, target, level.damageSources().indirectMagic(player, player), Math.max(6.0F, damage * 3.0F));
					}
				}
			}
			case "soultrap" -> {
				if (target instanceof Mob mob) {
					hold(mob, "soultrap", Math.max(ticks, 60), player);
					target.addEffect(new MobEffectInstance(MobEffects.GLOWING, Math.max(ticks, 60), 0), player);
				}
			}
			default -> {
			}
		}
	}

	private static boolean undead(LivingEntity target) {
		return target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
	}

	/** Skyrim's "affects creatures up to level N" (the effect's magnitude), with most health standing in for level. */
	private static boolean upTo(Mob mob, Effect effect) {
		return effect.magnitude() <= 0.0F || mob.getMaxHealth() <= effect.magnitude() * 4.0F;
	}

	/** A mob under one of Skyrim's lasting spells: which, till when (game time), and who cast it. */
	private record Spell(String kind, long until, java.util.UUID caster) {
	}

	private static final Map<Mob, Spell> SPELLS = new WeakHashMap<>();

	private static void hold(Mob mob, String kind, int ticks, ServerPlayer caster) {
		if (kind.equals("soultrap") && SPELLS.get(mob) instanceof Spell other && !other.kind().equals("soultrap")) {
			SOUL_TRAPPED.put(mob, new Spell(kind, mob.level().getGameTime() + ticks, caster.getUUID()));  // alongside another spell
			return;
		}
		SPELLS.put(mob, new Spell(kind, mob.level().getGameTime() + ticks, caster.getUUID()));
		if (!kind.equals("soultrap")) {
			mob.setTarget(null);
			mob.getNavigation().stop();
		} else {
			SOUL_TRAPPED.put(mob, SPELLS.get(mob));
		}
	}

	private static final Map<Mob, Spell> SOUL_TRAPPED = new WeakHashMap<>();

	/** Each tick: the mobs under a lasting spell do as it says till it wears off. */
	private static void spellTick(MinecraftServer server) {
		if (SPELLS.isEmpty()) {
			return;
		}
		for (var it = SPELLS.entrySet().iterator(); it.hasNext();) {
			var entry = it.next();
			Mob mob = entry.getKey();
			Spell spell = entry.getValue();
			if (!mob.isAlive() || mob.level().getGameTime() >= spell.until()) {
				it.remove();
				// Its own goals back (Minecraft would put them back within a few ticks anyway).
				for (var flag : new net.minecraft.world.entity.ai.goal.Goal.Flag[] { net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE,
					net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK, net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP }) {
					mob.getGoalSelector().enableControlFlag(flag);
				}
				continue;
			}
			ServerPlayer caster = server.getPlayerList().getPlayer(spell.caster());
			// SkyCraft+ : enchanting across the games. Feared or paralyzed, its own goals that move it
			// (chasing, attacking, aiming) don't run: dropping its target alone, it took the player
			// again the next tick and came straight back. (Minecraft turns them on again every few
			// ticks, after its goals have run: so turned off each tick.)
			if (spell.kind().equals("fear") || spell.kind().equals("paralyze")) {
				mob.getGoalSelector().disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.MOVE);
				mob.getGoalSelector().disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.LOOK);
				if (spell.kind().equals("paralyze")) {
					mob.getGoalSelector().disableControlFlag(net.minecraft.world.entity.ai.goal.Goal.Flag.JUMP);
				}
			}
			switch (spell.kind()) {
				case "paralyze" -> {
					// Frozen: no moving, no aiming, no attacking (its AI keeps running, so nothing sticks once it wears off).
					mob.setTarget(null);
					mob.getNavigation().stop();
					mob.setDeltaMovement(0.0, Math.min(0.0, mob.getDeltaMovement().y), 0.0);
					mob.setYRot(mob.yRotO);
					mob.setYHeadRot(mob.yHeadRotO);
				}
				case "calm" -> {
					if (mob.getTarget() != null) {
						mob.setTarget(null);
					}
					if (mob instanceof net.minecraft.world.entity.NeutralMob neutral) {
						neutral.stopBeingAngry();
					}
				}
				case "fear" -> {
					mob.setTarget(null);
					if (caster != null && caster.level() == mob.level() && (mob.getNavigation().isDone() || mob.tickCount % 20 == 0)) {
						Vec3 away = mob instanceof PathfinderMob runner ? DefaultRandomPos.getPosAway(runner, 16, 7, caster.position()) : null;
						if (away != null) {
							mob.getNavigation().moveTo(away.x, away.y, away.z, 1.4);
						} else {
							Vec3 push = mob.position().subtract(caster.position()).multiply(1.0, 0.0, 1.0).normalize().scale(0.25);
							mob.setDeltaMovement(push.x, mob.getDeltaMovement().y, push.z);
						}
					}
				}
				case "frenzy" -> {
					if (mob.tickCount % 10 == 0 && (mob.getTarget() == null || !mob.getTarget().isAlive() || mob.getTarget() instanceof ServerPlayer)) {
						Mob nearest = null;
						double best = Double.MAX_VALUE;
						for (Mob other : mob.level().getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(16.0), other -> other != mob && other.isAlive())) {
							double d = other.distanceToSqr(mob);
							if (d < best) {
								best = d;
								nearest = other;
							}
						}
						mob.setTarget(nearest);
						if (nearest != null && nearest.getTarget() == null) {
							nearest.setTarget(mob);  // it fights back
						}
					}
				}
				default -> {
				}
			}
		}
	}

	/** A mob that died soul-trapped: its soul for the player. */
	private static void trapped(LivingEntity entity) {
		Spell spell = entity instanceof Mob mob ? SOUL_TRAPPED.remove(mob) : null;
		if (spell == null || entity.level().getGameTime() >= spell.until() || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer caster = level.getServer().getPlayerList().getPlayer(spell.caster());
		if (caster == null) {
			return;
		}
		// Its soul fills one of the player's empty Skyrim soul gems (by the mob's health: petty .. grand); a guest's (no
		// Skyrim of the host's to fill) is experience instead, as much again as the mob gives.
		if (SkyNet.isHost(caster) && SkyLink.active()) {
			float health = entity.getMaxHealth();
			int soul = health <= 10.0F ? 1 : health <= 20.0F ? 2 : health <= 40.0F ? 3 : health <= 100.0F ? 4 : 5;
			SkyLink.pushEvent(Proto.EV_SOUL_TRAPPED, 0, 0.0F, 0.0F, 0.0F, 0.0F, soul);
		} else {
			int xp = Math.max(5, (int) (entity.getMaxHealth() / 2.0F));
			net.minecraft.world.entity.ExperienceOrb.award(level, entity.position(), xp);
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, entity.getX(), entity.getY() + entity.getBbHeight() / 2.0, entity.getZ(), 12, 0.3, 0.4, 0.3, 0.05);
		caster.sendOverlayMessage(Component.literal(entity.getName().getString() + "'s soul captured"));
	}

	private static void hurt(ServerPlayer player, LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float damage) {
		if (damage > 0.0F) {
			target.setInvulnerableTime(0);
			target.hurtServer((ServerLevel) target.level(), source, damage);
		}
	}

	/** Once a second: worn armor's Skyrim enchantments, as Minecraft effects lasting a little longer than that. */
	private static void wornTick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.OFFHAND }) {
				ItemStack worn = player.getItemBySlot(slot);
				Enchant enchant = slot == EquipmentSlot.OFFHAND && !worn.is(net.minecraft.world.item.Items.SHIELD) ? null : enchant(worn);
				if (enchant == null) {
					continue;
				}
				for (Effect effect : enchant.list()) {
					Holder<MobEffect> mob = null;
					int amplifier = 0;
					switch (effect.kind()) {
						case "fortify_health" -> {
							mob = MobEffects.HEALTH_BOOST;
							amplifier = Math.clamp(Math.round(effect.magnitude() / 20.0F) - 1, 0, 4);
						}
						case "regen" -> mob = MobEffects.REGENERATION;
						case "resist_fire" -> mob = effect.magnitude() >= 25.0F ? MobEffects.FIRE_RESISTANCE : null;
						case "water_breathing" -> mob = MobEffects.WATER_BREATHING;
						case "speed" -> mob = MobEffects.SPEED;
						case "fortify_attack" -> mob = MobEffects.STRENGTH;
						case "fortify_armor", "resist_magic", "resist_frost", "resist_shock" -> mob = MobEffects.RESISTANCE;
						case "fortify_stamina" -> mob = MobEffects.HASTE;
						case "sneak" -> mob = MobEffects.INVISIBILITY;
						case "fortify_magicka" -> mob = MobEffects.LUCK;  // SkyCraft+ : (no magicka in Minecraft: luck, for loot)
						case "fortify" -> mob = MobEffects.ABSORPTION;
						case "carry" -> mob = MobEffects.JUMP_BOOST;  // SkyCraft+ : (no carry weight in Minecraft: a lighter step)
						default -> {
						}
					}
					if (mob != null) {
						MobEffectInstance now = player.getEffect(mob);
						if (now == null || now.getDuration() < 60 || now.getAmplifier() < amplifier) {
							player.addEffect(new MobEffectInstance(mob, mob == MobEffects.REGENERATION ? 50 : 100, amplifier, true, false, true));
						}
					}
				}
			}
		}
	}
}
