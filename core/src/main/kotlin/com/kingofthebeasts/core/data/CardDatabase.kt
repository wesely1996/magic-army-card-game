package com.kingofthebeasts.core.data

import com.kingofthebeasts.core.game.GameEngine.SWARM_RAT
import com.kingofthebeasts.core.model.AbilityDef
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.EffectOp
import com.kingofthebeasts.core.model.EffectOp.Area
import com.kingofthebeasts.core.model.EffectOp.ClearField
import com.kingofthebeasts.core.model.EffectOp.Draw
import com.kingofthebeasts.core.model.Evolution
import com.kingofthebeasts.core.model.Keyword.COMMANDER
import com.kingofthebeasts.core.model.Keyword.GUARDIAN
import com.kingofthebeasts.core.model.Keyword.MENDING
import com.kingofthebeasts.core.model.Keyword.SENTRY
import com.kingofthebeasts.core.model.Keyword.TAUNT
import com.kingofthebeasts.core.model.Keyword.TEMPEST
import com.kingofthebeasts.core.model.EffectOp.Buff
import com.kingofthebeasts.core.model.EffectOp.Damage
import com.kingofthebeasts.core.model.EffectOp.Field
import com.kingofthebeasts.core.model.EffectOp.GrantKeyword
import com.kingofthebeasts.core.model.EffectOp.Heal
import com.kingofthebeasts.core.model.EffectOp.Poison
import com.kingofthebeasts.core.model.EffectOp.Shield
import com.kingofthebeasts.core.model.EffectOp.Stun
import com.kingofthebeasts.core.model.EffectOp.Summon
import com.kingofthebeasts.core.model.FieldRule
import com.kingofthebeasts.core.model.Keyword
import com.kingofthebeasts.core.model.Keyword.ARMORED
import com.kingofthebeasts.core.model.Keyword.BACKSTAB
import com.kingofthebeasts.core.model.Keyword.BROOD
import com.kingofthebeasts.core.model.Keyword.FLYING
import com.kingofthebeasts.core.model.Keyword.HIDDEN
import com.kingofthebeasts.core.model.Keyword.IMMOVABLE
import com.kingofthebeasts.core.model.Keyword.PACK_HUNTER
import com.kingofthebeasts.core.model.Keyword.POISONOUS
import com.kingofthebeasts.core.model.Keyword.REGENERATE
import com.kingofthebeasts.core.model.Keyword.RETALIATE
import com.kingofthebeasts.core.model.PushFrom
import com.kingofthebeasts.core.model.Race
import com.kingofthebeasts.core.model.Race.BEAR
import com.kingofthebeasts.core.model.Race.HAWK
import com.kingofthebeasts.core.model.Race.LION
import com.kingofthebeasts.core.model.Race.SERPENT
import com.kingofthebeasts.core.model.Race.VERMIN
import com.kingofthebeasts.core.model.Race.WOLF
import com.kingofthebeasts.core.model.Side
import com.kingofthebeasts.core.model.TargetKind
import com.kingofthebeasts.core.model.TargetRule
import com.kingofthebeasts.core.model.UnitStats

object CardDatabase {

    /** Every card, possibly adjusted by [applyTuning]. */
    val all: List<CardDef> get() = current

    private val base: List<CardDef> = buildList {
        // ------------------------------------------------------------------ WOLF
        add(king("w_king_alpha", "Alpha Wolf", WOLF, 4, 30, 2, 1, setOf(PACK_HUNTER, Keyword.PACK_CALLER), null,
            flavor = "One howl, and the forest answers."))
        add(king("w_king_moon", "Moon Howler", WOLF, 2, 28, 2, 1, setOf(Keyword.BLOODTHIRST),
            ability("Moon Call", "Give an ally within 3 a 2-point shield.", TargetRule(TargetKind.FRIENDLY_UNIT, 3), 3,
                quick = true, effects = listOf(Shield(2))),
            flavor = "Every fallen foe feeds the moon."))
        add(unit("w_pup", "Wolf Pup", WOLF, 4, 4, 3, 1, setOf(PACK_HUNTER)))
        add(unit("w_scout", "Wolf Scout", WOLF, 4, 5, 3, 1, setOf(PACK_HUNTER)))
        add(unit("w_hunter", "Grey Hunter", WOLF, 5, 6, 2, 1, setOf(PACK_HUNTER)))
        add(unit("w_stalker", "Shadow Stalker", WOLF, 5, 5, 3, 1, emptySet(),
            ability("Ambush Bite", "Deal 1 damage to an adjacent enemy.", TargetRule(TargetKind.ENEMY_UNIT, 1), 2,
                quick = true, effects = listOf(Damage(1)))))
        add(unit("w_direwolf", "Dire Wolf", WOLF, 6, 8, 2, 1, setOf(RETALIATE), elite = true))
        add(unit("w_shaman", "Wolf Shaman", WOLF, 3, 5, 2, 2, emptySet(),
            ability("Mend", "Heal an ally within 2 by 3.", TargetRule(TargetKind.FRIENDLY_UNIT, 2), 2,
                effects = listOf(Heal(3)))))
        add(unit("w_matron", "Pack Matron", WOLF, 4, 6, 2, 1, setOf(PACK_HUNTER),
            arrival = summons("w_pup", 1), text = "Arrival: a Wolf Pup appears next to her.", elite = true))
        add(unit("w_frost", "Frost Wolf", WOLF, 4, 6, 2, 1, setOf(ARMORED)))
        add(unit("w_runner", "Howling Runner", WOLF, 3, 4, 4, 1, setOf(PACK_HUNTER)))
        add(unit("w_ghost", "Ghost Wolf", WOLF, 4, 4, 3, 1, setOf(HIDDEN)))
        add(unit("w_ravager", "Pack Ravager", WOLF, 5, 6, 2, 1, setOf(PACK_HUNTER, RETALIATE), elite = true))
        add(unit("w_packlord", "Ironjaw Packlord", WOLF, 5, 10, 3, 1, setOf(PACK_HUNTER, RETALIATE),
            arrival = summons("w_pup", 2), text = "Arrival: two Wolf Pups appear next to him.", champion = true))
        add(unit("w_midnight", "Midnight Fang", WOLF, 5, 8, 3, 1, setOf(HIDDEN, BACKSTAB), champion = true))
        add(magic("w_frenzy", "Frenzy", WOLF, "Give an allied unit +2 attack for 1 turn.",
            TargetRule.FRIENDLY, Buff(attack = 2, turns = 1)))
        add(magic("w_bite", "Savage Bite", WOLF, "Deal 4 damage to an enemy unit.", TargetRule.ENEMY, Damage(4), rank = 3))
        add(magic("w_scatter", "Scatter", WOLF, "Interrupt only. Cancel the action you are responding to.",
            TargetRule.STACK, EffectOp.Counter, rank = 2))
        add(magic("w_relay", "Pack Relay", WOLF,
            "Shuffle an allied unit (not your King) into your deck. A random unit from your deck takes its square.",
            TargetRule.FRIENDLY, EffectOp.Replace, rank = 2))
        add(magic("w_howl", "Rallying Howl", WOLF, "An allied unit and allies next to it get +1 attack for 1 turn.",
            TargetRule.FRIENDLY, Area(1, Side.FRIENDLY, includeCenter = true, op = Buff(attack = 1, turns = 1))))
        add(magic("w_hamstring", "Hamstring", WOLF, "An enemy unit gets −2 movement for 2 turns.",
            TargetRule.ENEMY, Buff(move = -2, turns = 2)))
        add(magic("w_bloodmoon", "Blood Moon", WOLF, "Heal an allied unit by 2. It gets +1 attack and Pack Hunter for 2 turns.",
            TargetRule.FRIENDLY, Heal(2), Buff(attack = 1, turns = 2), GrantKeyword(PACK_HUNTER, 2), rank = 2))
        add(equipment("w_pelt", "Thick Pelt", WOLF, "+2 health.", Buff(health = 2)))
        add(equipment("w_necklace", "Alpha Fang Necklace", WOLF, "+2 attack, but −1 movement.", Buff(attack = 2, move = -1)))
        add(equipment("w_tracker", "Tracker's Collar", WOLF, "Pack Hunter and +1 movement.", GrantKeyword(PACK_HUNTER), Buff(move = 1)))
        add(strategy("w_hunt", "The Hunt", WOLF, FieldRule.BLITZ))
        // 0.11: the Wolf Pack is the unit-focused race — a third Champion and three new Elites.
        add(unit("w_fenrir", "Fenrir the Devourer", WOLF, 6, 10, 3, 1, setOf(Keyword.BLOODTHIRST, RETALIATE), champion = true))
        add(unit("w_rimefang", "Rimefang Alpha", WOLF, 4, 7, 2, 1, emptySet(),
            ability("Frost Bite", "Stun an adjacent enemy for 1 turn.", TargetRule(TargetKind.ENEMY_UNIT, 1), 3,
                effects = listOf(Stun(1))), elite = true))
        add(unit("w_warg", "Warg Rider", WOLF, 5, 6, 4, 1, setOf(PACK_HUNTER), elite = true))
        add(unit("w_elder", "Howling Elder", WOLF, 3, 7, 2, 2, emptySet(),
            ability("War Howl", "This unit and allies within 2 get +1 attack for 1 turn.", TargetRule.SELF, 2,
                effects = listOf(Area(2, Side.FRIENDLY, includeCenter = true, op = Buff(attack = 1, turns = 1)))), elite = true))
        add(magic("w_pursuit", "Moonlit Pursuit", WOLF, "An allied unit gets +2 movement and Pack Hunter for 1 turn.",
            TargetRule.FRIENDLY, Buff(move = 2, turns = 1), GrantKeyword(PACK_HUNTER, 1)))
        // 0.12: spells (buffs and quick tricks), evolving units and a watchtower.
        add(magic("w_scent", "Scent of Blood", WOLF, "Draw 2 cards.", TargetRule.NONE, Draw(2), rank = 2))
        add(magic("w_eclipse", "Eclipse", WOLF, "End the Strategy field on the battlefield (yours or your opponent's).", TargetRule.NONE, ClearField))
        add(magic("w_lunge", "Lunge", WOLF, "An allied unit gets +1 attack for 1 turn.",
            TargetRule.FRIENDLY, Buff(attack = 1, turns = 1), swift = true))
        add(magic("w_snarl", "Snarl", WOLF, "An enemy unit gets −1 attack for 1 turn.",
            TargetRule.ENEMY, Buff(attack = -1, turns = 1), swift = true))
        add(magic("w_rend", "Rend", WOLF, "Deal 2 damage to an enemy unit. It gets −1 movement for 2 turns.",
            TargetRule.ENEMY, Damage(2), Buff(move = -1, turns = 2)))
        add(unit("w_whelp", "Wolf Whelp", WOLF, 2, 3, 3, 1, setOf(PACK_HUNTER), evolve = Evolution("w_whelp2", turns = 2)))
        add(form("w_whelp2", "Young Wolf", WOLF, 3, 5, 3, 1, setOf(PACK_HUNTER), evolve = Evolution("w_whelp3", kills = 1)))
        add(form("w_whelp3", "Pack Leader", WOLF, 5, 7, 3, 1, setOf(PACK_HUNTER, RETALIATE)))
        add(unit("w_omen", "Moon-touched Pup", WOLF, 2, 4, 3, 1, setOf(HIDDEN), evolve = Evolution("w_omen2", kills = 1)))
        add(form("w_omen2", "Nightstalker Wolf", WOLF, 5, 6, 3, 1, setOf(HIDDEN, BACKSTAB)))
        add(structure("w_watch", "Hunters' Watchtower", WOLF, 2, 4, 2, SENTRY))
        add(strategy("w_moonlit", "Moonlit Hunt", WOLF, FieldRule.HUNTING_GROUNDS))
        add(equipment("w_collar", "Spiked Collar", WOLF, "+1 attack and Retaliate.",
            Buff(attack = 1), GrantKeyword(RETALIATE)))
        add(equipment("w_charm", "Fang Charm", WOLF, "+1 attack and +1 movement.", Buff(attack = 1, move = 1)))

        // ------------------------------------------------------------------ BEAR
        add(king("b_king_elder", "Elder Bear", BEAR, 2, 28, 1, 1, setOf(Keyword.UNSTOPPABLE),
            ability("Earthshaker Roar", "Stun all adjacent enemies for 1 turn.", TargetRule.SELF, 3,
                effects = listOf(Area(1, Side.ENEMY, includeCenter = false, op = Stun(1)))),
            flavor = "The mountain does not move for you."))
        add(king("b_king_warden", "Cave Warden", BEAR, 2, 28, 1, 1, setOf(Keyword.GUARDIAN, REGENERATE), null,
            flavor = "Behind her, the den is safe."))
        add(unit("b_cub", "Bear Cub", BEAR, 2, 2, 2, 1, setOf(REGENERATE)))
        add(unit("b_brawler", "Brown Brawler", BEAR, 3, 3, 1, 1, setOf(RETALIATE)))
        add(unit("b_grizzly", "Grizzly", BEAR, 4, 4, 1, 1, setOf(ARMORED), elite = true))
        add(unit("b_polar", "Polar Bear", BEAR, 3, 3, 2, 1, setOf(ARMORED)))
        add(unit("b_panda", "Panda Monk", BEAR, 2, 2, 2, 1, emptySet(),
            ability("Meditate", "Heal this unit by 3.", TargetRule.SELF, 2, effects = listOf(Heal(3)))))
        add(unit("b_honey", "Honey Gatherer", BEAR, 1, 2, 2, 2, emptySet(),
            ability("Sticky Honey", "Stun an enemy within 2 for 1 turn.", TargetRule(TargetKind.ENEMY_UNIT, 2), 4,
                quick = true, effects = listOf(Stun(1)))))
        add(unit("b_mountain", "Mountain Bear", BEAR, 3, 6, 1, 1, setOf(IMMOVABLE, ARMORED), elite = true))
        add(unit("b_moon", "Moon Bear", BEAR, 3, 3, 2, 1, emptySet(),
            ability("Maul", "Deal 2 damage to an adjacent enemy.", TargetRule(TargetKind.ENEMY_UNIT, 1), 3,
                effects = listOf(Damage(2)))))
        add(unit("b_kodiak", "Kodiak", BEAR, 5, 6, 1, 1, elite = true))
        add(unit("b_denmother", "Den Mother", BEAR, 2, 4, 1, 1, setOf(REGENERATE),
            ability("Hearty Meal", "Heal an adjacent ally by 3.", TargetRule(TargetKind.FRIENDLY_UNIT, 1), 2,
                effects = listOf(Heal(3)))))
        add(unit("b_spirit", "Spirit Bear", BEAR, 2, 3, 2, 2, emptySet(),
            ability("Ancestral Ward", "Give an ally within 2 a 2-point shield.", TargetRule(TargetKind.FRIENDLY_UNIT, 2), 3,
                quick = true, effects = listOf(Shield(2)))))
        add(unit("b_ancient", "Ancient Cave Bear", BEAR, 6, 14, 1, 1, setOf(ARMORED, IMMOVABLE, REGENERATE), champion = true))
        add(unit("b_quake", "Quakeback Bear", BEAR, 5, 12, 1, 1, setOf(ARMORED),
            ability("Ground Slam", "Deal 2 damage to every enemy next to it.", TargetRule.SELF, 3,
                effects = listOf(Area(1, Side.ENEMY, includeCenter = false, op = Damage(2)))),
            champion = true))
        add(magic("b_hide", "Thick Hide", BEAR, "Give an allied unit a 2-point shield.", TargetRule.FRIENDLY, Shield(2)))
        add(magic("b_hibernate", "Hibernate", BEAR, "Heal an allied unit by 3 and remove stun and poison.",
            TargetRule.FRIENDLY, Heal(3), EffectOp.Cleanse, rank = 2))
        add(magic("b_hug", "Bear Hug", BEAR, "Stun an enemy unit for 2 turns. As an interrupt it stops that unit's action.",
            TargetRule.ENEMY, Stun(2), rank = 3))
        add(magic("b_shove", "Mighty Shove", BEAR,
            "Push an enemy unit 2 squares directly away from your nearest unit. If a unit or the edge stops it, it takes 2 damage.",
            TargetRule.ENEMY, EffectOp.Push(2, PushFrom.NEAREST_ALLY, impactDamage = 2), rank = 2))
        add(magic("b_roar", "Intimidating Roar", BEAR, "Enemies next to an allied unit get −1 attack for 2 turns.",
            TargetRule.FRIENDLY, Area(1, Side.ENEMY, includeCenter = false, op = Buff(attack = -1, turns = 2))))
        add(magic("b_maul", "Crushing Maul", BEAR, "Deal 2 damage to an enemy unit and stun it for 1 turn.",
            TargetRule.ENEMY, Damage(2), Stun(1), rank = 2))
        add(magic("b_winter", "Long Winter's Rest", BEAR, "Heal an allied unit and allies next to it by 2.",
            TargetRule.FRIENDLY, Area(1, Side.FRIENDLY, includeCenter = true, op = Heal(2)), rank = 2))
        add(equipment("b_harness", "War Harness", BEAR, "+1 attack and +1 health.", Buff(attack = 1, health = 1)))
        add(equipment("b_coat", "Winter Coat", BEAR, "Regenerate.", GrantKeyword(REGENERATE)))
        add(equipment("b_totem", "Stone Totem", BEAR, "Immovable and +2 health.", GrantKeyword(IMMOVABLE), Buff(health = 2)))
        // 0.11: the Bear Clan leans on equipment.
        add(equipment("b_helm", "Horned Helm", BEAR, "+1 attack and Retaliate.", Buff(attack = 1), GrantKeyword(RETALIATE)))
        add(equipment("b_gauntlets", "Stone Gauntlets", BEAR, "+3 attack, but −1 movement.", Buff(attack = 3, move = -1)))
        add(equipment("b_plate", "Bearhide Plate", BEAR, "Armored and +1 health.", GrantKeyword(ARMORED), Buff(health = 1)))
        add(equipment("b_standard", "Clan Standard", BEAR, "Guardian: allies next to it take 1 less damage.", GrantKeyword(Keyword.GUARDIAN)))
        add(magic("b_tremor", "Tremor", BEAR, "Enemies next to an allied unit take 1 damage and get −1 movement for 1 turn.",
            TargetRule.FRIENDLY, Area(1, Side.ENEMY, includeCenter = false, op = Damage(1)),
            Area(1, Side.ENEMY, includeCenter = false, op = Buff(move = -1, turns = 1)), rank = 2))
        // 0.12: protective spells, slow-growing evolutions, a cairn and a healing totem.
        add(magic("b_stash", "Honey Stash", BEAR, "Draw 2 cards.", TargetRule.NONE, Draw(2), rank = 2))
        add(magic("b_thaw", "Spring Thaw", BEAR, "End the Strategy field on the battlefield (yours or your opponent's).", TargetRule.NONE, ClearField))
        add(magic("b_brace", "Brace", BEAR, "Give an allied unit a 1-point shield.", TargetRule.FRIENDLY, Shield(1), swift = true))
        add(magic("b_stoneskin", "Stoneskin", BEAR, "An allied unit heals 2 and gets Armored for 2 turns.",
            TargetRule.FRIENDLY, Heal(2), GrantKeyword(ARMORED, 2)))
        add(magic("b_bulwark", "Bulwark", BEAR, "An allied unit and allies next to it get Armored for 1 turn.",
            TargetRule.FRIENDLY, Area(1, Side.FRIENDLY, includeCenter = true, op = GrantKeyword(ARMORED, 1)), rank = 2))
        add(unit("b_youngbear", "Young Bear", BEAR, 2, 5, 2, 1, evolve = Evolution("b_youngbear2", turns = 3)))
        add(form("b_youngbear2", "Grizzled Bear", BEAR, 4, 8, 2, 1, setOf(ARMORED), evolve = Evolution("b_youngbear3", turns = 3)))
        add(form("b_youngbear3", "Elder Grizzly", BEAR, 6, 11, 2, 1, setOf(ARMORED, RETALIATE)))
        add(unit("b_sapling", "Spirit Sapling", BEAR, 1, 6, 1, 1, setOf(REGENERATE), evolve = Evolution("b_sapling2", turns = 2)))
        add(form("b_sapling2", "Spirit Oak", BEAR, 3, 10, 1, 1, setOf(REGENERATE, GUARDIAN)))
        add(structure("b_cairn", "Stone Cairn", BEAR, 0, 12, 1, TAUNT))
        add(structure("b_healtotem", "Healing Totem", BEAR, 0, 6, 1, MENDING))
        add(strategy("b_den", "Den Fortress", BEAR, FieldRule.FORTIFY))
        add(strategy("b_salmon", "Salmon Run", BEAR, FieldRule.SANCTUARY))
        add(equipment("b_bark", "Bark Armor", BEAR, "+2 health.", Buff(health = 2)))
        add(equipment("b_claws", "Iron Claws", BEAR, "+2 attack.", Buff(attack = 2)))

        // ------------------------------------------------------------------ HAWK
        add(king("h_king_sky", "Sky Sovereign", HAWK, 2, 35, 3, 2, setOf(FLYING),
            ability("Change of Winds", "Swap places with any allied unit.", TargetRule.FRIENDLY, 2,
                quick = true, effects = listOf(EffectOp.Swap)),
            flavor = "Where the wind blows, the Aerie follows."))
        add(king("h_king_storm", "Storm Eagle", HAWK, 2, 32, 2, 2, setOf(FLYING, Keyword.TEMPEST),
            ability("Lightning Strike", "Deal 1 damage to an enemy within 3 and stun it for 1 turn.",
                TargetRule(TargetKind.ENEMY_UNIT, 3), 4, quick = true, effects = listOf(Damage(1), Stun(1))),
            flavor = "Thunder is just her wings."))
        add(unit("h_sparrow", "Sparrow Scout", HAWK, 1, 2, 4, 1, setOf(FLYING)))
        add(unit("h_falcon", "Falcon", HAWK, 1, 2, 2, 2, setOf(FLYING)))
        add(unit("h_owl", "Night Owl", HAWK, 1, 2, 2, 3, setOf(FLYING)))
        add(unit("h_eagle", "War Eagle", HAWK, 2, 3, 3, 1, setOf(FLYING)))
        add(unit("h_crow", "Crow Trickster", HAWK, 1, 2, 3, 2, setOf(FLYING),
            ability("Mimic Caw", "Cancel the action you are responding to.", TargetRule.STACK, 6,
                quick = true, effects = listOf(EffectOp.Counter))))
        add(unit("h_condor", "Condor", HAWK, 2, 3, 2, 1, setOf(FLYING, RETALIATE)))
        add(unit("h_harrier", "Harrier", HAWK, 2, 2, 3, 1, setOf(FLYING, PACK_HUNTER)))
        add(unit("h_kestrel", "Kestrel", HAWK, 1, 2, 3, 2, setOf(FLYING, HIDDEN)))
        add(unit("h_heron", "Grey Heron", HAWK, 2, 3, 2, 2, setOf(FLYING), elite = true))
        add(unit("h_osprey", "Osprey", HAWK, 2, 3, 2, 1, setOf(FLYING),
            ability("Talon Dive", "Deal 2 damage to an enemy within 2.", TargetRule(TargetKind.ENEMY_UNIT, 2), 3,
                effects = listOf(Damage(2)))))
        add(unit("h_vulture", "Vulture", HAWK, 2, 3, 2, 1, setOf(FLYING, REGENERATE), elite = true))
        add(unit("h_thunderbird", "Thunderbird", HAWK, 4, 10, 3, 3, setOf(FLYING),
            ability("Thunderclap", "Deal 2 damage to an enemy within 3 and stun it for 1 turn.",
                TargetRule(TargetKind.ENEMY_UNIT, 3), 3, effects = listOf(Damage(2), Stun(1))),
            champion = true))
        add(unit("h_griffin", "Storm Griffin", HAWK, 6, 10, 3, 1, setOf(FLYING, ARMORED), champion = true))
        add(magic("h_gust", "Gust", HAWK, "Interrupt only. Cancel the action you are responding to.",
            TargetRule.STACK, EffectOp.Counter, rank = 2))
        add(magic("h_tailwind", "Tailwind", HAWK, "An allied unit gets +2 movement and Flying for 1 turn.",
            TargetRule.FRIENDLY, Buff(move = 2, turns = 1), GrantKeyword(FLYING, 1)))
        add(magic("h_skystrike", "Sky Strike", HAWK, "Deal 3 damage to an enemy unit and 1 damage to enemies next to it.",
            TargetRule.ENEMY, Damage(3), Area(1, Side.ENEMY, includeCenter = false, op = Damage(1)), rank = 3))
        add(magic("h_gale", "Gale Force", HAWK,
            "Blow an enemy unit up to 3 squares straight back toward its own side of the board.",
            TargetRule.ENEMY, EffectOp.Push(3, PushFrom.OWNER_SIDE), rank = 2))
        add(magic("h_dive", "Diving Strike", HAWK, "An allied unit gets +2 attack and Flying for 1 turn.",
            TargetRule.FRIENDLY, Buff(attack = 2, turns = 1), GrantKeyword(FLYING, 1)))
        add(magic("h_feathers", "Storm of Feathers", HAWK, "Stun an enemy unit and enemies next to it for 1 turn.",
            TargetRule.ENEMY, Area(1, Side.ENEMY, includeCenter = true, op = Stun(1)), rank = 2))
        add(magic("h_eagleeye", "Eagle Eye", HAWK, "An allied unit gets +1 range for 2 turns. Draw a card.",
            TargetRule.FRIENDLY, Buff(range = 1, turns = 2), EffectOp.Draw(1)))
        add(equipment("h_plume", "Storm Plume", HAWK, "Flying, +1 attack and +1 movement.", GrantKeyword(FLYING), Buff(attack = 1, move = 1)))
        add(equipment("h_hood", "Falconer's Hood", HAWK, "Retaliate and +1 health.", GrantKeyword(RETALIATE), Buff(health = 1)))
        add(equipment("h_steel", "Steel Feathers", HAWK, "Armored.", GrantKeyword(ARMORED)))
        // 0.11: the Hawk Aerie leans on magic.
        add(magic("h_thunderclap", "Lightning Jolt", HAWK, "Deal 1 damage to an enemy unit and stun it for 1 turn.",
            TargetRule.ENEMY, Damage(1), Stun(1)))
        add(magic("h_shear", "Wind Shear", HAWK, "An enemy unit gets −1 attack and −1 range for 2 turns.",
            TargetRule.ENEMY, Buff(attack = -1, range = -1, turns = 2)))
        add(magic("h_keen", "Keen Sight", HAWK, "Draw 2 cards.", TargetRule.NONE, EffectOp.Draw(2), rank = 2))
        add(magic("h_rain", "Rain of Feathers", HAWK,
            "An allied unit and allies next to it get a 1-point shield and +1 movement for 1 turn.",
            TargetRule.FRIENDLY, Area(1, Side.FRIENDLY, includeCenter = true, op = Shield(1)),
            Area(1, Side.FRIENDLY, includeCenter = true, op = Buff(move = 1, turns = 1))))
        add(equipment("h_harness", "Windrider Harness", HAWK, "Flying and +1 health.", GrantKeyword(FLYING), Buff(health = 1)))
        // 0.12: damage spells, birds that grow up, and a tower.
        add(magic("h_scouting", "Scouting Flight", HAWK, "Draw 2 cards.", TargetRule.NONE, Draw(2), rank = 2))
        add(magic("h_clearsky", "Clear Skies", HAWK, "End the Strategy field on the battlefield (yours or your opponent's).", TargetRule.NONE, ClearField))
        add(magic("h_spark", "Static Spark", HAWK, "Deal 1 damage to an enemy unit.", TargetRule.ENEMY, Damage(1), swift = true))
        add(magic("h_strafe", "Strafing Run", HAWK, "Deal 2 damage to an enemy unit and 1 damage to enemies next to it.",
            TargetRule.ENEMY, Damage(2), Area(1, Side.ENEMY, includeCenter = false, op = Damage(1)), rank = 2))
        add(magic("h_chain", "Chain Lightning", HAWK, "Deal 3 damage to an enemy unit and stun it for 1 turn.",
            TargetRule.ENEMY, Damage(3), Stun(1), rank = 3))
        add(unit("h_hatchling", "Hawk Hatchling", HAWK, 1, 3, 2, 2, evolve = Evolution("h_hatchling2", turns = 2)))
        add(form("h_hatchling2", "Fledgling Hawk", HAWK, 2, 4, 3, 2, setOf(FLYING), evolve = Evolution("h_hatchling3", turns = 2)))
        add(form("h_hatchling3", "Sky Raptor", HAWK, 4, 6, 3, 3, setOf(FLYING)))
        add(unit("h_stormling", "Stormling", HAWK, 2, 3, 2, 2, evolve = Evolution("h_stormling2", kills = 2)))
        add(form("h_stormling2", "Storm Spirit", HAWK, 4, 6, 3, 3, setOf(FLYING, TEMPEST)))
        add(structure("h_tower", "Aerie Tower", HAWK, 2, 4, 3, SENTRY))
        add(strategy("h_high", "High Ground", HAWK, FieldRule.HIGH_GROUND))
        add(strategy("h_winds", "Favorable Winds", HAWK, FieldRule.TAILWIND))
        add(equipment("h_talons", "Razor Talons", HAWK, "+1 attack and +1 range.", Buff(attack = 1, range = 1)))
        add(equipment("h_amulet", "Eagle Eye Amulet", HAWK, "+1 health and +1 range.", Buff(health = 1, range = 1)))

        // --------------------------------------------------------------- SERPENT
        add(king("s_king_naga", "Naga Queen", SERPENT, 2, 35, 2, 2, setOf(POISONOUS),
            ability("Enthrall", "Take control of a non-King enemy within 2 that has 3 or less health.",
                TargetRule(TargetKind.ENEMY_UNIT, 2), 3, effects = listOf(EffectOp.Enthrall(3))),
            flavor = "Look into her eyes. Now fight for her."))
        add(king("s_king_basilisk", "Basilisk", SERPENT, 2, 31, 1, 1, setOf(Keyword.PETRIFY), null,
            flavor = "Stone is patient. So is he."))
        add(unit("s_adder", "Adder", SERPENT, 2, 3, 2, 1, setOf(POISONOUS)))
        add(unit("s_viper", "Viper", SERPENT, 2, 4, 2, 1, setOf(POISONOUS)))
        add(unit("s_cobra", "Spitting Cobra", SERPENT, 2, 4, 1, 3, setOf(POISONOUS)))
        add(unit("s_python", "Python", SERPENT, 3, 8, 1, 1, emptySet(),
            ability("Constrict", "Deal 1 damage to an adjacent enemy and stun it for 2 turns.",
                TargetRule(TargetKind.ENEMY_UNIT, 1), 3, effects = listOf(Damage(1), Stun(2))), elite = true))
        add(unit("s_mamba", "Black Mamba", SERPENT, 3, 4, 3, 1, setOf(POISONOUS)))
        add(unit("s_charmer", "Snake Charmer", SERPENT, 2, 5, 2, 2, emptySet(),
            ability("Venom Spit", "Poison an enemy within 3: 2 damage per turn for 2 turns.",
                TargetRule(TargetKind.ENEMY_UNIT, 3), 3, effects = listOf(Poison(2, 2)))))
        add(unit("s_asp", "Sand Asp", SERPENT, 2, 3, 2, 1, setOf(POISONOUS, HIDDEN)))
        add(unit("s_rattler", "Rattlesnake", SERPENT, 2, 5, 2, 1, setOf(POISONOUS, RETALIATE)))
        add(unit("s_anaconda", "Anaconda", SERPENT, 4, 9, 1, 1, emptySet(),
            ability("Crush", "Deal 2 damage to an adjacent enemy.", TargetRule(TargetKind.ENEMY_UNIT, 1), 3,
                effects = listOf(Damage(2))), elite = true))
        add(unit("s_seer", "Coil Seer", SERPENT, 2, 5, 2, 2, emptySet(),
            ability("Hypnotize", "Stun an enemy within 2 for 1 turn.", TargetRule(TargetKind.ENEMY_UNIT, 2), 3,
                quick = true, effects = listOf(Stun(1)))))
        add(unit("s_krait", "Sea Krait", SERPENT, 2, 4, 3, 1, setOf(POISONOUS, BACKSTAB)))
        add(unit("s_hydra", "Great Hydra", SERPENT, 5, 12, 1, 1, setOf(POISONOUS, REGENERATE),
            ability("Many Heads", "Deal 1 damage to every enemy next to it.", TargetRule.SELF, 2,
                effects = listOf(Area(1, Side.ENEMY, includeCenter = false, op = Damage(1)))),
            champion = true))
        add(unit("s_gorgon", "Stone-Eyed Gorgon", SERPENT, 5, 11, 2, 2, setOf(POISONOUS),
            ability("Stony Glare", "Stun an enemy within 3 for 2 turns.", TargetRule(TargetKind.ENEMY_UNIT, 3), 2,
                effects = listOf(Stun(2))),
            champion = true))
        add(magic("s_hiss", "Hiss of Denial", SERPENT, "Interrupt only. Cancel the action you are responding to.",
            TargetRule.STACK, EffectOp.Counter, rank = 2))
        add(magic("s_venom", "Venom Surge", SERPENT, "Poison an enemy unit: 2 damage per turn for 2 turns.",
            TargetRule.ENEMY, Poison(2, 2), rank = 2))
        add(magic("s_shed", "Shed Skin", SERPENT, "Remove stun and poison from an allied unit and heal it by 2.",
            TargetRule.FRIENDLY, EffectOp.Cleanse, Heal(2)))
        add(magic("s_mirage", "Mirage", SERPENT,
            "Shuffle an enemy unit (not a King) into its owner's deck. A random unit from that deck takes its square.",
            TargetRule.ENEMY, EffectOp.Replace, rank = 3))
        add(magic("s_coil", "Tightening Coil", SERPENT, "An enemy unit gets −2 movement and −1 attack for 2 turns.",
            TargetRule.ENEMY, Buff(attack = -1, move = -2, turns = 2)))
        add(magic("s_toxic", "Toxic Cloud", SERPENT, "Poison an enemy unit and enemies next to it: 1 damage per turn for 2 turns.",
            TargetRule.ENEMY, Area(1, Side.ENEMY, includeCenter = true, op = Poison(1, 2)), rank = 2))
        add(magic("s_charm", "Serpent's Charm", SERPENT, "Take control of an enemy unit (not a King) with 2 or less health.",
            TargetRule.ENEMY, EffectOp.Enthrall(2), rank = 2))
        add(equipment("s_slick", "Slick Scales", SERPENT, "+2 health.", Buff(health = 2)))
        add(equipment("s_cobrahood", "Cobra Hood", SERPENT, "Retaliate and Poisonous.", GrantKeyword(RETALIATE), GrantKeyword(POISONOUS)))
        // 0.11: the Serpent Coil leans on magic.
        add(magic("s_paralyze", "Paralytic Bite", SERPENT, "Stun an enemy unit for 1 turn and poison it: 1 damage per turn for 2 turns.",
            TargetRule.ENEMY, Stun(1), Poison(1, 2), rank = 2))
        add(magic("s_acid", "Acid Spit", SERPENT, "Deal 2 damage to an enemy unit. It gets −1 attack for 2 turns.",
            TargetRule.ENEMY, Damage(2), Buff(attack = -1, turns = 2), rank = 2))
        add(magic("s_molt", "Molting", SERPENT, "An allied unit gets Regenerate for 3 turns and a 2-point shield.",
            TargetRule.FRIENDLY, GrantKeyword(REGENERATE, 3), Shield(2)))
        add(magic("s_patience", "Serpent's Patience", SERPENT, "Draw a card. An allied unit gets +1 attack for 2 turns.",
            TargetRule.FRIENDLY, EffectOp.Draw(1), Buff(attack = 1, turns = 2)))
        add(strategy("s_haze", "Hypnotic Haze", SERPENT, FieldRule.HYPNOTIC_HAZE))
        // 0.12: status spells, growing serpents and a venomous idol.
        add(magic("s_oracle", "Oracle's Coils", SERPENT, "Draw 2 cards.", TargetRule.NONE, Draw(2), rank = 2))
        add(magic("s_sands", "Shifting Sands", SERPENT, "End the Strategy field on the battlefield (yours or your opponent's).", TargetRule.NONE, ClearField))
        add(magic("s_flick", "Tongue Flick", SERPENT, "An enemy unit gets −1 movement for 1 turn.",
            TargetRule.ENEMY, Buff(move = -1, turns = 1), swift = true))
        add(magic("s_blind", "Blinding Spit", SERPENT, "An enemy unit gets −2 attack for 2 turns.",
            TargetRule.ENEMY, Buff(attack = -2, turns = 2)))
        add(magic("s_venomwave", "Venom Wave", SERPENT,
            "Poison an enemy unit and enemies next to it: 1 damage per turn for 3 turns.",
            TargetRule.ENEMY, Area(1, Side.ENEMY, includeCenter = true, op = Poison(1, 3)), rank = 3))
        add(unit("s_hatchling", "Viper Hatchling", SERPENT, 1, 3, 2, 1, setOf(POISONOUS), evolve = Evolution("s_hatchling2", turns = 2)))
        add(form("s_hatchling2", "Young Viper", SERPENT, 3, 5, 2, 1, setOf(POISONOUS), evolve = Evolution("s_hatchling3", kills = 1)))
        add(form("s_hatchling3", "Royal Cobra", SERPENT, 5, 8, 2, 1, setOf(POISONOUS, RETALIATE)))
        add(unit("s_wyrmling", "Sand Wyrmling", SERPENT, 2, 4, 2, 1, evolve = Evolution("s_wyrmling2", turns = 3)))
        add(form("s_wyrmling2", "Great Sand Wyrm", SERPENT, 5, 9, 2, 1, setOf(ARMORED)))
        add(structure("s_idol", "Serpent Idol", SERPENT, 1, 5, 2, SENTRY, POISONOUS))
        add(equipment("s_eye", "Hypnotic Eye", SERPENT, "+1 range.", Buff(range = 1)))
        add(strategy("s_swamp", "Murky Swamp", SERPENT, FieldRule.SWAMP))
        add(strategy("s_trance", "Hypnotic Trance", SERPENT, FieldRule.SILENCE))
        add(equipment("s_fangs", "Venom Fangs", SERPENT, "+1 attack and Poisonous.", Buff(attack = 1), GrantKeyword(POISONOUS)))
        add(equipment("s_scales", "Emerald Scales", SERPENT, "+1 health and Armored.", Buff(health = 1), GrantKeyword(ARMORED)))

        // ------------------------------------------------------------------ LION
        add(king("l_king_pride", "Pride King", LION, 5, 33, 2, 1, setOf(Keyword.COMMANDER), null,
            flavor = "His roar is an order."))
        add(king("l_king_queen", "Lioness Queen", LION, 2, 31, 2, 1, setOf(PACK_HUNTER, ARMORED),
            ability("Pounce", "Leap next to an enemy within 4, ignoring units in the way, and attack it.",
                TargetRule(TargetKind.ENEMY_UNIT, 4), 2, effects = listOf(EffectOp.Pounce)),
            flavor = "You never see the first strike."))
        add(unit("l_cub", "Lion Cub", LION, 2, 5, 2, 1, setOf(PACK_HUNTER)))
        add(unit("l_lioness", "Lioness Hunter", LION, 3, 6, 2, 1, setOf(PACK_HUNTER)))
        add(unit("l_guard", "Royal Guard", LION, 2, 7, 1, 1, setOf(ARMORED, RETALIATE)))
        add(unit("l_warlord", "Maned Warlord", LION, 4, 8, 2, 1, setOf(RETALIATE), elite = true))
        add(unit("l_runner", "Pride Runner", LION, 3, 4, 4, 1))
        add(unit("l_sage", "Pride Sage", LION, 1, 5, 2, 2, emptySet(),
            ability("Blessing", "Give an ally within 2 a 2-point shield.", TargetRule(TargetKind.FRIENDLY_UNIT, 2), 2,
                quick = true, effects = listOf(Shield(2)))))
        add(unit("l_matriarch", "Pride Matriarch", LION, 3, 6, 2, 1, setOf(PACK_HUNTER),
            ability("Rallying Roar", "Allies next to her get +1 attack for 1 turn.", TargetRule.SELF, 3,
                effects = listOf(Area(1, Side.FRIENDLY, includeCenter = false, op = Buff(attack = 1, turns = 1))))))
        add(unit("l_veteran", "Scarred Veteran", LION, 4, 6, 2, 1, setOf(ARMORED), elite = true))
        add(unit("l_stalker", "Savanna Stalker", LION, 3, 4, 3, 1, setOf(HIDDEN)))
        add(unit("l_cheetah", "Cheetah Outrider", LION, 3, 3, 5, 1, setOf(PACK_HUNTER)))
        add(unit("l_champion", "Pride Champion", LION, 5, 7, 2, 1, setOf(RETALIATE), elite = true))
        add(unit("l_paragon", "Sunmane Paragon", LION, 7, 12, 2, 1, setOf(ARMORED, RETALIATE),
            ability("Golden Roar", "Allies within 2 get +1 attack for 1 turn.", TargetRule.SELF, 3,
                effects = listOf(Area(2, Side.FRIENDLY, includeCenter = false, op = Buff(attack = 1, turns = 1)))),
            champion = true))
        add(unit("l_chimera", "Sunfire Chimera", LION, 6, 10, 2, 1, setOf(RETALIATE),
            ability("Fire Breath", "Deal 2 damage to an enemy within 2 and 1 damage to enemies next to it.",
                TargetRule(TargetKind.ENEMY_UNIT, 2), 3,
                effects = listOf(Damage(2), Area(1, Side.ENEMY, includeCenter = false, op = Damage(1)))),
            champion = true))
        add(magic("l_charge", "Glorious Charge", LION, "An allied unit gets +2 attack and +1 movement for 1 turn.",
            TargetRule.FRIENDLY, Buff(attack = 2, move = 1, turns = 1), rank = 2))
        add(magic("l_valor", "Valor", LION, "Heal an allied unit by 3. It gets +1 attack for 1 turn.",
            TargetRule.FRIENDLY, Heal(3), Buff(attack = 1, turns = 1)))
        add(magic("l_sunfire", "Sunfire", LION, "Deal 3 damage to an enemy unit and draw a card.",
            TargetRule.ENEMY, Damage(3), EffectOp.Draw(1), rank = 3))
        add(magic("l_rally", "Rally to the King", LION,
            "An allied unit moves to the empty square next to your King that is nearest to it.",
            TargetRule.FRIENDLY, EffectOp.RallyToKing))
        add(magic("l_courage", "Pride's Courage", LION, "Give an allied unit and allies next to it a 2-point shield.",
            TargetRule.FRIENDLY, Area(1, Side.FRIENDLY, includeCenter = true, op = Shield(2)), rank = 2))
        add(magic("l_sunbeam", "Sunbeam", LION, "Heal an allied unit by 2. It gets +1 movement for 1 turn.",
            TargetRule.FRIENDLY, Heal(2), Buff(move = 1, turns = 1)))
        add(magic("l_gaze", "Lion's Gaze", LION, "Stun an enemy unit for 1 turn. It gets −1 attack for 2 turns.",
            TargetRule.ENEMY, Stun(1), Buff(attack = -1, turns = 2), rank = 2))
        add(equipment("l_laurel", "Laurel Crown", LION, "+1 attack and +1 health.", Buff(attack = 1, health = 1)))
        add(equipment("l_spear", "Hunter's Spear", LION, "+2 attack.", Buff(attack = 2)))
        add(equipment("l_claws", "Golden Claws", LION, "Pack Hunter and +1 attack.", GrantKeyword(PACK_HUNTER), Buff(attack = 1)))
        add(strategy("l_banner", "War Banner", LION, FieldRule.WAR_DRUMS))
        add(strategy("l_grass", "Tall Grass", LION, FieldRule.AMBUSH))
        // 0.11: the Lion Pride leans on Strategy.
        add(strategy("l_decree", "Royal Decree", LION, FieldRule.ROYAL_DECREE))
        add(strategy("l_dawn", "Golden Dawn", LION, FieldRule.GOLDEN_DAWN))
        add(strategy("l_formation", "Pride Formation", LION, FieldRule.PRIDE_FORMATION))
        add(magic("l_roar", "Roar of the Pride", LION, "An allied unit and allies next to it get +1 attack and +1 movement for 1 turn.",
            TargetRule.FRIENDLY, Area(1, Side.FRIENDLY, includeCenter = true, op = Buff(attack = 1, move = 1, turns = 1)), rank = 2))
        add(equipment("l_scepter", "Sun Scepter", LION, "Commander: allies within 3 squares get +1 attack.", GrantKeyword(Keyword.COMMANDER)))
        // 0.12: buffs and healing, lions that rise through the ranks, a monument and a shrine.
        add(magic("l_tribute", "Royal Tribute", LION, "Draw 2 cards.", TargetRule.NONE, Draw(2), rank = 2))
        add(magic("l_twilight", "Twilight", LION, "End the Strategy field on the battlefield (yours or your opponent's).", TargetRule.NONE, ClearField))
        add(magic("l_battlecry", "Battle Cry", LION, "An allied unit gets +1 attack for 1 turn.",
            TargetRule.FRIENDLY, Buff(attack = 1, turns = 1), swift = true))
        add(magic("l_inspire", "Inspire", LION, "An allied unit gets a 1-point shield, and +1 attack and +1 movement for 2 turns.",
            TargetRule.FRIENDLY, Shield(1), Buff(attack = 1, move = 1, turns = 2), rank = 2))
        add(magic("l_manelight", "Mane of Light", LION, "Heal an allied unit by 4 and remove stun and poison.",
            TargetRule.FRIENDLY, EffectOp.Cleanse, Heal(4), rank = 2))
        add(unit("l_youngling", "Young Lion", LION, 2, 4, 2, 1, evolve = Evolution("l_youngling2", kills = 1)))
        add(form("l_youngling2", "Lion Warrior", LION, 4, 7, 2, 1, setOf(RETALIATE), evolve = Evolution("l_youngling3", kills = 2)))
        add(form("l_youngling3", "Lion Lord", LION, 6, 10, 2, 1, setOf(RETALIATE, COMMANDER)))
        add(unit("l_squire", "Pride Squire", LION, 2, 5, 2, 1, evolve = Evolution("l_squire2", turns = 3)))
        add(form("l_squire2", "Royal Knight", LION, 5, 8, 2, 1, setOf(ARMORED)))
        add(structure("l_monument", "War Monument", LION, 0, 10, 1, TAUNT, COMMANDER))
        add(structure("l_shrine", "Sun Shrine", LION, 0, 6, 1, MENDING))
        add(equipment("l_mane", "Golden Mane", LION, "+1 attack and +2 health.", Buff(attack = 1, health = 2)))
        add(equipment("l_shield", "Pride Shield", LION, "Armored.", GrantKeyword(ARMORED)))

        // ---------------------------------------------------------------- VERMIN
        add(king("v_king_rat", "Rat King", VERMIN, 2, 33, 2, 1, emptySet(),
            ability("Call the Mischief", "Two Swarm Rats appear next to him.", TargetRule.SELF, 3,
                effects = summons(SWARM_RAT, 2)),
            flavor = "A crown of tangled tails. A court of thousands."))
        add(king("v_king_seer", "Blight Seer", VERMIN, 2, 31, 2, 2, setOf(HIDDEN),
            ability("Blight Bolt", "Deal 2 damage to an enemy within 3 and 1 damage to every other unit next to it, friend or foe.",
                TargetRule(TargetKind.ENEMY_UNIT, 3), 3,
                effects = listOf(Damage(2), Area(1, Side.ALL, includeCenter = false, op = Damage(1)))),
            flavor = "The green stone whispers. He listens."))
        add(unit("v_rat", "Swarm Rat", VERMIN, 1, 2, 2, 1, setOf(PACK_HUNTER)))
        add(unit("v_tunnel", "Tunnel Rat", VERMIN, 2, 3, 2, 1, setOf(PACK_HUNTER)))
        add(unit("v_skulker", "Night Skulker", VERMIN, 2, 2, 3, 1, setOf(HIDDEN),
            ability("Knife in the Dark", "Deal 1 damage to an adjacent enemy.", TargetRule(TargetKind.ENEMY_UNIT, 1), 2,
                quick = true, effects = listOf(Damage(1)))))
        add(unit("v_blade", "Shadow Blade", VERMIN, 3, 3, 3, 1, setOf(HIDDEN, BACKSTAB)))
        add(unit("v_slinger", "Sling Rat", VERMIN, 1, 2, 2, 3))
        add(unit("v_brood", "Brood Mother", VERMIN, 1, 5, 1, 1, setOf(BROOD)))
        add(unit("v_driver", "Pack Driver", VERMIN, 2, 4, 2, 1, emptySet(),
            arrival = summons(SWARM_RAT, 2), text = "Arrival: two Swarm Rats appear next to it."))
        add(unit("v_warren", "Warren Keeper", VERMIN, 1, 4, 1, 1, emptySet(),
            arrival = summons(SWARM_RAT, 3), text = "Arrival: three Swarm Rats appear next to it.", elite = true))
        add(unit("v_brute", "Mutant Brute", VERMIN, 5, 7, 1, 1, setOf(RETALIATE), elite = true))
        add(unit("v_friar", "Plague Friar", VERMIN, 2, 4, 2, 1, setOf(POISONOUS)))
        add(unit("v_tinker", "Blight Tinker", VERMIN, 1, 3, 2, 2, emptySet(),
            ability("Blight Spark", "Deal 2 damage to an enemy within 3 and 1 damage to every other unit next to it, friend or foe.",
                TargetRule(TargetKind.ENEMY_UNIT, 3), 3,
                effects = listOf(Damage(2), Area(1, Side.ALL, includeCenter = false, op = Damage(1))))))
        add(unit("v_colossus", "Blightspawn Colossus", VERMIN, 6, 11, 1, 1, setOf(RETALIATE, REGENERATE),
            arrival = summons("v_rat", 2), text = "Arrival: two Swarm Rats appear next to it.", champion = true))
        add(unit("v_matriarch", "Warren Matriarch", VERMIN, 4, 12, 1, 1, setOf(BROOD, REGENERATE),
            arrival = summons("v_rat", 3), text = "Arrival: three Swarm Rats appear next to her.", champion = true))
        add(magic("v_blightfire", "Blightfire", VERMIN,
            "Deal 4 damage to an enemy unit and 1 damage to every other unit next to it, friend or foe.",
            TargetRule.ENEMY, Damage(4), Area(1, Side.ALL, includeCenter = false, op = Damage(1)), rank = 3))
        add(magic("v_vanish", "Vanishing Trick", VERMIN, "An allied unit becomes Hidden for 2 turns and gets +1 movement for 1 turn.",
            TargetRule.FRIENDLY, GrantKeyword(HIDDEN, 2), Buff(move = 1, turns = 1)))
        add(magic("v_swarm", "Call the Swarm", VERMIN, "Two Swarm Rats appear next to an allied unit.",
            TargetRule.FRIENDLY, Summon(SWARM_RAT), Summon(SWARM_RAT), rank = 2))
        add(magic("v_ratrun", "Rat Run", VERMIN,
            "An enemy unit swaps squares with your unit nearest to it (Immovable units can't be swapped).",
            TargetRule.ENEMY, EffectOp.SwapWithNearestAlly, rank = 2))
        add(magic("v_gnaw", "Gnaw", VERMIN, "Deal 1 damage to an enemy unit and poison it: 1 damage per turn for 2 turns.",
            TargetRule.ENEMY, Damage(1), Poison(1, 2)))
        add(magic("v_mutate", "Unstable Mutation", VERMIN, "An allied unit gets +2 attack and +2 health for good, but takes 1 damage.",
            TargetRule.FRIENDLY, Buff(attack = 2, health = 2), Damage(1), rank = 2))
        add(magic("v_rot", "Creeping Rot", VERMIN, "An enemy unit gets −2 attack and −1 movement for 2 turns.",
            TargetRule.ENEMY, Buff(attack = -2, move = -1, turns = 2), rank = 2))
        add(equipment("v_blades", "Rusty Blades", VERMIN, "+2 attack.", Buff(attack = 2)))
        add(equipment("v_mask", "Plague Mask", VERMIN, "Poisonous, Regenerate and +1 health.", GrantKeyword(POISONOUS), GrantKeyword(REGENERATE), Buff(health = 1)))
        add(equipment("v_rags", "Tattered Hood", VERMIN, "Hidden and +1 attack.", GrantKeyword(HIDDEN), Buff(attack = 1)))
        add(strategy("v_tunnels", "Warren Tunnels", VERMIN, FieldRule.TUNNELS))
        add(strategy("v_plague", "Creeping Plague", VERMIN, FieldRule.PLAGUE))
        add(equipment("v_grafts", "Mutant Grafts", VERMIN, "+2 attack and +2 health, but −1 movement.",
            Buff(attack = 2, health = 2, move = -1)))
        add(equipment("v_cloak", "Shadow Cloak", VERMIN, "Hidden, Backstab and +1 movement.", GrantKeyword(HIDDEN), GrantKeyword(BACKSTAB), Buff(move = 1)))
        // 0.11: a bit of everything for the Vermin Horde.
        add(equipment("v_tailblade", "Tail Blade", VERMIN, "Backstab and +1 attack.", GrantKeyword(BACKSTAB), Buff(attack = 1)))
        add(equipment("v_shard", "Glowshard Charm", VERMIN, "+2 attack and Regenerate, but −1 movement.",
            Buff(attack = 2, move = -1), GrantKeyword(REGENERATE)))
        add(magic("v_bomb", "Plague Bomb", VERMIN,
            "Deal 1 damage to an enemy unit and enemies next to it, and poison them: 1 damage per turn for 2 turns.",
            TargetRule.ENEMY, Area(1, Side.ENEMY, includeCenter = true, op = Damage(1)),
            Area(1, Side.ENEMY, includeCenter = true, op = Poison(1, 2)), rank = 2))
        add(magic("v_gorge", "Gorge", VERMIN, "Heal an allied unit by 3. It gets +1 attack for good.",
            TargetRule.FRIENDLY, Heal(3), Buff(attack = 1), rank = 2))
        add(strategy("v_tide", "Rat Tide", VERMIN, FieldRule.RAT_TIDE))
        // 0.12: dirty tricks, mutants that grow and a warpstone spire.
        add(magic("v_scrounge", "Scrounge", VERMIN, "Draw 2 cards.", TargetRule.NONE, Draw(2), rank = 2))
        add(magic("v_smoke", "Smoke Out", VERMIN, "End the Strategy field on the battlefield (yours or your opponent's).", TargetRule.NONE, ClearField))
        add(magic("v_skitter", "Skitter", VERMIN, "An allied unit gets +1 movement for 1 turn.",
            TargetRule.FRIENDLY, Buff(move = 1, turns = 1), swift = true))
        add(magic("v_ratbite", "Rat Bite", VERMIN, "Poison an enemy unit: 1 damage per turn for 2 turns.",
            TargetRule.ENEMY, Poison(1, 2), swift = true))
        add(magic("v_warpstorm", "Warp Storm", VERMIN, "Deal 2 damage to an enemy unit and enemies next to it.",
            TargetRule.ENEMY, Area(1, Side.ENEMY, includeCenter = true, op = Damage(2)), rank = 3))
        add(unit("v_runt", "Sewer Runt", VERMIN, 1, 3, 3, 1, evolve = Evolution("v_runt2", kills = 1)))
        add(form("v_runt2", "Plague Rat", VERMIN, 3, 4, 3, 1, setOf(POISONOUS), evolve = Evolution("v_runt3", kills = 1)))
        add(form("v_runt3", "Rat Ogre", VERMIN, 6, 9, 2, 1, setOf(REGENERATE)))
        add(unit("v_unstable", "Unstable Mutant", VERMIN, 2, 4, 2, 1, evolve = Evolution("v_unstable2", turns = 2)))
        add(form("v_unstable2", "Warped Hulk", VERMIN, 5, 7, 2, 1, setOf(ARMORED)))
        add(structure("v_spire", "Warpstone Spire", VERMIN, 2, 3, 2, SENTRY))
    }

    @Volatile private var current: List<CardDef> = base
    @Volatile private var byId: Map<String, CardDef> = base.associateBy { it.id }

    /**
     * Replaces card definitions with [transform] applied to the original ones. Only the balance
     * simulator uses this, to measure "what if" stat changes without editing the card list.
     */
    fun applyTuning(transform: (CardDef) -> CardDef) {
        current = base.map(transform)
        byId = current.associateBy { it.id }
    }

    fun resetTuning() = applyTuning { it }

    fun get(id: String): CardDef = byId[id] ?: error("Unknown card id: $id")
    fun find(id: String): CardDef? = byId[id]
    fun ofRace(race: Race): List<CardDef> = all.filter { it.race == race }

    // ---------------------------------------------------------------- builders

    private fun ability(
        name: String, text: String, target: TargetRule, cooldown: Int,
        quick: Boolean = false, effects: List<EffectOp>,
    ) = AbilityDef(name, text, target, effects, cooldown, quick)

    private fun unit(
        id: String, name: String, race: Race, atk: Int, hp: Int, move: Int, range: Int,
        keywords: Set<Keyword> = emptySet(), ability: AbilityDef? = null,
        arrival: List<EffectOp> = emptyList(), text: String = "", elite: Boolean = false, champion: Boolean = false,
        evolve: Evolution? = null, collectible: Boolean = true,
    ) = CardDef(id, name, race, CardType.UNIT, rulesText = text, collectible = collectible,
        unit = UnitStats(
            atk, hp, move, range, keywords, listOfNotNull(ability), arrival = arrival,
            slots = if (champion) 3 else if (elite) 2 else 1, evolve = evolve,
        ))

    /** An evolved form: only reached by evolving, never put in a deck. */
    private fun form(
        id: String, name: String, race: Race, atk: Int, hp: Int, move: Int, range: Int,
        keywords: Set<Keyword> = emptySet(), evolve: Evolution? = null,
    ) = unit(id, name, race, atk, hp, move, range, keywords, evolve = evolve, collectible = false)

    /** A structure: can't move or attack (and can't be pushed); see the Taunt, Sentry and Mending keywords. */
    private fun structure(id: String, name: String, race: Race, atk: Int, hp: Int, range: Int, vararg keywords: Keyword) =
        unit(id, name, race, atk, hp, 0, range, setOf(Keyword.STRUCTURE, IMMOVABLE) + keywords)

    /** Arrival effect: [n] Swarm Rats (or other tokens) appear next to the unit. */
    private fun summons(cardId: String, n: Int) = List(n) { Summon(cardId) }

    private fun king(
        id: String, name: String, race: Race, atk: Int, hp: Int, move: Int, range: Int,
        keywords: Set<Keyword>, ability: AbilityDef?, flavor: String,
    ) = CardDef(id, name, race, CardType.UNIT, flavor = flavor,
        // Every King is Immovable (and immune to damage from cards and abilities, see GameEngine).
        unit = UnitStats(atk, hp, move, range, setOf(Keyword.IMMOVABLE) + keywords, listOfNotNull(ability), isKing = true))

    private fun magic(
        id: String, name: String, race: Race, text: String, target: TargetRule, vararg effects: EffectOp,
        rank: Int = 1, swift: Boolean = false,
    ) = CardDef(id, name, race, CardType.MAGIC, rulesText = text, target = target, effects = effects.toList(), rank = rank, swift = swift)

    private fun strategy(id: String, name: String, race: Race, rule: FieldRule) =
        CardDef(id, name, race, CardType.STRATEGY,
            rulesText = "Field: ${rule.description} Lasts until any Strategy card replaces it.",
            effects = listOf(Field(rule)))

    private fun equipment(id: String, name: String, race: Race, text: String, vararg effects: EffectOp) =
        CardDef(id, name, race, CardType.EQUIPMENT, rulesText = "Equip an allied unit: $text",
            target = TargetRule.FRIENDLY, effects = effects.toList())
}
