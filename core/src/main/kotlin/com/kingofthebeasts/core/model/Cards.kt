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
    /** Sets the battlefield's one field (Strategy cards); it lasts until another Strategy replaces it. */
    data class Field(val rule: FieldRule) : EffectOp
    /** Places a token copy of [cardId] on an empty square next to the source unit (or the target, for cards). */
    data class Summon(val cardId: String) : EffectOp
    /** Source unit swaps squares with the target unit. */
    data object Swap : EffectOp
    /** Take permanent control of the target if its current health is at most [maxHealth]. It can't be a King. */
    data class Enthrall(val maxHealth: Int) : EffectOp
    /** Source leaps next to the target (ignoring blockers) and attacks it. */
    data object Pounce : EffectOp
    /**
     * Pushes the target up to [distance] squares in a straight line (direction set by [from]).
     * If a unit or the board edge stops it, it takes [impactDamage].
     */
    data class Push(val distance: Int, val from: PushFrom, val impactDamage: Int = 0) : EffectOp
    /**
     * The target (not a King) is shuffled into its controller's deck and a random unit card
     * from that deck takes its square. Tokens simply vanish. Nothing happens if the deck has no units.
     */
    data object Replace : EffectOp
    /** The allied target moves to the empty square next to your King that is nearest to it. */
    data object RallyToKing : EffectOp
    /** The enemy target swaps squares with the caster's movable unit nearest to it. */
    data object SwapWithNearestAlly : EffectOp
}

/** Damage (now or over time) and summons make a spell exhausting: it is used once, then gone. */
val EffectOp.exhausting: Boolean
    get() = when (this) {
        is EffectOp.Damage, is EffectOp.Poison, is EffectOp.Summon -> true
        is EffectOp.Push -> impactDamage > 0
        is EffectOp.Area -> op.exhausting
        else -> false
    }

enum class PushFrom {
    /** Directly away from the caster's unit nearest to the target. */
    NEAREST_ALLY,
    /** Straight back toward the target owner's own side of the board. */
    OWNER_SIDE,
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
    /** Effects that happen when the unit is played from a card (source and target: the unit itself). */
    val arrival: List<EffectOp> = emptyList(),
    /** Unit slots it takes up: 3 for Champions, 2 for Elite units, 1 for the rest, 0 for Kings. */
    val slots: Int = if (isKing) 0 else 1,
) {
    val isElite: Boolean get() = slots == 2
    val isChampion: Boolean get() = slots >= 3
}

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
    /** Magic cards: 1★, 2★ or 3★. Stronger spells are rarer in a deck (3, 2 or 1 copies). */
    val rank: Int = 1,
) {
    val isKing: Boolean get() = unit?.isKing == true

    /**
     * Star tier: units 1★ (normal, 1 slot), 2★ (Elite, 2 slots), 3★ (Champion, 3 slots); Magic by
     * [rank]; 0 for Kings, Strategy and Equipment.
     */
    val stars: Int
        get() = when {
            isKing -> 0
            unit != null -> unit.slots
            type == CardType.MAGIC -> rank
            else -> 0
        }

    /** Copies allowed in a deck: 3 / 2 / 1 for 1★ / 2★ / 3★ cards, 1 King, 3 of other cards. */
    val maxCopies: Int
        get() = when {
            isKing -> 1
            stars > 0 -> 4 - stars
            else -> 3
        }

    /** Magic cards can always be played as interrupts. */
    val isQuick: Boolean get() = type == CardType.MAGIC

    /**
     * After use, Magic cards that don't deal damage or summon go to the discard pile, which becomes
     * the new deck when the deck runs out. Everything else (units, equipment, Strategy cards, damage
     * and summoning spells) is exhausted: used once, then out of the game.
     */
    val returnsToDeck: Boolean
        get() = type == CardType.MAGIC && effects.none { it.exhausting }

    val text: String
        get() = if (unit == null) rulesText else buildString {
            if (unit.isKing) append("King: takes no damage from Magic cards or abilities. ")
            if (unit.isElite) append("Elite: takes 2 unit slots. ")
            if (unit.isChampion) append("Champion: takes 3 unit slots. ")
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
    HUNTING_GROUNDS("Hunting Grounds", "Your attacks deal +2 damage to wounded enemies."),
    FORTIFY("Fortify", "Your units take 1 less damage."),
    SANCTUARY("Sanctuary", "Your units heal 2 at the start of your turn."),
    HIGH_GROUND("High Ground", "Your ranged units (range 2+) get +1 range and +1 attack."),
    TAILWIND("Tailwind", "Your units get +1 movement and Flying."),
    SWAMP("Swamp", "Enemy units get −1 movement and −1 attack (never below 1)."),
    SILENCE("Silence", "Your opponent can't interrupt your actions."),
    WAR_DRUMS("War Drums", "Your units get +1 attack."),
    AMBUSH("Ambush", "You may play units anywhere in your half of the board (still 2+ squares from enemies)."),
    TUNNELS("Warren Tunnels", "Your units are Hidden."),
    PLAGUE("Creeping Plague", "At the start of your turn, every enemy unit next to one of your units takes 1 damage (not Kings)."),
}
