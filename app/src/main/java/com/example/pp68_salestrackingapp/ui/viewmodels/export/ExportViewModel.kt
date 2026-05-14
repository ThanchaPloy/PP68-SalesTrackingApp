package com.example.pp68_salestrackingapp.ui.screen.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.data.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class ExportUiState(
    val isLoading: Boolean = false,
    val activities: List<ExportActivityItem> = emptyList(),
    val projects: List<ExportProjectItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val weekRangeText: String = "",
    val error: String? = null
)

data class ExportActivityItem(
    val date: String,
    val projectName: String?,
    val companyName: String?,
    val topic: String?,
    val note: String?,
    val status: String,
    val results: List<String> = emptyList()
)

data class ExportProjectItem(
    val projectName: String,
    val companyName: String?,
    val value: Double,
    val status: String,
    val score: String?,
    val closeDate: String?
)

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val activityRepo: ActivityRepository,
    private val projectRepo: ProjectRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th", "TH"))

    fun loadWeeklyData(date: LocalDate) {
        val weekFields = WeekFields.of(Locale.getDefault())
        val startOfWeek = date.with(weekFields.dayOfWeek(), 1L)
        val endOfWeek = date.with(weekFields.dayOfWeek(), 7L)

        val rangeText = "${startOfWeek.format(dateFormatter)} - ${endOfWeek.format(dateFormatter)}"

        _uiState.update { it.copy(selectedDate = date, weekRangeText = rangeText) }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val activitiesResult = activityRepo.getMyActivitiesWithDetails()
                val allActivities = activitiesResult.getOrDefault(emptyList())
                val allResults = activityRepo.getAllResultsFlow().first()

                val filteredActivities = allActivities.filter { card ->
                    try {
                        if (card.plannedDate.isNullOrBlank()) false else {
                            val d = LocalDate.parse(card.plannedDate.take(10))
                            !d.isBefore(startOfWeek) && !d.isAfter(endOfWeek)
                        }
                    } catch (e: Exception) { false }
                }

                val filteredResults = allResults.filter { res ->
                    try {
                        if (res.reportDate.isNullOrBlank()) false else {
                            val d = LocalDate.parse(res.reportDate.take(10))
                            !d.isBefore(startOfWeek) && !d.isAfter(endOfWeek)
                        }
                    } catch (e: Exception) { false }
                }

                val projectsMap = projectRepo.getAllProjectsFlow().first().associateBy { it.projectId }
                val exportItems = mutableListOf<ExportActivityItem>()

                filteredActivities.forEach { act ->
                    val relatedResults = allResults.filter { it.activityId == act.activityId }
                    exportItems.add(
                        ExportActivityItem(
                            date = act.plannedDate ?: "",
                            projectName = act.projectName,
                            companyName = act.companyName,
                            topic = act.objective,
                            note = act.weeklyNote ?: "", 
                            status = act.planStatus,
                            results = relatedResults.mapNotNull { it.summary }
                        )
                    )
                }

                val appIdsInWeek = filteredActivities.map { it.activityId }.toSet()
                filteredResults.filter { it.activityId == null || it.activityId !in appIdsInWeek }.forEach { res ->
                    val project = projectsMap[res.projectId]
                    exportItems.add(
                        ExportActivityItem(
                            date = res.reportDate ?: "",
                            projectName = project?.projectName ?: "N/A",
                            companyName = null, 
                            topic = "บันทึกผลการทำงาน (ไม่มีแผนนัดหมาย)",
                            note = "",
                            status = "completed",
                            results = listOfNotNull(res.summary)
                        )
                    )
                }

                val sorted = exportItems.sortedWith(compareBy({ it.date }, { it.projectName }))
                _uiState.update { it.copy(isLoading = false, activities = sorted, projects = emptyList()) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMonthlyData(yearMonth: YearMonth) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val all = projectRepo.getAllProjectsFlow().first()

                val filtered = all.filter { p ->
                    try {
                        val startD = p.startDate?.let { LocalDate.parse(it.take(10)) }
                        val closeD = p.closingDate?.let { LocalDate.parse(it.take(10)) }

                        val isStartedThisMonth = startD?.let { YearMonth.from(it) == yearMonth } ?: false
                        val isClosingThisMonth = closeD?.let { YearMonth.from(it) == yearMonth } ?: false

                        isStartedThisMonth || isClosingThisMonth || p.projectStatus !in listOf("Completed", "Lost", "Failed")
                    } catch (e: Exception) { true }
                }.map {
                    ExportProjectItem(
                        projectName = it.projectName,
                        companyName = null, 
                        value = it.expectedValue ?: 0.0,
                        status = it.projectStatus ?: "",
                        score = it.opportunityScore,
                        closeDate = it.closingDate
                    )
                }
                _uiState.update { it.copy(isLoading = false, projects = filtered, activities = emptyList()) }

            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun generateActivityCsvString(): String {
        val activities = _uiState.value.activities
        val builder = StringBuilder()
        builder.append("Date,Project Name,Company Name,Topic,Note,Status,Results\n")
        activities.forEach {
            val safeProject = it.projectName?.replace("\"", "\"\"") ?: ""
            val safeCompany = it.companyName?.replace("\"", "\"\"") ?: ""
            val safeTopic = it.topic?.replace("\"", "\"\"") ?: ""
            val safeNote = it.note?.replace("\"", "\"\"") ?: ""
            val safeResults = it.results.joinToString("; ").replace("\"", "\"\"")
            builder.append("${it.date},\"$safeProject\",\"$safeCompany\",\"$safeTopic\",\"$safeNote\",${it.status},\"$safeResults\"\n")
        }
        return builder.toString()
    }

    fun generateProjectCsvString(): String {
        val projects = _uiState.value.projects
        val builder = StringBuilder()
        builder.append("Project Name,Expected Value,Status,Score,Close Date\n")
        projects.forEach {
            val safeProject = it.projectName.replace("\"", "\"\"")
            builder.append("\"$safeProject\",${it.value},${it.status},${it.score ?: ""},${it.closeDate ?: ""}\n")
        }
        return builder.toString()
    }
}
