package com.magicarmy.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class Race(val displayName: String, val blurb: String) {
    WOLF("Wolf Pack", "Fast pack hunters that overwhelm lone targets."),
    BEAR("Bear Clan", "Slow, stubborn brawlers that shrug off punishment."),
    HAWK("Hawk Aerie", "Flying skirmishers that strike from afar."),
    SERPENT("Serpent Coil", "Venom, hypnosis and denial."),
    LION("Lion Pride", "Proud warriors led by inspiring kings."),
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

    BLOODTHIRST("Bloodthirst", "Whenever an enemy unit dies, heals 2 and gains +1 attack permanently."),
    UNSTOPPABLE("Unstoppable", "Can't be stunned. No single hit deals it more than 3 damage."),
    GUARDIAN("Guardian", "Adjacent allies take 1 less damage."),
    TEMPEST("Tempest", "At the start of your turn, deals 1 damage to a random enemy unit."),
    PETRIFY("Petrifying Gaze", "Units it attacks are stunned for 1 turn."),
    COMMANDER("Commander", "Other allies within 2 squares get +1 attack."),
}
