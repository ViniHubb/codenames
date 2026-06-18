package com.codenames.host.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codenames.host.MainActivity
import com.codenames.host.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that hosts the embedded [GameServer]. Running in the foreground keeps the
 * server alive while the screen is locked or the app is backgrounded.
 */
class ServerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForegroundCompat()

        val server = GameServer(assets)
        server.start()
        ServerState.server = server
        ServerState.url.value = "http://${NetworkUtils.localIp() ?: "127.0.0.1"}:${server.port}"
        ServerState.running.value = true

        scope.launch {
            server.playerCount.collect { ServerState.playerCount.value = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        ServerState.server?.stop()
        ServerState.server = null
        ServerState.running.value = false
        ServerState.playerCount.value = 0
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1
        private const val CHANNEL_ID = "codenames_server"
    }
}
