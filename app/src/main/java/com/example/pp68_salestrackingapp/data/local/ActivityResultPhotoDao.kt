package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.ActivityResultPhoto

@Dao
interface ActivityResultPhotoDao {
    @Query("SELECT * FROM activity_result_photo WHERE result_id = :resultId ORDER BY photo_order")
    suspend fun getPhotosByResultId(resultId: String): List<ActivityResultPhoto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhotos(photos: List<ActivityResultPhoto>)

    @Query("DELETE FROM activity_result_photo WHERE result_id = :resultId")
    suspend fun deletePhotosByResultId(resultId: String)

    @Query("UPDATE activity_result_photo SET result_id = :newId WHERE result_id = :oldId")
    suspend fun updateResultId(oldId: String, newId: String)
}
