package com.kingofthebeasts.core.balance

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.Race
import java.io.File

/**
 * Multi-lever balance solve.
 *
 * Each lever is a concrete card change ("+1 ATK on every Wolf unit"). Its effect on the
 * centred race strengths is measured with paired simulations (same seeds and decks as the
 * baseline). Because strengths are centred, a lever that raises race r by δ moves the
 * strength vector by δ·(e_r − 1/5·𝟙); we use that structure rather than the noisy measured
 * off-diagonal entries. Then we solve the ridge least-squares problem
 *
 *     minimise  ‖s + J x‖² + λ‖x‖²      ⇒      (JᵀJ + λI) x = −Jᵀ s
 *
 * for lever amounts x (in lever steps). λ keeps the total change small. Runs are cached in
 * the same file as the HP solver, so results survive restarts.
 *
 * Args: <games per run> <ai> <output file> <cache file>
 */
data class Lever(val name: String, val race: Race, val step: Int, val tune: (CardDef) -> CardDef)

private fun unitsOf(race: Race, c: CardDef, pick: (CardDef) -> Boolean = { true }) =
    c.race == race && c.unit != null && !c.isKing && pick(c)

val levers: List<Lever> = Race.entries.map { r ->
    Lever("+1 HP on every ${r.displayName} unit", r, 3) { c ->
        if (unitsOf(r, c)) c.copy(unit = c.unit!!.copy(health = c.unit!!.health + 3)) else c
    }
} + listOf(
    Lever("+1 ATK on every Wolf Pack unit", Race.WOLF, 1) { c ->
        if (unitsOf(Race.WOLF, c)) c.copy(unit = c.unit!!.copy(attack = c.unit!!.attack + 1)) else c
    },
    Lever("−1 ATK on Hawk units with 2+ ATK", Race.HAWK, -1) { c ->
        if (unitsOf(Race.HAWK, c) { it.unit!!.attack >= 2 }) c.copy(unit = c.unit!!.copy(attack = c.unit!!.attack - 1)) else c
    },
)

fun main(args: Array<String>) {
    val games = args.getOrNull(0)?.toInt() ?: 2500
    val ai = args.getOrNull(1) ?: "greedy"
    val out = File(args.getOrNull(2) ?: "docs/BALANCE_EXPERIMENTS.md")
    val cache = File(args.getOrNull(3) ?: (out.path + ".cache"))
    val races = Race.entries
    val lambda = 30.0

    fun strengths(label: String): Map<Race, Double> {
        val key = "$games|$ai|${CardDatabase.all.toString().hashCode()}"
        cache.takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("$key|") }?.let { line ->
            System.err.println("Cached: $label")
            return races.zip(line.removePrefix("$key|").split(",").map { it.toDouble() }).toMap()
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
    // δ per lever step: own-race change, de-centred (×5/4) and divided by the probe size.
    val delta = levers.map { lever ->
        CardDatabase.applyTuning(lever.tune)
        val sl = strengths(lever.name)
        CardDatabase.resetTuning()
        val probe = if (lever.name.startsWith("+1 HP")) 3.0 else 1.0
        (sl.getValue(lever.race) - s.getValue(lever.race)) * races.size / (races.size - 1) / probe
    }
    // J[r][l] = δ_l · (1[r = race_l] − 1/5)
    val n = races.size
    val m = levers.size
    val j = Array(n) { r -> DoubleArray(m) { l -> delta[l] * ((if (races[r] == levers[l].race) 1.0 else 0.0) - 1.0 / n) } }
    val a = Array(m) { p -> DoubleArray(m) { q -> (0 until n).sumOf { j[it][p] * j[it][q] } + if (p == q) lambda else 0.0 } }
    val b = DoubleArray(m) { p -> -(0 until n).sumOf { j[it][p] * s.getValue(races[it]) } }
    val x = gauss(a, b)
    val predicted = races.indices.associate { r -> races[r] to s.getValue(races[r]) + (0 until m).sumOf { j[r][it] * x[it] } }

    val report = buildString {
        appendLine("# Balance experiments")
        appendLine()
        appendLine("$games paired games per run (`$ai` AI). Ridge least squares, λ = $lambda.")
        appendLine()
        appendLine("| Lever | Race | δ (Elo per step) | Solution (steps) |")
        appendLine("|---|---|---|---|")
        levers.forEachIndexed { i, l -> appendLine("| ${l.name} | ${l.race.displayName} | %.1f | %+.2f |".format(delta[i], x[i])) }
        appendLine()
        appendLine("| Race | Now (Elo) | Predicted after solution |")
        appendLine("|---|---|---|")
        races.forEach { appendLine("| ${it.displayName} | %+.0f | %+.0f |".format(s.getValue(it), predicted.getValue(it))) }
    }
    out.parentFile?.mkdirs()
    out.writeText(report)
    println(report)
}

private fun gauss(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
    val n = b.size
    val m = Array(n) { i -> a[i].copyOf() + b[i] }
    for (c in 0 until n) {
        val p = (c until n).maxBy { kotlin.math.abs(m[it][c]) }
        val t = m[c]; m[c] = m[p]; m[p] = t
        for (r in 0 until n) if (r != c) {
            val f = m[r][c] / m[c][c]
            for (k in c..n) m[r][k] -= f * m[c][k]
        }
    }
    return DoubleArray(n) { m[it][n] / m[it][it] }
}
