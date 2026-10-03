package com.kingofthebeasts.core.profile

import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.model.Race
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class MatchResult { WIN, LOSS, DRAW }

/** One finished battle, as the player's history keeps it. */
@Serializable
data class MatchRecord(
    /** When it ended (milliseconds since 1970). */
    val at: Long,
    val result: MatchResult,
    /** The AI level ([Difficulty] name), or null for a battle against a friend. */
    val difficulty: String? = null,
    /** The friend's name, for a battle against a friend. */
    val friend: String? = null,
    val myKing: String,
    val myRaces: List<Race>,
    val myDeck: String,
    val theirKing: String,
    val turns: Int,
    /** Someone gave up: you (a loss) or your friend (a win). */
    val forfeit: Boolean = false,
) {
    val vsFriend: Boolean get() = difficulty == null

    companion object {
        /** The record of [s] from engine player [me]'s side; a battle not over yet counts as [me] forfeiting. */
        fun of(
            s: GameState, me: Int, myDeck: Deck, theirDeck: Deck,
            difficulty: Difficulty?, friend: String?, at: Long, forfeitBy: Int? = null,
        ): MatchRecord {
            val result = when {
                forfeitBy != null -> if (forfeitBy == me) MatchResult.LOSS else MatchResult.WIN
                s.isDraw -> MatchResult.DRAW
                s.winner == me -> MatchResult.WIN
                else -> MatchResult.LOSS
            }
            return MatchRecord(
                at = at, result = result, difficulty = difficulty?.name, friend = friend,
                myKing = kingOf(myDeck), myRaces = myDeck.races, myDeck = myDeck.name,
                theirKing = kingOf(theirDeck), turns = s.turnNumber, forfeit = forfeitBy != null,
            )
        }

        private fun kingOf(d: Deck): String = d.cards.keys.firstOrNull { CardDatabase.find(it)?.isKing == true } ?: ""

        private val json = Json { ignoreUnknownKeys = true }
        fun encodeAll(list: List<MatchRecord>): String = json.encodeToString(ListSerializer(serializer()), list)
        fun decodeAll(text: String): List<MatchRecord> =
            runCatching { json.decodeFromString(ListSerializer(serializer()), text) }.getOrDefault(emptyList())
    }
}

/** Wins, losses and draws, with the win rate counting a draw as half a win. */
data class Tally(val wins: Int = 0, val losses: Int = 0, val draws: Int = 0) {
    val games: Int get() = wins + losses + draws
    val winRate: Double? get() = if (games == 0) null else (wins + draws * 0.5) / games
    operator fun plus(r: MatchResult) = when (r) {
        MatchResult.WIN -> copy(wins = wins + 1)
        MatchResult.LOSS -> copy(losses = losses + 1)
        MatchResult.DRAW -> copy(draws = draws + 1)
    }
}

/** What the profile shows, worked out from the match history. */
data class PlayerStats(
    val overall: Tally,
    /** Against each AI level, by [Difficulty]. */
    val vsAi: Map<Difficulty, Tally>,
    val vsFriends: Tally,
    /** Games and results with each race the player fielded (a two-race deck counts for both). */
    val byRace: Map<Race, Tally>,
    /** How often each King was played, most played first. */
    val kings: List<Pair<String, Int>>,
) {
    /** The King played most often (the profile picture), or null before the first battle. */
    val favoriteKing: String? get() = kings.firstOrNull()?.first
    /** The race fielded most often. */
    val favoriteRace: Race? get() = byRace.entries.maxWithOrNull(compareBy({ it.value.games }, { it.value.wins }))?.key

    companion object {
        fun of(history: List<MatchRecord>): PlayerStats {
            var overall = Tally()
            val vsAi = Difficulty.entries.associateWith { Tally() }.toMutableMap()
            var vsFriends = Tally()
            val byRace = mutableMapOf<Race, Tally>()
            val kingCounts = mutableMapOf<String, Int>()
            val lastPlayed = mutableMapOf<String, Long>()
            for (m in history) {
                overall += m.result
                val level = m.difficulty?.let { d -> runCatching { Difficulty.valueOf(d) }.getOrNull() }
                if (level != null) vsAi[level] = vsAi.getValue(level) + m.result
                else if (m.vsFriend) vsFriends += m.result
                for (r in m.myRaces) byRace[r] = (byRace[r] ?: Tally()) + m.result
                if (m.myKing.isNotEmpty()) {
                    kingCounts[m.myKing] = (kingCounts[m.myKing] ?: 0) + 1
                    lastPlayed[m.myKing] = maxOf(lastPlayed[m.myKing] ?: 0L, m.at)
                }
            }
            // Ties go to the King played most recently.
            val kings = kingCounts.entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenByDescending { lastPlayed[it.key] ?: 0L })
                .map { it.key to it.value }
            return PlayerStats(overall, vsAi, vsFriends, byRace, kings)
        }
    }
}
