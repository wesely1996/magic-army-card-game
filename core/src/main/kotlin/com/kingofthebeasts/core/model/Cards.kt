package com.kingofthebeasts.core.model

enum class CardType(val displayName: String) {
    UNIT("Unit"), MAGIC("Magic"), STRATEGY("Strategy"), EQUIPMENT("Equipment")
}

enum class TargetKind { NONE, SELF, FRIENDLY_UNIT, ENEMY_UNIT, ANY_UNIT, STACK_ITEM }

/** What an ability or card targets. [range] is measured from the source unit (abilities only). */
data class TargetRule(val kind: TargetKind, val range: Int = UNLIMITED) {
    companion object {
        const val UNLIMITED = 99
        val NONE = TargetRule(TargetKind.NONE)
        val SELF = TargetRule(TargetKind.SELF)
        val FRIENDLY = TargetRule(TargetKind.FRIENDLY_UNIT)
        val ENEMY = TargetRule(TargetKind.ENEMY_UNIT)
        val STACK = TargetRule(TargetKind.STACK_ITEM)
    }
}

enum class Side { FRIENDLY, ENEMY, ALL }

/** Durations are counted in "ends of the affected unit owner's turns". */
const val PERMANENT = -1

sealed interface EffectOp {
    data class Damage(val amount: Int) : EffectOp
    data class Heal(val amount: Int) : EffectOp
    data class Buff(
        val attack: Int = 0,
        val health: Int = 0,
        val move: Int = 0,
        val range: Int = 0,
        val turns: Int = PERMANENT,
    ) : EffectOp
    data class Stun(val turns: Int) : EffectOp
    data class Poison(val damage: Int, val turns: Int) : EffectOp
    data class Shield(val amount: Int) : EffectOp
    data class GrantKeyword(val keyword: Keyword, val turns: Int = PERMANENT) : EffectOp
    data object Cleanse : EffectOp
    /** Cancels the targeted stack item. */
    data object Counter : EffectOp
    /** Applies [op] to every unit of [side] within [radius] of the target. */
    data class Area(val radius: Int, val side: Side, val includeCenter: Boolean, val op: EffectOp) : EffectOp
    data class Draw(val count: Int) : EffectOp
    data class Field(val rule: FieldRule, val turns: Int) : EffectOp
    /** Places a token copy of [cardId] on an empty square next to the source unit. */
    data class Summon(val cardId: String) : EffectOp
    /** Source unit swaps squares with the target unit. */
    data object Swap : EffectOp
    /** Take permanent control of the target if its current health is at most [maxHealth]. It can't be a King. */
    data class Enthrall(val maxHealth: Int) : EffectOp
    /** Source leaps next to the target (ignoring blockers) and attacks it. */
    data object Pounce : EffectOp
}

data class AbilityDef(
    val name: String,
    val text: String,
    val target: TargetRule,
    val effects: List<EffectOp>,
    val cooldown: Int,
    /** Quick abilities can be used to interrupt the opponent. */
    val quick: Boolean = false,
)

data class UnitStats(
    val attack: Int,
    val health: Int,
    val move: Int,
    val range: Int,
    val keywords: Set<Keyword> = emptySet(),
    val abilities: List<AbilityDef> = emptyList(),
    val isKing: Boolean = false,
)

data class CardDef(
    val id: String,
    val name: String,
    val race: Race,
    val type: CardType,
    /** Rules text for non-unit cards; for units it is built from keywords and abilities. */
    val rulesText: String = "",
    val flavor: String = "",
    val unit: UnitStats? = null,
    val target: TargetRule = TargetRule.NONE,
    val effects: List<EffectOp> = emptyList(),
) {
    val isKing: Boolean get() = unit?.isKing == true
    val maxCopies: Int get() = if (isKing) 1 else 3

    /** Magic cards can always be played as interrupts. */
    val isQuick: Boolean get() = type == CardType.MAGIC

    val text: String
        get() = if (unit == null) rulesText else buildString {
            if (unit.isKing) append("King. ")
            unit.keywords.forEach { append(it.displayName).append(": ").append(it.description).append(' ') }
            unit.abilities.forEach {
                append(it.name)
                append(if (it.quick) " (quick, " else " (")
                append("cooldown ").append(it.cooldown).append("): ")
                append(it.text).append(' ')
            }
            if (rulesText.isNotEmpty()) append(rulesText)
        }.trim()
}

enum class FieldRule(val displayName: String, val description: String) {
    BLITZ("Blitz", "Once per turn, moving a unit doesn't end your turn."),
    HUNTING_GROUNDS("Hunting Grounds", "Your attacks deal +1 damage to wounded enemies."),
    FORTIFY("Fortify", "Your units take 1 less damage."),
    SANCTUARY("Sanctuary", "Your units heal 1 at the start of your turn."),
    HIGH_GROUND("High Ground", "Your ranged units (range 2+) get +1 range."),
    TAILWIND("Tailwind", "Your units get +1 movement."),
    SWAMP("Swamp", "Enemy units get -1 movement."),
    SILENCE("Silence", "Your opponent can't interrupt your actions."),
    WAR_DRUMS("War Drums", "Your units get +1 attack."),
    AMBUSH("Ambush", "You may play units anywhere in your half of the board (still 2+ squares from enemies)."),
}
