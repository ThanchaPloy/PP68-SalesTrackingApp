package com.pp68.backend.application.routes

import com.pp68.backend.data.repository.AppointmentRepositoryImpl
import com.pp68.backend.data.service.FcmService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.ktor.ext.inject

fun Route.notificationRoutes() {
    val appointmentRepo: AppointmentRepositoryImpl by inject()
    val fcmService: FcmService by inject()

    post("/send-appointment-reminders") {
        val secret = System.getenv("SCHEDULER_SECRET")
        val headerSecret = call.request.headers["X-Scheduler-Secret"]
        if (secret == null || headerSecret != secret) {
            return@post call.respond(HttpStatusCode.Forbidden)
        }

        val upcoming = appointmentRepo.findUpcomingForReminders(withinMinutes = 60)
        var sentCount = 0
        for ((appointment, token) in upcoming) {
            if (token == null) continue
            val success = withContext(Dispatchers.IO) {
                fcmService.sendNotification(
                    token = token,
                    title = "แจ้งเตือนนัดหมาย",
                    body  = "คุณมีนัดหมาย \"${appointment.topic ?: appointment.activityType}\" เร็วๆ นี้",
                    data  = mapOf("appointment_id" to appointment.appointmentId)
                )
            }
            if (success) sentCount++
        }

        call.respond(HttpStatusCode.OK, mapOf("checked" to upcoming.size, "sent" to sentCount))
    }
}
