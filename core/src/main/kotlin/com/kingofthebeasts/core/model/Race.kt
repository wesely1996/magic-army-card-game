package com.kingofthebeasts.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Race(val displayName: String, val blurb: String) {
    WOLF("Wolf Pack", "Fast pack hunters that overwhelm lone targets."),
    BEAR("Bear Clan", "Slow, stubborn brawlers that shrug off punishment."),
    HAWK("Hawk Aerie", "Flying skirmishers that strike from afar."),
    SERPENT("Serpent Coil", "Venom, hypnosis and denial."),
    LION("Lion Pride", "Proud warriors led by inspiring kings."),
    VERMIN("Vermin Horde", "Mutant rat swarms that hide, backstab and bury the enemy in numbers."),
    ;

    /** The racial trait an army gets when its King is of this race. */
    val trait: RacialTrait
        get() = when (this) {
            WOLF -> RacialTrait.PACK_TACTICS
            BEAR -> RacialTrait.THICK_FUR
            HAWK -> RacialTrait.EAGLE_EYES
            SERPENT -> RacialTrait.VENOM_BLOOD
            LION -> RacialTrait.ROYAL_PRIDE
            VERMIN -> RacialTrait.ENDLESS_HORDE
        }
}

/** Army-wide bonus (sometimes with a drawback) set by the race of the deck's King. */
enum class RacialTrait(val displayName: String, val description: String) {
    PACK_TACTICS("Pack Tactics", "All your units have Pack Hunter."),
    THICK_FUR("Thick Fur", "Your units have +2 health, but units with 3 or more movement get −1 movement."),
    EAGLE_EYES("Eagle Eyes", "Your ranged units (range 2+) get +1 range, but all your units have −1 health (never below 1)."),
    VENOM_BLOOD("Venom Blood", "All your units are Poisonous, but have −1 attack (never below 1)."),
    ROYAL_PRIDE("Royal Pride", "Your King has +3 health and +1 attack, but your other units have −1 health (never below 1)."),
    ENDLESS_HORDE("Endless Horde", "You can have up to 20 units on the board instead of 10."),
}

/**
 * Passive traits. The first group are common keywords; the second group are
 * signature gimmicks that only Kings have.
 */
enum class Keyword(val displayName: String, val description: String) {
    FLYING("Flying", "Moves over other units."),
    ARMORED("Armored", "Takes 1 less damage from every hit."),
    RETALIATE("Retaliate", "Strikes back when it survives a melee attack."),
    POISONOUS("Poisonous", "Its attacks poison the target (1 damage per turn for 2 turns)."),
    PACK_HUNTER("Pack Hunter", "+1 attack if another ally is next to the target."),
    REGENERATE("Regenerate", "Heals 1 at the start of your turn."),
    IMMOVABLE("Immovable", "Can't be pushed, swapped or replaced by cards or abilities."),
    HIDDEN("Hidden", "Enemies can only attack or target it from a square next to it."),
    BACKSTAB("Backstab", "+2 attack when it attacks from behind (from the target's own side)."),
    BROOD("Brood", "At the start of your next 2 turns after it arrives, a Swarm Rat pops out next to it."),

    BLOODTHIRST("Bloodthirst", "Whenever an enemy unit dies, heals 2 and gains +1 attack permanently."),
    UNSTOPPABLE("Unstoppable", "Can't be stunned. No single hit deals it more than 3 damage."),
    GUARDIAN("Guardian", "Adjacent allies take 1 less damage."),
    TEMPEST("Tempest", "At the start of your turn, deals 1 damage to a random enemy within 3 squares."),
    PETRIFY("Petrifying Gaze", "Units it attacks are stunned for 1 turn."),
    COMMANDER("Commander", "Other allies within 3 squares get +1 attack."),
    PACK_CALLER("Call the Pack", "At the start of your turn, summons a Wolf Pup token next to it (at most 2 pups at a time)."),
}
