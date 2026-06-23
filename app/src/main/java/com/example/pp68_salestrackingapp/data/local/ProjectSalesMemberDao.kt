package com.example.pp68_salestrackingapp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pp68_salestrackingapp.data.model.ProjectSalesMember

@Dao
interface ProjectSalesMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<ProjectSalesMember>)

    @Query("DELETE FROM project_sales_member WHERE project_code = :projectId")
    suspend fun deleteByProject(projectId: String)

    @Query("SELECT emp_code FROM project_sales_member WHERE project_code = :projectId")
    suspend fun getMemberIdsByProject(projectId: String): List<String>
}
