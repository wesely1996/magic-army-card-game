package com.kingofthebeasts.core.game

import com.kingofthebeasts.core.data.CardDatabase
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.EffectOp
import com.kingofthebeasts.core.model.FieldRule
import com.kingofthebeasts.core.model.Keyword
import com.kingofthebeasts.core.model.PERMANENT
import com.kingofthebeasts.core.model.PushFrom
import com.kingofthebeasts.core.model.RacialTrait
import com.kingofthebeasts.core.model.Side
import com.kingofthebeasts.core.model.TargetKind
import com.kingofthebeasts.core.model.TargetRule
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

/**
 * All game rules. The engine is deterministic: the same seed and the same
 * sequence of actions always produce the same game, which keeps the door open
 * for replays and online play.
 */
object GameEngine {
    const val MAX_DEPLOY = 5
    /**
     * Unit slots each side has on the board (reinforcements, summons, enthralled units). Elite units
     * take 2 slots, other units 1 and Kings none.
     */
    const val MAX_UNITS_ON_FIELD = 16
    /** Unit slots for an army with the Endless Horde trait (Vermin King). */
    const val HORDE_UNITS_ON_FIELD = 24
    /** Extra attack for Backstab when attacking from behind. */
    const val BACKSTAB_BONUS = 2
    /** Turns a Brood unit keeps spawning Swarm Rats. */
    const val BROOD_TURNS = 2
    const val SWARM_RAT = "v_rat"
    /** Tempest only reaches enemies this close to the unit that has it. */
    const val TEMPEST_RANGE = 3
    /** Most Wolf Pups that Call the Pack can keep on the board at once. */
    const val MAX_SUMMONED = 2
    /** From this turn on, each King loses health at the start of its owner's turn (anti-stalemate). */
    const val EXHAUSTION_TURN = 120
    const val COMMANDER_RANGE = 3

    /** Once Exhaustion sets in, Kings can no longer be healed. */
    fun exhausted(s: GameState): Boolean = s.turnNumber >= EXHAUSTION_TURN
    const val OPENING_HAND = 5
    const val HAND_LIMIT = 8
    const val TURN_LIMIT = 200
    /** Battle-phase unit cards must be played at least this far (in squares) from every enemy. */
    const val MIN_ENEMY_DISTANCE = 2

    // ------------------------------------------------------------------ setup

    fun newGame(deck0: Deck, deck1: Deck, names: List<String>, seed: Long): GameState {
        val rng = Rng(seed)
        var uid = 1
        val players = listOf(deck0, deck1).mapIndexed { i, d ->
            val cards = d.cardIds().mapTo(mutableListOf()) { CardInstance(uid++, it) }
            rng.shuffle(cards)
            val king = d.cards.keys.mapNotNull { CardDatabase.find(it) }.firstOrNull { it.isKing }
            PlayerState(i, names[i], cards, trait = king?.race?.trait)
        }
        val first = rng.nextInt(2)
        val s = GameState(players, mutableListOf(), Phase.DEPLOY, first, first, nextId = uid, rng = rng)
        s.log("Coin flip won by ${players[first].name}. Deployment begins.")
        s.event { GameEvent.Announce(it, first, "${players[first].name} won the coin flip") }
        return s
    }

    // --------------------------------------------------------------- decisions

    fun decision(s: GameState): Decision = when {
        s.phase == Phase.GAME_OVER -> Decision(-1, DecisionKind.NONE)
        s.phase == Phase.DEPLOY -> Decision(s.activePlayer, DecisionKind.DEPLOY)
        s.priority != null -> Decision(s.priority!!, DecisionKind.RESPOND)
        else -> Decision(s.activePlayer, DecisionKind.MAIN)
    }

    /**
     * Every legal action for whoever has to decide now. With [distinctCards]
     * duplicate copies of the same card are listed once (used by the AI).
     */
    fun legalActions(s: GameState, distinctCards: Boolean = false): List<Action> {
        val d = decision(s)
        return when (d.kind) {
            DecisionKind.DEPLOY -> deployActions(s, d.player, distinctCards)
            DecisionKind.MAIN -> mainActions(s, d.player, distinctCards)
            DecisionKind.RESPOND -> responseActions(s, d.player, distinctCards)
            DecisionKind.NONE -> emptyList()
        }
    }

    fun isLegal(s: GameState, action: Action): Boolean = action in legalActions(s)

    fun deployableCards(s: GameState, p: Int): List<CardInstance> {
        val ps = s.players[p]
        val units = ps.deck.filter { it.def.unit != null }
        return if (ps.deployed == 0) units.filter { it.def.isKing } else units.filter { !it.def.isKing }
    }

    fun deployTiles(s: GameState, p: Int): List<Pos> =
        Board.allTiles.filter { Board.isDeployZone(p, it) && s.unitAt(it) == null }

    private fun deployActions(s: GameState, p: Int, distinct: Boolean): List<Action> {
        val out = mutableListOf<Action>()
        val cards = deployableCards(s, p).let { if (distinct) it.distinctBy { c -> c.cardId } else it }
        val tiles = deployTiles(s, p)
        for (c in cards) for (t in tiles) out += Action.Deploy(c.uid, t)
        if (s.players[p].deployed > 0) out += Action.EndDeploy
        return out
    }

    private fun mainActions(s: GameState, p: Int, distinct: Boolean): List<Action> {
        val out = mutableListOf<Action>()
        for (u in s.unitsOf(p)) {
            if (u.stun > 0) continue
            reachable(s, u).forEach { out += Action.Move(u.id, it) }
            attackTargets(s, u).forEach { out += Action.Attack(u.id, it.id) }
            u.abilities.forEachIndexed { i, a ->
                if (a.cooldown == 0) abilityTargets(s, u, i).forEach { out += Action.UseAbility(u.id, i, it) }
            }
        }
        val hand = s.players[p].hand.let { if (distinct) it.distinctBy { c -> c.cardId } else it }
        for (c in hand) cardTargets(s, p, c.def).forEach { out += Action.PlayCard(c.uid, it) }
        out += Action.Pass
        return out
    }

    fun responseActions(s: GameState, r: Int, distinct: Boolean = false): List<Action> {
        val out = mutableListOf<Action>()
        val hand = s.players[r].hand.let { if (distinct) it.distinctBy { c -> c.cardId } else it }
        for (c in hand) if (c.def.isQuick) cardTargets(s, r, c.def).forEach { out += Action.PlayCard(c.uid, it) }
        for (u in s.unitsOf(r)) {
            if (u.stun > 0) continue
            u.abilities.forEachIndexed { i, a ->
                if (a.def.quick && a.cooldown == 0) abilityTargets(s, u, i).forEach { out += Action.UseAbility(u.id, i, it) }
            }
        }
        out += Action.Pass
        return out
    }

    private fun hasResponse(s: GameState, r: Int): Boolean = responseActions(s, r, distinct = true).size > 1

    // ---------------------------------------------------------------- queries

    fun attackOf(s: GameState, u: UnitState): Int {
        var a = u.attack + u.mods.sumOf { it.attack }
        if (s.fieldActive(u.owner, FieldRule.WAR_DRUMS)) a += 1
        if (s.unitsOf(u.owner).any { it.id != u.id && it.has(Keyword.COMMANDER) && it.pos.distanceTo(u.pos) <= COMMANDER_RANGE }) a += 1
        return max(0, a)
    }

    fun moveOf(s: GameState, u: UnitState): Int {
        var m = u.move + u.mods.sumOf { it.move }
        if (s.fieldActive(u.owner, FieldRule.TAILWIND)) m += 1
        if (s.fieldActive(1 - u.owner, FieldRule.SWAMP)) m -= 1
        return max(0, m)
    }

    fun rangeOf(s: GameState, u: UnitState): Int {
        var r = u.range + u.mods.sumOf { it.range }
        if (u.range >= 2 && s.fieldActive(u.owner, FieldRule.HIGH_GROUND)) r += 1
        return max(1, r)
    }

    /** Damage [a] would deal to [t] with a normal attack, before the target's reductions. */
    fun attackDamage(s: GameState, a: UnitState, t: UnitState): Int {
        var d = attackOf(s, a)
        if (a.has(Keyword.PACK_HUNTER) &&
            s.unitsOf(a.owner).any { it.id != a.id && it.pos.distanceTo(t.pos) == 1 }
        ) d += 1
        if (s.fieldActive(a.owner, FieldRule.HUNTING_GROUNDS) && t.hp < t.maxHp) d += 1
        if (a.has(Keyword.BACKSTAB) && attacksFromBehind(a, t)) d += BACKSTAB_BONUS
        return d
    }

    /** Behind = on the target's own side of it (nearer the target owner's back row). */
    fun attacksFromBehind(a: UnitState, t: UnitState): Boolean =
        if (t.owner == 0) a.pos.y < t.pos.y else a.pos.y > t.pos.y

    fun reachable(s: GameState, u: UnitState): List<Pos> = reachableWithMove(s, u, moveOf(s, u))

    fun reachableWithMove(s: GameState, u: UnitState, mv: Int): List<Pos> {
        if (mv <= 0) return emptyList()
        val flying = u.has(Keyword.FLYING)
        val dist = HashMap<Pos, Int>()
        val queue = ArrayDeque<Pos>()
        dist[u.pos] = 0
        queue += u.pos
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val d = dist.getValue(cur)
            if (d == mv) continue
            for (n in cur.neighbors()) {
                if (n in dist) continue
                if (s.unitAt(n) != null && !flying) continue
                dist[n] = d + 1
                queue += n
            }
        }
        return dist.keys.filter { it != u.pos && s.unitAt(it) == null }
    }

    fun attackTargets(s: GameState, u: UnitState): List<UnitState> {
        val r = rangeOf(s, u)
        return s.units.filter { it.alive && it.owner != u.owner && it.pos.distanceTo(u.pos) <= r && canSee(s, u.owner, it, u) }
    }

    fun abilityTargets(s: GameState, u: UnitState, index: Int): List<Target> {
        val ab = u.abilities[index].def
        return ruleTargets(s, u.owner, ab.target, u).filter { effectApplicable(s, u, ab.effects, it) }
    }

    fun cardTargets(s: GameState, p: Int, def: CardDef): List<Target> = when (def.type) {
        CardType.UNIT -> if (hasRoomForUnit(s, p, slotsOf(def))) battleDeployTiles(s, p).map { Target.Tile(it) } else emptyList()
        CardType.STRATEGY -> listOf(Target.None)
        CardType.MAGIC, CardType.EQUIPMENT -> ruleTargets(s, p, def.target, null).filter { cardEffectApplicable(s, p, def.effects, it) }
    }

    /** Filters out card targets that a displacement effect can't work on. */
    private fun cardEffectApplicable(s: GameState, p: Int, effects: List<EffectOp>, target: Target): Boolean {
        val t = (target as? Target.Unit)?.let { s.unit(it.unitId) }
        if (t != null && t.isKing && onlyHarms(effects)) return false
        for (op in effects) when (op) {
            EffectOp.Replace -> if (t == null || t.isKing || t.has(Keyword.IMMOVABLE)) return false
            EffectOp.RallyToKing -> if (t == null || rallySquare(s, t) == null) return false
            EffectOp.SwapWithNearestAlly -> if (t == null || t.has(Keyword.IMMOVABLE) || nearestMovableAlly(s, p, t) == null) return false
            is EffectOp.Summon -> if (t == null || !hasRoomForUnit(s, t.owner, slotsOf(CardDatabase.get(op.cardId))) ||
                t.pos.neighbors().none { s.unitAt(it) == null }
            ) return false
            is EffectOp.Push -> {
                if (t == null || t.has(Keyword.IMMOVABLE)) return false
                val (to, blocked) = pushPath(s, p, t, op) ?: return false
                if (to == t.pos && !(blocked && op.impactDamage > 0)) return false
            }
            else -> {}
        }
        return true
    }

    /** Kings take no damage from cards or abilities, so pure damage/poison can't target them. */
    private fun onlyHarms(effects: List<EffectOp>) = effects.isNotEmpty() && effects.all { it is EffectOp.Damage || it is EffectOp.Poison }

    /** The empty square next to [t]'s King nearest to [t], if [t] may rally there. */
    fun rallySquare(s: GameState, t: UnitState): Pos? {
        if (t.isKing || t.has(Keyword.IMMOVABLE)) return null
        val king = s.king(t.owner) ?: return null
        if (t.pos.distanceTo(king.pos) == 1) return null
        return king.pos.neighbors().filter { s.unitAt(it) == null }
            .minWithOrNull(compareBy<Pos>({ it.distanceTo(t.pos) }, { it.y }, { it.x }))
    }

    /** [p]'s unit (that can be moved) nearest to [t]. */
    fun nearestMovableAlly(s: GameState, p: Int, t: UnitState): UnitState? =
        s.unitsOf(p).filter { it.id != t.id && !it.has(Keyword.IMMOVABLE) }
            .minWithOrNull(compareBy<UnitState>({ it.pos.distanceTo(t.pos) }, { it.id }))

    /** Where a push would leave [t], and whether something stopped it early. Null if there is no direction. */
    fun pushPath(s: GameState, p: Int, t: UnitState, op: EffectOp.Push): Pair<Pos, Boolean>? {
        val (dx, dy) = when (op.from) {
            PushFrom.NEAREST_ALLY -> {
                val from = s.unitsOf(p).filter { it.id != t.id }
                    .minWithOrNull(compareBy<UnitState>({ it.pos.distanceTo(t.pos) }, { it.id })) ?: return null
                (t.pos.x - from.pos.x).sign to (t.pos.y - from.pos.y).sign
            }
            PushFrom.OWNER_SIDE -> 0 to if (t.owner == 0) -1 else 1
        }
        if (dx == 0 && dy == 0) return null
        var pos = t.pos
        repeat(op.distance) {
            val next = Pos(pos.x + dx, pos.y + dy)
            if (!next.onBoard || s.unitAt(next) != null) return pos to true
            pos = next
        }
        return pos to false
    }

    /** Most units player [p] may have on the board (the Endless Horde trait raises it). */
    fun unitCap(s: GameState, p: Int): Int =
        if (s.players[p].trait == RacialTrait.ENDLESS_HORDE) HORDE_UNITS_ON_FIELD else MAX_UNITS_ON_FIELD

    fun slotsOf(def: CardDef): Int = def.unit?.slots ?: 0

    /** Unit slots player [p]'s units currently take up. */
    fun usedSlots(s: GameState, p: Int): Int = s.unitsOf(p).sumOf { slotsOf(it.def) }

    /** Whether [p] has [slots] free unit slots. */
    fun hasRoomForUnit(s: GameState, p: Int, slots: Int = 1): Boolean = usedSlots(s, p) + slots <= unitCap(s, p)

    fun isHidden(s: GameState, u: UnitState): Boolean = u.has(Keyword.HIDDEN) || s.fieldActive(u.owner, FieldRule.TUNNELS)

    /**
     * Whether player [p] can attack or target [t]: Hidden enemies only from a square next to them
     * (the acting unit's square, or for cards any of [p]'s units).
     */
    fun canSee(s: GameState, p: Int, t: UnitState, source: UnitState?): Boolean =
        t.owner == p || !isHidden(s, t) ||
            (if (source != null) source.pos.distanceTo(t.pos) == 1 else s.unitsOf(p).any { it.pos.distanceTo(t.pos) == 1 })

    fun battleDeployTiles(s: GameState, p: Int): List<Pos> {
        if (!hasRoomForUnit(s, p)) return emptyList()
        val ambush = s.fieldActive(p, FieldRule.AMBUSH)
        val enemies = s.unitsOf(1 - p)
        return Board.allTiles.filter { t ->
            s.unitAt(t) == null &&
                (t.isBorder || (ambush && Board.isOwnHalf(p, t))) &&
                enemies.all { it.pos.distanceTo(t) >= MIN_ENEMY_DISTANCE }
        }
    }

    private fun ruleTargets(s: GameState, p: Int, rule: TargetRule, source: UnitState?): List<Target> {
        fun inRange(u: UnitState) = source == null || source.pos.distanceTo(u.pos) <= rule.range
        return when (rule.kind) {
            TargetKind.NONE -> listOf(Target.None)
            TargetKind.SELF -> listOfNotNull(source?.let { Target.Unit(it.id) })
            TargetKind.FRIENDLY_UNIT -> s.unitsOf(p).filter { it.id != source?.id && inRange(it) }.map { Target.Unit(it.id) }
            TargetKind.ENEMY_UNIT -> s.unitsOf(1 - p).filter { inRange(it) && canSee(s, p, it, source) }.map { Target.Unit(it.id) }
            TargetKind.ANY_UNIT -> s.units.filter { it.alive && inRange(it) && canSee(s, p, it, source) }.map { Target.Unit(it.id) }
            TargetKind.STACK_ITEM -> {
                val top = s.stack.lastOrNull()
                if (top != null && top.controller != p) listOf(Target.StackEntry(top.id)) else emptyList()
            }
        }
    }

    private fun effectApplicable(s: GameState, source: UnitState, effects: List<EffectOp>, target: Target): Boolean {
        val t = (target as? Target.Unit)?.let { s.unit(it.unitId) }
        if (t != null && t.isKing && onlyHarms(effects)) return false
        for (op in effects) when (op) {
            EffectOp.Swap -> if (t == null || (t.id != source.id && t.has(Keyword.IMMOVABLE))) return false
            is EffectOp.Enthrall -> if (t == null || t.isKing || t.hp > op.maxHealth || !hasRoomForUnit(s, source.owner, slotsOf(t.def))) return false
            is EffectOp.Pounce -> if (t == null || pounceSquare(s, source, t) == null) return false
            is EffectOp.Summon -> if (!hasRoomForUnit(s, source.owner, slotsOf(CardDatabase.get(op.cardId))) ||
                source.pos.neighbors().none { s.unitAt(it) == null }
            ) return false
            else -> {}
        }
        return true
    }

    private fun pounceSquare(s: GameState, source: UnitState, t: UnitState): Pos? {
        if (source.pos.distanceTo(t.pos) == 1) return source.pos
        return t.pos.neighbors().filter { s.unitAt(it) == null }.minByOrNull { it.distanceTo(source.pos) }
    }

    // ---------------------------------------------------------------- actions

    fun apply(s: GameState, action: Action) {
        require(isLegal(s, action)) { "Illegal action: $action" }
        applyUnchecked(s, action)
    }

    /** Applies an action already known to be legal (used by the AI's simulations). */
    fun applyUnchecked(s: GameState, action: Action) {
        val d = decision(s)
        when (d.kind) {
            DecisionKind.DEPLOY -> applyDeploy(s, d.player, action)
            DecisionKind.MAIN -> {
                if (action == Action.Pass) {
                    s.log("${s.players[d.player].name}: skips the turn")
                    endTurn(s)
                } else {
                    declare(s, d.player, action)
                    openWindowOrResolve(s, d.player)
                }
            }
            DecisionKind.RESPOND -> {
                if (action == Action.Pass) {
                    resolveStack(s)
                } else {
                    declare(s, d.player, action)
                    openWindowOrResolve(s, d.player)
                }
            }
            DecisionKind.NONE -> error("The game is over")
        }
    }

    private fun applyDeploy(s: GameState, p: Int, action: Action) {
        val ps = s.players[p]
        when (action) {
            is Action.Deploy -> {
                val card = ps.deck.first { it.uid == action.cardUid }
                ps.deck.remove(card)
                val u = summon(s, p, card, action.pos, token = false)
                arrive(s, p, u)
                ps.deployed++
                s.log("${ps.name}: deploys ${u.name} at ${action.pos}")
                if (ps.deployed >= MAX_DEPLOY || deployableCards(s, p).isEmpty()) ps.deployDone = true
            }
            Action.EndDeploy -> {
                ps.deployDone = true
                s.log("${ps.name}: finishes deployment")
            }
            else -> error("Not a deploy action: $action")
        }
        val other = 1 - p
        when {
            !s.players[other].deployDone -> s.activePlayer = other
            !ps.deployDone -> s.activePlayer = p
            else -> startBattle(s)
        }
    }

    private fun startBattle(s: GameState) {
        s.phase = Phase.BATTLE
        for (ps in s.players) {
            s.rng.shuffle(ps.deck)
            repeat(OPENING_HAND) { drawCard(s, ps.index) }
        }
        s.activePlayer = s.firstPlayer
        s.turnNumber = 1
        s.log("The battle begins!")
        s.event { GameEvent.Announce(it, s.firstPlayer, "Battle! ${s.players[s.firstPlayer].name} moves first") }
        startTurn(s, draw = false)
    }

    private fun declare(s: GameState, p: Int, action: Action) {
        val label = describe(s, action)
        var card: CardInstance? = null
        when (action) {
            is Action.PlayCard -> {
                val hand = s.players[p].hand
                card = hand.first { it.uid == action.cardUid }
                hand.remove(card)
            }
            is Action.UseAbility -> {
                val u = s.unit(action.unitId)!!
                val ab = u.abilities[action.abilityIndex]
                ab.cooldown = ab.def.cooldown
            }
            else -> {}
        }
        val responding = s.stack.isNotEmpty()
        s.stack += StackItem(s.newId(), p, action, label, card)
        s.log("${s.players[p].name}: ${if (responding) "responds — " else ""}$label")
    }

    private fun openWindowOrResolve(s: GameState, declarer: Int) {
        val opp = 1 - declarer
        if (!s.fieldActive(declarer, FieldRule.SILENCE) && hasResponse(s, opp)) s.priority = opp
        else resolveStack(s)
    }

    private fun resolveStack(s: GameState) {
        s.priority = null
        val bottom = s.stack.firstOrNull() ?: return
        while (s.stack.isNotEmpty()) {
            val item = s.stack.removeAt(s.stack.lastIndex)
            if (item.countered) {
                s.log("Cancelled: ${item.label}")
                item.card?.let { retire(s, item.controller, it) }
                item.action.sourcePos(s)?.let { p -> s.event { GameEvent.Status(it, p, "Cancelled") } }
            } else {
                resolveItem(s, item)
            }
            cleanupDeaths(s)
            if (s.phase == Phase.GAME_OVER) {
                s.stack.clear()
                return
            }
        }
        val p = s.activePlayer
        if (bottom.controller == p && bottom.action is Action.Move &&
            s.fieldActive(p, FieldRule.BLITZ) && !s.blitzUsed
        ) {
            s.blitzUsed = true
            s.log("Blitz: ${s.players[p].name} may act again")
        } else {
            endTurn(s)
        }
    }

    private fun Action.sourcePos(s: GameState): Pos? = when (this) {
        is Action.Move -> s.unit(unitId)?.pos
        is Action.Attack -> s.unit(unitId)?.pos
        is Action.UseAbility -> s.unit(unitId)?.pos
        is Action.PlayCard -> when (target) {
            is Target.Unit -> s.unit(target.unitId)?.pos
            is Target.Tile -> target.pos
            else -> null
        }
        else -> null
    }

    private fun fizzle(s: GameState, item: StackItem) {
        s.log("Fizzled: ${item.label}")
        item.action.sourcePos(s)?.let { p -> s.event { GameEvent.Status(it, p, "Fizzled") } }
    }

    private fun resolveItem(s: GameState, item: StackItem) {
        val p = item.controller
        when (val a = item.action) {
            is Action.Move -> {
                val u = s.unit(a.unitId)
                if (u == null || u.owner != p || u.stun > 0 || a.to !in reachable(s, u)) fizzle(s, item)
                else moveUnit(s, u, a.to)
            }
            is Action.Attack -> {
                val u = s.unit(a.unitId)
                val t = s.unit(a.targetId)
                if (u == null || t == null || u.owner != p || t.owner == p || u.stun > 0 ||
                    u.pos.distanceTo(t.pos) > rangeOf(s, u) || !canSee(s, p, t, u)
                ) fizzle(s, item)
                else performAttack(s, u, t)
            }
            is Action.UseAbility -> {
                val u = s.unit(a.unitId)
                val ab = u?.abilities?.getOrNull(a.abilityIndex)?.def
                if (u == null || ab == null || u.owner != p || u.stun > 0 ||
                    !targetStillValid(s, p, ab.target, u, a.target)
                ) fizzle(s, item)
                else applyEffects(s, p, u, ab.effects, a.target, null)
            }
            is Action.PlayCard -> {
                val card = item.card!!
                val def = card.def
                when (def.type) {
                    CardType.UNIT -> {
                        val pos = (a.target as Target.Tile).pos
                        if (s.unitAt(pos) != null || !hasRoomForUnit(s, p, slotsOf(def))) {
                            fizzle(s, item)
                            s.players[p].exhausted += card
                        } else {
                            arrive(s, p, summon(s, p, card, pos, token = false))
                        }
                    }
                    CardType.STRATEGY -> {
                        applyEffects(s, p, null, def.effects, a.target, def.id)
                        retire(s, p, card)
                    }
                    CardType.MAGIC, CardType.EQUIPMENT -> {
                        if (!targetStillValid(s, p, def.target, null, a.target)) {
                            fizzle(s, item)
                        } else {
                            applyEffects(s, p, null, def.effects, a.target, def.id)
                            if (def.type == CardType.EQUIPMENT) {
                                (a.target as? Target.Unit)?.let { s.unit(it.unitId) }?.equipment?.add(def.name)
                            }
                        }
                        retire(s, p, card)
                    }
                }
            }
            else -> {}
        }
    }

    private fun targetStillValid(s: GameState, p: Int, rule: TargetRule, source: UnitState?, target: Target): Boolean =
        when (target) {
            Target.None, is Target.Tile -> true
            is Target.StackEntry -> s.stack.any { it.id == target.itemId }
            is Target.Unit -> {
                val t = s.unit(target.unitId)
                t != null &&
                    when (rule.kind) {
                        TargetKind.FRIENDLY_UNIT -> t.owner == p
                        TargetKind.ENEMY_UNIT -> t.owner != p
                        TargetKind.SELF -> t.id == source?.id
                        else -> true
                    } &&
                    (source == null || rule.kind == TargetKind.SELF || source.pos.distanceTo(t.pos) <= rule.range) &&
                    canSee(s, p, t, source)
            }
        }

    // ---------------------------------------------------------------- effects

    private fun applyEffects(
        s: GameState, p: Int, source: UnitState?, effects: List<EffectOp>, target: Target, cardId: String?,
    ) {
        val tUnit = (target as? Target.Unit)?.let { s.unit(it.unitId) }
        val center = tUnit?.pos ?: source?.pos
        for (op in effects) applyOp(s, p, source, op, tUnit, center, target, cardId)
    }

    private fun applyOp(
        s: GameState, p: Int, source: UnitState?, op: EffectOp, t: UnitState?, center: Pos?,
        target: Target, cardId: String?,
    ) {
        when (op) {
            is EffectOp.Damage -> t?.let { if (it.isKing) kingImmune(s, it) else dealDamage(s, it, op.amount) }
            is EffectOp.Heal -> t?.let { heal(s, it, op.amount) }
            is EffectOp.Buff -> t?.let { buff(s, it, op) }
            is EffectOp.Stun -> t?.let { stun(s, it, op.turns) }
            is EffectOp.Poison -> t?.let { if (it.isKing) kingImmune(s, it) else poison(s, it, op.damage, op.turns) }
            is EffectOp.Shield -> t?.let { u ->
                u.shield += op.amount
                s.event { GameEvent.Status(it, u.pos, "+${op.amount} shield") }
            }
            is EffectOp.GrantKeyword -> t?.let { u ->
                if (op.turns == PERMANENT) u.keywords += op.keyword else u.timedKeywords += TimedKeyword(op.keyword, timedTurns(s, u, op.turns))
                s.event { GameEvent.Status(it, u.pos, op.keyword.displayName) }
            }
            EffectOp.Cleanse -> t?.let { u ->
                u.stun = 0
                u.poisonTurns = 0
                u.poisonDamage = 0
                u.mods.removeAll { m -> m.attack < 0 || m.move < 0 || m.range < 0 }
                s.event { GameEvent.Status(it, u.pos, "Cleansed") }
            }
            EffectOp.Counter -> (target as? Target.StackEntry)?.let { e ->
                s.stack.firstOrNull { it.id == e.itemId }?.countered = true
            }
            is EffectOp.Area -> center?.let { c ->
                s.units.filter {
                    it.alive && it.pos.distanceTo(c) <= op.radius && (op.includeCenter || it.pos != c) &&
                        when (op.side) {
                            Side.FRIENDLY -> it.owner == p
                            Side.ENEMY -> it.owner != p
                            Side.ALL -> true
                        }
                }.forEach { u -> applyOp(s, p, source, op.op, u, c, Target.Unit(u.id), cardId) }
            }
            is EffectOp.Draw -> repeat(op.count) { drawCard(s, p) }
            is EffectOp.Field -> {
                s.fields.removeAll { it.owner == p }
                s.fields += FieldEffect(p, op.rule, op.turns, cardId ?: "")
                s.log("${s.players[p].name}: ${op.rule.displayName} is now active")
            }
            is EffectOp.Summon -> (source ?: t)?.let { summonToken(s, it, op.cardId) }
            EffectOp.Swap -> if (source != null && t != null && (t.id == source.id || !t.has(Keyword.IMMOVABLE))) {
                val a = source.pos
                source.pos = t.pos
                t.pos = a
                s.event { GameEvent.Moved(it, source.id, t.pos, source.pos) }
                s.event { GameEvent.Moved(it, t.id, source.pos, t.pos) }
            }
            is EffectOp.Enthrall -> t?.let { u ->
                if (!u.isKing && u.hp <= op.maxHealth && hasRoomForUnit(s, p, slotsOf(u.def))) {
                    u.owner = p
                    u.stun = 0
                    s.log("${u.tag} is enthralled and now fights for ${s.players[p].name}")
                    s.event { GameEvent.Status(it, u.pos, "Enthralled!") }
                } else {
                    s.event { GameEvent.Status(it, u.pos, "Resisted") }
                }
            }
            is EffectOp.Push -> t?.let { u ->
                if (u.has(Keyword.IMMOVABLE)) return@let
                val (to, blocked) = pushPath(s, p, u, op) ?: return@let
                if (to != u.pos) moveUnit(s, u, to)
                s.log("${u.name} is pushed to $to")
                if (blocked && op.impactDamage > 0) {
                    s.event { GameEvent.Status(it, u.pos, "Slammed!") }
                    dealDamage(s, u, op.impactDamage)
                }
            }
            EffectOp.Replace -> t?.let { u -> if (!u.isKing && !u.has(Keyword.IMMOVABLE)) replaceUnit(s, u) }
            EffectOp.RallyToKing -> t?.let { u ->
                rallySquare(s, u)?.let { to ->
                    moveUnit(s, u, to)
                    s.log("${u.name} rallies to the King at $to")
                }
            }
            EffectOp.SwapWithNearestAlly -> t?.let { u ->
                val ally = nearestMovableAlly(s, p, u)
                if (ally != null && !u.has(Keyword.IMMOVABLE)) {
                    val a = u.pos
                    moveUnit(s, u, ally.pos)
                    moveUnit(s, ally, a)
                    s.log("${u.name} and ${ally.name} trade places: ${u.name} to ${u.pos}, ${ally.name} to ${ally.pos}")
                }
            }
            EffectOp.Pounce -> if (source != null && t != null) {
                val spot = pounceSquare(s, source, t)
                if (spot != null) {
                    if (spot != source.pos) moveUnit(s, source, spot)
                    performAttack(s, source, t)
                }
            }
        }
    }

    /** Shuffles [u] into its controller's deck and puts a random unit card from that deck on its square. */
    private fun replaceUnit(s: GameState, u: UnitState) {
        val p = u.owner
        val deck = s.players[p].deck
        val free = unitCap(s, p) - usedSlots(s, p) + slotsOf(u.def)
        val pool = deck.filter { it.def.unit != null && !it.def.isKing && slotsOf(it.def) <= free }
        if (pool.isEmpty()) {
            s.event { GameEvent.Status(it, u.pos, "No answer") }
            return
        }
        val incoming = pool[s.rng.nextInt(pool.size)]
        deck.remove(incoming)
        s.units.remove(u)
        if (!u.isToken) deck.add(s.rng.nextInt(deck.size + 1), u.card)
        val fresh = summon(s, p, incoming, u.pos, token = false)
        s.event { GameEvent.Status(it, fresh.pos, "Replaced") }
        s.log("${u.tag} returns to the deck; ${fresh.name} takes its place")
    }

    /** A used card goes to the discard pile (it will come back) or is exhausted (out of the game). */
    private fun retire(s: GameState, p: Int, card: CardInstance) {
        val ps = s.players[p]
        if (card.def.returnsToDeck) ps.discard += card else ps.exhausted += card
    }

    /** Kings shrug off damage and poison from cards and abilities. */
    private fun kingImmune(s: GameState, k: UnitState) {
        s.event { GameEvent.Status(it, k.pos, "Immune") }
    }

    /** A unit played from a card: triggers its arrival effects. */
    private fun arrive(s: GameState, p: Int, u: UnitState) {
        val arrival = u.def.unit?.arrival.orEmpty()
        if (arrival.isNotEmpty()) applyEffects(s, p, u, arrival, Target.Unit(u.id), null)
    }

    /**
     * Summons a token of [cardId] next to [source], respecting the unit cap. [limit] caps how many
     * tokens of that kind the side may have at once (Call the Pack).
     */
    private fun summonToken(s: GameState, source: UnitState, cardId: String, limit: Int? = null) {
        val p = source.owner
        if (!hasRoomForUnit(s, p, slotsOf(CardDatabase.get(cardId)))) return
        if (limit != null && s.unitsOf(p).count { it.isToken && it.def.id == cardId } >= limit) return
        val spot = source.pos.neighbors().firstOrNull { s.unitAt(it) == null } ?: return
        val u = summon(s, p, CardInstance(s.newId(), cardId), spot, token = true)
        s.event { GameEvent.Status(it, spot, "Summoned") }
        s.log("${u.name} joins the battle at $spot")
    }

    internal fun summon(s: GameState, p: Int, card: CardInstance, pos: Pos, token: Boolean): UnitState {
        val st = card.def.unit ?: error("${card.cardId} is not a unit")
        val u = UnitState(
            id = s.newId(), card = card, owner = p, pos = pos,
            maxHp = st.health, hp = st.health, attack = st.attack, move = st.move, range = st.range,
            keywords = st.keywords.toMutableSet(),
            abilities = st.abilities.mapTo(mutableListOf()) { AbilityState(it) },
            isKing = st.isKing, isToken = token,
        )
        applyTrait(s.players[p].trait, u)
        if (!token && u.has(Keyword.BROOD)) u.broodLeft = BROOD_TURNS
        s.units += u
        return u
    }

    /** Racial trait adjustments for a unit entering [trait]'s army. */
    private fun applyTrait(trait: RacialTrait?, u: UnitState) {
        fun health(delta: Int) {
            val hp = max(1, u.maxHp + delta)
            u.maxHp = hp
            u.hp = hp
        }
        when (trait) {
            RacialTrait.PACK_TACTICS -> u.keywords += Keyword.PACK_HUNTER
            RacialTrait.THICK_FUR -> {
                health(+2)
                if (u.move >= 3) u.move -= 1
            }
            RacialTrait.EAGLE_EYES -> {
                if (u.range >= 2) u.range += 1
                health(-1)
            }
            RacialTrait.VENOM_BLOOD -> {
                u.keywords += Keyword.POISONOUS
                u.attack = max(1, u.attack - 1)
            }
            RacialTrait.ROYAL_PRIDE -> if (u.isKing) {
                health(+3)
                u.attack += 1
            } else health(-1)
            RacialTrait.ENDLESS_HORDE, null -> {}
        }
    }

    private fun moveUnit(s: GameState, u: UnitState, to: Pos) {
        val from = u.pos
        u.pos = to
        s.event { GameEvent.Moved(it, u.id, from, to) }
    }

    private fun performAttack(s: GameState, a: UnitState, t: UnitState) {
        val dealt = dealDamage(s, t, attackDamage(s, a, t))
        if (t.alive) {
            if (a.has(Keyword.POISONOUS) && dealt > 0) poison(s, t, 1, 2)
            if (a.has(Keyword.PETRIFY)) stun(s, t, 1)
            if (t.has(Keyword.RETALIATE) && t.stun == 0 && a.pos.distanceTo(t.pos) == 1) {
                s.log("${t.tag} retaliates")
                dealDamage(s, a, attackDamage(s, t, a))
            }
        }
    }

    /** Returns the damage actually dealt. */
    fun dealDamage(s: GameState, t: UnitState, amount: Int, ignoreReductions: Boolean = false): Int {
        if (!t.alive) return 0
        var d = amount
        if (!ignoreReductions) {
            if (t.has(Keyword.ARMORED)) d -= 1
            if (s.fieldActive(t.owner, FieldRule.FORTIFY)) d -= 1
            if (s.unitsOf(t.owner).any { it.id != t.id && it.has(Keyword.GUARDIAN) && it.pos.distanceTo(t.pos) == 1 }) d -= 1
        }
        d = max(0, d)
        if (t.has(Keyword.UNSTOPPABLE)) d = min(d, 3)
        if (!ignoreReductions && t.shield > 0 && d > 0) {
            val absorbed = min(t.shield, d)
            t.shield -= absorbed
            d -= absorbed
            s.event { GameEvent.Status(it, t.pos, "Blocked $absorbed") }
        }
        t.hp -= d
        s.event { GameEvent.Damaged(it, t.id, t.pos, d) }
        return d
    }

    private fun heal(s: GameState, t: UnitState, amount: Int) {
        if (t.isKing && exhausted(s)) return
        val h = min(amount, t.maxHp - t.hp)
        if (h <= 0) return
        t.hp += h
        s.event { GameEvent.Healed(it, t.id, t.pos, h) }
    }

    /**
     * Timed effects count down at the end of their unit's owner's turns. One applied during the
     * owner's own turn would otherwise expire before the unit could use it (playing the card was
     * the turn's action), so it gets one extra turn: "for 1 turn" means "through your next turn".
     */
    private fun timedTurns(s: GameState, u: UnitState, turns: Int): Int =
        if (s.phase == Phase.BATTLE && s.activePlayer == u.owner) turns + 1 else turns

    private fun buff(s: GameState, t: UnitState, b: EffectOp.Buff) {
        if (b.turns == PERMANENT) {
            t.attack += b.attack
            t.maxHp += b.health
            t.hp += b.health
            t.move += b.move
            t.range += b.range
        } else {
            t.mods += TimedMod(b.attack, b.move, b.range, timedTurns(s, t, b.turns))
        }
        val parts = buildList {
            if (b.attack != 0) add("%+d ATK".format(b.attack))
            if (b.health != 0) add("%+d HP".format(b.health))
            if (b.move != 0) add("%+d MOV".format(b.move))
            if (b.range != 0) add("%+d RNG".format(b.range))
        }
        if (parts.isNotEmpty()) s.event { GameEvent.Status(it, t.pos, parts.joinToString(" ")) }
    }

    private fun stun(s: GameState, t: UnitState, turns: Int) {
        if (t.has(Keyword.UNSTOPPABLE)) {
            s.event { GameEvent.Status(it, t.pos, "Immune") }
            return
        }
        t.stun = max(t.stun, turns)
        s.event { GameEvent.Status(it, t.pos, "Stunned") }
    }

    private fun poison(s: GameState, t: UnitState, damage: Int, turns: Int) {
        t.poisonDamage = max(t.poisonDamage, damage)
        t.poisonTurns = max(t.poisonTurns, turns)
        s.event { GameEvent.Status(it, t.pos, "Poisoned") }
    }

    private fun cleanupDeaths(s: GameState) {
        val dead = s.units.filter { it.hp <= 0 }
        if (dead.isEmpty()) return
        for (u in dead) {
            s.units.remove(u)
            s.log("${u.tag} is defeated")
            s.event { GameEvent.Died(it, u.id, u.pos) }
            if (!u.isToken) s.players[u.owner].exhausted += u.card
            for (b in s.units.filter { it.alive && it.owner != u.owner && it.has(Keyword.BLOODTHIRST) }) {
                b.attack += 1
                heal(s, b, 2)
                s.event { GameEvent.Status(it, b.pos, "Bloodthirst +1 ATK") }
            }
        }
        if (dead.any { it.isKing }) {
            val k0 = s.king(0) != null
            val k1 = s.king(1) != null
            s.phase = Phase.GAME_OVER
            s.priority = null
            when {
                !k0 && !k1 -> {
                    s.isDraw = true
                    s.log("Both Kings have fallen. It's a draw!")
                }
                else -> {
                    val w = if (k0) 0 else 1
                    s.winner = w
                    s.log("${s.players[1 - w].name}'s King has fallen. ${s.players[w].name} wins!")
                }
            }
        }
    }

    // ------------------------------------------------------------------ turns

    private fun endTurn(s: GameState) {
        val p = s.activePlayer
        for (u in s.unitsOf(p)) {
            u.mods.forEach { it.turns-- }
            u.mods.removeAll { it.turns <= 0 }
            u.timedKeywords.forEach { it.turns-- }
            u.timedKeywords.removeAll { it.turns <= 0 }
            if (u.stun > 0) u.stun--
            u.abilities.forEach { if (it.cooldown > 0) it.cooldown-- }
        }
        s.fields.filter { it.owner == p }.forEach { it.turns-- }
        s.fields.filter { it.turns <= 0 }.forEach { s.log("${it.rule.displayName} has ended") }
        s.fields.removeAll { it.turns <= 0 }
        s.blitzUsed = false
        s.activePlayer = 1 - p
        s.turnNumber++
        if (s.turnNumber > TURN_LIMIT) {
            s.phase = Phase.GAME_OVER
            s.isDraw = true
            s.log("The battle drags on too long. It's a draw!")
            return
        }
        startTurn(s, draw = true)
    }

    private fun startTurn(s: GameState, draw: Boolean) {
        val p = s.activePlayer
        s.log("— Turn ${s.turnNumber}: ${s.players[p].name} —")
        for (u in s.unitsOf(p)) {
            if (u.poisonTurns > 0) {
                dealDamage(s, u, u.poisonDamage, ignoreReductions = true)
                u.poisonTurns--
                if (u.poisonTurns == 0) u.poisonDamage = 0
            }
            if (u.alive) {
                var h = 0
                if (u.has(Keyword.REGENERATE)) h++
                if (s.fieldActive(p, FieldRule.SANCTUARY)) h++
                if (h > 0) heal(s, u, h)
            }
        }
        for (u in s.unitsOf(p).filter { it.has(Keyword.TEMPEST) }) {
            val enemies = s.unitsOf(1 - p).filter { !it.isKing && it.pos.distanceTo(u.pos) <= TEMPEST_RANGE }
            if (enemies.isEmpty()) continue
            val t = enemies[s.rng.nextInt(enemies.size)]
            s.log("Tempest strikes ${t.tag}")
            dealDamage(s, t, 1)
        }
        for (u in s.unitsOf(p).filter { it.has(Keyword.PACK_CALLER) }) summonToken(s, u, "w_pup", limit = MAX_SUMMONED)
        for (u in s.unitsOf(p).filter { it.broodLeft > 0 }) {
            u.broodLeft--
            summonToken(s, u, SWARM_RAT)
        }
        if (s.fieldActive(p, FieldRule.PLAGUE)) {
            val mine = s.unitsOf(p)
            s.unitsOf(1 - p).filter { e -> !e.isKing && mine.any { it.pos.distanceTo(e.pos) == 1 } }.forEach { e ->
                s.log("Creeping Plague hurts ${e.tag}")
                dealDamage(s, e, 1)
            }
        }
        // Exhaustion: very long battles wear the Kings down so that games reach an ending.
        if (exhausted(s)) {
            s.king(p)?.let { k ->
                val dmg = 1 + (s.turnNumber - EXHAUSTION_TURN) / 20
                s.log("Exhaustion: ${k.name} loses $dmg health")
                dealDamage(s, k, dmg, ignoreReductions = true)
            }
        }
        cleanupDeaths(s)
        if (s.phase == Phase.GAME_OVER) return
        if (draw) drawCard(s, p)
    }

    private fun drawCard(s: GameState, p: Int) {
        val ps = s.players[p]
        if (ps.deck.isEmpty() && ps.discard.isNotEmpty()) {
            // An empty deck is refilled by shuffling the discard pile.
            ps.deck += ps.discard
            ps.discard.clear()
            s.rng.shuffle(ps.deck)
            s.log("${ps.name} shuffles the discard pile into a new deck")
        }
        if (ps.deck.isEmpty()) {
            s.log("${ps.name} has no cards left to draw")
            return
        }
        val card = ps.deck.removeAt(0)
        if (ps.hand.size >= HAND_LIMIT) {
            ps.discard += card
            s.log("${ps.name}'s hand is full: ${card.def.name} is discarded")
        } else {
            ps.hand += card
        }
    }

    // ------------------------------------------------------------ descriptions

    fun describe(s: GameState, action: Action): String = when (action) {
        is Action.Deploy -> "deploy at ${action.pos}"
        Action.EndDeploy -> "finish deployment"
        Action.Pass -> "pass"
        is Action.Move -> "${s.unit(action.unitId)?.tag} moves to ${action.to}"
        is Action.Attack -> "${s.unit(action.unitId)?.tag} attacks ${s.unit(action.targetId)?.tag}"
        is Action.UseAbility -> {
            val u = s.unit(action.unitId)
            "${u?.tag} uses ${u?.abilities?.getOrNull(action.abilityIndex)?.def?.name}${targetText(s, action.target)}"
        }
        is Action.PlayCard -> {
            val c = s.players.flatMap { it.hand }.firstOrNull { it.uid == action.cardUid }
            "plays ${c?.def?.name}${targetText(s, action.target)}"
        }
    }

    private fun targetText(s: GameState, t: Target): String = when (t) {
        Target.None -> ""
        is Target.Unit -> s.unit(t.unitId)?.let { " on ${it.tag}" } ?: ""
        is Target.Tile -> " at ${t.pos}"
        is Target.StackEntry -> s.stack.firstOrNull { it.id == t.itemId }?.let { " against “${it.label}”" } ?: ""
    }
}
