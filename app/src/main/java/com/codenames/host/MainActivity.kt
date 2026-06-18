package com.codenames.host

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.content.ContextCompat
import com.codenames.host.server.ServerService
import com.codenames.host.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startServer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermissionThenStart()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppRoot()
            }
        }
    }

    private fun ensureNotificationPermissionThenStart() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startServer()
        }
    }

    private fun startServer() {
        ContextCompat.startForegroundService(this, Intent(this, ServerService::class.java))
    }
}
