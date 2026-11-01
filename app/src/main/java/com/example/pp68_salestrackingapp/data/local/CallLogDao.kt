package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.CallLogSync

@Dao
interface CallLogDao {
    // IGNORE กันแถวซ้ำเวลาอ่าน ContentResolver ซ้อนกันสองรอบ (log_id คำนวณจาก
    // เบอร์+เวลาเริ่มเสมอ ดู CallLogRepository — สายเดียวกันได้ log_id เดียวกันทุกครั้ง)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(logs: List<CallLogSync>)

    @Query("SELECT * FROM call_log_sync WHERE is_synced = 0")
    suspend fun getUnsyncedCallLogs(): List<CallLogSync>

    @Query("SELECT log_id FROM call_log_sync WHERE is_synced = 0")
    suspend fun getUnsyncedCallLogIds(): List<String>

    @Query("UPDATE call_log_sync SET is_synced = 1 WHERE log_id = :logId")
    suspend fun markSynced(logId: String)
}
