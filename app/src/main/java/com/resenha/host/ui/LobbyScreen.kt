package com.resenha.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resenha.host.server.ServerState

/** Game picker. This phone is the master; everyone else joins whichever game is picked here. */
@Composable
fun LobbyScreen(onPick: (CatalogGame) -> Unit) {
    val running by ServerState.running.collectAsStateWithLifecycle()
    val players by ServerState.playerCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Resenha", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            if (running) "Escolha o jogo da mesa" else "Iniciando servidor…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        CatalogGame.entries.forEach { game ->
            GameCard(game = game, enabled = running, onClick = { onPick(game) })
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Jogadores conectados: $players",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** One card per game. Only [CatalogGame.accent] varies: the spine, the tint and the tagline. */
@Composable
private fun GameCard(game: CatalogGame, enabled: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = game.accent.copy(alpha = 0.14f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        // Min intrinsic height, else the spine's fillMaxHeight collapses to zero.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .fillMaxHeight()
                    .background(game.accent)
            )
            Column(modifier = Modifier.padding(20.dp)) {
                Text(game.title, style = MaterialTheme.typography.titleLarge)
                Text(
                    game.tagline,
                    style = MaterialTheme.typography.labelMedium,
                    color = game.accent
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    game.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
