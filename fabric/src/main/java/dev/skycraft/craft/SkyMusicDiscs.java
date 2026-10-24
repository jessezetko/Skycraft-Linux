// SkyCraft+ : Skyrim music discs
package dev.skycraft.craft;

import dev.skycraft.SkyCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Rarity;

/**
 * A Minecraft music disc for each of Skyrim's songs. Put in a jukebox, Skyrim plays that song from
 * the jukebox (Minecraft has no sound of its own here): the client sees the jukebox start and stop
 * (LevelEventHandlerJukeboxMixin) and tells Skyrim with Proto.EV_JUKEBOX, the disc's index in DISCS
 * (Skyrim keeps the same list of its music files, MusicDiscs.cpp). Each disc's song (length, name)
 * is data/skycraft/jukebox_song/<disc>.json. Found in the loot chests in Skyrim's dungeons, and as a
 * creeper's drop when a skeleton kills it, like Minecraft's discs.
 */
public final class SkyMusicDiscs {
	private SkyMusicDiscs() {
	}

	/** item: the disc's item id; file: its song in Skyrim's Data (the same order as MusicDiscs.cpp). */
	public record Disc(String item, String file) {
	}

	public static final Disc[] DISCS = {
		new Disc("skyrim_disc_explore_dlc2solstheim_01", "music\\mus_explore_dlc2solstheim_01.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_02", "music\\mus_explore_dlc2solstheim_02.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_03", "music\\mus_explore_dlc2solstheim_03.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_04", "music\\mus_explore_dlc2solstheim_04.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_05", "music\\mus_explore_dlc2solstheim_05.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_06", "music\\mus_explore_dlc2solstheim_06.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_07", "music\\mus_explore_dlc2solstheim_07.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_08", "music\\mus_explore_dlc2solstheim_08.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_09", "music\\mus_explore_dlc2solstheim_09.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_10", "music\\mus_explore_dlc2solstheim_10.xwm"),
		new Disc("skyrim_disc_explore_dlc2solstheim_11", "music\\mus_explore_dlc2solstheim_11.xwm"),
		new Disc("skyrim_disc_castle_stormcloaks", "music\\mus_castle_stormcloaks.xwm"),
		new Disc("skyrim_disc_castle_imperial_01", "music\\mus_castle_imperial_01.xwm"),
		new Disc("skyrim_disc_dungeon_dlc2apocrypha_01", "music\\mus_dungeon_dlc2apocrypha_01.xwm"),
		new Disc("skyrim_disc_dungeon_dlc2apocrypha_02", "music\\mus_dungeon_dlc2apocrypha_02.xwm"),
		new Disc("skyrim_disc_boatarrival_01", "music\\mus_dlc2boatarrival_01.xwm"),
		new Disc("skyrim_disc_dungeon_ice_01", "music\\dungeon\\ice\\mus_dungeon_ice_01.xwm"),
		new Disc("skyrim_disc_dungeon_ice_02", "music\\dungeon\\ice\\mus_dungeon_ice_02.xwm"),
		new Disc("skyrim_disc_explore_day_01", "music\\explore\\mus_explore_day_01.xwm"),
		new Disc("skyrim_disc_explore_day_02", "music\\explore\\mus_explore_day_02.xwm"),
		new Disc("skyrim_disc_explore_day_03", "music\\explore\\mus_explore_day_03.xwm"),
		new Disc("skyrim_disc_explore_day_04", "music\\explore\\mus_explore_day_04.xwm"),
		new Disc("skyrim_disc_explore_day_05", "music\\explore\\mus_explore_day_05.xwm"),
		new Disc("skyrim_disc_explore_day_06", "music\\explore\\mus_explore_day_06.xwm"),
		new Disc("skyrim_disc_explore_day_07", "music\\explore\\mus_explore_day_07.xwm"),
		new Disc("skyrim_disc_explore_day_08", "music\\explore\\mus_explore_day_08.xwm"),
		new Disc("skyrim_disc_explore_day_09", "music\\explore\\mus_explore_day_09.xwm"),
		new Disc("skyrim_disc_explore_night_01", "music\\explore\\mus_explore_night_01.xwm"),
		new Disc("skyrim_disc_explore_night_02", "music\\explore\\mus_explore_night_02.xwm"),
		new Disc("skyrim_disc_explore_night_03", "music\\explore\\mus_explore_night_03.xwm"),
		new Disc("skyrim_disc_explore_night_04", "music\\explore\\mus_explore_night_04.xwm"),
		new Disc("skyrim_disc_explore_night_05", "music\\explore\\mus_explore_night_05.xwm"),
		new Disc("skyrim_disc_explore_night_06", "music\\explore\\mus_explore_night_06.xwm"),
		new Disc("skyrim_disc_explore_dusk_01", "music\\explore\\mus_explore_dusk_01.xwm"),
		new Disc("skyrim_disc_explore_dusk_02", "music\\explore\\mus_explore_dusk_02.xwm"),
		new Disc("skyrim_disc_explore_dusk_03", "music\\explore\\mus_explore_dusk_03.xwm"),
		new Disc("skyrim_disc_explore_morning_01", "music\\explore\\mus_explore_morning_01.xwm"),
		new Disc("skyrim_disc_explore_morning_02", "music\\explore\\mus_explore_morning_02.xwm"),
		new Disc("skyrim_disc_dungeon_cave_01", "music\\dungeon\\cave\\mus_dungeon_cave_01.xwm"),
		new Disc("skyrim_disc_dungeon_cave_02", "music\\dungeon\\cave\\mus_dungeon_cave_02.xwm"),
		new Disc("skyrim_disc_dungeon_cave_03", "music\\dungeon\\cave\\mus_dungeon_cave_03.xwm"),
		new Disc("skyrim_disc_dungeon_fort_01", "music\\dungeon\\fort\\mus_dungeon_fort_01.xwm"),
		new Disc("skyrim_disc_dungeon_fort_02", "music\\dungeon\\fort\\mus_dungeon_fort_02.xwm"),
		new Disc("skyrim_disc_cc_dungeon_01", "music\\creationclub\\mus_dungeon_01.xwm"),
		new Disc("skyrim_disc_cc_dungeon_02", "music\\creationclub\\mus_dungeon_02.xwm"),
		new Disc("skyrim_disc_cc_dungeon_03", "music\\creationclub\\mus_dungeon_03.xwm"),
		new Disc("skyrim_disc_cc_dungeon_04", "music\\creationclub\\mus_dungeon_04.xwm"),
		new Disc("skyrim_disc_cc_dungeon_05", "music\\creationclub\\mus_dungeon_05.xwm"),
		new Disc("skyrim_disc_explore_dlc_falmervalley_01", "music\\dlc01\\explore\\falmervalley\\mus_explore_dlc_falmervalley_01.xwm"),
		new Disc("skyrim_disc_dungeon_01", "music\\dungeon\\mus_dungeon_01.xwm"),
		new Disc("skyrim_disc_dungeon_02", "music\\dungeon\\mus_dungeon_02.xwm"),
		new Disc("skyrim_disc_dungeon_03", "music\\dungeon\\mus_dungeon_03.xwm"),
		new Disc("skyrim_disc_special_cartintro_01", "music\\special\\mus_special_cartintro_01.xwm"),
		new Disc("skyrim_disc_maintheme", "music\\special\\mus_maintheme.xwm"),
		new Disc("skyrim_disc_intro", "music\\special\\mus_intro.xwm"),
		new Disc("skyrim_disc_combat_boss_02", "music\\combat\\mus_combat_boss_02.xwm"),
		new Disc("skyrim_disc_combat_boss_03", "music\\combat\\mus_combat_boss_03.xwm"),
		new Disc("skyrim_disc_combat_01", "music\\combat\\mus_combat_01.xwm"),
		new Disc("skyrim_disc_combat_02", "music\\combat\\mus_combat_02.xwm"),
		new Disc("skyrim_disc_combat_03", "music\\combat\\mus_combat_03.xwm"),
		new Disc("skyrim_disc_combat_04", "music\\combat\\mus_combat_04.xwm"),
		new Disc("skyrim_disc_combat_06", "music\\combat\\mus_combat_06.xwm"),
		new Disc("skyrim_disc_combat_boss", "music\\combat\\mus_combat_boss.xwm"),
		new Disc("skyrim_disc_tavern_01", "music\\tavern\\mus_tavern_01.xwm"),
		new Disc("skyrim_disc_tavern_02", "music\\tavern\\mus_tavern_02.xwm"),
		new Disc("skyrim_disc_tavern_03", "music\\tavern\\mus_tavern_03.xwm"),
		new Disc("skyrim_disc_tavern_04", "music\\tavern\\mus_tavern_04.xwm"),
		new Disc("skyrim_disc_tavern_05", "music\\tavern\\mus_tavern_05.xwm"),
		new Disc("skyrim_disc_dungeon_dlc_vampirecastle_01", "music\\dlc01\\dungeon\\mus_dungeon_dlc_vampirecastle_01.xwm"),
		new Disc("skyrim_disc_dungeon_dlc_vampirecastle_02", "music\\dlc01\\dungeon\\mus_dungeon_dlc_vampirecastle_02.xwm"),
		new Disc("skyrim_disc_dungeon_dlc_soulcairn_01", "music\\dlc01\\dungeon\\mus_dungeon_dlc_soulcairn_01.xwm"),
		new Disc("skyrim_disc_town_day_01", "music\\town\\mus_town_day_01.xwm"),
		new Disc("skyrim_disc_town_day_02", "music\\town\\mus_town_day_02.xwm"),
		new Disc("skyrim_disc_town_day_03", "music\\town\\mus_town_day_03.xwm"),
		new Disc("skyrim_disc_town_day_04", "music\\town\\mus_town_day_04.xwm"),
	};

	public static final List<Item> ITEMS = new ArrayList<>();

	public static void init() {
		for (Disc disc : DISCS) {
			Identifier id = Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, disc.item());
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
			ResourceKey<JukeboxSong> song = ResourceKey.create(Registries.JUKEBOX_SONG, id);
			ITEMS.add(Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE).jukeboxPlayable(song))));
		}
		// A creative tab of their own (at the end of Tools & Utilities, after Minecraft's discs, they weren't found).
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(SkyCraft.MOD_ID, "skyrim_music_discs"),
			net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab.builder()
				.title(net.minecraft.network.chat.Component.literal("Skyrim Music Discs"))
				.icon(() -> new net.minecraft.world.item.ItemStack(ITEMS.get(0)))
				.displayItems((parameters, output) -> ITEMS.forEach(output::accept))
				.build());
	}

	/** The disc whose song this is (its key's path is the disc's item id), or -1: not one of ours. */
	public static int indexOf(ResourceKey<JukeboxSong> song) {
		if (!SkyCraft.MOD_ID.equals(song.identifier().getNamespace())) {
			return -1;
		}
		for (int i = 0; i < DISCS.length; i++) {
			if (DISCS[i].item().equals(song.identifier().getPath())) {
				return i;
			}
		}
		return -1;
	}
}
