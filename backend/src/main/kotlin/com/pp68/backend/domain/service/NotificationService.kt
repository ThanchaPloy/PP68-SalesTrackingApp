package com.pp68.backend.domain.service

import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class NotificationService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val projectId = System.getenv("FIREBASE_PROJECT_ID") ?: ""
    private val schedulerSecret = System.getenv("SCHEDULER_SECRET") ?: ""
    private val httpClient = HttpClient.newHttpClient()

    fun validateSecret(secret: String?) =
        schedulerSecret.isEmpty() || secret == schedulerSecret

    suspend fun sendUpcomingReminders(): Int {
        if (projectId.isEmpty()) {
            log.warn("FIREBASE_PROJECT_ID not configured — skipping reminders")
            return 0
        }

        val thai = ZoneId.of("Asia/Bangkok")
        val now = ZonedDateTime.now(thai)
        val today = now.toLocalDate().toString()
        val nowTime = now.toLocalTime().truncatedTo(ChronoUnit.SECONDS)
        val futureTime = nowTime.plusMinutes(30)

        data class Reminder(val id: String, val topic: String, val time: String, val token: String)

        val reminders: List<Reminder> = newSuspendedTransaction(Dispatchers.IO) {
            exec("""
                SELECT a.appointment_id, a.topic, a.planned_time, u.fcm_token
                FROM appointment a
                JOIN "user" u ON u.user_id = a.user_id
                WHERE a.planned_date = '$today'
                  AND a.plan_status = 'planned'
                  AND a.planned_time IS NOT NULL
                  AND u.fcm_token IS NOT NULL
                  AND a.planned_time::time >= '$nowTime'::time
                  AND a.planned_time::time <  '$futureTime'::time
            """.trimIndent()) { rs ->
                buildList {
                    while (rs.next()) {
                        add(Reminder(
                            id    = rs.getString("appointment_id"),
                            topic = rs.getString("topic") ?: "นัดหมาย",
                            time  = rs.getString("planned_time")?.take(5) ?: "",
                            token = rs.getString("fcm_token")
                        ))
                    }
                }
            } ?: emptyList()
        }

        log.info("Found ${reminders.size} upcoming appointments for $today $nowTime–$futureTime")

        var sent = 0
        for (r in reminders) {
            try {
                sendFcm(
                    token      = r.token,
                    title      = "แจ้งเตือนนัดหมาย",
                    body       = "${r.topic} — เวลา ${r.time} น.",
                    activityId = r.id
                )
                sent++
                log.info("FCM sent: ${r.id}")
            } catch (e: Exception) {
                log.error("FCM failed for ${r.id}: ${e.message}")
            }
        }
        return sent
    }

    private fun sendFcm(token: String, title: String, body: String, activityId: String) {
        val accessToken = getAccessToken()
        fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val json = """
            {
              "message": {
                "token": "${esc(token)}",
                "notification": { "title": "${esc(title)}", "body": "${esc(body)}" },
                "data": { "activity_id": "${esc(activityId)}" },
                "android": { "priority": "high" }
              }
            }
        """.trimIndent()

        val req = HttpRequest.newBuilder()
            .uri(URI("https://fcm.googleapis.com/v1/projects/$projectId/messages:send"))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json; charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()

        val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw Exception("FCM HTTP ${resp.statusCode()}: ${resp.body()}")
        }
    }

    private fun getAccessToken(): String {
        val base = GoogleCredentials.getApplicationDefault()
        val creds = if (base.createScopedRequired())
            base.createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
        else base
        creds.refresh()
        return creds.accessToken.tokenValue
    }
}
