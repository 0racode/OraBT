package com.pastq.cbt

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.pastq.cbt.data.LiveManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Live-session notices: channel, token registration and showing a notice while the app is open. */
object Push {
    const val CHANNEL = "live"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Live tests", NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = "Tells you when a live test is created or starts" },
            )
        }
        // Register this phone with the server (at most every 12 hours; the server only keeps the token).
        val prefs = context.getSharedPreferences("push", Context.MODE_PRIVATE)
        val last = prefs.getLong("registered_at", 0L)
        if (System.currentTimeMillis() - last < 12 * 3600_000L) return
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                register(context, token)
            }
        }
    }

    fun register(context: Context, token: String) {
        scope.launch {
            LiveManager.registerToken(token)
            context.getSharedPreferences("push", Context.MODE_PRIVATE)
                .edit().putLong("registered_at", System.currentTimeMillis()).apply()
        }
    }

    /** Shows a notice while the app is in front (the system shows them itself otherwise). */
    fun show(context: Context, title: String, body: String, code: String?) {
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("kind", "live")
            if (!code.isNullOrBlank()) putExtra("code", code)
        }
        val pi = PendingIntent.getActivity(
            context, (System.currentTimeMillis() % 100000).toInt(), open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((System.currentTimeMillis() % 100000).toInt(), n) }
    }
}
