package com.example.pp68_salestrackingapp.ui.viewmodels.export

import com.example.pp68_salestrackingapp.utils.policyFacts
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
import com.example.pp68_salestrackingapp.utils.formatPhotoUrl
import com.example.pp68_salestrackingapp.utils.ProjectStages
import com.example.pp68_salestrackingapp.data.repository.PlaceSearchRepository
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

// checkInTime เก็บเป็น ISO instant (UTC) ส่วน plannedTime เก็บเป็นเวลาท้องถิ่น "HH:mm:ss"
// การเทียบสองค่านี้ด้วย > ตรง ๆ คือการเทียบสตริงคนละรูปแบบ: "2026-04-06T09:15:30Z" ขึ้นต้นด้วย '2'
// จึงมากกว่า "09:00:00" เสมอ ทำให้นัดก่อน 20:00 ถูกตีว่าสายทุกครั้ง ส่วนนัด 20:00 ขึ้นไปไม่เคยสายเลย
internal fun isCheckInLate(checkInIso: String?, plannedLocalTime: String?): Boolean {
    if (checkInIso.isNullOrBlank() || plannedLocalTime.isNullOrBlank()) return false
    return try {
        val actual = java.time.Instant.parse(checkInIso)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalTime()
        actual.isAfter(java.time.LocalTime.parse(plannedLocalTime.trim()))
    } catch (e: Exception) {
        false
    }
}

data class ExportUiState(
    val isLoading: Boolean = false,
    val activities: List<ExportActivityItem> = emptyList(),
    val projects: List<ExportProjectItem> = emptyList(),
    val startDate: LocalDate = LocalDate.now().minusDays(6),
    val endDate: LocalDate = LocalDate.now(),
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
    val resultDetails: List<ExportResultDetail> = emptyList(),
    val activityType: String? = null,
    val checkInTime: String? = null,
    val contactName: String? = null,
    val checkInStatus: String? = null,
    val locationName: String? = null
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
    private val projectRepo: ProjectRepository,
    private val placeSearchRepository: PlaceSearchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th", "TH"))

    fun loadWeeklyData(start: LocalDate, end: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                // Formatting for display
                val startStr = start.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th", "TH")))
                val endStr = end.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("th", "TH")))
                val weekRangeStr = "$startStr - $endStr"

                _uiState.update { it.copy(startDate = start, endDate = end, weekRangeText = weekRangeStr) }
            
                val activitiesResult = activityRepo.getMyActivitiesWithDetails()
                val allActivities = activitiesResult.getOrThrow()
                val allResults = activityRepo.getAllResultsFlow().first()

                val filteredActivities = allActivities.filter { act ->
                    try {
                        if (act.plannedDate.isNullOrBlank()) false else {
                            val d = LocalDate.parse(act.plannedDate.take(10))
                            !d.isBefore(start) && !d.isAfter(end)
                        }
                    } catch (e: Exception) { false }
                }

                val filteredResults = allResults.filter { res ->
                    try {
                        if (res.reportDate.isNullOrBlank()) false else {
                            val d = LocalDate.parse(res.reportDate.take(10))
                            !d.isBefore(start) && !d.isAfter(end)
                        }
                    } catch (e: Exception) { false }
                }

                val projectsMap = projectRepo.getAllProjectsFlow().first().associateBy { it.projectId }
                val exportItems = mutableListOf<ExportActivityItem>()

                // ✅ เดิม filter allResults ทับทุก activity (O(N·M)) — group ครั้งเดียวแล้ว lookup O(1) แทน
                val resultsByActivityId = allResults.filter { it.activityId != null }.groupBy { it.activityId }
                val latestResultByActivityId = filteredActivities.associate { act ->
                    val matched = resultsByActivityId[act.activityId] ?: emptyList()
                    act.activityId to matched.filter { it.isLatest == true }.ifEmpty { matched }.maxByOrNull { res -> res.version ?: 0 }
                }

                // 2. ✅ ประมวลผลบันทึกที่ไม่มีนัดหมาย (คำนวณก่อนเพื่อรวม resultId เข้าไปในการดึงรูปแบบ batch ด้านล่าง)
                val appIdsInWeek = filteredActivities.map { it.activityId }.toSet()
                val standaloneResults = filteredResults
                    .filter { it.activityId == null || it.activityId !in appIdsInWeek }
                    // ✅ รวมเวอร์ชันของบันทึกเดียวกันด้วย result_group_id ซึ่งเป็นคีย์จริงของ "กลุ่มเวอร์ชัน"
                    // เดิมเดาจากโครงการ+วันที่+ข้อความสรุป ซึ่งผิดได้สองทาง: บันทึกสองใบของโครงการเดียวกัน
                    // วันเดียวกันที่เผอิญสรุปเหมือนกัน จะถูกยุบเหลือใบเดียวในรายงาน (ข้อมูลหาย) และ
                    // การแก้คำในสรุปตอนบันทึกเวอร์ชันใหม่ ทำให้ contentKey เปลี่ยน กลายเป็นสองกลุ่ม
                    // โผล่ทั้งเวอร์ชันเก่าและใหม่พร้อมกัน — fallback เป็นคีย์เดิมเฉพาะแถวเก่าที่ยังไม่มี group id
                    .groupBy { res ->
                        res.resultGroupId ?: run {
                            val dateKey = res.reportDate?.take(10) ?: "no_date"
                            val contentKey = res.summary?.replace("\\s".toRegex(), "") ?: ""
                            "${res.projectId}_${dateKey}_$contentKey"
                        }
                    }
                    .mapNotNull { (_, group) ->
                        group.filter { it.isLatest == true }.ifEmpty { group }.maxByOrNull { res -> res.version ?: 0 }
                    }
                    .sortedByDescending { it.reportDate ?: "" }

                // ✅ เดิม query รูปทีละ result_id ต่อ activity/standalone result (N query ต่อการ export
                // หนึ่งครั้ง) — ดึงรวดเดียวเป็น map แทน แล้ว lookup O(1) ในลูปด้านล่าง
                val neededResultIds = (latestResultByActivityId.values.filterNotNull().map { it.resultId } +
                    standaloneResults.map { it.resultId }).distinct()
                val photosByResultId = activityRepo.getResultPhotosBatch(neededResultIds)

                // 1. ✅ ประมวลผลกิจกรรมที่มีนัดหมาย (ดึงเฉพาะบันทึกหลังการขายเวอร์ชันล่าสุด + รูปภาพทั้งหมด)
                var didGeocode = false
                filteredActivities.forEach { act ->
                    // แปลงพิกัดเป็นชื่อสถานที่เฉพาะนัดหมายที่ยังไม่เคยแปลงไว้
                    // (แปลงแล้วเก็บลง Room ครั้งเดียว — export รอบถัดไปใช้ค่าที่ cache ไว้เลย)
                    val needsGeocode = act.locationName.isNullOrBlank() &&
                        act.plannedLat != null && act.plannedLong != null
                    // เว้นจังหวะระหว่างการยิงจริงเพื่อไม่ให้ export สัปดาห์ที่มีนัดหมายใหม่เยอะ
                    // ยิงรัวจนกินโควตา Geoapify รวดเดียว — ถ้าอ่านจาก cache ได้หมดก็ไม่ต้องรอเลย
                    if (needsGeocode && didGeocode) {
                        kotlinx.coroutines.delay(300)
                    }
                    val resolvedLocationName = if (needsGeocode) {
                        didGeocode = true
                        getAddressFromLatLong(act.plannedLat, act.plannedLong)
                            .also { name ->
                                if (name.isNotBlank()) activityRepo.cacheLocationName(act.activityId, name)
                            }
                    } else {
                        act.locationName ?: ""
                    }
                    val latestResult = latestResultByActivityId[act.activityId]

                    val resultDetailsList = if (latestResult != null) {
                        val photos = (listOfNotNull(latestResult.photoUrl) + (photosByResultId[latestResult.resultId] ?: emptyList())).filter { it.isNotBlank() }.distinct()
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
                                lossReason = latestResult.lossReasonNote?.takeIf { it.isNotBlank() } ?: latestResult.lossReason,
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
                            // W6: แผนที่เลยวันนัดไปแล้วยังไม่เช็คอิน/บันทึกผล ให้รายงานเห็น "missing"
                            // เหมือนที่แอปโชว์ "ขาดนัด" ด้วย ไม่ใช่โชว์ raw status ดิบว่ายัง "planned"
                            status = com.example.pp68_salestrackingapp.utils.AppointmentPolicy
                                .effectiveStatus(act.policyFacts()),
                            results = summaryList,
                            resultDetails = resultDetailsList,
                            activityType = act.activityType,
                            checkInTime = act.checkInTime,
                            contactName = act.contactName,
                            checkInStatus = run {
                                val statuses = mutableListOf<String>()
                                if (act.isLocationVerified == false) statuses.add("นอกสถานที่")
                                if (isCheckInLate(act.checkInTime, act.plannedTime)) statuses.add("ช้ากว่าเวลานัด")
                                if (statuses.isEmpty()) null else statuses.joinToString(", ")
                            },
                            locationName = resolvedLocationName
                        )
                    )
                }

                standaloneResults.forEach { res ->
                    val project = projectsMap[res.projectId]
                    val photos = (listOfNotNull(res.photoUrl) + (photosByResultId[res.resultId] ?: emptyList())).filter { it.isNotBlank() }.distinct()
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
                        lossReason = res.lossReasonNote?.takeIf { it.isNotBlank() } ?: res.lossReason,
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
                            resultDetails = listOf(detail),
                            activityType = null,
                            checkInTime = null,
                            contactName = null,
                            checkInStatus = null,
                            locationName = null
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

                // รายงานของเดือนที่เลือก = โครงการที่ "ยังเดินอยู่ในเดือนนั้น" + โครงการที่ "แพ้ในเดือนนั้น"
                //
                // เงื่อนไขเดิม (เริ่มเดือนนี้ || ปิดเดือนนี้ || สถานะยังไม่แพ้) ข้อสุดท้ายทำให้โครงการที่ยัง
                // เดินอยู่ติดมาทุกเดือนไม่ว่าจะเลือกเดือนไหน ตัวเลือกเดือนจึงแทบไม่มีผล ตอนนี้ผูกกับ
                // ช่วงเวลาจริงทั้งสองกลุ่ม กดย้อนดูเดือนเก่าจะได้ภาพของเดือนนั้นจริง ๆ
                val monthStart = yearMonth.atDay(1)
                val monthEnd = yearMonth.atEndOfMonth()
                fun parseDate(raw: String?): LocalDate? =
                    raw?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                val filtered = all.filter { p ->
                    val start = parseDate(p.startDate)
                    val close = parseDate(p.closingDate)
                    if (p.projectStatus in ProjectStages.LOST) {
                        // วันที่แพ้ไม่มีฟิลด์ของตัวเอง — ใช้วันปิดเป็นตัวแทน ไม่มีก็ถอยไปใช้วันเริ่ม
                        // ถ้าไม่มีวันไหนเลยก็วางบนเส้นเวลาไม่ได้ จึงไม่ขึ้นในเดือนใด ดีกว่าขึ้นทุกเดือน
                        val lostOn = close ?: start
                        lostOn != null && !lostOn.isBefore(monthStart) && !lostOn.isAfter(monthEnd)
                    } else {
                        // ยังเดินอยู่ในเดือนนั้น = เริ่มไม่เกินสิ้นเดือน และยังไม่ปิดก่อนต้นเดือน
                        // (ไม่มีวันเริ่ม/วันปิด = ยังเปิดอยู่ ให้ผ่าน — พิสูจน์ไม่ได้ว่าจบไปแล้ว)
                        val startedByMonthEnd = start == null || !start.isAfter(monthEnd)
                        val notClosedBeforeMonth = close == null || !close.isBefore(monthStart)
                        startedByMonthEnd && notClosedBeforeMonth
                    }
                }.map {
                    ExportProjectItem(
                        projectName = it.projectName,
                        companyName = null,
                        value = it.expectedValue ?: 0.0,
                        // ป้ายจาก master data ไม่ใช่รหัสดิบ — ให้ตรงกับที่แสดงในหน้าอื่นทั้งแอป
                        // (ฟิลด์นี้ถูกเอาไปแสดงบนจอ/CSV/PDF เท่านั้น ไม่มีที่ไหนเทียบกับรหัส)
                        status = ProjectStages.labelFor(it.projectStatus),
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

    
    private suspend fun getAddressFromLatLong(lat: Double?, lon: Double?): String {
        if (lat == null || lon == null) return ""
        // ผ่าน backend proxy เหมือนการค้นหา — โควตา Geoapify ถูกนับรวมที่เดียว
        // ผลที่ได้ถูก cache ลง Room ต่อ (ดู loadWeeklyData) จึงยิงแค่ครั้งเดียวต่อนัดหมาย
        val place = placeSearchRepository.reverseGeocode(lat, lon) ?: return ""
        return place.formattedAddress.ifBlank { place.name }
    }

    fun generateActivityCsvString(): String {
        val activities = _uiState.value.activities
        val builder = StringBuilder()
        // Added UTF-8 BOM (\uFEFF) and using Unicode escapes for Thai headers
        // \u2705 header \u0E15\u0E49\u0E2D\u0E07\u0E15\u0E23\u0E07\u0E25\u0E33\u0E14\u0e31\u0E1A field \u0E08\u0E23\u0E34\u0E07\u0E43\u0e19\u0E41\u0E15\u0e48\u0E25\u0E30\u0E41\u0E16\u0e27\u0E02\u0E49\u0E32\u0E07\u0E25\u0e48\u0E32\u0E07 \u2014 \u0E40\u0E14\u0E34\u0E21 "\u0E23\u0E39\u0E1B\u0E20\u0E32\u0E1E (Photos)" \u0E2D\u0E22\u0E39\u0e48\u0E1C\u0E34\u0E14\u0e17\u0e35\u0e48 (\u0E15\u0e48\u0E2D\u0E08\u0E32\u0E01
        // Summary) \u0e17\u0e31\u0E49\u0E07\u0e17\u0e35\u0e48 data \u0E08\u0E23\u0E34\u0E07\u0E22\u0E49\u0E32\u0E22 photos \u0E44\u0E1B\u0E04\u0E2D\u0E25\u0e31\u0E21\u0e19\u0E4C\u0E2A\u0E38\u0E14\u0e17\u0E49\u0E32\u0E22\u0E15\u0e31\u0E49\u0E07\u0E41\u0E15\u0e48 commit b349c66 (Excel export \u0E16\u0E39\u0E01\u0E2D\u0E22\u0E39\u0e48\u0E41\u0E25\u0E49\u0e27)
        builder.append("\uFEFF\u0e27\u0e31\u0e19\u0e17\u0e35\u0e48 (Date),\u0e1b\u0e23\u0e30\u0e40\u0e20\u0e17 (Type),\u0e2b\u0e31\u0e27\u0e02\u0e49\u0e2d (Topic),\u0e1a\u0e23\u0e34\u0e29\u0e31\u0e17 (Company),\u0e1c\u0e39\u0e49\u0e15\u0e34\u0e14\u0e15\u0e48\u0e2d (Contact),\u0e42\u0e04\u0e23\u0e07\u0e01\u0e32\u0e23 (Project),\u0e40\u0e0a\u0e47\u0e04\u0e2d\u0e34\u0e19 (Check-in),\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e40\u0e0a\u0e47\u0e04\u0e2d\u0e34น (Check-in Status),\u0e2a\u0e16\u0e32\u0e19\u0e17\u0e35\u0e48\u0e19\u0e31\u0e14\u0e2b\u0e21\u0e32\u0e22 (Location Name),\u0e2a\u0e16\u0e32น\u0e30 (Status),\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e43\u0e2b\u0e21\u0e48 (New Status),\u0e2a\u0e23\u0e38\u0e1b\u0e1c\u0e25 (Summary),\u0e42\u0e2d\u0e01\u0e32\u0e2a (Opportunity),\u0e43\u0e1a\u0e40\u0e2a\u0e19\u0e2d\u0e23\u0e32\u0e04\u0e32 (Proposal),\u0e27\u0e31\u0e19\u0e17\u0e35\u0e48\u0e40\u0e2a\u0e19\u0e2d\u0e23\u0e32\u0e04\u0e32 (Proposal Date),DM \u0e23\u0e48\u0e27\u0e21\u0e1b\u0e23\u0e30\u0e0a\u0e38\u0e21 (DM Involved),\u0e08\u0e33\u0e19\u0e27\u0e19\u0e04\u0e39\u0e48\u0e41\u0e02\u0e48\u0e07 (CompetitorCount),\u0e04\u0e27\u0e32\u0e21\u0e40\u0e23\u0e47\u0e27 (Speed),\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e14\u0e35\u0e25 (Deal),\u0e42\u0e0b\u0e25\u0e39\u0e0a\u0e31\u0e48\u0e19\u0e40\u0e14\u0e34\u0e21 (Solution),\u0e40\u0e2b\u0e15\u0e38\u0e1c\u0e25\u0e41\u0e1e\u0e49 (Loss),\u0e23\u0e39\u0e1b\u0e20\u0e32\u0e1e (Photos)\n")
        
        activities.forEach { item ->
            val safeProject = item.projectName?.replace("\"", "\"\"") ?: ""
            val safeCompany = item.companyName?.replace("\"", "\"\"") ?: ""
            val safeTopic = item.topic?.replace("\"", "\"\"") ?: ""
            val safeType = item.activityType?.uppercase() ?: ""
            val contact = item.contactName ?: ""
            val checkInStatus = item.checkInStatus ?: ""
            val locationName = item.locationName?.replace("\"", "\"\"") ?: ""
            val checkIn = item.checkInTime ?: ""

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

                    builder.append("${item.date},\"${safeType}\",\"${safeTopic}\",\"${safeCompany}\",\"${contact}\",\"${safeProject}\",\"${checkIn}\",\"${checkInStatus}\",\"${locationName}\",\"${item.status}\",\"${newStatus}\",\"${summary}\",\"${score}\",\"${propSent}\",\"${propDate}\",\"${dm}\",\"${comp}\",\"${speed}\",\"${dealPos}\",\"${sol}\",\"${loss}\",\"${photos}\"\n")
                }
            } else {
                val safeResults = item.results.joinToString("; ").replace("\"", "\"\"").replace("\n", " ")
                builder.append("${item.date},\"${safeType}\",\"${safeTopic}\",\"${safeCompany}\",\"${contact}\",\"${safeProject}\",\"${checkIn}\",\"${checkInStatus}\",\"${locationName}\",\"${item.status}\",\"\",\"${safeResults}\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\"\n")
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

