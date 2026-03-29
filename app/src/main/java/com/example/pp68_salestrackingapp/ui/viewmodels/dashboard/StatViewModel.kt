package com.example.pp68_salestrackingapp.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import com.example.pp68_salestrackingapp.data.repository.AuthRepository
import com.example.pp68_salestrackingapp.data.model.AuthUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
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

    // date helpers
    private val today      = LocalDate.now(ZoneId.of("UTC"))
    private val weekStart  = today.minusDays(today.dayOfWeek.value.toLong() - 1)
    private val monthStart = today.withDayOfMonth(1)
    private val currentMonth = YearMonth.now(ZoneId.of("UTC"))
    private val fmt        = DateTimeFormatter.ofPattern("yyyy-MM-dd", java.util.Locale.US)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // โหลด projects และ activities แบบขนานกัน
                val projectsDeferred  = async { projectRepo.getAllProjectsFlow().first() }
                val activitiesDeferred = async { activityRepo.getMyActivitiesWithDetails().getOrThrow() }

                val projects   = projectsDeferred.await()
                val activities = activitiesDeferred.await()

                // ── Weekly ────────────────────────────────────────────────────
                val weeklyLeads = projects.count { p ->
                    p.projectStatus == "Lead" &&
                            isInRange(p.createdAt?.take(10), weekStart, today)
                }

                val weeklyNewProj = projects.count { p ->
                    p.projectStatus !in listOf("Lead", "Completed", "Lost", "Failed") &&
                            isInRange(p.createdAt?.take(10), weekStart, today)
                }


                // คำนวณจำนวน Visit (เช็คจาก ActivityCard)
                val weeklyVisit = activities.count { a ->
                    a.planStatus == "completed" && isInRange(a.plannedDate, weekStart, today)
                }

                // ── Monthly ───────────────────────────────────
                val closedSales = projects
                    .filter { it.projectStatus in listOf("PO", "Completed") &&
                            isInRange(it.closingDate ?: it.startDate, monthStart, today) }
                    .sumOf { it.expectedValue ?: 0.0 }

                val activeProjectsList = projects.filter {
                    it.projectStatus !in listOf("Completed", "Lost", "Failed")
                }
                val activeValue = activeProjectsList.sumOf { it.expectedValue ?: 0.0 }

                val totalValue = projects.filter { it.projectStatus !in listOf("Lost", "Failed") }
                    .sumOf { it.expectedValue ?: 0.0 }

                val monthlyLeads = projects.count { p ->
                    p.projectStatus == "Lead" &&
                            isInRange(p.createdAt?.take(10), monthStart, today)
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

                _uiState.update {
                    it.copy(
                        weeklyNewLeads     = weeklyLeads,
                        weeklyVisitCount   = weeklyVisit,
                        monthlyClosedSales = closedSales,
                        totalActiveValue   = activeValue,
                        totalProjectValue  = totalValue,
                        monthlyNewLeads    = monthlyLeads,
                        activeProjects     = activeProjectsList.size,
                        closingThisMonth   = closingMonthCount,
                        pipelineStages     = stageCounts,
                        opportunityGroups  = oppGroups,
                        isLoading          = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
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

    private fun <T> Result<T>.getOrThrow(): T =
        getOrElse { throw it }
}
