package com.example.pp68_salestrackingapp.ui.viewmodels.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.ui.screen.activity.NotiAction
import com.example.pp68_salestrackingapp.ui.screen.activity.NotiType
import com.example.pp68_salestrackingapp.ui.screen.activity.NotificationItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

data class NotificationUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val activityRepo: ActivityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    init {
        loadNotifications()
    }

    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val activitiesResult = activityRepo.getMyActivitiesWithDetails()
                val cards = activitiesResult.getOrDefault(emptyList())

                // ✅ ใช้ timezone Bangkok
                val bangkokZone = java.time.ZoneId.of("Asia/Bangkok")
                val now   = LocalDateTime.now(bangkokZone)
                val today = LocalDate.now(bangkokZone)
                val tomorrow = today.plusDays(1)

                val notis = mutableListOf<NotificationItem>()

                cards.filter {
                    it.planStatus == "planned" || it.planStatus == "checked_in"
                }.forEach { card ->
                    val date = card.plannedDate?.let {
                        try { LocalDate.parse(it.take(10), dateFormatter) }
                        catch (e: Exception) { null }
                    }
                    val time = card.plannedTime?.let {
                        try { LocalTime.parse(it.take(5), timeFormatter) }
                        catch (e: Exception) { LocalTime.of(9, 0) }
                    } ?: LocalTime.of(9, 0)

                    if (date != null && (date == today || date == tomorrow)) {
                        val planDateTime = LocalDateTime.of(date, time)
                        val minutesLeft  = Duration.between(now, planDateTime).toMinutes()

                        // ✅ แสดงเวลาที่เหลือให้ละเอียด
                        val timeLabel = when {
                            date == tomorrow ->
                                "พรุ่งนี้ ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                            minutesLeft < 0  ->
                                "เลยกำหนด ${Math.abs(minutesLeft)} นาที"
                            minutesLeft == 0L ->
                                "ถึงเวลาแล้ว!"
                            minutesLeft in 1..59 ->
                                "อีก $minutesLeft นาที"
                            minutesLeft in 60..119 ->
                                "อีก 1 ชม. ${minutesLeft % 60} นาที"
                            minutesLeft in 120..1439 ->
                                "อีก ${minutesLeft / 60} ชม."
                            else ->
                                "วันนี้ ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                        }

                        notis.add(
                            NotificationItem(
                                id        = card.activityId,
                                type      = NotiType.REMINDER,
                                title     = card.activityType ?: "Upcoming Plan",
                                timeLabel = timeLabel,
                                subtitle  = card.projectName ?: card.companyName ?: "",
                                location  = card.objective ?: "",
                                action    = if (card.planStatus == "checked_in")
                                    NotiAction.REPORT else NotiAction.CHECK_IN,
                                isToday   = date == today
                            )
                        )
                    }
                }

                // Completed today
                cards.filter {
                    it.planStatus == "planned" || it.planStatus == "checked_in"
                }.forEach { card ->
                    val date = card.plannedDate?.let {
                        try { LocalDate.parse(it.take(10), dateFormatter) }
                        catch (e: Exception) { null }
                    }

                    // ✅ ถ้าไม่มีเวลา ให้ default เป็น 09:00
                    val time = card.plannedTime?.let {
                        try { LocalTime.parse(it.take(5), DateTimeFormatter.ofPattern("HH:mm")) }
                        catch (e: Exception) { LocalTime.of(9, 0) }
                    } ?: LocalTime.of(9, 0)

                    // ✅ แสดงถ้าเป็นวันนี้หรือพรุ่งนี้
                    if (date != null && (date == today || date == tomorrow)) {
                        val planDateTime = LocalDateTime.of(date, time)
                        val minutesLeft  = Duration.between(now, planDateTime).toMinutes()

                        val timeLabel = when {
                            date == tomorrow ->
                                "พรุ่งนี้ ${time.format(DateTimeFormatter.ofPattern("HH:mm"))}"
                            minutesLeft < -60 ->
                                "เลยกำหนด ${Math.abs(minutesLeft / 60)} ชม."
                            minutesLeft < 0 ->
                                "เลยกำหนด ${Math.abs(minutesLeft)} นาที"
                            minutesLeft == 0L ->
                                "ถึงเวลาแล้ว!"
                            minutesLeft in 1..59 ->
                                "อีก $minutesLeft นาที"
                            minutesLeft in 60..119 ->
                                "อีก 1 ชม. ${minutesLeft % 60} นาที"
                            else ->
                                "อีก ${minutesLeft / 60} ชม."
                        }

                        notis.add(
                            NotificationItem(
                                id        = card.activityId,
                                type      = NotiType.REMINDER,
                                title     = card.objective














































































































































                                    ?: card.activityType ?: "แผนการเข้าพบ",  // ✅ แสดง topic
                                timeLabel = timeLabel,
                                subtitle  = card.projectName ?: card.companyName ?: "",
                                location  = card.companyName ?: "",
                                action    = if (card.planStatus == "checked_in")
                                    NotiAction.REPORT else NotiAction.CHECK_IN,
                                isToday   = date == today
                            )
                        )
                    }
                }

                // Weekly report reminder
                val endOfWeek = today.with(
                    TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)
                )
                if (Duration.between(
                        today.atStartOfDay(),
                        endOfWeek.atStartOfDay()
                    ).toDays() <= 3
                ) {
                    notis.add(
                        NotificationItem(
                            id        = "weekly_report",
                            type      = NotiType.REPORT_WEEKLY,
                            title     = "Weekly Report Due",
                            timeLabel = "Action required",
                            subtitle  = "กรุณาส่งรายงานประจำสัปดาห์",
                            location  = "",
                            action    = NotiAction.VIEW_WEEKLY,
                            isToday   = true
                        )
                    )
                }

                _uiState.update {
                    it.copy(
                        notifications = notis.sortedWith(
                            compareByDescending<NotificationItem> { it.isToday }
                        ),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
