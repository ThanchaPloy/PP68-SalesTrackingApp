package com.example.pp68_salestrackingapp.utils

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.pp68_salestrackingapp.receiver.AppointmentAlarmReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AppointmentAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleAlarm(
        activityId: String,
        companyName: String,
        topic: String,
        plannedDateStr: String,
        plannedTimeStr: String,
        leadMinutesList: List<Int> = listOf(30, 15, 0)
    ) {
        try {
            if (plannedDateStr.isBlank()) return
            val date = LocalDate.parse(plannedDateStr.take(10))
            val time = if (!plannedTimeStr.isNullOrBlank() && plannedTimeStr.contains(":")) {
                LocalTime.parse(plannedTimeStr.trim().take(5))
            } else {
                LocalTime.of(9, 0)
            }

            val appointmentDateTime = LocalDateTime.of(date, time)

            leadMinutesList.forEach { leadMinutesBefore ->
                val triggerDateTime = appointmentDateTime.minusMinutes(leadMinutesBefore.toLong())
                val triggerMillis = triggerDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                if (triggerMillis > System.currentTimeMillis()) {
                    val intent = Intent(context, AppointmentAlarmReceiver::class.java).apply {
                        putExtra("activity_id", activityId)
                        putExtra("company_name", companyName)
                        putExtra("topic", topic)
                        putExtra("planned_time", plannedTimeStr)
                        putExtra("lead_minutes", leadMinutesBefore)
                    }

                    val requestCode = (activityId + leadMinutesBefore).hashCode()
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                        } else {
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
                    }
                    Log.d("AlarmScheduler", "ตั้งเวลาแจ้งเตือนนัดหมาย $activityId สำเร็จ ที่เวลา $triggerDateTime (ล่วงหน้า $leadMinutesBefore นาที)")
                } else {
                    Log.d("AlarmScheduler", "เวลาเตือนย้อนหลังไปแล้ว ไม่ต้องตั้ง Alarm สำหรับ $activityId ล่วงหน้า $leadMinutesBefore นาที (ขณะนี้ ${LocalDateTime.now()})")
                }
            }

        } catch (e: Exception) {
            Log.e("AlarmScheduler", "เกิดข้อผิดพลาดในการตั้งเวลาแจ้งเตือน: ${e.message}")
        }
    }

    fun cancelAlarm(activityId: String, leadMinutesList: List<Int> = listOf(30, 15, 0)) {
        leadMinutesList.forEach { leadMinutesBefore ->
            val intent = Intent(context, AppointmentAlarmReceiver::class.java)
            val requestCode = (activityId + leadMinutesBefore).hashCode()
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                Log.d("AlarmScheduler", "ยกเลิกการตั้งเวลาแจ้งเตือน $activityId ล่วงหน้า $leadMinutesBefore นาที สำเร็จ")
            }
        }
    }
}
