package com.kingofthebeasts.core.balance

import com.kingofthebeasts.core.data.CardDatabase
import java.io.File
import kotlin.math.roundToInt

/**
 * King balance: prices one point of King health with a paired experiment (+2 HP on one
 * King, same games), then proposes the whole-number HP change for every King that moves
 * its strength towards the average. King strengths come from the same regression model
 * (King features sit on top of race features, so they measure the King itself).
 *
 * Args: <games per run> <ai> <output file> <cache file> [probe King id]
 */
fun main(args: Array<String>) {
    val games = args.getOrNull(0)?.toInt() ?: 2500
    val ai = args.getOrNull(1) ?: "greedy"
    val out = File(args.getOrNull(2) ?: "docs/BALANCE_KINGS.md")
    val cache = File(args.getOrNull(3) ?: (out.path + ".cache"))
    val probeKing = args.getOrNull(4) ?: "w_king_moon"
    val kings = CardDatabase.all.filter { it.isKing }.map { it.id }

    fun kingStrengths(label: String): Map<String, Double> {
        val key = "kings|$games|$ai|${CardDatabase.all.toString().hashCode()}"
        cache.takeIf { it.exists() }?.readLines()?.firstOrNull { it.startsWith("$key|") }?.let { line ->
            System.err.println("Cached: $label")
            return kings.zip(line.removePrefix("$key|").split(",").map { it.toDouble() }).toMap()
        }
        System.err.println("Simulating: $label")
        val fit = StrengthModel.fit(simulate(games, ai), StrengthModel::raceKingFeatures)
        val result = kings.associateWith { StrengthModel.elo(fit.weights.getValue("king:$it")) }
        cache.appendText("$key|" + kings.joinToString(",") { result.getValue(it).toString() } + "\n")
        return result
    }

    CardDatabase.resetTuning()
    val s = kingStrengths("baseline")
    val probe = 2
    CardDatabase.applyTuning { c ->
        if (c.id == probeKing) c.copy(unit = c.unit!!.copy(health = c.unit!!.health + probe)) else c
    }
    val sp = kingStrengths("+$probe HP on $probeKing")
    CardDatabase.resetTuning()
    // Centred over 10 Kings: a raw gain δ shows up as δ·(1 − 1/10) on the King itself.
    val delta = (sp.getValue(probeKing) - s.getValue(probeKing)) * kings.size / (kings.size - 1) / probe

    val report = buildString {
        appendLine("# King balance")
        appendLine()
        appendLine("$games paired games per run (`$ai` AI). One point of King health is worth %.1f Elo".format(delta))
        appendLine("(measured with +$probe HP on ${CardDatabase.get(probeKing).name}).")
        appendLine()
        appendLine("| King | Strength (Elo) | HP now | Suggested HP change |")
        appendLine("|---|---|---|---|")
        kings.sortedByDescending { s.getValue(it) }.forEach { k ->
            val d = CardDatabase.get(k)
            val change = (-s.getValue(k) / delta).roundToInt()
            appendLine("| ${d.name} | %+.0f | ${d.unit!!.health} | %+d |".format(s.getValue(k), change))
        }
    }
    out.parentFile?.mkdirs()
    out.writeText(report)
    println(report)
}
