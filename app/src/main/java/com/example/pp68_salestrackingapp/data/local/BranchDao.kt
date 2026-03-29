package com.example.pp68_salestrackingapp.data.local

import androidx.room.*
import com.example.pp68_salestrackingapp.data.model.Branch

@Dao
interface BranchDao {

    @Query("SELECT * FROM branch ORDER BY region, branch_name")
    suspend fun getAll(): List<Branch>

    @Query("SELECT * FROM branch WHERE region = :region ORDER BY branch_name")
    suspend fun getByRegion(region: String): List<Branch>

    @Query("SELECT * FROM branch WHERE branch_id = :branchId LIMIT 1")
    suspend fun getById(branchId: String): Branch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(branches: List<Branch>)

    @Upsert
    suspend fun upsertAll(branches: List<Branch>)

    @Query("DELETE FROM branch")
    suspend fun deleteAll()

    @Query("SELECT * FROM branch")
    suspend fun getAllBranches(): List<Branch>
}