package com.kingofthebeasts.core.balance

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.model.Race
import java.io.File

/**
 * Solves for per-race health adjustments that equalise race strength.
 *
 * 1. Measure each race's strength s_r (Elo, centred so the races average 0) with the
 *    regression model on N simulated games.
 * 2. For each race, give every one of its non-King units +1 health and replay the *same*
 *    games (same seeds, same decks). The change in its strength gives the sensitivity
 *    δ_r = dElo / d(+1 HP on every unit of race r). Because strengths are centred, a raw
 *    gain δ shows up as δ·(1 − 1/5) on the race itself, so δ_r = Δs_r · 5/4.
 * 3. With a linear model the centred strengths after a change h are
 *        s + (I − 11ᵀ/5)·diag(δ)·h,
 *    which is zero when δ_r·h_r = k − s_r for any constant k (adding the same strength to
 *    every race changes nothing). We pick the k that minimises Σ h_r² (the smallest total
 *    change): k = Σ(s_r/δ_r²) / Σ(1/δ_r²).
 *
 * Sensitivities measured per race are noisy, so each is shrunk halfway towards the mean
 * over all races before solving (a simple James–Stein-style estimate), and the probe uses a
 * step of several HP so the signal clears the simulation noise.
 *
 * Args: <games per run> <ai> <output file> [HP step, default 3]
 */
fun main(args: Array<String>) {
    val games = args.getOrNull(0)?.toInt() ?: 2000
    val ai = args.getOrNull(1) ?: "greedy"
    val out = File(args.getOrNull(2) ?: "docs/BALANCE_SOLVE.md")
    val step = args.getOrNull(3)?.toInt() ?: 3
    val races = Race.entries

    // Each run is cached (keyed by the current card definitions) so an interrupted solve can resume.
    val cache = File(out.path + ".cache").apply { parentFile?.mkdirs() }
    fun strengths(label: String): Map<Race, Double> {
        // toString() is stable across runs (enum hashCodes are not).
        val key = "$games|$ai|${CardDatabase.all.toString().hashCode()}"
        cache.takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("$key|") }?.let { line ->
            System.err.println("Cached: $label")
            val values = line.removePrefix("$key|").split(",").map { it.toDouble() }
            return races.zip(values).toMap()
        }
        System.err.println("Simulating: $label")
        val fit = StrengthModel.fit(simulate(games, ai), StrengthModel::raceKingFeatures)
        val result = races.associateWith { StrengthModel.elo(fit.weights.getValue("race:${it.name}")) }
        cache.appendText("$key|" + races.joinToString(",") { result.getValue(it).toString() } + "\n")
        System.err.println("  $label: " + races.joinToString { "${it.displayName} %+.0f".format(result.getValue(it)) })
        return result
    }

    CardDatabase.resetTuning()
    val s = strengths("baseline")
    val measured = races.associateWith { race ->
        CardDatabase.applyTuning { c ->
            val u = c.unit
            if (c.race == race && u != null && !c.isKing) c.copy(unit = u.copy(health = u.health + step)) else c
        }
        val sr = strengths("+$step HP for every ${race.displayName} unit")
        CardDatabase.resetTuning()
        (sr.getValue(race) - s.getValue(race)) * races.size / (races.size - 1) / step
    }
    val raw = measured
    val pooled = raw.values.average()
    val delta = races.associateWith { (raw.getValue(it) + pooled) / 2 }
    val k = races.sumOf { s.getValue(it) / (delta.getValue(it) * delta.getValue(it)) } /
        races.sumOf { 1.0 / (delta.getValue(it) * delta.getValue(it)) }
    val h = races.associateWith { (k - s.getValue(it)) / delta.getValue(it) }

    val report = buildString {
        appendLine("# Balance solver")
        appendLine()
        appendLine("$games paired games per run (`$ai` AI). s = race strength (Elo, centred), δ = Elo gained per +1 HP on every")
        appendLine("unit of the race, h = HP change per unit that equalises all races (smallest total change).")
        appendLine()
        appendLine("Probe step: +$step HP per unit. Pooled δ over all races: %.1f Elo per +1 HP per unit.".format(pooled))
        appendLine()
        appendLine("| Race | s (Elo) | δ measured | δ used (shrunk) | h (HP per unit) | Units | Total HP to change |")
        appendLine("|---|---|---|---|---|---|---|")
        for (r in races) {
            val units = CardDatabase.all.count { it.race == r && it.unit != null && !it.isKing }
            appendLine(
                "| ${r.displayName} | %+.0f | %.1f | %.1f | %+.2f | %d | %+.1f |".format(
                    s.getValue(r), raw.getValue(r), delta.getValue(r), h.getValue(r), units, h.getValue(r) * units,
                ),
            )
        }
    }
    out.parentFile?.mkdirs()
    out.writeText(report)
    println(report)
}
