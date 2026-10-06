package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.AppointmentDraft
import kotlinx.coroutines.flow.Flow

/**
 * ทุก query รับ ownerKey โดยไม่มีข้อยกเว้น (แผนงาน C.4)
 *
 * ไม่มีเมธอดที่คืนฉบับร่างของทุกบัญชี แม้แต่ตัวเดียว เพราะถ้ามี วันหนึ่งจะมีคนเรียกมันจาก UI
 * แล้วฉบับร่างของเซลส์คนก่อนจะโผล่ให้คนถัดไปเห็นบนเครื่องที่ใช้ร่วมกัน
 */
@Dao
interface AppointmentDraftDao {

    @Query(
        """
        SELECT * FROM appointment_draft
        WHERE owner_key = :ownerKey
        ORDER BY updated_at DESC
        """
    )
    fun observeByOwner(ownerKey: String): Flow<List<AppointmentDraft>>

    @Query("SELECT * FROM appointment_draft WHERE owner_key = :ownerKey AND draft_id = :draftId")
    suspend fun getById(ownerKey: String, draftId: String): AppointmentDraft?

    @Query("SELECT COUNT(*) FROM appointment_draft WHERE owner_key = :ownerKey")
    suspend fun countForOwner(ownerKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: AppointmentDraft)

    @Query("DELETE FROM appointment_draft WHERE owner_key = :ownerKey AND draft_id = :draftId")
    suspend fun deleteById(ownerKey: String, draftId: String)

    /** เทียบสตริง ISO-8601 ตรง ๆ ได้ เพราะรูปแบบนี้เรียงตามตัวอักษรแล้วตรงกับเรียงตามเวลา */
    @Query("DELETE FROM appointment_draft WHERE owner_key = :ownerKey AND expires_at <= :nowIso")
    suspend fun deleteExpired(ownerKey: String, nowIso: String): Int

    @Query("DELETE FROM appointment_draft WHERE owner_key = :ownerKey")
    suspend fun deleteForOwner(ownerKey: String): Int
}
