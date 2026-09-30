package com.kingofthebeasts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.game.GameEngine
import com.kingofthebeasts.core.game.GameState
import com.kingofthebeasts.core.game.UnitState
import com.kingofthebeasts.core.model.CardDef
import com.kingofthebeasts.core.model.CardType
import com.kingofthebeasts.core.model.TargetKind
import com.kingofthebeasts.core.model.TargetRule

/**
 * Full-screen inspection of a card (opened with a long press): the card at a
 * large size plus every rule spelled out below it. When [unit] is given (a
 * unit on the board) its live stats and status effects are shown too.
 */
@Composable
fun CardInspectDialog(
    def: CardDef,
    onDismiss: () -> Unit,
    unit: UnitState? = null,
    state: GameState? = null,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val dismissSource = remember { MutableInteractionSource() }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(Color(0xB3201812))
                .clickable(dismissSource, indication = null, onClick = onDismiss),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (maxWidth > maxHeight) {
                // Landscape: the card as tall as the screen allows, the rules beside it.
                val cardWidth = min((maxHeight - 32.dp) * CARD_ASPECT, maxWidth * 0.42f)
                Row(
                    Modifier.fillMaxSize().systemBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardFace(def, width = cardWidth, full = true, onClick = onDismiss)
                    Column(Modifier.widthIn(max = 460.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        RulesPanel(def, unit, state, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Text("Tap anywhere to close", style = MaterialTheme.typography.bodySmall, color = Ink.Paper)
                    }
                }
            } else {
                val cardWidth = min(maxWidth - 32.dp, 340.dp)
                Column(
                    Modifier
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CardFace(def, width = cardWidth, full = true, onClick = onDismiss)
                    Spacer(Modifier.height(12.dp))
                    RulesPanel(def, unit, state, Modifier.width(cardWidth))
                    Spacer(Modifier.height(10.dp))
                    Text("Tap anywhere to close", style = MaterialTheme.typography.bodySmall, color = Ink.Paper)
                }
            }
        }
    }
}

@Composable
private fun RulesPanel(def: CardDef, unit: UnitState?, state: GameState?, modifier: Modifier) {
    Column(
        modifier
            .background(Ink.Paper, RoundedCornerShape(14.dp))
            .watercolor(Ink.race(def.race), def.id.hashCode(), 0.5f)
            .sketchBorder(seed = def.id.hashCode(), corner = 14.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(def.name, style = MaterialTheme.typography.headlineMedium)
        Text(
            "${def.race.displayName} · ${def.type.displayName}" + if (def.isKing) " · King" else "",
            style = MaterialTheme.typography.labelLarge, color = Ink.Faded,
        )

        if (def.isKing) {
            Section("King rules")
            Line("Immune", "takes no damage from Magic cards or abilities (attacks still hurt)")
            Line("Immovable", "can't be pushed, swapped or replaced by other cards and abilities")
            Line("No slot", "doesn't count toward the unit slots")
            Line("Racial trait: ${def.race.trait.displayName}", def.race.trait.description + " (for the army this King leads)")
        } else if (def.unit != null) {
            Line(
                if (def.unit!!.isElite) "Elite" else "Unit slot",
                if (def.unit!!.isElite) "takes 2 of your unit slots" else "takes 1 of your unit slots",
            )
        }
        Line(
            "After use",
            when {
                def.unit != null -> "exhausted: a unit card can be played only once"
                def.returnsToDeck -> "goes to your discard pile, which becomes your new deck when the deck runs out"
                else -> "exhausted: out of the game once used"
            },
        )

        def.unit?.let { st ->
            Section("Base stats")
            Line("Attack ${st.attack}", "damage dealt by a normal attack")
            Line("Health ${st.health}", "the unit is defeated at 0")
            Line("Movement ${st.move}", "squares per move, in any of 8 directions")
            Line("Range ${st.range}", if (st.range == 1) "attacks adjacent enemies only" else "attacks enemies up to ${st.range} squares away")
        }

        if (unit != null && state != null) {
            Section(if (unit.owner == 0) "On the battlefield (yours)" else "On the battlefield (opponent's)")
            Line(
                "ATK ${GameEngine.attackOf(state, unit)} · HP ${unit.hp}/${unit.maxHp} · MOV ${GameEngine.moveOf(state, unit)} · RNG ${GameEngine.rangeOf(state, unit)}",
                "current values including all bonuses",
            )
            if (unit.stun > 0) Line("Stunned", "can't act for ${unit.stun} more turn(s)")
            if (unit.poisonTurns > 0) Line("Poisoned", "takes ${unit.poisonDamage} damage at the start of its turn, ${unit.poisonTurns} more time(s)")
            if (unit.shield > 0) Line("Shield ${unit.shield}", "absorbs that much damage")
            unit.timedKeywords.forEach { Line(it.keyword.displayName, "${it.keyword.description} (${it.turns} turn(s) left)") }
            unit.mods.forEach { m ->
                val parts = listOfNotNull(
                    m.attack.takeIf { it != 0 }?.let { "%+d attack".format(it) },
                    m.move.takeIf { it != 0 }?.let { "%+d movement".format(it) },
                    m.range.takeIf { it != 0 }?.let { "%+d range".format(it) },
                )
                if (parts.isNotEmpty()) Line("Temporary boost", "${parts.joinToString()} for ${m.turns} turn(s)")
            }
            unit.equipment.forEach { Line("Equipped: $it", "permanent upgrade") }
            unit.abilities.filter { it.cooldown > 0 }.forEach { Line(it.def.name, "ready again in ${it.cooldown} turn(s)") }
        }

        def.unit?.keywords?.takeIf { it.isNotEmpty() }?.let { kws ->
            Section("Traits")
            kws.forEach { Line(it.displayName, it.description) }
        }

        def.unit?.abilities?.takeIf { it.isNotEmpty() }?.let { abilities ->
            Section("Abilities")
            abilities.forEach { a ->
                Line(a.name + if (a.quick) " ⚡" else "", a.text)
                Text(
                    buildString {
                        append("Target: ").append(targetDescription(a.target))
                        append(" · Cooldown: ").append(a.cooldown).append(" turn(s)")
                        if (a.quick) append(" · Quick: can interrupt the opponent")
                    },
                    style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        if (def.unit == null) {
            Section("Effect")
            Text(def.rulesText, style = MaterialTheme.typography.bodyLarge)
            Text(
                when (def.type) {
                    CardType.MAGIC -> "⚡ Magic: play it on your turn, or as an interrupt when your opponent acts."
                    CardType.STRATEGY -> "Strategy: changes the rules of the battlefield. You can have one active at a time; a new one replaces the old."
                    CardType.EQUIPMENT -> "Equipment: attaches to one of your units and stays for the rest of the battle."
                    CardType.UNIT -> ""
                } + " Target: " + targetDescription(def.target) + ".",
                style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
            )
        } else {
            Section("Playing it")
            Text(
                if (def.isKing) "Your King is deployed first. If it falls, you lose the battle."
                else "Deploy it in your first 3 rows before the battle, or during the battle on an empty edge square at least 2 squares from every enemy. " +
                    "You can have at most 10 units on the board.",
                style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
            )
        }

        if (def.flavor.isNotEmpty()) {
            Text("“${def.flavor}”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, color = Ink.Faded)
        }
    }
}

@Composable
private fun Section(title: String) {
    Box(Modifier.padding(top = 6.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.brushUnderline(Ink.Gold, title.hashCode()))
    }
}

@Composable
private fun Line(label: String, text: String) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
            append(" — ")
            append(text)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun targetDescription(rule: TargetRule): String {
    val within = if (rule.range < TargetRule.UNLIMITED) " within ${rule.range} square(s)" else ""
    return when (rule.kind) {
        TargetKind.NONE -> "none (affects the battlefield)"
        TargetKind.SELF -> "this unit"
        TargetKind.FRIENDLY_UNIT -> "one of your units$within"
        TargetKind.ENEMY_UNIT -> "an enemy unit$within"
        TargetKind.ANY_UNIT -> "any unit$within"
        TargetKind.STACK_ITEM -> "the action you are responding to"
    }
}
