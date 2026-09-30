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
    private val activityRepo: ActivityRepository,
    // ✅ ฉีดนาฬิกาเข้ามาแทนการเรียก now() ตรง ๆ — เทสต์ตรึงวันได้โดยไม่ต้องมี
    // System.getProperty("is_test") เป็นทางแยกอยู่ในโค้ดที่ผู้ใช้รัน (ดู AppModule.provideClock)
    private val clock: java.time.Clock = java.time.Clock.system(java.time.ZoneId.of("Asia/Bangkok"))
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

                // เวลาทั้งหมดอ่านจากนาฬิกาที่ฉีดเข้ามา (โปรดักชัน = โซนกรุงเทพฯ, เทสต์ = ตรึงวันได้)
                val now   = LocalDateTime.now(clock)
                val today = LocalDate.now(clock)
                val tomorrow = today.plusDays(1)

                val notis = mutableListOf<NotificationItem>()
                // ✅ เก็บเวลานัดคู่กับแต่ละรายการไว้เรียงลำดับ — เดิมเรียงด้วย isToday อย่างเดียว
                // ลำดับภายในวันจึงเป็นลำดับที่ repository คืนมา ผู้ใช้เห็นนัดบ่ายอยู่เหนือนัดเช้าได้
                // (NotificationItem เก็บแต่ timeLabel ที่เป็นข้อความ เรียงตามนั้นไม่ได้)
                val timed = mutableListOf<Pair<LocalDateTime, NotificationItem>>()

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

                        timed.add(
                            planDateTime to NotificationItem(
                                id        = card.activityId,
                                type      = NotiType.REMINDER,
                                // หัวข้อนัดมาก่อน ถ้าไม่มีก็ใช้ "ชื่อชนิดนัด" ที่อ่านได้ — เดิม fallback
                                // เป็น activityType ดิบ ผู้ใช้เห็นแจ้งเตือนหัวข้อว่า "onsite"
                                title     = card.objective?.takeIf { t -> t.isNotBlank() }
                                    ?: com.example.pp68_salestrackingapp.utils.AppointmentStatus
                                        .typeLabel(card.activityType).ifBlank { "แผนการเข้าพบ" },
                                timeLabel = timeLabel,
                                subtitle  = card.projectName ?: card.companyName ?: "",
                                location  = card.companyName ?: "",
                                // เฉพาะ onsite ที่มีขั้นเช็คอิน — online/call ไปหน้าบันทึกผลตรง ๆ
                                // เหมือนที่ ActivityDetailScreen ทำ ถ้าใช้ planStatus อย่างเดียว
                                // แจ้งเตือนนัดโทรจะมีปุ่ม check-in ซึ่งทั้งแอปถือว่าไม่มีสำหรับชนิดนี้
                                action    = if (card.planStatus == "checked_in" ||
                                    card.activityType.lowercase() != "onsite")
                                    NotiAction.REPORT else NotiAction.CHECK_IN,
                                isToday   = date == today
                            )
                        )
                    }
                }

                // เรียงตามเวลานัดจริงก่อน แล้วค่อยเอาเข้า notis — ของวันนี้ที่ใกล้ถึงเวลาจะอยู่บนสุด
                notis.addAll(timed.sortedBy { it.first }.map { it.second })

                // Weekly report reminder
                val endOfWeek = today.with(
                    TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY)
                )
                // เตือนช่วงท้ายสัปดาห์ (เหลือไม่เกิน 3 วันจะถึงอาทิตย์) — เดิมมี
                // || System.getProperty("is_test") == "true" ต่อท้ายไว้ให้เทสต์บังคับให้โผล่
                // ตอนนี้เทสต์ตรึงวันผ่านนาฬิกาที่ฉีดเข้ามาแทน ไม่ต้องมีกิ่งพิเศษในโค้ดจริง
                if (Duration.between(today.atStartOfDay(), endOfWeek.atStartOfDay()).toDays() <= 3) {
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
                        // ของวันนี้ขึ้นก่อนพรุ่งนี้ และภายในกลุ่มเดียวกันคงลำดับเวลาที่เรียงไว้แล้ว
                        // (sortedWith เสถียร จึงไม่สลับลำดับที่จัดมาข้างบน)
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
