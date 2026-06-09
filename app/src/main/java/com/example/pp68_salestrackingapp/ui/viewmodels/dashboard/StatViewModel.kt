package com.example.pp68_salestrackingapp.ui.viewmodels.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.repository.CustomerRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.data.model.Customer
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
    val monthlyNewProjects: Int    = 0,
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
    private val customerRepo: CustomerRepository,
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
            val user = authRepo.currentUser()
            combine(
                projectRepo.getAllProjectsFlow(),
                activityRepo.getAllActivitiesFlow(),
                customerRepo.getAllCustomersFlow()
            ) { projects, activities, customers ->
                calculateStats(projects, activities, customers, user?.userId ?: "")
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
            
            if (_uiState.value.pipelineStages.isEmpty()) {
                _uiState.update { it.copy(isLoading = true, error = null) }
            }

            try {
                projectRepo.refreshProjects(user.userId)
                activityRepo.refreshActivities(user.userId)
                customerRepo.refreshCustomers(user.teamId ?: "")
            } catch (e: Exception) {
                // error handling
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun calculateStats(
        projects: List<Project>,
        activities: List<SalesActivity>,
        customers: List<Customer>,
        currentUserId: String
    ): StatsUiState {
        // ✅ กรองข้อมูล Lead/Customer ให้เป็นของ User ปัจจุบันรายบุคคล
        val myCustomers = customers.filter { it.createdBy == currentUserId }
        
        // ✅ ใช้ข้อมูล Project ทั้งหมดใน DB (ซึ่งถูกกรองมาแล้วว่า User เป็นสมาชิกของโครงการนั้น)
        val myProjects = projects

        // ── Weekly ────────────────────────────────────────────────────
        val weeklyLeads = myCustomers.count { c ->
            isInRange(c.createdAt?.take(10), weekStart, weekEnd)
        }

        val weeklyNewProj = myProjects.count { p ->
            isInRange(p.createdAt?.take(10), weekStart, weekEnd)
        }

        val weeklyVisit = activities.count { a ->
            a.status == "completed" && isInRange(a.activityDate, weekStart, weekEnd)
        }

        // ── Monthly ───────────────────────────────────
        val closedSales = myProjects
            .filter { it.projectStatus in listOf("PO", "Completed") &&
                    isInRange(it.closingDate ?: it.startDate, monthStart, monthEnd) }
            .sumOf { it.expectedValue ?: 0.0 }

        val activeProjectsList = myProjects.filter {
            it.projectStatus !in listOf("Completed", "Lost", "Failed")
        }
        val activeValue = activeProjectsList.sumOf { it.expectedValue ?: 0.0 }

        val totalValue = myProjects.filter { it.projectStatus !in listOf("Lost", "Failed") }
            .sumOf { it.expectedValue ?: 0.0 }

        val monthlyLeads = myCustomers.count { c ->
            isInRange(c.createdAt?.take(10), monthStart, monthEnd)
        }

        val monthlyNewProj = myProjects.count { p ->
            isInRange(p.createdAt?.take(10), monthStart, monthEnd)
        }

        val closingMonthCount = myProjects.count { p ->
            p.projectStatus !in listOf("Completed", "Lost", "Failed") &&
                    isSameMonth(p.closingDate, currentMonth)
        }

        // ── Pipeline stages ───────────────────────────
        val stageOrder = listOf(
            "Lead", "New Project", "Quotation", "Bidding",
            "Make a Decision", "Assured", "PO", "Completed", "Lost", "Failed"
        )
        val stageCounts = stageOrder.map { stage ->
            val stageProjects = myProjects.filter { it.projectStatus == stage }
            PipelineStageCount(
                stage      = stage,
                count      = stageProjects.size,
                totalValue = stageProjects.sumOf { it.expectedValue ?: 0.0 }
            )
        }.filter { it.count > 0 }

        // ── Opportunity HOT/WARM/COLD ─────────────────
        val scoreOrder = listOf("HOT", "WARM", "COLD")
        val oppGroups = scoreOrder.map { score ->
            val scored = myProjects.filter {
                val pScore = it.opportunityScore?.uppercase() ?: ""
                pScore.contains(score) &&
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
            monthlyNewProjects = monthlyNewProj,
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
