package com.pastq.cbt

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PushService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Push.register(applicationContext, token)
    }

    // Called only while the app is open; when it is closed the system shows the notice by itself.
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "PastQ CBT"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        Push.show(applicationContext, title, body, message.data["code"])
    }
}
