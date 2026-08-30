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

data class PipelineStageCount(val stage: String, val count: Int, val totalValue: Double, val projects: List<Project> = emptyList())
data class OpportunityGroup(val score: String, val count: Int, val totalValue: Double, val projects: List<Project> = emptyList())

data class StatsUiState(
    // Weekly
    
    val weeklyNewLeadsList:     List<Customer> = emptyList(),
    val weeklyNewProjectsList:  List<Project>  = emptyList(),
    val weeklyVisitList:        List<SalesActivity> = emptyList(),
    val monthlyClosedSalesList: List<Project>  = emptyList(),
    val monthlyNewLeadsList:    List<Customer> = emptyList(),
    val monthlyNewProjectsList: List<Project>  = emptyList(),
    val activeProjectsList:     List<Project>  = emptyList(),
    val closingThisMonthList:   List<Project>  = emptyList(),
    val monthlyVisitList:       List<SalesActivity> = emptyList(),
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
    val monthlyVisitCount:  Int    = 0,

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

    // â”€â”€ Date Logic matching Export UI â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
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
            val hasData = _uiState.value.pipelineStages.isNotEmpty() ||
                    _uiState.value.activeProjects > 0 ||
                    _uiState.value.weeklyVisitCount > 0
            if (!hasData) _uiState.update { it.copy(isLoading = true, error = null) }
            else _uiState.update { it.copy(error = null) }
            try {
                val r1 = projectRepo.refreshProjects(user.userId)
                val r2 = activityRepo.refreshActivities(user.userId)
                val r3 = customerRepo.refreshCustomers(user.teamId ?: "")
                val err = listOf(r1, r2, r3).firstOrNull { it.isFailure }
                if (err != null) {
                    _uiState.update { it.copy(error = err.exceptionOrNull()?.message) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
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
        // âœ… à¸à¸£à¸­à¸‡à¸‚à¹‰à¸­à¸¡à¸¹à¸¥ Lead/Customer à¹ƒà¸«à¹‰à¹€à¸›à¹‡à¸™à¸‚à¸­à¸‡ User à¸›à¸±à¸ˆà¸ˆà¸¸à¸šà¸±à¸™à¸£à¸²à¸¢à¸šà¸¸à¸„à¸„à¸¥
        val myCustomers = customers.filter { it.createdBy == currentUserId }
        
        // âœ… à¹ƒà¸Šà¹‰à¸‚à¹‰à¸­à¸¡à¸¹à¸¥ Project à¸—à¸±à¹‰à¸‡à¸«à¸¡à¸”à¹ƒà¸™ DB (à¸‹à¸¶à¹ˆà¸‡à¸–à¸¹à¸à¸à¸£à¸­à¸‡à¸¡à¸²à¹à¸¥à¹‰à¸§à¸§à¹ˆà¸² User à¹€à¸›à¹‡à¸™à¸ªà¸¡à¸²à¸Šà¸´à¸à¸‚à¸­à¸‡à¹‚à¸„à¸£à¸‡à¸à¸²à¸£à¸™à¸±à¹‰à¸™)
        val myProjects = projects

        // â”€â”€ Weekly â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val weeklyLeadsList = myCustomers.filter { c -> isInRange(c.createdAt?.take(10), weekStart, weekEnd) }
        val weeklyLeads = weeklyLeadsList.size

        val weeklyNewProjList = myProjects.filter { p -> isInRange(p.startDate?.take(10), weekStart, weekEnd) }
        val weeklyNewProj = weeklyNewProjList.size

        val weeklyVisitListRaw = activities.filter { a -> a.status.lowercase() == "completed" && isInRange(a.activityDate, weekStart, weekEnd) }
        val weeklyVisit = weeklyVisitListRaw.size

        // â”€â”€ Monthly â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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

        val monthlyLeadsList = myCustomers.filter { c -> isInRange(c.createdAt?.take(10), monthStart, monthEnd) }
        val monthlyLeads = monthlyLeadsList.size

        val monthlyNewProjList = myProjects.filter { p -> isInRange(p.startDate?.take(10), monthStart, monthEnd) }
        val monthlyNewProj = monthlyNewProjList.size

        val monthlyVisitListRaw = activities.filter { a -> a.status.lowercase() == "completed" && isInRange(a.activityDate, monthStart, monthEnd) }
        val monthlyVisit = monthlyVisitListRaw.size

        val closingMonthList = myProjects.filter { p -> p.projectStatus !in listOf("Completed", "Lost", "Failed") && isSameMonth(p.closingDate, currentMonth) }
        val closingMonthCount = closingMonthList.size

        // â”€â”€ Pipeline stages â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        val stageOrder = listOf(
            "Lead", "New Project", "Quotation", "Bidding",
            "Make a Decision", "Assured", "PO", "Completed", "Lost", "Failed"
        )
        val stageCounts = stageOrder.map { stage ->
            val stageProjects = myProjects.filter { it.projectStatus == stage }
            PipelineStageCount(
                stage      = stage,
                count      = stageProjects.size,
                totalValue = stageProjects.sumOf { it.expectedValue ?: 0.0 },
                projects   = stageProjects
            )
        }.filter { it.count > 0 }

        // â”€â”€ Opportunity HOT/WARM/COLD â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                totalValue = scored.sumOf { it.expectedValue ?: 0.0 },
                projects   = scored
            )
        }

        return StatsUiState(
            weeklyNewLeadsList = weeklyLeadsList,
            weeklyNewProjectsList = weeklyNewProjList,
            weeklyVisitList    = weeklyVisitListRaw,
            monthlyClosedSalesList = myProjects.filter { it.projectStatus in listOf("PO", "Completed") && isInRange(it.closingDate ?: it.startDate, monthStart, monthEnd) },
            monthlyNewLeadsList = monthlyLeadsList,
            monthlyNewProjectsList = monthlyNewProjList,
            activeProjectsList = activeProjectsList,
            closingThisMonthList = closingMonthList,
            monthlyVisitList   = monthlyVisitListRaw,
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
            monthlyVisitCount  = monthlyVisit,
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


