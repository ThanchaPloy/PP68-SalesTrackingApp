package com.example.pp68_salestrackingapp.ui.viewmodels.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.repository.DashboardRepository
import com.example.pp68_salestrackingapp.data.repository.DashboardSummary
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val dashboardRepository: DashboardRepository,
    private val customerRepo: CustomerRepository,
    private val projectRepo: ProjectRepository,
    private val activityRepo: ActivityRepository,
    private val authRepo: AuthRepository
) : ViewModel() {

    // สถิติแบบ Realtime (ถ้ามีการเพิ่มลูกค้าในหน้าอื่น ตัวเลขหน้านี้จะเด้งขึ้นเอง!)
    val summary: StateFlow<DashboardSummary> = dashboardRepository.getDashboardSummary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardSummary(0, 0, 0)
        )

    init {
        refreshAllData()
    }

    // ฟังก์ชันสำหรับหน้า Dashboard ให้ดึงข้อมูลล่าสุดจาก API ทุกตาราง
    fun refreshAllData() {
        val userId = authRepo.currentUser()?.userId ?: return
        viewModelScope.launch {
            // โหลดขนานกันแบบ Asynchronous เพื่อความรวดเร็ว
            launch { customerRepo.refreshCustomers(userId) }
            launch { projectRepo.refreshProjects(userId) }
            launch { activityRepo.refreshActivities(userId) }
        }
    }
}
