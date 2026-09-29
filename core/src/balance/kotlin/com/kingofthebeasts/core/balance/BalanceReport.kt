package com.kingofthebeasts.core.balance

import com.kingofthebeasts.core.ai.AiPlayer
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.deck.DeckGenerator
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.Phase
import com.kingofthebeasts.core.model.Race
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Balance simulator. Every game pits two generated decks against each other:
 * each side gets 1-3 random races, a random King from those races and a
 * randomly built 40-card deck. Results are aggregated per race, King, race
 * combination and card, and written as a Markdown report.
 *
 * Args: <games> <ai: greedy|medium|easy> <output file>
 */
fun main(args: Array<String>) {
    val games = args.getOrNull(0)?.toInt() ?: 2000
    val ai = args.getOrNull(1) ?: "greedy"
    val out = File(args.getOrNull(2) ?: "docs/BALANCE.md")
    // Optional 4th argument: a King id that side A always plays (for focused experiments).
    val results = simulate(games, ai, forcedKing = args.getOrNull(3))
    val report = report(results, ai) + modelReport(results) + starterReport(maxOf(40, games / 30), ai)
    out.parentFile?.mkdirs()
    out.writeText(report)
    println(report)
    println("Wrote ${out.path}")
}

data class Side(val deck: Deck, val king: String)
data class GameResult(val sides: List<Side>, val winner: Int?, val first: Int, val turns: Int)

fun randomSide(rng: Random, forcedKing: String? = null): Side {
    val forced = forcedKing?.let { CardDatabase.get(it) }
    val races = if (forced == null) Race.entries.shuffled(rng).take(1 + rng.nextInt(3)).sorted()
    else (listOf(forced.race) + (Race.entries - forced.race).shuffled(rng).take(rng.nextInt(3))).sorted()
    val king = forced?.id ?: CardDatabase.all.filter { it.isKing && it.race in races }.random(rng).id
    return Side(DeckGenerator.generate(races, king, rng), king)
}

fun aiFor(kind: String, seed: Long): AiPlayer = when (kind) {
    "medium" -> AiPlayer(Difficulty.MEDIUM, seed)
    "easy" -> AiPlayer(Difficulty.EASY, seed)
    // Steady greedy player: little noise and never skips an interrupt, so results reflect the cards.
    else -> AiPlayer(Difficulty.EASY, seed, greedyNoise = 0.3, skipInterruptChance = 0.0)
}

fun playGame(g: Int, ai: String, sides: List<Side>): GameResult {
    val s = GameEngine.newGame(sides[0].deck, sides[1].deck, listOf("A", "B"), 10_000L + g)
    val players = listOf(aiFor(ai, g * 2L), aiFor(ai, g * 2L + 1))
    var steps = 0
    while (s.phase != Phase.GAME_OVER && steps++ < 6000) {
        GameEngine.applyUnchecked(s, players[GameEngine.decision(s).player].choose(s))
    }
    return GameResult(sides, if (s.isDraw) null else s.winner, s.firstPlayer, s.turnNumber)
}

fun simulate(
    games: Int,
    ai: String,
    threads: Int = Runtime.getRuntime().availableProcessors(),
    forcedKing: String? = null,
): List<GameResult> {
    val pool = Executors.newFixedThreadPool(threads)
    val done = AtomicInteger()
    val start = System.nanoTime()
    val futures = (0 until games).map { g ->
        pool.submit<GameResult> {
            val rng = Random(g * 7919L + 1)
            val r = playGame(g, ai, listOf(randomSide(rng, forcedKing), randomSide(rng)))
            val n = done.incrementAndGet()
            if (n % 250 == 0) System.err.println("  $n/$games games (%.0fs)".format((System.nanoTime() - start) / 1e9))
            r
        }
    }
    val results = futures.map { it.get() }
    pool.shutdown()
    return results
}

class Tally {
    var games = 0
    var wins = 0
    var draws = 0
    fun add(result: Int?, side: Int) {
        games++
        if (result == null) draws++ else if (result == side) wins++
    }
    /** Win rate counting a draw as half a win. */
    val rate: Double get() = if (games == 0) 0.0 else (wins + 0.5 * draws) / games
    /** 95% confidence half-width. */
    val margin: Double get() = if (games == 0) 1.0 else 1.96 * sqrt(rate * (1 - rate) / games)
}

/** Regression-based strengths (see [StrengthModel]). */
fun modelReport(results: List<GameResult>): String {
    val rk = StrengthModel.fit(results, StrengthModel::raceKingFeatures)
    val cards = StrengthModel.fit(results, StrengthModel::cardFeatures)
    val formula = StrengthModel.powerFormula(cards.weights.filterKeys { it.startsWith("card:") })
    fun elo(b: Double) = "%+.0f".format(StrengthModel.elo(b))
    fun wr(b: Double) = "%.1f%%".format(StrengthModel.winRate(b) * 100)
    return buildString {
        appendLine()
        appendLine("## Strength model")
        appendLine("Logistic (Bradley–Terry) regression of game results on deck features: `P(A beats B) = σ(Σ β·(x_A − x_B) + θ·first)`.")
        appendLine("Strengths are relative to the average member of their group, in Elo points (β·400/ln 10); the")
        appendLine("\"vs average\" column is the implied win rate against an average opponent.")
        appendLine("First-player edge: ${elo(rk.firstPlayer)} Elo.")
        appendLine()
        appendLine("| Race | Elo | vs average |")
        appendLine("|---|---|---|")
        rk.weights.filterKeys { it.startsWith("race:") }.entries.sortedByDescending { it.value }.forEach { (k, b) ->
            appendLine("| ${com.kingofthebeasts.core.model.Race.valueOf(k.removePrefix("race:")).displayName} | ${elo(b)} | ${wr(b)} |")
        }
        appendLine()
        appendLine("| King (on top of its race) | Elo |")
        appendLine("|---|---|")
        rk.weights.filterKeys { it.startsWith("king:") }.entries.sortedByDescending { it.value }.forEach { (k, b) ->
            appendLine("| ${CardDatabase.get(k.removePrefix("king:")).name} | ${elo(b)} |")
        }
        appendLine()
        appendLine("### Unit power formula")
        appendLine("Least-squares fit of each unit's strength (from a card-level model) on its stats, R² = %.2f:".format(formula.r2))
        appendLine()
        appendLine("| Stat | Elo per point |")
        appendLine("|---|---|")
        formula.coefficients.forEach { (k, b) -> appendLine("| $k | ${elo(b)} |") }
        appendLine()
        appendLine("Units that perform furthest from what their stats predict (positive = stronger than its stats):")
        appendLine()
        appendLine("| Unit | Race | Residual Elo |")
        appendLine("|---|---|---|")
        formula.residuals.entries.sortedByDescending { abs(it.value) }.take(10).forEach { (id, r) ->
            val d = CardDatabase.get(id)
            appendLine("| ${d.name} | ${d.race.displayName} | ${elo(r)} |")
        }
    }
}

/** Round robin between the starter decks, each pairing played from both seats. */
fun starterReport(gamesPerPair: Int, ai: String): String {
    val decks = StarterDecks.all
    val pairs = decks.indices.flatMap { a -> decks.indices.filter { it != a }.map { b -> a to b } }
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    val outcomes = pairs.associateWith { (a, b) ->
        (0 until gamesPerPair).map { g ->
            pool.submit<GameResult> {
                val sides = listOf(decks[a], decks[b]).map { d -> Side(d, d.cards.keys.first { CardDatabase.get(it).isKing }) }
                playGame(50_000 + a * 1000 + b * 100 + g, ai, sides)
            }
        }
    }.mapValues { (_, fs) -> fs.map { it.get() } }
    pool.shutdown()
    return buildString {
        appendLine()
        appendLine("## Starter decks")
        appendLine("Row deck's win rate against the column deck ($gamesPerPair games from each seat).")
        appendLine()
        appendLine("| | " + decks.joinToString(" | ") { it.name } + " | Overall |")
        appendLine("|---|" + decks.joinToString("") { "---|" } + "---|")
        for (a in decks.indices) {
            val overall = Tally()
            val cells = decks.indices.map { b ->
                if (a == b) "—" else {
                    val t = Tally()
                    outcomes.getValue(a to b).forEach { t.add(it.winner, 0); overall.add(it.winner, 0) }
                    outcomes.getValue(b to a).forEach { t.add(it.winner, 1); overall.add(it.winner, 1) }
                    "%.0f%%".format(t.rate * 100)
                }
            }
            appendLine("| ${decks[a].name} | " + cells.joinToString(" | ") + " | %.1f%% |".format(overall.rate * 100))
        }
    }
}

fun report(results: List<GameResult>, ai: String): String {
    val races = sortedMapOf<String, Tally>()
    val kings = sortedMapOf<String, Tally>()
    val combos = sortedMapOf<String, Tally>()
    val cards = sortedMapOf<String, Tally>()
    val first = Tally()
    for (r in results) {
        first.add(r.winner, r.first)
        r.sides.forEachIndexed { i, side ->
            side.deck.races.forEach { races.getOrPut(it.displayName) { Tally() }.add(r.winner, i) }
            kings.getOrPut(side.king) { Tally() }.add(r.winner, i)
            combos.getOrPut(side.deck.races.joinToString(" + ") { it.displayName }) { Tally() }.add(r.winner, i)
            side.deck.cards.keys.forEach { cards.getOrPut(it) { Tally() }.add(r.winner, i) }
        }
    }
    fun pct(t: Tally) = "%.1f%% ± %.1f".format(t.rate * 100, t.margin * 100)
    val draws = results.count { it.winner == null }
    return buildString {
        appendLine("# Balance report")
        appendLine()
        appendLine("Generated by `./gradlew :core:balanceReport` — ${results.size} AI-vs-AI games (`$ai` AI) with randomly generated")
        appendLine("decks of 1–3 races. Win rates count a draw as half a win; ± is the 95% confidence margin.")
        appendLine()
        appendLine("- First player wins: ${pct(first)}")
        appendLine("- Draws: %.1f%%".format(100.0 * draws / results.size))
        appendLine("- Average game length: %.1f turns".format(results.map { it.turns }.average()))
        appendLine()
        appendLine("## Races")
        appendLine("| Race | Games | Win rate |")
        appendLine("|---|---|---|")
        races.entries.sortedByDescending { it.value.rate }.forEach { (k, t) -> appendLine("| $k | ${t.games} | ${pct(t)} |") }
        appendLine()
        appendLine("## Kings")
        appendLine("| King | Race | Games | Win rate | Draws |")
        appendLine("|---|---|---|---|---|")
        kings.entries.sortedByDescending { it.value.rate }.forEach { (k, t) ->
            val d = CardDatabase.get(k)
            appendLine("| ${d.name} | ${d.race.displayName} | ${t.games} | ${pct(t)} | ${t.draws} |")
        }
        appendLine()
        appendLine("## Race combinations")
        appendLine("| Races | Games | Win rate |")
        appendLine("|---|---|---|")
        combos.entries.sortedByDescending { it.value.rate }.forEach { (k, t) -> appendLine("| $k | ${t.games} | ${pct(t)} |") }
        appendLine()
        appendLine("## Cards")
        appendLine("Win rate of decks that include the card, and the difference from its race's overall win rate.")
        appendLine()
        appendLine("| Card | Race | Type | Games | Win rate | vs race |")
        appendLine("|---|---|---|---|---|---|")
        cards.entries.filter { !CardDatabase.get(it.key).isKing }
            .sortedByDescending { it.value.rate - races.getValue(CardDatabase.get(it.key).race.displayName).rate }
            .forEach { (k, t) ->
                val d = CardDatabase.get(k)
                val delta = t.rate - races.getValue(d.race.displayName).rate
                val vsRace = "%+.1f".format(delta * 100)
                appendLine("| ${d.name} | ${d.race.displayName} | ${d.type.displayName} | ${t.games} | ${pct(t)} | $vsRace |")
            }
    }
}
