package com.kingofthebeasts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kingofthebeasts.app.net.DEFAULT_PORT
import com.kingofthebeasts.app.net.FoundGame
import com.kingofthebeasts.app.net.FriendsLobby
import com.kingofthebeasts.app.net.OnlineSession
import com.kingofthebeasts.app.settings.AppSettings
import com.kingofthebeasts.app.ui.theme.Ink
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.deck.DeckRules

/** Play with a friend on the same Wi-Fi: pick a name and an army, then host or join. */
@Composable
fun FriendsScreen(lobby: FriendsLobby, decks: List<Deck>, onBack: () -> Unit) {
    val playable = decks.filter { DeckRules.isValid(it) } + StarterDecks.all
    var mine by remember { mutableStateOf(playable.first()) }
    val name = AppSettings.playerName
    val busy = lobby.step !is FriendsLobby.Step.Idle && lobby.step !is FriendsLobby.Step.Failed
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("With friends", {
                lobby.reset()
                onBack()
            })
            Row(Modifier.weight(1f).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Text("Your name", style = MaterialTheme.typography.titleLarge)
                    TextBox(name, { AppSettings.setName(it.take(24)) }, "Your name", enabled = !busy)
                    Spacer(Modifier.height(10.dp))
                    Text("Your army", style = MaterialTheme.typography.titleLarge)
                    playable.forEach { d ->
                        Box(Modifier.padding(vertical = 4.dp)) {
                            DeckRow(d, selected = d == mine, onClick = if (busy) null else ({ mine = d }))
                        }
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    LobbyPanel(lobby, name.ifBlank { "Player" }, mine)
                }
            }
        }
    }
}

@Composable
private fun LobbyPanel(lobby: FriendsLobby, name: String, deck: Deck) {
    when (val step = lobby.step) {
        FriendsLobby.Step.Idle, is FriendsLobby.Step.Failed -> {
            Text("Play a friend on the same Wi-Fi", style = MaterialTheme.typography.titleLarge)
            Text(
                "One of you hosts a game, the other joins it. Both phones need to be on the same Wi-Fi " +
                    "network and on the same version of the game.",
                style = MaterialTheme.typography.bodyMedium, color = Ink.Faded,
            )
            if (step is FriendsLobby.Step.Failed) {
                Spacer(Modifier.height(8.dp))
                Text(step.reason, style = MaterialTheme.typography.bodyMedium, color = Ink.Enemy)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SketchButton("🏰  Host a game", { lobby.host(name, deck) }, Modifier.weight(1f), color = Ink.Deploy)
                SketchButton("🔎  Join a game", { lobby.browse() }, Modifier.weight(1f), color = Ink.You)
            }
        }
        is FriendsLobby.Step.Hosting -> {
            Text("Waiting for a friend…", style = MaterialTheme.typography.titleLarge)
            Text(
                "Your game “${step.name}” is open on this Wi-Fi. Ask your friend to tap Join a game.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (step.addresses.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("If it doesn't show up for them, they can join by address:", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
                step.addresses.forEach { a ->
                    Text(if (step.port == DEFAULT_PORT) a else "$a:${step.port}", style = MaterialTheme.typography.titleMedium, color = Ink.You)
                }
            }
            Spacer(Modifier.height(16.dp))
            SketchButton("Cancel", { lobby.reset() }, color = Ink.PaperDeep)
        }
        FriendsLobby.Step.Browsing -> {
            val games by lobby.browser.games.collectAsState()
            Text("Games on this Wi-Fi", style = MaterialTheme.typography.titleLarge)
            if (games.isEmpty()) {
                Text(
                    "Looking… Ask your friend to tap Host a game.",
                    style = MaterialTheme.typography.bodyMedium, color = Ink.Faded,
                )
            }
            games.forEach { g -> GameRow(g) { lobby.join(name, deck, g.host, g.port, g.name) } }
            Spacer(Modifier.height(14.dp))
            Text("Or join by address", style = MaterialTheme.typography.titleSmall)
            var address by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { TextBox(address, { address = it.trim().take(40) }, "e.g. 192.168.1.23") }
                SketchButton("Join", {
                    val host = address.substringBefore(':')
                    val port = address.substringAfter(':', "").toIntOrNull() ?: DEFAULT_PORT
                    if (host.isNotBlank()) lobby.join(name, deck, host, port)
                }, small = true, color = Ink.You, enabled = address.isNotBlank())
            }
            Spacer(Modifier.height(16.dp))
            SketchButton("Cancel", { lobby.reset() }, color = Ink.PaperDeep)
        }
        is FriendsLobby.Step.Connecting -> {
            Text("Connecting to ${step.to}…", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            SketchButton("Cancel", { lobby.reset() }, color = Ink.PaperDeep)
        }
        FriendsLobby.Step.Connected -> {
            val status = lobby.session?.status?.collectAsState()?.value
            if (status is OnlineSession.Status.Ended) {
                Text("Couldn't start the battle", style = MaterialTheme.typography.titleLarge, color = Ink.Enemy)
                Text(status.reason, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
                Spacer(Modifier.height(16.dp))
                SketchButton("Back", { lobby.reset() }, color = Ink.PaperDeep)
            } else {
                Text("Connected!", style = MaterialTheme.typography.titleLarge, color = Ink.Deploy)
                Text("Setting up the battle…", style = MaterialTheme.typography.bodyMedium, color = Ink.Faded)
            }
        }
    }
}

@Composable
private fun GameRow(game: FoundGame, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .watercolor(Ink.You, game.name.hashCode(), 0.8f)
            .sketchBorder(seed = game.name.hashCode())
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🏰  ${game.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text("Join ▶", style = MaterialTheme.typography.labelLarge, color = Ink.You)
    }
}

@Composable
private fun TextBox(value: String, onChange: (String) -> Unit, hint: String, enabled: Boolean = true) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Ink.Paper.copy(alpha = 0.7f))
            .sketchBorder(seed = hint.hashCode())
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (value.isEmpty()) Text(hint, style = MaterialTheme.typography.bodyLarge, color = Ink.Faded)
        BasicTextField(
            value, onChange, enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
    }
}
