package com.codenames.host.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.codenames.host.server.GameServer

/**
 * In-app host control board. Reuses the same web client served to players, but with role=host,
 * which enables tap-to-reveal. Loads over loopback so it never leaves the device.
 */
@Composable
fun HostBoardScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = onBack) { Text("← Voltar") }
            Text("Tabuleiro (Host)", style = MaterialTheme.typography.titleMedium)
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                @SuppressLint("SetJavaScriptEnabled")
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl("http://127.0.0.1:${GameServer.DEFAULT_PORT}/?role=host")
                }
            }
        )
    }
}
