package com.example.pp68_salestrackingapp.utils

import android.content.Context
import androidx.work.*
import com.example.pp68_salestrackingapp.worker.SyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * จองคิวงาน Sync ข้อมูล โดยจะทำงานเมื่อโทรศัพท์เชื่อมต่ออินเทอร์เน็ตเท่านั้น
     */
    fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED) // รอจนกว่าจะมีเน็ต
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                OneTimeWorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("data_sync_tag")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "DataSyncWorkName",
            ExistingWorkPolicy.REPLACE, // ถ้ามีงานเดิมค้างอยู่ ให้ทับด้วยงานใหม่ที่มีข้อมูลล่าสุด
            syncRequest
        )
    }
}
