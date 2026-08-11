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
import java.util.Locale
import com.example.pp68_salestrackingapp.utils.formatPhotoUrl
import javax.inject.Inject

data class ExportUiState(
    val isLoading: Boolean = false,
    val activities: List<ExportActivityItem> = emptyList(),
    val projects: List<ExportProjectItem> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val weekRangeText: String = "",
    val error: String? = null
)

data class ExportResultDetail(
    val resultId: String? = null,
    val reportDate: String? = null,
    val newStatus: String? = null,
    val opportunityScore: String? = null,
    val dmInvolved: Boolean = false,
    val isProposalSent: Boolean = false,
    val proposalDate: String? = null,
    val competitorCount: Int = 0,
    val responseSpeed: String? = null,
    val dealPosition: String? = null,
    val previousSolution: String? = null,
    val counterpartyMultiplier: String? = null,
    val summary: String? = null,
    val lossReason: String? = null,
    val photoUrls: List<String> = emptyList()
)

data class ExportActivityItem(
    val date: String,
    val projectName: String?,
    val companyName: String?,
    val topic: String?,
    val note: String?,
    val status: String,
    val results: List<String> = emptyList(),
    val resultDetails: List<ExportResultDetail> = emptyList()
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
                val allActivities = activitiesResult.getOrThrow()
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

                // 1. ✅ ประมวลผลกิจกรรมที่มีนัดหมาย (ดึงเฉพาะบันทึกหลังการขายเวอร์ชันล่าสุด + รูปภาพทั้งหมด)
                filteredActivities.forEach { act ->
                    val matchedResults = allResults.filter { it.activityId == act.activityId }
                    val latestResult = matchedResults
                        .filter { it.isLatest == true }
                        .ifEmpty { matchedResults }
                        .maxByOrNull { res -> res.version ?: 0 }

                    val resultDetailsList = if (latestResult != null) {
                        val photos = (listOfNotNull(latestResult.photoUrl) + activityRepo.getResultPhotos(latestResult.resultId)).filter { it.isNotBlank() }.distinct()
                        listOf(
                            ExportResultDetail(
                                resultId = latestResult.resultId,
                                reportDate = latestResult.reportDate,
                                newStatus = latestResult.newStatus,
                                opportunityScore = latestResult.opportunityScore,
                                dmInvolved = latestResult.dmInvolved,
                                isProposalSent = latestResult.isProposalSent,
                                proposalDate = latestResult.proposalDate,
                                competitorCount = latestResult.competitorCount,
                                responseSpeed = latestResult.responseSpeed,
                                dealPosition = latestResult.dealPosition,
                                previousSolution = latestResult.previousSolution,
                                counterpartyMultiplier = latestResult.counterpartyMultiplier,
                                summary = latestResult.summary,
                                lossReason = latestResult.lossReason,
                                photoUrls = photos
                            )
                        )
                    } else emptyList()

                    val summaryList = if (resultDetailsList.isNotEmpty()) {
                        resultDetailsList.mapNotNull { it.summary }.filter { it.isNotBlank() }
                    } else {
                        if (latestResult?.summary != null) listOf(latestResult.summary) else emptyList()
                    }

                    exportItems.add(
                        ExportActivityItem(
                            date = act.plannedDate ?: "",
                            projectName = act.projectName,
                            companyName = act.companyName,
                            topic = act.objective,
                            note = act.weeklyNote ?: "", 
                            status = act.planStatus,
                            results = summaryList,
                            resultDetails = resultDetailsList
                        )
                    )
                }

                // 2. ✅ ประมวลผลบันทึกที่ไม่มีนัดหมาย (Standalone Results เวอร์ชันล่าสุด + รูปภาพทั้งหมด)
                val appIdsInWeek = filteredActivities.map { it.activityId }.toSet()
                val standaloneResults = filteredResults
                    .filter { it.activityId == null || it.activityId !in appIdsInWeek }
                    .groupBy { res ->
                        val dateKey = res.reportDate?.take(10) ?: "no_date"
                        val contentKey = res.summary?.replace("\\s".toRegex(), "") ?: ""
                        "${res.projectId}_${dateKey}_$contentKey"
                    }
                    .mapNotNull { (_, group) ->
                        group.filter { it.isLatest == true }.ifEmpty { group }.maxByOrNull { res -> res.version ?: 0 }
                    }
                    .sortedByDescending { it.reportDate ?: "" }

                standaloneResults.forEach { res ->
                    val project = projectsMap[res.projectId]
                    val photos = (listOfNotNull(res.photoUrl) + activityRepo.getResultPhotos(res.resultId)).filter { it.isNotBlank() }.distinct()
                    val detail = ExportResultDetail(
                        resultId = res.resultId,
                        reportDate = res.reportDate,
                        newStatus = res.newStatus,
                        opportunityScore = res.opportunityScore,
                        dmInvolved = res.dmInvolved,
                        isProposalSent = res.isProposalSent,
                        proposalDate = res.proposalDate,
                        competitorCount = res.competitorCount,
                        responseSpeed = res.responseSpeed,
                        dealPosition = res.dealPosition,
                        previousSolution = res.previousSolution,
                        counterpartyMultiplier = res.counterpartyMultiplier,
                        summary = res.summary,
                        lossReason = res.lossReason,
                        photoUrls = photos
                    )

                    exportItems.add(
                        ExportActivityItem(
                            date = res.reportDate ?: "",
                            projectName = project?.projectName ?: "N/A",
                            companyName = null, 
                            topic = "บันทึกผลการทำงาน",
                            note = "",
                            status = "completed",
                            results = listOfNotNull(res.summary),
                            resultDetails = listOf(detail)
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
        builder.append("\uFEFFDate,Project Name,Company Name,Topic,Status,New Status,Opportunity Score,Proposal Sent,Proposal Date,DM Involved,Competitor Count,Response Speed,Deal Position,Solution,Loss Reason,Summary,Photo URLs\n")
        activities.forEach { item ->
            val safeProject = item.projectName?.replace("\"", "\"\"") ?: ""
            val safeCompany = item.companyName?.replace("\"", "\"\"") ?: ""
            val safeTopic = item.topic?.replace("\"", "\"\"") ?: ""

            if (item.resultDetails.isNotEmpty()) {
                item.resultDetails.forEach { res ->
                    val newStatus = res.newStatus ?: ""
                    val score = res.opportunityScore ?: ""
                    val propSent = if (res.isProposalSent) "Yes" else "No"
                    val propDate = res.proposalDate ?: ""
                    val dm = if (res.dmInvolved) "Yes" else "No"
                    val comp = res.competitorCount.toString()
                    val speed = res.responseSpeed ?: ""
                    val dealPos = res.dealPosition ?: ""
                    val sol = res.previousSolution?.replace("\"", "\"\"") ?: ""
                    val loss = res.lossReason?.replace("\"", "\"\"") ?: ""
                    val summary = res.summary?.replace("\"", "\"\"")?.replace("\n", " ") ?: ""
                    val photos = res.photoUrls.joinToString("; ") { formatPhotoUrl(it) }.replace("\"", "\"\"")

                    builder.append("${item.date},\"$safeProject\",\"$safeCompany\",\"$safeTopic\",${item.status},\"$newStatus\",\"$score\",\"$propSent\",\"$propDate\",\"$dm\",\"$comp\",\"$speed\",\"$dealPos\",\"$sol\",\"$loss\",\"$summary\",\"$photos\"\n")
                }
            } else {
                val safeResults = item.results.joinToString("; ").replace("\"", "\"\"")
                builder.append("${item.date},\"$safeProject\",\"$safeCompany\",\"$safeTopic\",${item.status},\"\",\"\",\"No\",\"\",\"No\",\"0\",\"\",\"\",\"\",\"\",\"$safeResults\",\"\"\n")
            }
        }
        return builder.toString()
    }

    fun generateProjectCsvString(): String {
        val projects = _uiState.value.projects
        val builder = StringBuilder()
        builder.append("\uFEFFProject Name,Expected Value,Status,Score,Close Date\n")
        projects.forEach {
            val safeProject = it.projectName.replace("\"", "\"\"")
            builder.append("\"$safeProject\",${it.value},${it.status},${it.score ?: ""},${it.closeDate ?: ""}\n")
        }
        return builder.toString()
    }
}
