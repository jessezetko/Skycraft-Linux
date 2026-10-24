// SkyCraft+ : Minecraft's things at Skyrim's workbenches
#include "Game.h"

// Skyrim's forge, tanning rack, smelter, cooking pot, grindstone, workbench, enchanter and alchemy lab
// see Minecraft's matching things: from the moment one is activated (before its menu opens) till its
// menu closes, Minecraft knows which (SkyState::workbench) and lends its materials into Skyrim's
// inventory as the same Skyrim ones (and a weapon or armor held, to the enchanter, as a stand-in);
// whatever is left when the menu closes goes back. Items lent while a menu is already open put its
// list right again (the forge's recipes, the grindstone's and workbench's items).
namespace skycraft::Workbench
{
	namespace
	{
		std::atomic<std::uint8_t>  bench{ 0 };
		std::atomic<std::uint32_t> seq{ 0 };
		float                      sinceActivated = 0.0f;
		std::atomic<bool>          menuSeen{ false };
		std::atomic<bool>          refresh{ false };

		class ActivateSink final : public RE::BSTEventSink<RE::TESActivateEvent>
		{
		public:
			static ActivateSink* Get()
			{
				static ActivateSink sink;
				return &sink;
			}

			RE::BSEventNotifyControl ProcessEvent(const RE::TESActivateEvent* a_event, RE::BSTEventSource<RE::TESActivateEvent>*) override
			{
				auto* what = a_event ? a_event->objectActivated.get() : nullptr;
				auto* by = a_event ? a_event->actionRef.get() : nullptr;
				auto* base = what ? what->GetBaseObject() : nullptr;
				auto* furniture = base ? base->As<RE::TESFurniture>() : nullptr;
				if (!by || !by->IsPlayerRef() || !furniture) {
					return RE::BSEventNotifyControl::kContinue;
				}
				const auto type = static_cast<std::uint8_t>(furniture->workBenchData.benchType.get());
				if (type == 0) {
					return RE::BSEventNotifyControl::kContinue;
				}
				bench = type;
				++seq;
				sinceActivated = 0.0f;
				menuSeen = false;
				logger::info("workbench: {} (kind {}): Minecraft's things lent", furniture->GetName(), type);
				return RE::BSEventNotifyControl::kContinue;
			}
		};

		// The workbench's menu opening and closing (the player update stops while it's open: it pauses the game).
		class MenuSink final : public RE::BSTEventSink<RE::MenuOpenCloseEvent>
		{
		public:
			static MenuSink* Get()
			{
				static MenuSink sink;
				return &sink;
			}

			RE::BSEventNotifyControl ProcessEvent(const RE::MenuOpenCloseEvent* a_event, RE::BSTEventSource<RE::MenuOpenCloseEvent>*) override
			{
				if (!a_event || a_event->menuName != RE::CraftingMenu::MENU_NAME || bench == 0) {
					return RE::BSEventNotifyControl::kContinue;
				}
				if (a_event->opening) {
					menuSeen = true;
				} else {
					logger::info("workbench: its menu closed");
					bench = 0;
				}
				return RE::BSEventNotifyControl::kContinue;
			}
		};

		std::atomic<bool> taskQueued{ false };

		// The lists the menu made when it opened, made again with what was lent since (on the UI thread).
		void RefreshLists()
		{
			SKSE::GetTaskInterface()->AddUITask([] {
				auto* ui = RE::UI::GetSingleton();
				auto  menu = ui ? ui->GetMenu<RE::CraftingMenu>() : nullptr;
				if (auto* sub = menu ? menu->GetCraftingSubMenu() : nullptr) {
					sub->UpdateConstructibleList();
					sub->UpdateSmithingList();
					logger::info("workbench: its lists made again with what Minecraft lent");
				}
			});
		}
	}

	void Install()
	{
		static bool installed = false;
		if (!installed) {
			auto* events = RE::ScriptEventSourceHolder::GetSingleton();
			auto* ui = RE::UI::GetSingleton();
			if (events && ui) {
				events->AddEventSink<RE::TESActivateEvent>(ActivateSink::Get());
				ui->AddEventSink<RE::MenuOpenCloseEvent>(MenuSink::Get());
				installed = true;
			}
		}
	}

	// Present, every frame: while the workbench's menu has the game paused, what Minecraft lends late (the
	// player update that handles it has stopped) is handled on the main thread all the same.
	void WhilePaused()
	{
		auto* ui = RE::UI::GetSingleton();
		if (bench == 0 || !ui || !ui->GameIsPaused() || taskQueued.exchange(true)) {
			return;
		}
		SKSE::GetTaskInterface()->AddTask([] {
			taskQueued = false;
			if (auto* player = RE::PlayerCharacter::GetSingleton(); player && bench != 0) {
				SkyInventory::ProcessStoreNow(player);
				if (refresh.exchange(false)) {
					RefreshLists();
				}
			}
		});
	}

	std::uint8_t Kind()
	{
		return bench;
	}

	std::uint32_t Seq()
	{
		return seq;
	}

	void Given()
	{
		refresh = true;
	}

	void PerFrame(float a_delta)
	{
		Install();
		if (bench == 0) {
			return;
		}
		auto*      ui = RE::UI::GetSingleton();
		const bool open = ui && ui->IsMenuOpen(RE::CraftingMenu::MENU_NAME);
		sinceActivated += a_delta;
		if (open) {
			menuSeen = true;
		}
		// Done: its menu came and went (MenuSink, mostly), or never came (walked away, a menu-less bench).
		if ((menuSeen && !open) || (!menuSeen && sinceActivated > 8.0f)) {
			logger::info("workbench: left (its menu {})", menuSeen ? "closed" : "never opened");
			// SkyCraft+ : its menu never came, and the player is still at it: they'd stay stuck there (Skyrim keeps a player
			// in furniture): out they come.
			auto* player = RE::PlayerCharacter::GetSingleton();
			if (!menuSeen && player && player->AsActorState()->GetSitSleepState() != RE::SIT_SLEEP_STATE::kNormal && !player->IsOnMount()) {
				logger::warn("workbench: the player was still at it (state {}); let go", static_cast<int>(player->AsActorState()->GetSitSleepState()));
				player->NotifyAnimationGraph("IdleForceDefaultState");
				player->AsActorState()->actorState1.sitSleepState = RE::SIT_SLEEP_STATE::kNormal;
			}
			bench = 0;
			return;
		}
		if (open && refresh.exchange(false)) {
			RefreshLists();
		}
	}

	// SkyCraft+ : Skyrim's workbenches taken whole with a pickaxe and placed again (a Skyrim workbench item
	// on Minecraft's side). Some are a marker the player uses plus a separate model that's seen.
	namespace
	{
		struct Look
		{
			RE::FormID furniture;
			RE::FormID look;
		};
		constexpr Look kLooks[] = {
			{ 0x000CAE0B, 0x00048748 },  // the forge
			{ 0x000BF9E1, 0x00048748 },  // Whiterun's forge (Warmaiden's)
			{ 0x0009C6CE, 0x0001DA0D },  // the smelter
			{ 0x000727A1, 0x000E2414 },  // the tanning rack
		};

		bool IsLook(RE::FormID a_base)
		{
			return std::ranges::any_of(kLooks, [&](const Look& l) { return l.look == a_base; });
		}

		RE::TESFurniture* BenchOf(RE::TESObjectREFR* a_ref)
		{
			auto* base = a_ref ? a_ref->GetBaseObject() : nullptr;
			auto* furniture = base ? base->As<RE::TESFurniture>() : nullptr;
			return furniture && furniture->workBenchData.benchType.get() != RE::TESFurniture::WorkBenchData::BenchType::kNone ? furniture : nullptr;
		}
	}

	bool IsWorkbenchPart(RE::TESObjectREFR* a_ref)
	{
		auto* base = a_ref ? a_ref->GetBaseObject() : nullptr;
		return base && (BenchOf(a_ref) || IsLook(base->GetFormID()));
	}

	bool IsPlaced(RE::TESObjectREFR* a_ref)
	{
		return a_ref && (a_ref->GetFormID() >> 24) == 0xFF && IsWorkbenchPart(a_ref);
	}

	RE::FormID Take(RE::PlayerCharacter* a_player, float a_x, float a_y, float a_z, std::string& a_name, int& a_kind)
	{
		auto* tes = RE::TES::GetSingleton();
		if (!tes || !a_player) {
			return 0;
		}
		const RE::NiPoint3 at = McToSky(a_x, a_y, a_z);
		constexpr float    kReach = 260.0f;  // (a forge's marker stands a way off its model)
		RE::TESObjectREFR* bench = nullptr;
		float              best = kReach;
		std::vector<RE::TESObjectREFR*> looks;
		tes->ForEachReferenceInRange(a_player, a_player->GetPosition().GetDistance(at) + kReach + 100.0f, [&](RE::TESObjectREFR* ref) {
			if (!ref || ref->IsDisabled() || ref->IsDeleted()) {
				return RE::BSContainer::ForEachResult::kContinue;
			}
			const float d = ref->GetPosition().GetDistance(at);
			if (BenchOf(ref) && d < best) {
				best = d;
				bench = ref;
			} else if (auto* base = ref->GetBaseObject(); base && IsLook(base->GetFormID()) && d < kReach * 1.5f) {
				looks.push_back(ref);
			}
			return RE::BSContainer::ForEachResult::kContinue;
		});
		if (!bench) {
			logger::info("workbench: none to take near the point mined");
			return 0;
		}
		auto* furniture = BenchOf(bench);
		for (auto* look : looks) {
			auto* lookBase = look->GetBaseObject();
			const bool ours = std::ranges::any_of(kLooks, [&](const Look& l) { return l.furniture == furniture->GetFormID() && l.look == lookBase->GetFormID(); });
			if (ours && look->GetPosition().GetDistance(bench->GetPosition()) < kReach) {
				look->Disable();
			}
		}
		bench->Disable();
		a_name = furniture->GetName();
		a_kind = static_cast<int>(furniture->workBenchData.benchType.get());
		logger::info("workbench: {} ({:08X}) taken whole", a_name, furniture->GetFormID());
		return furniture->GetFormID();
	}

	namespace
	{
		// A workbench just placed, turned once its model has loaded so the side it's used from faces the
		// player who placed it (each model's front is its own: one angle for all stood the workbench
		// sideways and the enchanter backwards).
		struct Turning
		{
			RE::ObjectRefHandle              bench;
			std::vector<RE::ObjectRefHandle> looks;
			RE::NiPoint3                     from;  // the player who placed it
			int                              frames = 0;
		};
		std::vector<Turning> turning;
	}

	void PerFrame()
	{
		for (auto it = turning.begin(); it != turning.end();) {
			auto bench = it->bench.get();
			auto* root = bench ? bench->Get3D() : nullptr;
			auto* node = root ? RE::BSFurnitureMarkerNode::FindBSFurnitureMarkerNode(root) : nullptr;
			if (!node || node->markers.empty()) {
				if (!bench || ++it->frames > 600) {
					if (bench) {
						logger::info("workbench: {} has no use markers to turn by; left facing the player", bench->GetDisplayFullName());
					}
					it = turning.erase(it);
				} else {
					++it;
				}
				continue;
			}
			// Where it's used from, in its own frame: the first marker's spot; at its middle, behind
			// where the user faces.
			const auto& marker = node->markers[0];
			float ox = marker.offset.x, oy = marker.offset.y;
			if (ox * ox + oy * oy < 4.0f) {
				ox = -std::sin(marker.heading), oy = -std::cos(marker.heading);
			}
			const auto  at = bench->GetPosition();
			const float heading = std::atan2(it->from.x - at.x, it->from.y - at.y) - std::atan2(ox, oy);
			auto turn = [&](RE::TESObjectREFR* a_ref) {
				a_ref->SetAngle(RE::NiPoint3(0.0f, 0.0f, heading));
				a_ref->Update3DPosition(true);
			};
			turn(bench.get());
			for (auto& handle : it->looks) {
				if (auto look = handle.get()) {
					turn(look.get());
				}
			}
			logger::info("workbench: {} turned to face the player (its use spot at {:.0f}, {:.0f})", bench->GetDisplayFullName(), ox, oy);
			it = turning.erase(it);
		}
	}

	void Place(RE::PlayerCharacter* a_player, RE::FormID a_furniture, float a_x, float a_y, float a_z, float a_yaw)
	{
		auto* base = RE::TESForm::LookupByID<RE::TESFurniture>(a_furniture);
		if (!base || !a_player) {
			logger::warn("workbench: no furniture {:08X} to place", a_furniture);
			return;
		}
		const RE::NiPoint3 at = McToSky(a_x, a_y, a_z);
		const RE::NiPoint3 angle{ 0.0f, 0.0f, McYawToHeading(a_yaw + 180.0f) };  // (it faces the player who placed it)
		auto put = [&](RE::TESBoundObject* a_what) {
			auto ref = a_player->PlaceObjectAtMe(a_what, true);  // (persists: saved with the game)
			if (ref) {
				ref->SetPosition(at);
				ref->SetAngle(angle);
			}
			return ref;
		};
		Turning turn;
		turn.from = a_player->GetPosition();
		if (auto bench = put(base)) {
			turn.bench = bench->CreateRefHandle();
		}
		for (const auto& l : kLooks) {
			if (l.furniture == a_furniture) {
				if (auto* look = RE::TESForm::LookupByID<RE::TESBoundObject>(l.look)) {
					if (auto ref = put(look)) {
						turn.looks.push_back(ref->CreateRefHandle());
					}
				}
				break;
			}
		}
		if (turn.bench) {
			turning.push_back(std::move(turn));
		}
		logger::info("workbench: {} placed", base->GetName());
	}

	// SkyCraft+ : soul trap on a Minecraft mob (kEvSoulTrapped). The smallest empty soul gem the player carries that holds a
	// soul this size becomes its filled one (the filled gem links back to the empty, as Skyrim's do).
	void SoulTrapped(RE::PlayerCharacter* a_player, std::uint32_t a_soul)
	{
		auto* handler = RE::TESDataHandler::GetSingleton();
		if (!a_player || !handler || a_soul == 0) {
			return;
		}
		RE::TESSoulGem* empty = nullptr;
		for (const auto& [item, data] : a_player->GetInventory()) {
			auto* gem = item ? item->As<RE::TESSoulGem>() : nullptr;
			if (!gem || data.first <= 0 || gem->GetContainedSoul() != RE::SOUL_LEVEL::kNone || static_cast<std::uint32_t>(gem->GetMaximumCapacity()) < a_soul) {
				continue;
			}
			if (!empty || gem->GetMaximumCapacity() < empty->GetMaximumCapacity()) {
				empty = gem;
			}
		}
		if (!empty) {
			logger::info("soul trap: no empty soul gem big enough for a size {} soul", a_soul);
			return;
		}
		RE::TESSoulGem* filled = nullptr;
		for (auto* gem : handler->GetFormArray<RE::TESSoulGem>()) {
			if (gem && gem->linkedSoulGem == empty && gem->GetContainedSoul() == empty->GetMaximumCapacity()) {
				filled = gem;
				break;
			}
		}
		if (!filled) {
			logger::info("soul trap: {} has no filled kind", empty->GetName());
			return;
		}
		a_player->RemoveItem(empty, 1, RE::ITEM_REMOVE_REASON::kRemove, nullptr, nullptr);
		a_player->AddObjectToContainer(filled, nullptr, 1, nullptr);
		RE::SendHUDMessage::ShowHUDMessage("Soul captured!");
		logger::info("soul trap: a Minecraft mob's soul filled {}", empty->GetName());
	}

	// Classifies a magic effect the way Minecraft acts it out (SkyEnchanting on the Minecraft side).
	const char* EffectKind(RE::EffectSetting* a_effect)
	{
		if (!a_effect) {
			return nullptr;
		}
		using A = RE::EffectArchetypes::ArchetypeID;
		const auto av = a_effect->data.primaryAV;
		const bool hurts = a_effect->IsDetrimental();
		switch (a_effect->GetArchetype()) {
		case A::kParalysis:
			return "paralyze";
		case A::kDemoralize:
			return "fear";
		case A::kFrenzy:
			return "frenzy";
		case A::kCalm:
			return "calm";
		case A::kSoulTrap:
			return "soultrap";
		case A::kBanish:
			return "banish";
		case A::kTurnUndead:
			return "turn";
		case A::kAbsorb:
			return av == RE::ActorValue::kHealth ? "absorb_health" : av == RE::ActorValue::kStamina ? "absorb_stamina" : "absorb_magicka";
		case A::kValueModifier:
		case A::kPeakValueModifier:
		case A::kDualValueModifier:
			if (hurts) {
				if (av == RE::ActorValue::kHealth) {
					const auto resist = a_effect->data.resistVariable;
					return resist == RE::ActorValue::kResistFire ? "fire" : resist == RE::ActorValue::kResistFrost ? "frost" : resist == RE::ActorValue::kResistShock ? "shock" : "damage";
				}
				return av == RE::ActorValue::kStamina ? "stamina" : av == RE::ActorValue::kMagicka ? "magicka" : "damage";
			}
			switch (av) {
			case RE::ActorValue::kHealth:
				return "fortify_health";
			case RE::ActorValue::kStamina:
				return "fortify_stamina";
			case RE::ActorValue::kMagicka:
				return "fortify_magicka";
			case RE::ActorValue::kHealRate:
				return "regen";
			case RE::ActorValue::kResistFire:
				return "resist_fire";
			case RE::ActorValue::kResistFrost:
				return "resist_frost";
			case RE::ActorValue::kResistShock:
				return "resist_shock";
			case RE::ActorValue::kResistMagic:
				return "resist_magic";
			case RE::ActorValue::kWaterBreathing:
				return "water_breathing";
			case RE::ActorValue::kSpeedMult:
				return "speed";
			case RE::ActorValue::kOneHanded:
			case RE::ActorValue::kTwoHanded:
			case RE::ActorValue::kArchery:
				return "fortify_attack";
			case RE::ActorValue::kBlock:
			case RE::ActorValue::kHeavyArmor:
			case RE::ActorValue::kLightArmor:
				return "fortify_armor";
			case RE::ActorValue::kSneak:
				return "sneak";
			case RE::ActorValue::kCarryWeight:
				return "carry";
			default:
				return "fortify";
			}
		default:
			return nullptr;
		}
	}

	void EnchantHit(RE::PlayerCharacter* a_player, RE::FormID a_target, RE::FormID a_enchantment)
	{
		auto* target = RE::TESForm::LookupByID<RE::Actor>(a_target);
		auto* enchantment = RE::TESForm::LookupByID<RE::EnchantmentItem>(a_enchantment);
		auto* caster = a_player->GetMagicCaster(RE::MagicSystem::CastingSource::kInstant);
		if (!target || !enchantment || !caster || target->IsDead()) {
			return;
		}
		caster->CastSpellImmediate(enchantment, false, target, 1.0f, false, 0.0f, a_player);
		static int logged = 0;
		if (logged++ < 10) {
			logger::info("workbench: a Minecraft weapon's {} on {}", enchantment->GetName(), target->GetName());
		}
	}
}
