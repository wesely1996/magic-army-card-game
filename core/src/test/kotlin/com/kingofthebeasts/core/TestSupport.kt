package com.kingofthebeasts.core

import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.game.CardInstance
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.game.Pos
import com.kingofthebeasts.core.game.UnitState

/** Builds a battle-phase position with exactly the given units and empty hands. */
fun battle(vararg units: Triple<String, Int, Pos>, active: Int = 0): Pair<GameState, List<UnitState>> {
    val s = GameEngine.newGame(StarterDecks.all[0], StarterDecks.all[1], listOf("A", "B"), 1)
    // No racial traits unless a test asks for one, so unit stats match the card database.
    s.players.forEach { it.hand.clear(); it.deployDone = true; it.trait = null }
    s.phase = Phase.BATTLE
    s.activePlayer = active
    s.turnNumber = 1
    val placed = units.map { (id, owner, pos) -> GameEngine.summon(s, owner, CardInstance(s.newId(), id), pos, token = false) }
    return s to placed
}

fun GameState.giveCard(player: Int, cardId: String): CardInstance =
    CardInstance(newId(), cardId).also { players[player].hand += it }

fun p(x: Int, y: Int) = Pos(x, y)
