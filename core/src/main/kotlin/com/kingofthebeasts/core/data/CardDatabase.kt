package com.kingofthebeasts.core.data

import com.kingofthebeasts.core.game.GameEngine.SWARM_RAT
import com.kingofthebeasts.core.model.AbilityDef
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.EffectOp
import com.kingofthebeasts.core.model.EffectOp.Area
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
        add(king("w_king_alpha", "Alpha Wolf", WOLF, 4, 11, 2, 1, setOf(PACK_HUNTER, Keyword.PACK_CALLER), null,
            flavor = "One howl, and the forest answers."))
        add(king("w_king_moon", "Moon Howler", WOLF, 2, 8, 2, 1, setOf(Keyword.BLOODTHIRST),
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
        add(magic("w_frenzy", "Frenzy", WOLF, "Give an allied unit +2 attack for 1 turn.",
            TargetRule.FRIENDLY, Buff(attack = 2, turns = 1)))
        add(magic("w_bite", "Savage Bite", WOLF, "Deal 3 damage to an enemy unit.", TargetRule.ENEMY, Damage(3)))
        add(magic("w_scatter", "Scatter", WOLF, "Interrupt only. Cancel the action you are responding to.",
            TargetRule.STACK, EffectOp.Counter))
        add(magic("w_relay", "Pack Relay", WOLF,
            "Shuffle an allied unit (not your King) into your deck. A random unit from your deck takes its square.",
            TargetRule.FRIENDLY, EffectOp.Replace))
        add(strategy("w_hunt", "The Hunt", WOLF, FieldRule.BLITZ, 4))
        add(strategy("w_moonlit", "Moonlit Hunt", WOLF, FieldRule.HUNTING_GROUNDS, 4))
        add(equipment("w_collar", "Spiked Collar", WOLF, "+1 attack and Retaliate.",
            Buff(attack = 1), GrantKeyword(RETALIATE)))
        add(equipment("w_charm", "Fang Charm", WOLF, "+1 attack and +1 movement.", Buff(attack = 1, move = 1)))

        // ------------------------------------------------------------------ BEAR
        add(king("b_king_elder", "Elder Bear", BEAR, 3, 7, 1, 1, setOf(Keyword.UNSTOPPABLE),
            ability("Earthshaker Roar", "Stun all adjacent enemies for 1 turn.", TargetRule.SELF, 3,
                effects = listOf(Area(1, Side.ENEMY, includeCenter = false, op = Stun(1)))),
            flavor = "The mountain does not move for you."))
        add(king("b_king_warden", "Cave Warden", BEAR, 3, 7, 1, 1, setOf(Keyword.GUARDIAN, REGENERATE), null,
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
        add(magic("b_hide", "Thick Hide", BEAR, "Give an allied unit a 2-point shield.", TargetRule.FRIENDLY, Shield(2)))
        add(magic("b_hibernate", "Hibernate", BEAR, "Heal an allied unit by 3 and remove stun and poison.",
            TargetRule.FRIENDLY, Heal(3), EffectOp.Cleanse))
        add(magic("b_hug", "Bear Hug", BEAR, "Stun an enemy unit for 1 turn. As an interrupt it stops that unit's action.",
            TargetRule.ENEMY, Stun(1)))
        add(magic("b_shove", "Mighty Shove", BEAR,
            "Push an enemy unit 2 squares directly away from your nearest unit. If a unit or the edge stops it, it takes 2 damage.",
            TargetRule.ENEMY, EffectOp.Push(2, PushFrom.NEAREST_ALLY, impactDamage = 2)))
        add(strategy("b_den", "Den Fortress", BEAR, FieldRule.FORTIFY, 3))
        add(strategy("b_salmon", "Salmon Run", BEAR, FieldRule.SANCTUARY, 3))
        add(equipment("b_bark", "Bark Armor", BEAR, "+2 health.", Buff(health = 2)))
        add(equipment("b_claws", "Iron Claws", BEAR, "+2 attack.", Buff(attack = 2)))

        // ------------------------------------------------------------------ HAWK
        add(king("h_king_sky", "Sky Sovereign", HAWK, 2, 10, 3, 2, setOf(FLYING),
            ability("Change of Winds", "Swap places with any allied unit.", TargetRule.FRIENDLY, 2,
                quick = true, effects = listOf(EffectOp.Swap)),
            flavor = "Where the wind blows, the Aerie follows."))
        add(king("h_king_storm", "Storm Eagle", HAWK, 2, 8, 2, 2, setOf(FLYING, Keyword.TEMPEST),
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
        add(magic("h_gust", "Gust", HAWK, "Interrupt only. Cancel the action you are responding to.",
            TargetRule.STACK, EffectOp.Counter))
        add(magic("h_tailwind", "Tailwind", HAWK, "An allied unit gets +2 movement and Flying for 1 turn.",
            TargetRule.FRIENDLY, Buff(move = 2, turns = 1), GrantKeyword(FLYING, 1)))
        add(magic("h_skystrike", "Sky Strike", HAWK, "Deal 2 damage to an enemy unit and 1 damage to enemies next to it.",
            TargetRule.ENEMY, Damage(2), Area(1, Side.ENEMY, includeCenter = false, op = Damage(1))))
        add(magic("h_gale", "Gale Force", HAWK,
            "Blow an enemy unit up to 3 squares straight back toward its own side of the board.",
            TargetRule.ENEMY, EffectOp.Push(3, PushFrom.OWNER_SIDE)))
        add(strategy("h_high", "High Ground", HAWK, FieldRule.HIGH_GROUND, 4))
        add(strategy("h_winds", "Favorable Winds", HAWK, FieldRule.TAILWIND, 4))
        add(equipment("h_talons", "Razor Talons", HAWK, "+1 attack and +1 range.", Buff(attack = 1, range = 1)))
        add(equipment("h_amulet", "Eagle Eye Amulet", HAWK, "+1 health and +1 range.", Buff(health = 1, range = 1)))

        // --------------------------------------------------------------- SERPENT
        add(king("s_king_naga", "Naga Queen", SERPENT, 2, 11, 2, 2, setOf(POISONOUS),
            ability("Enthrall", "Take control of a non-King enemy within 2 that has 3 or less health.",
                TargetRule(TargetKind.ENEMY_UNIT, 2), 3, effects = listOf(EffectOp.Enthrall(3))),
            flavor = "Look into her eyes. Now fight for her."))
        add(king("s_king_basilisk", "Basilisk", SERPENT, 3, 10, 1, 1, setOf(Keyword.PETRIFY), null,
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
        add(magic("s_hiss", "Hiss of Denial", SERPENT, "Interrupt only. Cancel the action you are responding to.",
            TargetRule.STACK, EffectOp.Counter))
        add(magic("s_venom", "Venom Surge", SERPENT, "Poison an enemy unit: 2 damage per turn for 2 turns.",
            TargetRule.ENEMY, Poison(2, 2)))
        add(magic("s_shed", "Shed Skin", SERPENT, "Remove stun and poison from an allied unit and heal it by 2.",
            TargetRule.FRIENDLY, EffectOp.Cleanse, Heal(2)))
        add(magic("s_mirage", "Mirage", SERPENT,
            "Shuffle an enemy unit (not a King) into its owner's deck. A random unit from that deck takes its square.",
            TargetRule.ENEMY, EffectOp.Replace))
        add(strategy("s_swamp", "Murky Swamp", SERPENT, FieldRule.SWAMP, 4))
        add(strategy("s_trance", "Hypnotic Trance", SERPENT, FieldRule.SILENCE, 3))
        add(equipment("s_fangs", "Venom Fangs", SERPENT, "+1 attack and Poisonous.", Buff(attack = 1), GrantKeyword(POISONOUS)))
        add(equipment("s_scales", "Emerald Scales", SERPENT, "+1 health and Armored.", Buff(health = 1), GrantKeyword(ARMORED)))

        // ------------------------------------------------------------------ LION
        add(king("l_king_pride", "Pride King", LION, 5, 10, 2, 1, setOf(Keyword.COMMANDER), null,
            flavor = "His roar is an order."))
        add(king("l_king_queen", "Lioness Queen", LION, 4, 8, 2, 1, setOf(PACK_HUNTER, ARMORED),
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
        add(magic("l_charge", "Glorious Charge", LION, "An allied unit gets +2 attack and +1 movement for 1 turn.",
            TargetRule.FRIENDLY, Buff(attack = 2, move = 1, turns = 1)))
        add(magic("l_valor", "Valor", LION, "Heal an allied unit by 3. It gets +1 attack for 1 turn.",
            TargetRule.FRIENDLY, Heal(3), Buff(attack = 1, turns = 1)))
        add(magic("l_sunfire", "Sunfire", LION, "Deal 2 damage to an enemy unit and draw a card.",
            TargetRule.ENEMY, Damage(2), EffectOp.Draw(1)))
        add(magic("l_rally", "Rally to the King", LION,
            "An allied unit moves to the empty square next to your King that is nearest to it.",
            TargetRule.FRIENDLY, EffectOp.RallyToKing))
        add(strategy("l_banner", "War Banner", LION, FieldRule.WAR_DRUMS, 4))
        add(strategy("l_grass", "Tall Grass", LION, FieldRule.AMBUSH, 4))
        add(equipment("l_mane", "Golden Mane", LION, "+1 attack and +2 health.", Buff(attack = 1, health = 2)))
        add(equipment("l_shield", "Pride Shield", LION, "Armored.", GrantKeyword(ARMORED)))

        // ---------------------------------------------------------------- VERMIN
        add(king("v_king_rat", "Rat King", VERMIN, 3, 13, 2, 1, emptySet(),
            ability("Call the Mischief", "Two Swarm Rats appear next to him.", TargetRule.SELF, 2,
                effects = summons(SWARM_RAT, 2)),
            flavor = "A crown of tangled tails. A court of thousands."))
        add(king("v_king_seer", "Blight Seer", VERMIN, 2, 8, 2, 2, setOf(HIDDEN),
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
        add(magic("v_blightfire", "Blightfire", VERMIN,
            "Deal 3 damage to an enemy unit and 1 damage to every other unit next to it, friend or foe.",
            TargetRule.ENEMY, Damage(3), Area(1, Side.ALL, includeCenter = false, op = Damage(1))))
        add(magic("v_vanish", "Vanishing Trick", VERMIN, "An allied unit becomes Hidden for 2 turns and gets +1 movement for 1 turn.",
            TargetRule.FRIENDLY, GrantKeyword(HIDDEN, 2), Buff(move = 1, turns = 1)))
        add(magic("v_swarm", "Call the Swarm", VERMIN, "Two Swarm Rats appear next to an allied unit.",
            TargetRule.FRIENDLY, Summon(SWARM_RAT), Summon(SWARM_RAT)))
        add(magic("v_ratrun", "Rat Run", VERMIN,
            "An enemy unit swaps squares with your unit nearest to it (Immovable units can't be swapped).",
            TargetRule.ENEMY, EffectOp.SwapWithNearestAlly))
        add(strategy("v_tunnels", "Warren Tunnels", VERMIN, FieldRule.TUNNELS, 3))
        add(strategy("v_plague", "Creeping Plague", VERMIN, FieldRule.PLAGUE, 3))
        add(equipment("v_grafts", "Mutant Grafts", VERMIN, "+2 attack and +2 health, but −1 movement.",
            Buff(attack = 2, health = 2, move = -1)))
        add(equipment("v_cloak", "Shadow Cloak", VERMIN, "Hidden and Backstab.", GrantKeyword(HIDDEN), GrantKeyword(BACKSTAB)))
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
        arrival: List<EffectOp> = emptyList(), text: String = "", elite: Boolean = false,
    ) = CardDef(id, name, race, CardType.UNIT, rulesText = text,
        unit = UnitStats(atk, hp, move, range, keywords, listOfNotNull(ability), arrival = arrival, slots = if (elite) 2 else 1))

    /** Arrival effect: [n] Swarm Rats (or other tokens) appear next to the unit. */
    private fun summons(cardId: String, n: Int) = List(n) { Summon(cardId) }

    private fun king(
        id: String, name: String, race: Race, atk: Int, hp: Int, move: Int, range: Int,
        keywords: Set<Keyword>, ability: AbilityDef?, flavor: String,
    ) = CardDef(id, name, race, CardType.UNIT, flavor = flavor,
        // Every King is Immovable (and immune to damage from cards and abilities, see GameEngine).
        unit = UnitStats(atk, hp, move, range, setOf(Keyword.IMMOVABLE) + keywords, listOfNotNull(ability), isKing = true))

    private fun magic(id: String, name: String, race: Race, text: String, target: TargetRule, vararg effects: EffectOp) =
        CardDef(id, name, race, CardType.MAGIC, rulesText = text, target = target, effects = effects.toList())

    private fun strategy(id: String, name: String, race: Race, rule: FieldRule, turns: Int) =
        CardDef(id, name, race, CardType.STRATEGY,
            rulesText = "For $turns of your turns: ${rule.description}",
            effects = listOf(Field(rule, turns)))

    private fun equipment(id: String, name: String, race: Race, text: String, vararg effects: EffectOp) =
        CardDef(id, name, race, CardType.EQUIPMENT, rulesText = "Equip an allied unit: $text",
            target = TargetRule.FRIENDLY, effects = effects.toList())
}
