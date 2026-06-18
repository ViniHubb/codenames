package com.codenames.host.server

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    /** Best-effort local Wi-Fi IPv4 address (e.g. 192.168.x.x), or null if none found. */
    fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList().asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .firstOrNull { it.startsWith("192.168.") || it.startsWith("10.") || it.startsWith("172.") }
    } catch (e: Exception) {
        null
    }

    /** Renders [text] as a QR code bitmap for display on the start screen. */
    fun qrBitmap(text: String, size: Int = 600): Bitmap {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bmp
    }
}
