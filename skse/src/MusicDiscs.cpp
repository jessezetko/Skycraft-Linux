// SkyCraft+ : Skyrim music discs
#include "Game.h"

// A jukebox playing one of Minecraft's Skyrim music discs (SkyMusicDiscs on Minecraft's side, which
// has no sound of its own here) plays that song from Skyrim's own music files, at the jukebox: it
// fades with distance like any sound in the world. kEvJukebox: flags the disc's index + 1, 0 stops.
namespace skycraft::MusicDiscs
{
	namespace
	{
		// The discs' songs, in SkyMusicDiscs.DISCS's order (Data\Music in Skyrim - Sounds.bsa).
		constexpr const char* kSongs[] = {
			"music\\mus_explore_dlc2solstheim_01.xwm",
			"music\\mus_explore_dlc2solstheim_02.xwm",
			"music\\mus_explore_dlc2solstheim_03.xwm",
			"music\\mus_explore_dlc2solstheim_04.xwm",
			"music\\mus_explore_dlc2solstheim_05.xwm",
			"music\\mus_explore_dlc2solstheim_06.xwm",
			"music\\mus_explore_dlc2solstheim_07.xwm",
			"music\\mus_explore_dlc2solstheim_08.xwm",
			"music\\mus_explore_dlc2solstheim_09.xwm",
			"music\\mus_explore_dlc2solstheim_10.xwm",
			"music\\mus_explore_dlc2solstheim_11.xwm",
			"music\\mus_castle_stormcloaks.xwm",
			"music\\mus_castle_imperial_01.xwm",
			"music\\mus_dungeon_dlc2apocrypha_01.xwm",
			"music\\mus_dungeon_dlc2apocrypha_02.xwm",
			"music\\mus_dlc2boatarrival_01.xwm",
			"music\\dungeon\\ice\\mus_dungeon_ice_01.xwm",
			"music\\dungeon\\ice\\mus_dungeon_ice_02.xwm",
			"music\\explore\\mus_explore_day_01.xwm",
			"music\\explore\\mus_explore_day_02.xwm",
			"music\\explore\\mus_explore_day_03.xwm",
			"music\\explore\\mus_explore_day_04.xwm",
			"music\\explore\\mus_explore_day_05.xwm",
			"music\\explore\\mus_explore_day_06.xwm",
			"music\\explore\\mus_explore_day_07.xwm",
			"music\\explore\\mus_explore_day_08.xwm",
			"music\\explore\\mus_explore_day_09.xwm",
			"music\\explore\\mus_explore_night_01.xwm",
			"music\\explore\\mus_explore_night_02.xwm",
			"music\\explore\\mus_explore_night_03.xwm",
			"music\\explore\\mus_explore_night_04.xwm",
			"music\\explore\\mus_explore_night_05.xwm",
			"music\\explore\\mus_explore_night_06.xwm",
			"music\\explore\\mus_explore_dusk_01.xwm",
			"music\\explore\\mus_explore_dusk_02.xwm",
			"music\\explore\\mus_explore_dusk_03.xwm",
			"music\\explore\\mus_explore_morning_01.xwm",
			"music\\explore\\mus_explore_morning_02.xwm",
			"music\\dungeon\\cave\\mus_dungeon_cave_01.xwm",
			"music\\dungeon\\cave\\mus_dungeon_cave_02.xwm",
			"music\\dungeon\\cave\\mus_dungeon_cave_03.xwm",
			"music\\dungeon\\fort\\mus_dungeon_fort_01.xwm",
			"music\\dungeon\\fort\\mus_dungeon_fort_02.xwm",
			"music\\creationclub\\mus_dungeon_01.xwm",
			"music\\creationclub\\mus_dungeon_02.xwm",
			"music\\creationclub\\mus_dungeon_03.xwm",
			"music\\creationclub\\mus_dungeon_04.xwm",
			"music\\creationclub\\mus_dungeon_05.xwm",
			"music\\dlc01\\explore\\falmervalley\\mus_explore_dlc_falmervalley_01.xwm",
			"music\\dungeon\\mus_dungeon_01.xwm",
			"music\\dungeon\\mus_dungeon_02.xwm",
			"music\\dungeon\\mus_dungeon_03.xwm",
			"music\\special\\mus_special_cartintro_01.xwm",
			"music\\special\\mus_maintheme.xwm",
			"music\\special\\mus_intro.xwm",
			"music\\combat\\mus_combat_boss_02.xwm",
			"music\\combat\\mus_combat_boss_03.xwm",
			"music\\combat\\mus_combat_01.xwm",
			"music\\combat\\mus_combat_02.xwm",
			"music\\combat\\mus_combat_03.xwm",
			"music\\combat\\mus_combat_04.xwm",
			"music\\combat\\mus_combat_06.xwm",
			"music\\combat\\mus_combat_boss.xwm",
			"music\\tavern\\mus_tavern_01.xwm",
			"music\\tavern\\mus_tavern_02.xwm",
			"music\\tavern\\mus_tavern_03.xwm",
			"music\\tavern\\mus_tavern_04.xwm",
			"music\\tavern\\mus_tavern_05.xwm",
			"music\\dlc01\\dungeon\\mus_dungeon_dlc_vampirecastle_01.xwm",
			"music\\dlc01\\dungeon\\mus_dungeon_dlc_vampirecastle_02.xwm",
			"music\\dlc01\\dungeon\\mus_dungeon_dlc_soulcairn_01.xwm",
			"music\\town\\mus_town_day_01.xwm",
			"music\\town\\mus_town_day_02.xwm",
			"music\\town\\mus_town_day_03.xwm",
			"music\\town\\mus_town_day_04.xwm",
		};

		struct Playing
		{
			RE::BSSoundHandle sound;
			std::uint32_t     disc;
		};
		std::unordered_map<std::int64_t, Playing> playing;  // by the jukebox's block

		std::int64_t Key(float a_x, float a_y, float a_z)
		{
			const auto x = static_cast<std::int64_t>(std::floor(a_x)) & 0x3FFFFFF;
			const auto y = static_cast<std::int64_t>(std::floor(a_y)) & 0xFFF;
			const auto z = static_cast<std::int64_t>(std::floor(a_z)) & 0x3FFFFFF;
			return (x << 38) | (z << 12) | y;
		}

		void Stop(std::int64_t a_key)
		{
			if (auto it = playing.find(a_key); it != playing.end()) {
				it->second.sound.FadeOutAndRelease(1000);
				playing.erase(it);
				logger::info("music discs: a jukebox's song stopped");
			}
		}
	}

	void OnJukebox(std::uint32_t a_disc, float a_x, float a_y, float a_z)
	{
		const auto key = Key(a_x, a_y, a_z);
		Stop(key);
		if (a_disc == 0 || a_disc > std::size(kSongs)) {
			return;
		}
		auto* audio = RE::BSAudioManager::GetSingleton();
		if (!audio) {
			return;
		}
		const char* song = kSongs[a_disc - 1];
		Playing entry{ {}, a_disc };
		// (A path from Data; tried as is, then under Data\ in case the lookup wants the whole of it.)
		for (const std::string path : { std::string(song), std::string("Data\\") + song }) {
			RE::BSResource::ID id;
			id.GenerateFromPath(path.c_str());
			audio->GetSoundHandleByFile(entry.sound, id, 0x1A, 128);
			if (entry.sound.IsValid()) {
				break;
			}
		}
		if (!entry.sound.IsValid()) {
			logger::warn("music discs: Skyrim couldn't open {}", song);
			return;
		}
		entry.sound.SetPosition(McToSky(a_x, a_y, a_z));
		entry.sound.SetVolume(1.0f);
		const bool played = entry.sound.Play();
		logger::info("music discs: a jukebox plays {} ({})", song, played ? "playing" : "didn't start");
		playing.emplace(key, std::move(entry));
	}

	// Each frame: songs that ended on their own are let go.
	void PerFrame()
	{
		for (auto it = playing.begin(); it != playing.end();) {
			if (!it->second.sound.IsValid() || it->second.sound.state.get() == RE::BSSoundHandle::AssumedState::kStopped) {
				it = playing.erase(it);
			} else {
				++it;
			}
		}
	}
}
