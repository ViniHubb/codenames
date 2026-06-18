package com.codenames.host.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codenames.host.server.NetworkUtils
import com.codenames.host.server.ServerState

@Composable
fun AppRoot() {
    var showBoard by rememberSaveable { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (showBoard) {
            HostBoardScreen(onBack = { showBoard = false })
        } else {
            StartScreen(onOpenBoard = { showBoard = true })
        }
    }
}

@Composable
fun StartScreen(onOpenBoard: () -> Unit) {
    val url by ServerState.url.collectAsStateWithLifecycle()
    val players by ServerState.playerCount.collectAsStateWithLifecycle()
    val running by ServerState.running.collectAsStateWithLifecycle()

    val qr = remember(url) {
        url?.let { runCatching { NetworkUtils.qrBitmap(it) }.getOrNull() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Codenames", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            if (running) "Servidor ativo" else "Iniciando servidor…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        if (qr != null) {
            Image(
                bitmap = qr.asImageBitmap(),
                contentDescription = "QR code de conexão",
                modifier = Modifier.size(220.dp)
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            url ?: "—",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            "Conecte pelo navegador na mesma rede Wi-Fi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        Text(
            "Jogadores conectados: $players",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onOpenBoard,
            enabled = running,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abrir tabuleiro (Host)") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { ServerState.server?.newGame() },
            enabled = running,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Nova partida") }
    }
}
