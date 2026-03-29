package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.local.BranchDao
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BranchRepository @Inject constructor(
    private val dao: BranchDao,
    private val api: ApiService
) {
    // ✅ เปลี่ยนจาก Flow เป็น suspend fun
    suspend fun observeBranches(): List<Branch> {
        return withContext(Dispatchers.IO) {
            dao.getAll()
        }
    }

    suspend fun getBranches(): kotlin.Result<List<Branch>> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getBranches()
                if (response.isSuccessful && response.body() != null) {
                    kotlin.Result.success(response.body()!!)
                } else {
                    kotlin.Result.failure(Exception("API Error: ${response.code()} ${response.message()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    suspend fun syncFromRemote(): kotlin.Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val response = api.getBranches()
                if (response.isSuccessful) {
                    val branches = response.body() ?: emptyList()
                    dao.upsertAll(branches)
                    kotlin.Result.success(Unit)
                } else {
                    kotlin.Result.failure(Exception("API Error: ${response.code()} ${response.message()}"))
                }
            } catch (e: Exception) {
                kotlin.Result.failure(e)
            }
        }
    }

    // ✅ เพิ่ม — ดึง branch name จาก local DB
    suspend fun getBranchById(branchId: String): Branch? {
        return withContext(Dispatchers.IO) {
            dao.getById(branchId)
        }
    }
}
