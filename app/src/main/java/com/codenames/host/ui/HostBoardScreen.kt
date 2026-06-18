package com.codenames.host.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.codenames.host.server.GameServer

/**
 * In-app host control board. Reuses the same web client served to players, but with role=host,
 * which enables tap-to-reveal. Loads over loopback so it never leaves the device. The web UI
 * draws its own back button (top-left) and calls back into [onBack] through [WebBridge].
 */
@Composable
fun HostBoardScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    // The board is much more readable in landscape (5x5 grid of words). Force landscape while
    // this screen is shown and restore the previous orientation when leaving.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previous = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation =
                previous ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            @SuppressLint("SetJavaScriptEnabled")
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
                // Required so JS dialogs (e.g. the "Nova partida?" confirm) actually appear.
                webChromeClient = WebChromeClient()
                // Lets the web back button trigger the native navigation.
                addJavascriptInterface(WebBridge(onBack), "AndroidHost")
                loadUrl("http://127.0.0.1:${GameServer.DEFAULT_PORT}/?role=host")
            }
        }
    )
}

/** Bridge exposed to the host web page as `AndroidHost`. */
private class WebBridge(private val onBack: () -> Unit) {
    @JavascriptInterface
    fun back() {
        // JS interface calls arrive on a binder thread; hop to the main thread for Compose.
        Handler(Looper.getMainLooper()).post(onBack)
    }
}

/** Unwraps the Activity from a (possibly wrapped) Compose Context. */
private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
