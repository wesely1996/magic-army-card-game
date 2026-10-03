package com.kingofthebeasts.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.kingofthebeasts.app.audio.GameAudio
import com.kingofthebeasts.app.audio.Music
import com.kingofthebeasts.app.settings.AppSettings
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingofthebeasts.app.decks.BattleSaveRepository
import com.kingofthebeasts.app.decks.DeckRepository
import com.kingofthebeasts.app.game.GameScreen
import com.kingofthebeasts.app.game.GameViewModel
import com.kingofthebeasts.app.game.OnlineInfo
import com.kingofthebeasts.app.net.FriendsLobby
import com.kingofthebeasts.app.net.OnlineSession
import com.kingofthebeasts.app.ui.FriendsScreen
import com.kingofthebeasts.app.ui.DeckBuilderScreen
import com.kingofthebeasts.app.ui.DeckListScreen
import com.kingofthebeasts.app.ui.DeckViewScreen
import com.kingofthebeasts.app.ui.MenuScreen
import com.kingofthebeasts.app.ui.PlaySetupScreen
import com.kingofthebeasts.app.ui.RulesScreen
import com.kingofthebeasts.app.ui.SettingsScreen
import com.kingofthebeasts.app.ui.theme.KingOfTheBeastsTheme
import com.kingofthebeasts.core.ai.Difficulty
import com.kingofthebeasts.core.data.StarterDecks
import com.kingofthebeasts.core.deck.Deck
import com.kingofthebeasts.core.game.SavedBattle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppSettings.load(this)
        GameAudio.init(this)
        setContent { KingOfTheBeastsTheme { App() } }
    }

    override fun onStart() {
        super.onStart()
        GameAudio.resume()
    }

    override fun onStop() {
        GameAudio.pause()
        super.onStop()
    }
}

private sealed interface Screen {
    data object Menu : Screen
    data object Decks : Screen
    data class Builder(val deck: Deck?) : Screen
    /** A starter deck, read-only. */
    data class ViewDeck(val deck: Deck) : Screen
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
    data object Settings : Screen
    /** Finding a friend, on the same Wi-Fi or over the internet. */
    data object Friends : Screen
    /** The current game of the online session. */
    data object Online : Screen
    /** The guided tutorial battle; [run] keeps each attempt fresh. */
    data class Tutorial(val run: Long) : Screen
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
    var playMenu by remember { mutableStateOf(false) }

    // Online play: the lobby owns the connection; a new game (or rematch) opens the battle.
    val scope = rememberCoroutineScope()
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val lobby = remember { FriendsLobby(context.applicationContext, scope, appVersion) { AppSettings.relayUrl } }
    val session = lobby.session
    val onlineGame = session?.game?.collectAsState()?.value
    LaunchedEffect(onlineGame) {
        if (onlineGame != null) screen = Screen.Online
    }
    fun startTutorial() {
        AppSettings.markTutorialOffered()
        screen = Screen.Tutorial(System.nanoTime())
    }

    fun leaveOnline() {
        lobby.reset()
        playMenu = true
        screen = Screen.Menu
    }

    fun saveDecks(list: List<Deck>) {
        decks = list
        repo.save(list)
    }

    fun newBattle(player: Deck, opponent: Deck?, difficulty: Difficulty) {
        val foe = opponent ?: StarterDecks.all.filter { it != player }.random()
        storeBattle(null) // a new battle replaces the one in progress
        screen = Screen.Battle(player, foe, difficulty, System.nanoTime())
    }

    // Menu music everywhere except in battle; each new battle starts its theme from the top.
    val battleKey = (screen as? Screen.Battle)?.seed ?: (screen as? Screen.Tutorial)?.run
        ?: onlineGame?.start?.seed?.takeIf { screen == Screen.Online }
    LaunchedEffect(battleKey) {
        if (battleKey == null) GameAudio.music(Music.MENU) else {
            GameAudio.music(null)
            GameAudio.music(Music.BATTLE)
        }
    }
    // Keep the screen awake during battles if the player wants that.
    val view = LocalView.current
    val awake = (screen is Screen.Battle || screen == Screen.Online) && AppSettings.keepScreenOn
    DisposableEffect(awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }

    BackHandler(enabled = screen != Screen.Menu && screen !is Screen.Battle && screen != Screen.Online && screen !is Screen.Tutorial) {
        if (screen == Screen.Friends) lobby.reset()
        screen = if (screen is Screen.Builder || screen is Screen.ViewDeck) Screen.Decks else Screen.Menu
    }

    when (val s = screen) {
        Screen.Menu -> MenuScreen(
            onPlay = { screen = Screen.Setup },
            onDecks = { screen = Screen.Decks },
            onRules = { screen = Screen.Rules },
            onSettings = { screen = Screen.Settings },
            playMenu = playMenu,
            onPlayMenu = { playMenu = it },
            onFriends = { screen = Screen.Friends },
            friendsLabel = lobby.saved?.let { "Unfinished battle with ${it.peerName}" },
            onTutorial = ::startTutorial,
            offerTutorial = !AppSettings.tutorialOffered,
            onTutorialOffered = AppSettings::markTutorialOffered,
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
            onView = { screen = Screen.ViewDeck(it) },
        )
        is Screen.ViewDeck -> DeckViewScreen(
            deck = s.deck,
            onBack = { screen = Screen.Decks },
            onCopy = { screen = Screen.Builder(s.deck.copy(name = s.deck.name + " (copy)")) },
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
        Screen.Rules -> RulesScreen(onBack = { screen = Screen.Menu }, onTutorial = ::startTutorial)
        is Screen.Tutorial -> {
            val vm: GameViewModel = viewModel(key = "tutorial-${s.run}") {
                GameViewModel(
                    com.kingofthebeasts.core.tutorial.Tutorial.playerDeck, com.kingofthebeasts.core.tutorial.Tutorial.rivalDeck,
                    Difficulty.EASY, 0L, tutorial = true,
                )
            }
            GameScreen(vm, onExit = { screen = Screen.Menu }, onRematch = ::startTutorial)
        }
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Menu })
        Screen.Friends -> FriendsScreen(lobby, decks, onBack = { screen = Screen.Menu })
        Screen.Online -> {
            val s = session
            val g = onlineGame
            if (s == null || g == null) {
                LaunchedEffect(Unit) { screen = Screen.Friends }
            } else {
                val status by s.status.collectAsState()
                val rematch by s.rematch.collectAsState()
                // A rejoined battle replays the saved moves first; each session gets fresh screens.
                val vm: GameViewModel = viewModel(key = "online-${System.identityHashCode(s)}-${g.start.game}") {
                    GameViewModel(
                        g.start.hostDeck, g.start.guestDeck, Difficulty.EASY, g.start.seed, resume = g.replay,
                        remote = g, human = g.human, names = listOf(g.start.hostName, g.start.guestName),
                    )
                }
                GameScreen(
                    vm,
                    onExit = ::leaveOnline,
                    onRematch = { s.requestRematch() },
                    onForfeit = ::leaveOnline,
                    online = OnlineInfo(
                        ended = (status as? OnlineSession.Status.Ended)?.reason,
                        rematchMine = rematch.mine,
                        rematchTheirs = rematch.theirs,
                        reconnecting = status == OnlineSession.Status.Reconnecting,
                        onLeaveForNow = {
                            lobby.leaveForNow()
                            playMenu = true
                            screen = Screen.Menu
                        },
                    ),
                )
            }
        }
    }
}
