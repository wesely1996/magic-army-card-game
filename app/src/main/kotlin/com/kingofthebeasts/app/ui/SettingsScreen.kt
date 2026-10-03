package com.kingofthebeasts.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kingofthebeasts.app.audio.GameAudio
import com.kingofthebeasts.app.audio.Sfx
import com.kingofthebeasts.app.net.Relay
import com.kingofthebeasts.app.settings.AnimationSpeed
import com.kingofthebeasts.app.settings.AppSettings
import com.kingofthebeasts.app.ui.theme.Ink
import kotlin.math.roundToInt

/** The Settings page from the main menu: options on the left, credits on the right. */
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    PaperBackground {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            ScreenHeader("Settings", onBack)
            Row(Modifier.weight(1f).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    SettingsPanel()
                    ServerSetting()
                }
                Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    Credits()
                }
            }
        }
    }
}

/** The options themselves; also shown in a dialog during battle. */
@Composable
fun SettingsPanel(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle("Sound")
        VolumeRow("🎵  Music", AppSettings.musicVolume, AppSettings::setMusic)
        VolumeRow("🔊  Sound effects", AppSettings.sfxVolume, AppSettings::setSfx, onRelease = { GameAudio.play(Sfx.SWORD1) })

        Spacer(Modifier.height(8.dp))
        SectionTitle("Battle")
        Text("Board view", style = MaterialTheme.typography.labelLarge)
        Text(
            "2.5D shows the board in perspective with standing pieces; Top-down shows it flat from above with round tokens.",
            style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            Choice("2.5D", !AppSettings.boardFlat) { AppSettings.chooseBoardView(false) }
            Choice("Top-down 2D", AppSettings.boardFlat) { AppSettings.chooseBoardView(true) }
        }
        Spacer(Modifier.height(4.dp))
        Text("Animation speed", style = MaterialTheme.typography.labelLarge)
        Text(
            "How quickly attacks, spells and the opponent's moves play out.",
            style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            AnimationSpeed.entries.forEach { sp ->
                Choice(sp.displayName, AppSettings.animationSpeed == sp) { AppSettings.setSpeed(sp) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Keep the screen on", style = MaterialTheme.typography.labelLarge)
        Text("Stops the phone from dimming during a battle.", style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            Choice("On", AppSettings.keepScreenOn) { AppSettings.setScreenAwake(true) }
            Choice("Off", !AppSettings.keepScreenOn) { AppSettings.setScreenAwake(false) }
        }
    }
}

/** Which relay server internet play goes through; only needed to use a server of your own. */
@Composable
private fun ServerSetting() {
    Spacer(Modifier.height(8.dp))
    SectionTitle("Online")
    Text("Game server", style = MaterialTheme.typography.labelLarge)
    Text(
        if (Relay.DEFAULT_URL.isBlank()) "Internet play needs a server address, e.g. https://kotb-relay.example.workers.dev"
        else "Leave empty to use the built-in server. Both friends need the same server.",
        style = MaterialTheme.typography.bodySmall, color = Ink.Faded,
    )
    TextBox(AppSettings.serverUrl, { AppSettings.chooseServer(it.trim().take(200)) }, Relay.DEFAULT_URL.ifBlank { "Server address" })
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp).brushUnderline(Ink.Gold, text.length))
}

@Composable
private fun VolumeRow(label: String, value: Float, onChange: (Float) -> Unit, onRelease: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(150.dp))
        Slider(
            value, onChange,
            modifier = Modifier.weight(1f),
            onValueChangeFinished = onRelease,
            colors = SliderDefaults.colors(
                thumbColor = Ink.Gold, activeTrackColor = Ink.Gold, inactiveTrackColor = Ink.PaperDeep,
            ),
        )
        Text(
            if (value == 0f) "Off" else "${(value * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(44.dp).padding(start = 6.dp),
        )
    }
}

/** One option of a choice row: highlighted when [selected]. */
@Composable
internal fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Ink.Line else Ink.Faded,
        modifier = Modifier
            .then(if (selected) Modifier.watercolor(Ink.Gold, text.hashCode(), 1.3f) else Modifier.background(Ink.Paper.copy(alpha = 0.5f)))
            .sketchBorder(if (selected) Ink.Line else Ink.Faded, seed = text.hashCode())
            .clickable {
                GameAudio.play(Sfx.TOGGLE)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Credits() {
    SectionTitle("Credits")
    val credits = listOf(
        "Created by" to "wesely1996 — game idea, design and direction",
        "Card illustrations" to "Painted from game-icons.net icons by Lorc, Delapouite, Caro Asercion, Skoll, Sbed, " +
            "DarkZaitzev, Cathelineau, Sparker, Lucas and Faithtoken (CC BY 3.0)",
        "Music" to "RandomMind — “Medieval: The Bard's Tale”, “Medieval: Battle”, “Medieval: Victory Theme” and " +
            "“Medieval: Defeat Theme” (OpenGameArt.org, CC0)",
        "Sound effects" to "Kenney (kenney.nl) — Interface, Impact and Music Jingles packs · rubberduck — 80 CC0 RPG SFX · " +
            "haeldb — Card Game Sounds · StarNinjas — 20 Sword Sound Effects · artisticdude — Swishes Sound Pack · " +
            "someoneman — Cure Magic (OpenGameArt.org, all CC0)",
        "Fonts" to "Kalam and Caveat Brush (SIL Open Font License)",
        "Made with" to "Claude Code (Anthropic) — code, rules engine, AI and generated art",
    )
    for ((title, text) in credits) {
        Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Ink.Faded)
    }
}
