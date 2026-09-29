package com.magicarmy.app

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
import com.magicarmy.app.decks.DeckRepository
import com.magicarmy.app.game.GameScreen
import com.magicarmy.app.game.GameViewModel
import com.magicarmy.app.ui.DeckBuilderScreen
import com.magicarmy.app.ui.DeckListScreen
import com.magicarmy.app.ui.MenuScreen
import com.magicarmy.app.ui.PlaySetupScreen
import com.magicarmy.app.ui.RulesScreen
import com.magicarmy.app.ui.theme.MagicArmyTheme
import com.magicarmy.core.data.StarterDecks
import com.magicarmy.core.deck.Deck

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MagicArmyTheme { App() } }
    }
}

private sealed interface Screen {
    data object Menu : Screen
    data object Decks : Screen
    data class Builder(val deck: Deck?) : Screen
    data object Setup : Screen
    data class Battle(val player: Deck, val opponent: Deck, val seed: Long) : Screen
    data object Rules : Screen
}

@Composable
private fun App() {
    val context = LocalContext.current
    val repo = remember { DeckRepository(context.applicationContext) }
    var decks by remember { mutableStateOf(repo.load()) }
    var screen by remember { mutableStateOf<Screen>(Screen.Menu) }

    fun saveDecks(list: List<Deck>) {
        decks = list
        repo.save(list)
    }

    fun newBattle(player: Deck, opponent: Deck?) {
        val foe = opponent ?: StarterDecks.all.filter { it != player }.random()
        screen = Screen.Battle(player, foe, System.nanoTime())
    }

    BackHandler(enabled = screen != Screen.Menu && screen !is Screen.Battle) {
        screen = if (screen is Screen.Builder) Screen.Decks else Screen.Menu
    }

    when (val s = screen) {
        Screen.Menu -> MenuScreen(
            onPlay = { screen = Screen.Setup },
            onDecks = { screen = Screen.Decks },
            onRules = { screen = Screen.Rules },
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
            val vm: GameViewModel = viewModel(key = "battle-${s.seed}") { GameViewModel(s.player, s.opponent, s.seed) }
            GameScreen(
                vm,
                onExit = { screen = Screen.Menu },
                onRematch = { newBattle(s.player, s.opponent) },
            )
        }
        Screen.Rules -> RulesScreen(onBack = { screen = Screen.Menu })
    }
}
