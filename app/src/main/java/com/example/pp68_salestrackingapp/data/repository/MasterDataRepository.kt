package com.example.pp68_salestrackingapp.data.repository

import com.example.pp68_salestrackingapp.data.model.LossReasonMaster
import com.example.pp68_salestrackingapp.data.model.ProjectStageMaster
import com.example.pp68_salestrackingapp.data.remote.ApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// W5a: master data ของ stage/เหตุผล — fetch-on-demand ไม่มี cache เหมือน getMasterActivities()
// (ActivityRepository) ค่า fallback เมื่อ offline/error อยู่ที่ utils/ProjectStages.kt, LossReasons.kt
@Singleton
class MasterDataRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getProjectStages(): List<ProjectStageMaster> = withContext(Dispatchers.IO) {
        try {
            val resp = apiService.getProjectStageMasters()
            if (resp.isSuccessful && resp.body() != null) {
                resp.body()!!.map {
                    ProjectStageMaster(
                        code = it.code,
                        label = it.label,
                        sequence = it.sequence,
                        probabilityPct = it.probabilityPct,
                        isClosed = it.isClosed,
                        isWon = it.isWon
                    )
                }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun getLossReasons(): List<LossReasonMaster> = withContext(Dispatchers.IO) {
        try {
            val resp = apiService.getLossReasonMasters()
            if (resp.isSuccessful && resp.body() != null) {
                resp.body()!!.map { LossReasonMaster(code = it.code, label = it.label, sequence = it.sequence) }
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }
}
