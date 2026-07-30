package com.resenha.host.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.resenha.host.server.NetworkUtils
import com.resenha.host.server.ServerState

/** Connection screen for the picked [game]: QR, address, player count and the themed modes. */
@Composable
fun StartScreen(game: CatalogGame, onOpenBoard: () -> Unit, onBack: () -> Unit) {
    val url by ServerState.url.collectAsStateWithLifecycle()
    val players by ServerState.playerCount.collectAsStateWithLifecycle()
    val running by ServerState.running.collectAsStateWithLifecycle()
    val mode by game.selectedModeFlow.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val copyUrl: () -> Unit = {
        url?.let {
            clipboard.setText(AnnotatedString(it))
            Toast.makeText(context, "IP copiado", Toast.LENGTH_SHORT).show()
        }
        Unit
    }

    val qr = remember(url) {
        url?.let { runCatching { NetworkUtils.qrBitmap(it) }.getOrNull() }
    }
    val modeLabels = game.modeLabels

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(game.title, style = MaterialTheme.typography.headlineLarge)
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
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable(enabled = url != null) { copyUrl() }
        )
        Text(
            "Toque no IP para copiar • conecte na mesma rede Wi-Fi",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(24.dp))
        Text("Jogadores conectados: $players", style = MaterialTheme.typography.titleMedium)

        Spacer(Modifier.height(24.dp))
        Text(
            "Modo",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modeLabels.forEachIndexed { index, label ->
                SegmentedButton(
                    selected = mode.ordinal == index,
                    onClick = { game.selectMode(index) },
                    enabled = running,
                    shape = SegmentedButtonDefaults.itemShape(index, modeLabels.size),
                    icon = {}
                ) { Text(label, maxLines = 1) }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onOpenBoard,
            enabled = running,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Abrir mesa") }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Trocar de jogo")
        }
    }
}
