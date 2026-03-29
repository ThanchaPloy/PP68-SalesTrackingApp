package com.example.pp68_salestrackingapp.data.remote

import com.google.firebase.Firebase
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.database
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseRealtimeService @Inject constructor() {

    private val db = Firebase.database(
        "https://algebraic-ratio-490214-r0-default-rtdb.asia-southeast1.firebasedatabase.app"
    ).reference

    // ✅ เขียน project status update ลง Firebase
    // path: /project_updates/{projectId}
    suspend fun updateProjectStatus(
        projectId:   String,
        newStatus:   String,
        projectName: String,
        updatedBy:   String
    ) {
        try {
            val data = mapOf(
                "project_id"   to projectId,
                "project_name" to projectName,
                "new_status"   to newStatus,
                "updated_by"   to updatedBy,
                "updated_at"   to java.time.Instant.now().toString()
            )
            db.child("project_updates")
                .child(projectId)
                .setValue(data)
                .await()
        } catch (e: Exception) {
            // ถ้า Firebase fail ไม่กระทบ main flow
            android.util.Log.w("Firebase", "updateProjectStatus failed: ${e.message}")
        }
    }

    // ✅ เขียน latest updates สำหรับ Web Dashboard แสดง feed
    suspend fun pushStatusChangeEvent(
        projectId:   String,
        projectName: String,
        oldStatus:   String,
        newStatus:   String,
        updatedBy:   String
    ) {
        try {
            val data = mapOf(
                "project_id"   to projectId,
                "project_name" to projectName,
                "old_status"   to oldStatus,
                "new_status"   to newStatus,
                "updated_by"   to updatedBy,
                "timestamp"    to java.time.Instant.now().toEpochMilli()
            )
            // push() สร้าง key ใหม่อัตโนมัติ เช่น -NxXXXXXXXX
            db.child("status_change_log")
                .push()
                .setValue(data)
                .await()
        } catch (e: Exception) {
            android.util.Log.w("Firebase", "pushStatusChangeEvent failed: ${e.message}")
        }
    }
}