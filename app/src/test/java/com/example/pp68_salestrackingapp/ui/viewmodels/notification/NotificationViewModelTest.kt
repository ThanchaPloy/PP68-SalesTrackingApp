package com.example.pp68_salestrackingapp.ui.viewmodels.notification

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.example.pp68_salestrackingapp.data.repository.ActivityRepository
import com.example.pp68_salestrackingapp.ui.screen.activity.NotiAction
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val dispatcher = StandardTestDispatcher()
    private val activityRepo = mockk<ActivityRepository>(relaxed = true)

    // วันศุกร์ที่ 10 เม.ย. 2026 เวลา 08:00 ตามเวลากรุงเทพฯ — ตรึงไว้เพื่อให้เทสต์ไม่ขึ้นกับวันจริง
    // เลือกวันศุกร์เพราะเป็นช่วงท้ายสัปดาห์ (เหลือไม่เกิน 3 วันจะถึงอาทิตย์) การเตือน "ส่งรายงาน
    // รายสัปดาห์" จึงต้องโผล่เองตามกติกาจริง ไม่ต้องใช้ System.getProperty("is_test") บังคับ
    private val zone = java.time.ZoneId.of("Asia/Bangkok")
    private val fixedToday: java.time.LocalDate = java.time.LocalDate.of(2026, 4, 10)
    private val fixedClock: java.time.Clock =
        java.time.Clock.fixed(fixedToday.atTime(8, 0).atZone(zone).toInstant(), zone)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadNotifications should create reminders and weekly notification`() = runTest {
        val today = fixedToday.toString()
        val tomorrow = fixedToday.plusDays(1).toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "Visit",
                    projectName = "Project A",
                    companyName = "Company A",
                    contactName = "Contact A",
                    objective = "Obj A",
                    planStatus = "planned",
                    plannedDate = today,
                    plannedTime = "09:00",
                    plannedEndTime = "10:00"
                ),
                ActivityCard(
                    activityId = "A2",
                    activityType = "Call",
                    projectName = "Project B",
                    companyName = "Company B",
                    contactName = "Contact B",
                    objective = "Obj B",
                    planStatus = "checked_in",
                    plannedDate = tomorrow,
                    plannedTime = "11:00",
                    plannedEndTime = "12:00"
                )
            )
        )

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.notifications.isNotEmpty())
        assertTrue(vm.uiState.value.notifications.any { it.id == "A1" || it.id == "A2" })
        assertTrue(vm.uiState.value.notifications.any { it.id == "weekly_report" })
    }

    @Test
    fun `loadNotifications when repository throws should set error`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } throws Exception("fetch fail")

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.error == "fetch fail")
    }

    @Test
    fun `loadNotifications should ignore invalid date and map checked in to report action`() = runTest {
        val today = fixedToday.toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A1",
                    activityType = "Visit",
                    projectName = "Project A",
                    companyName = "Company A",
                    contactName = null,
                    objective = "Obj A",
                    planStatus = "planned",
                    plannedDate = "invalid-date",
                    plannedTime = "xx:yy",
                    plannedEndTime = null
                ),
                ActivityCard(
                    activityId = "A2",
                    activityType = "Call",
                    projectName = "Project B",
                    companyName = "Company B",
                    contactName = null,
                    objective = "Obj B",
                    planStatus = "checked_in",
                    plannedDate = today,
                    plannedTime = "09:61",
                    plannedEndTime = null
                ),
                ActivityCard(
                    activityId = "A3",
                    activityType = "Visit",
                    projectName = "Project C",
                    companyName = "Company C",
                    contactName = null,
                    objective = "Obj C",
                    planStatus = "completed",
                    plannedDate = today,
                    plannedTime = "09:00",
                    plannedEndTime = null
                )
            )
        )

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        val notis = vm.uiState.value.notifications
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(notis.none { it.id == "A1" })
        assertTrue(notis.any { it.id == "A2" && it.action == NotiAction.REPORT })
        assertTrue(notis.none { it.id == "A3" })
    }

    @Test
    fun `loadNotifications should map objective fallback when time string is unparsable`() = runTest {
        val today = fixedToday.toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A10",
                    activityType = "VisitType",
                    projectName = null,
                    companyName = "Comp",
                    contactName = null,
                    objective = null,
                    planStatus = "planned",
                    plannedDate = today,
                    plannedTime = "unparsable-time-string",
                    plannedEndTime = null
                )
            )
        )

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        val noti = vm.uiState.value.notifications.first { it.id == "A10" }
        assertEquals("VisitType", noti.title)
        assertEquals("Comp", noti.subtitle)
        assertTrue(noti.timeLabel.isNotBlank())
    }

    @Test
    fun `loadNotifications should keep previous error after throw then subsequent success`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } throws Exception("first fail")
        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()
        assertEquals("first fail", vm.uiState.value.error)

        val today = fixedToday.toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A11",
                    activityType = "Visit",
                    projectName = "P",
                    companyName = "C",
                    contactName = null,
                    objective = "Obj",
                    planStatus = "planned",
                    plannedDate = today,
                    plannedTime = "09:00",
                    plannedEndTime = null
                )
            )
        )
        vm.loadNotifications()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.notifications.any { it.id == "A11" })
        assertEquals("first fail", vm.uiState.value.error)
    }

    @Test
    fun `loadNotifications with no cards should still finish loading and contain only weekly report`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.notifications.any { it.id == "weekly_report" })
        assertTrue(vm.uiState.value.notifications.all { it.id == "weekly_report" })
    }

    @Test
    fun `initial uiState should start empty then go loading while fetch is running`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } coAnswers {
            kotlinx.coroutines.delay(200)
            Result.success(emptyList())
        }

        val vm = NotificationViewModel(activityRepo, fixedClock)
        assertTrue(vm.uiState.value.notifications.isEmpty())
        assertNull(vm.uiState.value.error)

        dispatcher.scheduler.runCurrent()
        assertTrue(vm.uiState.value.isLoading)

        advanceUntilIdle()
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `loadNotifications with Result failure should handle gracefully without throwing`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.failure(Exception("api fail"))

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.error)
        assertTrue(vm.uiState.value.notifications.all { it.id == "weekly_report" })
    }

    @Test
    fun `loadNotifications should exclude cards not in today and tomorrow`() = runTest {
        val futureDateString = fixedToday.plusDays(2).toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "A-FUTURE",
                    activityType = "Visit",
                    projectName = "Project",
                    companyName = "Company",
                    contactName = null,
                    objective = "Future",
                    planStatus = "planned",
                    plannedDate = futureDateString,
                    plannedTime = "09:00",
                    plannedEndTime = null
                )
            )
        )

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.notifications.none { it.id == "A-FUTURE" })
    }

    @Test
    fun `calling loadNotifications manually should trigger repository again`() = runTest {
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(emptyList())

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()
        vm.loadNotifications()
        advanceUntilIdle()

        coVerify(exactly = 2) { activityRepo.getMyActivitiesWithDetails() }
    }

    // ปุ่มในแจ้งเตือนต้องตรงกับที่หน้ารายละเอียดนัดทำ — มีแต่ onsite ที่มีขั้นเช็คอิน
    // เดิมดูแต่ planStatus อย่างเดียว นัดโทรจึงได้ปุ่ม check-in ทั้งที่กดไปก็ไม่มีจุดให้เช็คอิน
    @Test
    fun `only an onsite appointment is offered check-in`() = runTest {
        val today = fixedToday.toString()
        coEvery { activityRepo.getMyActivitiesWithDetails() } returns Result.success(
            listOf(
                ActivityCard(
                    activityId = "ONSITE", activityType = "onsite",
                    projectName = "P", companyName = "C", contactName = null, objective = "o",
                    planStatus = "planned", plannedDate = today,
                    plannedTime = "09:00", plannedEndTime = "10:00"
                ),
                ActivityCard(
                    activityId = "ONLINE", activityType = "online",
                    projectName = "P", companyName = "C", contactName = null, objective = "o",
                    planStatus = "planned", plannedDate = today,
                    plannedTime = "09:00", plannedEndTime = "10:00"
                ),
                ActivityCard(
                    activityId = "CALL", activityType = "call",
                    projectName = "P", companyName = "C", contactName = null, objective = "o",
                    planStatus = "planned", plannedDate = today,
                    plannedTime = "09:00", plannedEndTime = "10:00"
                )
            )
        )

        val vm = NotificationViewModel(activityRepo, fixedClock)
        advanceUntilIdle()

        val byId = vm.uiState.value.notifications.associateBy { it.id }
        assertEquals(NotiAction.CHECK_IN, byId["ONSITE"]?.action)
        assertEquals(NotiAction.REPORT,   byId["ONLINE"]?.action)
        assertEquals(NotiAction.REPORT,   byId["CALL"]?.action)
    }
}
