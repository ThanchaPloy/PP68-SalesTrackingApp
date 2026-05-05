package com.example.pp68_salestrackingapp.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import java.time.ZoneId

data class PipelineStageCount(val stage: String, val count: Int, val totalValue: Double)
data class OpportunityGroup(val score: String, val count: Int, val totalValue: Double)

data class StatsUiState(
    // Weekly
    val weeklyNewLeads:     Int    = 0,
    val weeklyNewProjects:  Int    = 0,
    val weeklyVisitCount:   Int    = 0,

    // Monthly
    val monthlyClosedSales: Double = 0.0,
    val totalActiveValue:   Double = 0.0,
    val totalProjectValue:  Double = 0.0,
    val monthlyNewLeads:    Int    = 0,
    val activeProjects:     Int    = 0,
    val closingThisMonth:   Int    = 0,

    // Pipeline
    val pipelineStages:     List<PipelineStageCount> = emptyList(),

    // Opportunity HOT/WARM/COLD
    val opportunityGroups:  List<OpportunityGroup>   = emptyList(),

    val isLoading: Boolean = false,
    val error:     String? = null,
    val authUser:  AuthUser? = null
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    private val projectRepo:  ProjectRepository,
    private val activityRepo: ActivityRepository,
    private val authRepo:     AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatsUiState(authUser = authRepo.currentUser()))
    val uiState: StateFlow<StatsUiState> = _uiState

    // ── Date Logic matching Export UI ────────────────────────────
    private val today        = LocalDate.now(ZoneId.systemDefault())
    private val weekFields   = WeekFields.of(Locale.getDefault())
    private val weekStart    = today.with(weekFields.dayOfWeek(), 1L)
    private val weekEnd      = today.with(weekFields.dayOfWeek(), 7L)
    
    private val currentMonth = YearMonth.now(ZoneId.systemDefault())
    private val monthStart   = currentMonth.atDay(1)
    private val monthEnd     = currentMonth.atEndOfMonth()
    
    private val fmt          = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)

    init {
        observeData()
        load()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(
                projectRepo.getAllProjectsFlow(),
                activityRepo.getAllActivitiesFlow()
            ) { projects, activities ->
                calculateStats(projects, activities)
            }.collect { updatedStats ->
                _uiState.update { current ->
                    updatedStats.copy(
                        isLoading = current.isLoading,
                        error     = current.error,
                        authUser  = current.authUser
                    )
                }
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            val user = authRepo.currentUser() ?: return@launch
            
            // แสดง Loading เฉพาะถ้ายังไม่มีข้อมูลใน Pipeline
            if (_uiState.value.pipelineStages.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                // Refresh ข้อมูลจาก Server ใน Background เพื่อให้ Database อัปเดต
                // เมื่อ Database อัปเดต Flow จะปล่อยข้อมูลใหม่มาเองผ่าน observeData()
                projectRepo.refreshProjects(user.userId)
                activityRepo.refreshActivities(user.userId)
            } catch (e: Exception) {
                // _uiState.update { it.copy(error = e.message) }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun calculateStats(projects: List<Project>, activities: List<SalesActivity>): StatsUiState {
        // ── Weekly ────────────────────────────────────────────────────
        // ✅ แก้ไข: นับโปรเจคที่สร้างใหม่ทั้งหมดในสัปดาห์นี้ เป็น "ลูกค้ามุ่งหวังใหม่" โดยไม่สนสถานะปัจจุบัน
        val weeklyLeads = projects.count { p ->
            isInRange(p.createdAt?.take(10), weekStart, weekEnd)
        }

        val weeklyNewProj = projects.count { p ->
            p.projectStatus !in listOf("Lead", "Completed", "Lost", "Failed") &&
                    isInRange(p.createdAt?.take(10), weekStart, weekEnd)
        }

        // จำนวน Visit
        val weeklyVisit = activities.count { a ->
            a.status == "completed" && isInRange(a.activityDate, weekStart, weekEnd)
        }

        // ── Monthly ───────────────────────────────────
        val closedSales = projects
            .filter { it.projectStatus in listOf("PO", "Completed") &&
                    isInRange(it.closingDate ?: it.startDate, monthStart, monthEnd) }
            .sumOf { it.expectedValue ?: 0.0 }

        val activeProjectsList = projects.filter {
            it.projectStatus !in listOf("Completed", "Lost", "Failed")
        }
        val activeValue = activeProjectsList.sumOf { it.expectedValue ?: 0.0 }

        val totalValue = projects.filter { it.projectStatus !in listOf("Lost", "Failed") }
            .sumOf { it.expectedValue ?: 0.0 }

        // ✅ แก้ไข: นับโปรเจคที่สร้างใหม่ทั้งหมดในเดือนนี้ เป็น "ลูกค้ามุ่งหวังใหม่" โดยไม่สนสถานะปัจจุบัน
        val monthlyLeads = projects.count { p ->
            isInRange(p.createdAt?.take(10), monthStart, monthEnd)
        }

        val closingMonthCount = projects.count { p ->
            p.projectStatus !in listOf("Completed", "Lost", "Failed") &&
                    isSameMonth(p.closingDate, currentMonth)
        }

        // ── Pipeline stages ───────────────────────────
        val stageOrder = listOf(
            "Lead", "New Project", "Quotation", "Bidding",
            "Make a Decision", "Assured", "PO", "Completed", "Lost", "Failed"
        )
        val stageCounts = stageOrder.map { stage ->
            val stageProjects = projects.filter { it.projectStatus == stage }
            PipelineStageCount(
                stage      = stage,
                count      = stageProjects.size,
                totalValue = stageProjects.sumOf { it.expectedValue ?: 0.0 }
            )
        }.filter { it.count > 0 }

        // ── Opportunity HOT/WARM/COLD ─────────────────
        val scoreOrder = listOf("HOT", "WARM", "COLD")
        val oppGroups = scoreOrder.map { score ->
            val scored = projects.filter {
                it.opportunityScore?.uppercase() == score &&
                        it.projectStatus !in listOf("Completed", "Lost", "Failed")
            }
            OpportunityGroup(
                score      = score,
                count      = scored.size,
                totalValue = scored.sumOf { it.expectedValue ?: 0.0 }
            )
        }

        return StatsUiState(
            weeklyNewLeads     = weeklyLeads,
            weeklyNewProjects  = weeklyNewProj,
            weeklyVisitCount   = weeklyVisit,
            monthlyClosedSales = closedSales,
            totalActiveValue   = activeValue,
            totalProjectValue  = totalValue,
            monthlyNewLeads    = monthlyLeads,
            activeProjects     = activeProjectsList.size,
            closingThisMonth   = closingMonthCount,
            pipelineStages     = stageCounts,
            opportunityGroups  = oppGroups
        )
    }

    fun logout() {
        viewModelScope.launch {
            authRepo.logout()
        }
    }

    private fun isInRange(dateStr: String?, from: LocalDate, to: LocalDate): Boolean {
        if (dateStr.isNullOrBlank()) return false
        return try {
            val d = LocalDate.parse(dateStr.take(10), fmt)
            !d.isBefore(from) && !d.isAfter(to)
        } catch (e: Exception) {
            false
        }
    }

    private fun isSameMonth(dateStr: String?, targetMonth: YearMonth): Boolean {
        if (dateStr.isNullOrBlank()) return false
        return try {
            val d = LocalDate.parse(dateStr.take(10), fmt)
            YearMonth.from(d) == targetMonth
        } catch (e: Exception) { false }
    }
}
