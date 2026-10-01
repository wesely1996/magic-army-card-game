package com.kingofthebeasts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingofthebeasts.app.decks.BattleSaveRepository
import com.kingofthebeasts.app.decks.DeckRepository
import com.kingofthebeasts.app.game.GameScreen
import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.app.ui.DeckBuilderScreen
import com.kingofthebeasts.app.ui.DeckListScreen
import com.kingofthebeasts.app.ui.MenuScreen
import com.kingofthebeasts.app.ui.PlaySetupScreen
import com.kingofthebeasts.app.ui.RulesScreen
import com.kingofthebeasts.app.ui.theme.KingOfTheBeastsTheme
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.game.SavedBattle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KingOfTheBeastsTheme { App() } }
    }
}

private sealed interface Screen {
    data object Menu : Screen
    data object Decks : Screen
    data class Builder(val deck: Deck?) : Screen
    data object Setup : Screen
    data class Battle(
        val player: Deck,
        val opponent: Deck,
        val difficulty: Difficulty,
        val seed: Long,
        /** Actions to replay when resuming a saved battle. */
        val resume: List<String> = emptyList(),
    ) : Screen
    data object Rules : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    val repo = remember { DeckRepository(context.applicationContext) }
    var decks by remember { mutableStateOf(repo.load()) }
    val battles = remember { BattleSaveRepository(context.applicationContext) }
    // Only offer to resume a save that still replays (cards may have changed in an update).
    var saved by remember { mutableStateOf(battles.load()?.takeIf { it.replay() != null }) }

    fun storeBattle(b: SavedBattle?) {
        saved = b
        if (b == null) battles.clear() else battles.save(b)
    }
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    fun saveDecks(list: List<Deck>) {
        decks = list
        repo.save(list)
    }

    fun newBattle(player: Deck, opponent: Deck?, difficulty: Difficulty) {
        val foe = opponent ?: StarterDecks.all.filter { it != player }.random()
        storeBattle(null) // a new battle replaces the one in progress
        screen = Screen.Battle(player, foe, difficulty, System.nanoTime())
    }

    BackHandler(enabled = screen != Screen.Menu && screen !is Screen.Battle) {
        screen = if (screen is Screen.Builder) Screen.Decks else Screen.Menu
    }

    when (val s = screen) {
        Screen.Menu -> MenuScreen(
            onPlay = { screen = Screen.Setup },
            onDecks = { screen = Screen.Decks },
            onRules = { screen = Screen.Rules },
            resumeLabel = saved?.let { "Turn ${it.turn} vs ${it.opponent.name} (${Difficulty.valueOf(it.difficulty).displayName})" },
            onResume = {
                saved?.let { b ->
                    screen = Screen.Battle(b.player, b.opponent, Difficulty.valueOf(b.difficulty), b.seed, b.actions)
                }
            },
        )
        Screen.Decks -> DeckListScreen(
            decks = decks,
            onBack = { screen = Screen.Menu },
            onEdit = { screen = Screen.Builder(it) },
            onDelete = { d -> saveDecks(decks.filter { it.name != d.name }) },
        )
        is Screen.Builder -> DeckBuilderScreen(
            initial = s.deck,
            existingNames = decks.map { it.name }.toSet(),
            onBack = { screen = Screen.Decks },
            onSave = { d ->
                val original = s.deck?.name
                saveDecks(decks.filter { it.name != original && it.name != d.name } + d)
                screen = Screen.Decks
            },
        )
        Screen.Setup -> PlaySetupScreen(decks, onBack = { screen = Screen.Menu }, onStart = ::newBattle)
        is Screen.Battle -> {
            val vm: GameViewModel = viewModel(key = "battle-${s.seed}-${s.resume.size}") {
                GameViewModel(s.player, s.opponent, s.difficulty, s.seed, s.resume, onSave = ::storeBattle)
            }
            GameScreen(
                vm,
                onExit = { screen = Screen.Menu },
                onRematch = { newBattle(s.player, s.opponent, s.difficulty) },
                onForfeit = {
                    storeBattle(null)
                    screen = Screen.Menu
                },
            )
        }
        Screen.Rules -> RulesScreen(onBack = { screen = Screen.Menu })
    }
}
