package com.kingofthebeasts.core.game

sealed interface Target {
    data object None : Target
    data class Unit(val unitId: Int) : Target
    data class Tile(val pos: Pos) : Target
    data class StackEntry(val itemId: Int) : Target
}

sealed interface Action {
    /** Deploy phase: place a unit card from the deck on a square of your deploy zone. */
    data class Deploy(val cardUid: Int, val pos: Pos) : Action
    data object EndDeploy : Action
    data class Move(val unitId: Int, val to: Pos) : Action
    data class Attack(val unitId: Int, val targetId: Int) : Action
    data class UseAbility(val unitId: Int, val abilityIndex: Int, val target: Target) : Action
    data class PlayCard(val cardUid: Int, val target: Target) : Action
    /** In the battle phase: skip your turn. In a response window: let the stack resolve. */
    data object Pass : Action
}

enum class DecisionKind { DEPLOY, MAIN, RESPOND, NONE }

data class Decision(val player: Int, val kind: DecisionKind)
