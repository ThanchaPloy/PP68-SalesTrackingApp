package com.pp68.backend.data.service

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory

class FcmService {
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        if (FirebaseApp.getApps().isEmpty()) {
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(System.getenv("FIREBASE_PROJECT_ID")?.trim())
                .build()
            FirebaseApp.initializeApp(options)
        }
    }

    fun sendNotification(
        token: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Boolean {
        return try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data)
                .build()
            FirebaseMessaging.getInstance().send(message)
            true
        } catch (e: Exception) {
            // individual token failure must not crash the whole batch
            log.warn("FCM send failed for token …${token.takeLast(8)}: ${e.message}")
            false
        }
    }
}
