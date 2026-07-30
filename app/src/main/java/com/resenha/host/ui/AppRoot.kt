package com.resenha.host.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.resenha.host.server.ServerState

/** The three screens of the host app. Small enough that a `when` beats a navigation library. */
private enum class Screen { LOBBY, START, BOARD }

@Composable
fun AppRoot() {
    var screen by rememberSaveable { mutableStateOf(Screen.LOBBY) }
    var gameId by rememberSaveable { mutableStateOf(CatalogGame.CODENAMES.id) }
    val game = CatalogGame.from(gameId)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (screen) {
            Screen.LOBBY -> LobbyScreen(
                onPick = { picked ->
                    gameId = picked.id
                    // Switch here, not on "abrir mesa", so connected browsers follow right away.
                    ServerState.server?.selectGame(picked.id)
                    screen = Screen.START
                }
            )

            Screen.START -> {
                BackHandler { screen = Screen.LOBBY }
                StartScreen(
                    game = game,
                    onOpenBoard = { screen = Screen.BOARD },
                    onBack = { screen = Screen.LOBBY }
                )
            }

            Screen.BOARD -> HostBoardScreen(
                game = game,
                onBack = { screen = Screen.START }
            )
        }
    }
}
