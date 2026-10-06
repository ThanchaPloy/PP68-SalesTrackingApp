package com.example.pp68_salestrackingapp.worker

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pp68_salestrackingapp.MainActivity
import com.example.pp68_salestrackingapp.R
import com.example.pp68_salestrackingapp.di.TokenManager
import com.example.pp68_salestrackingapp.data.local.SyncConflictDao
import com.example.pp68_salestrackingapp.data.repository.syncAccountKey
import com.example.pp68_salestrackingapp.utils.NotificationChannels
import com.example.pp68_salestrackingapp.utils.SyncManager
import com.example.pp68_salestrackingapp.utils.SyncStatusNavigation
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.ZoneId

internal object EndOfDayReminderPolicy {
    fun shouldNotify(nowBangkok: java.time.ZonedDateTime, pendingCount: Int, lastNotifiedDate: String?): Boolean {
        if (pendingCount <= 0 || nowBangkok.hour < 22) return false
        return lastNotifiedDate != nowBangkok.toLocalDate().toString()
    }
}

@HiltWorker
class EndOfDaySyncReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
    private val tokenManager: TokenManager,
    private val syncConflictDao: SyncConflictDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (tokenManager.getToken().isNullOrBlank()) return Result.success()
        val pending = syncManager.pendingSummary()
        val rejected = syncManager.rejectedSummary()
        val conflictCount = tokenManager.getUserData()?.userId?.let { userId ->
            syncConflictDao.countForAccount(syncAccountKey(userId))
        } ?: 0
        val count = pending.sumOf { it.second } + rejected.size + conflictCount
        val now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Bangkok"))
        val date = now.toLocalDate().toString()
        val prefs = context.getSharedPreferences("sync_reminder", Context.MODE_PRIVATE)
        if (!EndOfDayReminderPolicy.shouldNotify(now, count, prefs.getString("last_notified_date", null))) {
            return Result.success()
        }

        NotificationChannels.ensureCreated(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(SyncStatusNavigation.EXTRA_OPEN_SYNC_STATUS, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 2200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = if (rejected.isEmpty() && conflictCount == 0) {
            "มีข้อมูล $count รายการรอส่ง กรุณาเปิดแอปและตรวจสอบการเชื่อมต่อ"
        } else {
            "มีข้อมูล $count รายการที่ยังไม่เรียบร้อย โดย ${rejected.size + conflictCount} รายการต้องตรวจสอบ"
        }
        val notification = NotificationCompat.Builder(context, NotificationChannels.PENDING_SYNC_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("ข้อมูลวันนี้ยังส่งไม่ครบ")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify("end-of-day-sync".hashCode(), notification)
        prefs.edit().putString("last_notified_date", date).apply()
        return Result.success()
    }
}
