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
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject

data class ExportUiState(
    val isLoading: Boolean = false,
    val activities: List<ExportActivityItem> = emptyList(),
    val projects: List<ExportProjectItem> = emptyList(),
    val error: String? = null
)

data class ExportActivityItem(
    val date: String,
    val projectName: String?,
    val companyName: String?,
    val topic: String?,
    val note: String?,
    val status: String
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

    fun loadWeeklyData(date: LocalDate) {
        val weekFields = WeekFields.of(Locale.getDefault())
        val startOfWeek = date.with(weekFields.dayOfWeek(), 1L)
        val endOfWeek = date.with(weekFields.dayOfWeek(), 7L)

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            activityRepo.getMyActivitiesWithDetails().fold(
                onSuccess = { all ->
                    val filtered = all.filter { card ->
                        try {
                            if (card.plannedDate.isNullOrBlank()) false else {
                                val d = LocalDate.parse(card.plannedDate.take(10))
                                !d.isBefore(startOfWeek) && !d.isAfter(endOfWeek)
                            }
                        } catch (e: Exception) { false }
                    }.map {
                        ExportActivityItem(
                            date = it.plannedDate ?: "",
                            projectName = it.projectName,
                            companyName = it.companyName,
                            topic = it.objective,
                            // ActivityCard ไม่มี weeklyNote เลยใส่ค่าว่างไปก่อนครับ
                            note = "",
                            status = it.planStatus
                        )
                    }.sortedBy { it.date }

                    _uiState.update { it.copy(isLoading = false, activities = filtered, projects = emptyList()) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            )
        }
    }

    fun loadMonthlyData(yearMonth: YearMonth) {
        val startOfMonth = yearMonth.atDay(1)
        val endOfMonth = yearMonth.atEndOfMonth()

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // แก้ให้ใช้ getAllProjectsFlow().first() แทน getAllProjects() ที่ไม่มีอยู่จริง
                val all = projectRepo.getAllProjectsFlow().first()

                val filtered = all.filter { p ->
                    try {
                        // เปลี่ยนจาก closeDate เป็น closingDate ตาม Project Model
                        val startD = p.startDate?.let { LocalDate.parse(it.take(10)) }
                        val closeD = p.closingDate?.let { LocalDate.parse(it.take(10)) }

                        val isStartedThisMonth = startD?.let { YearMonth.from(it) == yearMonth } ?: false
                        val isClosingThisMonth = closeD?.let { YearMonth.from(it) == yearMonth } ?: false

                        isStartedThisMonth || isClosingThisMonth || p.projectStatus !in listOf("Completed", "Lost", "Failed")
                    } catch (e: Exception) { true }
                }.map {
                    ExportProjectItem(
                        projectName = it.projectName,
                        companyName = null, // ต้องการข้อมูล Customer เพิ่มเติมถ้าจะเอาชื่อบริษัท
                        value = it.expectedValue ?: 0.0,
                        status = it.projectStatus ?: "",
                        score = it.opportunityScore,
                        closeDate = it.closingDate // เปลี่ยนจาก closeDate เป็น closingDate
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

        // สร้าง Header
        builder.append("Date,Project Name,Company Name,Topic,Note,Status\n")

        // วนลูปสร้าง Data
        activities.forEach {
            val safeProject = it.projectName?.replace("\"", "\"\"") ?: ""
            val safeCompany = it.companyName?.replace("\"", "\"\"") ?: ""
            val safeTopic = it.topic?.replace("\"", "\"\"") ?: ""
            val safeNote = it.note?.replace("\"", "\"\"") ?: ""

            builder.append("${it.date},\"$safeProject\",\"$safeCompany\",\"$safeTopic\",\"$safeNote\",${it.status}\n")
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