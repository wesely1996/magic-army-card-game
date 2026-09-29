package com.kingofthebeasts.core.balance

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.model.CardType
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Mathematical strength estimates from simulated games.
 *
 * Bradley–Terry / logistic model: for a game between side A and side B,
 *
 *     P(A wins) = σ( θ·first + Σ_f β_f · (x_A,f − x_B,f) )
 *
 * where x are deck features (races, King, card copies) and β_f is feature f's
 * strength in log-odds. Unlike raw win rates this separates effects that
 * always appear together (a strong card inflates its race's raw win rate).
 * Fitted by L2-regularised gradient descent; draws count as half a win.
 *
 * Strengths are reported in Elo points: Elo = β · 400 / ln 10.
 */
object StrengthModel {
    const val ELO_PER_LOGIT = 400.0 / 2.302585092994046

    fun elo(beta: Double) = beta * ELO_PER_LOGIT

    /** Expected win rate against an average opponent (strength 0) for a strength in log-odds. */
    fun winRate(beta: Double) = 1.0 / (1.0 + exp(-beta))

    class Fit(val weights: Map<String, Double>, val firstPlayer: Double, val logLoss: Double)

    fun fit(
        games: List<GameResult>,
        features: (Side) -> Map<String, Double>,
        l2: Double = 0.5,
        iterations: Int = 3000,
    ): Fit {
        val rows = games.map { g ->
            val a = features(g.sides[0])
            val b = features(g.sides[1])
            val diff = HashMap<String, Double>()
            a.forEach { (k, v) -> diff[k] = (diff[k] ?: 0.0) + v }
            b.forEach { (k, v) -> diff[k] = (diff[k] ?: 0.0) - v }
            val y = when (g.winner) { 0 -> 1.0; 1 -> 0.0; else -> 0.5 }
            Triple(diff.filterValues { abs(it) > 1e-12 }, if (g.first == 0) 1.0 else -1.0, y)
        }
        val names = rows.flatMap { it.first.keys }.toSortedSet().toList()
        val index = names.withIndex().associate { it.value to it.index }
        val w = DoubleArray(names.size)
        var theta = 0.0
        // Adam optimiser on the mean negative log-likelihood + L2 penalty.
        val m = DoubleArray(names.size + 1)
        val v = DoubleArray(names.size + 1)
        val lr = 0.05
        val n = rows.size.toDouble()
        for (t in 1..iterations) {
            val grad = DoubleArray(names.size + 1)
            for ((x, first, y) in rows) {
                var z = theta * first
                x.forEach { (k, value) -> z += w[index.getValue(k)] * value }
                val err = 1.0 / (1.0 + exp(-z)) - y
                x.forEach { (k, value) -> grad[index.getValue(k)] += err * value / n }
                grad[names.size] += err * first / n
            }
            for (i in names.indices) grad[i] += l2 * w[i] / n
            for (i in grad.indices) {
                m[i] = 0.9 * m[i] + 0.1 * grad[i]
                v[i] = 0.999 * v[i] + 0.001 * grad[i] * grad[i]
                val step = lr * (m[i] / (1 - Math.pow(0.9, t.toDouble()))) /
                    (sqrt(v[i] / (1 - Math.pow(0.999, t.toDouble()))) + 1e-8)
                if (i == names.size) theta -= step else w[i] -= step
            }
        }
        var loss = 0.0
        for ((x, first, y) in rows) {
            var z = theta * first
            x.forEach { (k, value) -> z += w[index.getValue(k)] * value }
            val p = (1.0 / (1.0 + exp(-z))).coerceIn(1e-9, 1 - 1e-9)
            loss -= y * ln(p) + (1 - y) * ln(1 - p)
        }
        // Centre each feature group so that the average member is 0.
        val centred = names.groupBy { it.substringBefore(':') }.flatMap { (_, group) ->
            val mean = group.map { w[index.getValue(it)] }.average()
            group.map { it to w[index.getValue(it)] - mean }
        }.toMap()
        return Fit(centred, theta, loss / n)
    }

    fun raceKingFeatures(side: Side): Map<String, Double> =
        side.deck.races.associate { "race:${it.name}" to 1.0 } + ("king:${side.king}" to 1.0)

    fun cardFeatures(side: Side): Map<String, Double> =
        side.deck.cards.filterKeys { !CardDatabase.get(it).isKing }.mapKeys { "card:${it.key}" }
            .mapValues { it.value / 3.0 } + ("king:${side.king}" to 1.0)

    /**
     * Linear "power formula" for units: regresses each unit card's fitted strength
     * on its stats, so we learn what one point of each stat is worth.
     */
    class PowerFormula(val coefficients: Map<String, Double>, val residuals: Map<String, Double>, val r2: Double)

    val statNames = listOf("ATK", "HP", "MOV", "RNG", "Traits", "Abilities")

    fun unitStats(id: String): DoubleArray {
        val u = CardDatabase.get(id).unit!!
        return doubleArrayOf(
            u.attack.toDouble(), u.health.toDouble(), u.move.toDouble(), (u.range - 1).toDouble(),
            u.keywords.size.toDouble(), u.abilities.size.toDouble(),
        )
    }

    fun powerFormula(cardStrength: Map<String, Double>): PowerFormula {
        val units = cardStrength.keys.map { it.removePrefix("card:") }
            .filter { CardDatabase.get(it).type == CardType.UNIT && !CardDatabase.get(it).isKing }
        val xs = units.map { doubleArrayOf(1.0) + unitStats(it) }
        val ys = units.map { cardStrength.getValue("card:$it") }
        val k = xs.first().size
        // Ridge-regularised normal equations: (XᵀX + λI) b = Xᵀy
        val a = Array(k) { DoubleArray(k) }
        val rhs = DoubleArray(k)
        for ((x, y) in xs.zip(ys)) for (i in 0 until k) {
            rhs[i] += x[i] * y
            for (j in 0 until k) a[i][j] += x[i] * x[j]
        }
        for (i in 1 until k) a[i][i] += 0.1
        val b = solve(a, rhs)
        val predicted = xs.map { x -> x.indices.sumOf { b[it] * x[it] } }
        val mean = ys.average()
        val ssTot = ys.sumOf { (it - mean) * (it - mean) }
        val ssRes = ys.zip(predicted).sumOf { (y, p) -> (y - p) * (y - p) }
        return PowerFormula(
            coefficients = statNames.withIndex().associate { (i, n) -> n to b[i + 1] },
            residuals = units.zip(ys.zip(predicted)).associate { (id, yp) -> id to yp.first - yp.second },
            r2 = if (ssTot == 0.0) 0.0 else 1 - ssRes / ssTot,
        )
    }

    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf() + b[i] }
        for (c in 0 until n) {
            val p = (c until n).maxBy { abs(m[it][c]) }
            val tmp = m[c]; m[c] = m[p]; m[p] = tmp
            for (r in 0 until n) if (r != c) {
                val f = m[r][c] / m[c][c]
                for (j in c..n) m[r][j] -= f * m[c][j]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }
}
