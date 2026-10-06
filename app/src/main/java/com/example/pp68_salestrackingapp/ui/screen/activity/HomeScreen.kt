package com.example.pp68_salestrackingapp.ui.screen.activity

import com.example.pp68_salestrackingapp.utils.policyFacts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityCard
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.HomeUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.HomeViewModel
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── Colors ────────────────────────────────────────────────────
private val White        = Color.White
private val TextDark     = Color(0xFF1A1A1A)
private val TextGray     = Color(0xFF888888)
private val RedPrimary   = Color(0xFFAE2138)
private val BgLight      = Color(0xFFF5F5F5)
private val BorderGray   = Color(0xFFE8E8E8)
private val BlueBtn      = Color(0xFF1976D2)
private val GreenStatus  = Color(0xFF2E7D32)
private val OrangeStatus = Color(0xFFE65100)
private val GrayStatus   = Color(0xFF546E7A)

// ── Activity type config ──────────────────────────────────────
private data class TypeConfig(val label: String, val icon: ImageVector, val color: Color)
private val typeConfigs = mapOf(
    "onsite" to TypeConfig("ONSITE VISIT",    Icons.Default.Store,        Color(0xFF93278A)),
    "online" to TypeConfig("ONLINE MEETING",  Icons.Default.Videocam,     Color(0xFF1565C0)),
    "call"   to TypeConfig("CALL",            Icons.Default.Call,          Color(0xFFE33E76))
)

// ── Status config ─────────────────────────────────────────────
private data class StatusConfig(
    val label:     String,
    val textColor: Color,
    val bgColor:   Color,
    val action:    String?
)
// ✅ "checked_in" ชี้ไป "report" ตรง ๆ (ไม่ใช่ "finish" อีกแล้ว) — เดิมพา user ไป
// ActivityDetailScreen ให้กดปุ่ม Finish ที่ไม่ได้บันทึกข้อมูลอะไร แค่เปลี่ยนสถานะแล้วเด้งกลับ Home
// ให้แตะการ์ดซ้ำ ตอนนี้ปุ่ม "บันทึกผล" เดียวกับที่ completed ใช้อยู่แล้วพาไปหน้าบันทึกผลได้เลย
// "cancelled" เอาออกแล้ว — ไม่มีจุดไหนในแอปตั้งค่านี้ให้ appointment.status เลย (ไม่มีปุ่ม/flow ยกเลิกนัด)
private val statusConfigs = mapOf(
    "planned"    to StatusConfig("กำลังดำเนินการ", GreenStatus,  Color(0xFFE8F5E9), "checkin"),
    "checked_in" to StatusConfig("กำลังดำเนินการ", GreenStatus,  Color(0xFFE8F5E9), "report"),
    "completed"  to StatusConfig("เสร็จสิ้น",        GrayStatus, Color(0xFFECEFF1), "report"),
    // W6: เลยวันนัดไปแล้วยังไม่เช็คอิน/บันทึกผล — เช็คอินไม่ได้อีกแล้ว แต่ยังบันทึกผลย้อนหลังได้
    // เหมือน checked_in/completed (action="report" เหมือนกัน ไม่ใช่ "checkin")
    com.example.pp68_salestrackingapp.utils.AppointmentPolicy.MISSING to
        StatusConfig("ขาดนัด", Color(0xFFC62828), Color(0xFFFFEBEE), "report")
)

@Composable
fun HomeScreen(
    onAddClick:          () -> Unit,
    onCardClick:         (String) -> Unit,
    onCheckin:           (String) -> Unit,
    onReport:            (String) -> Unit,
    onNotificationClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onLogoutClick:       () -> Unit = {},
    onAddPhoneClick:     () -> Unit = {},
    onOpenDrafts:        () -> Unit = {},
    currentTab:          Int,
    onTabChange:         (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.loadActivities()
                // กรอกเบอร์ในหน้าโปรไฟล์แล้วย้อนกลับมา การ์ดทวงเบอร์ต้องหายทันที ไม่ต้องรอลากรีเฟรช
                viewModel.recheckPhoneNumber()
                viewModel.refreshDraftCount()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }




    HomeScreenContent(
        uiState             = uiState,
        onAddClick          = onAddClick,
        onCardClick         = onCardClick,
        onCheckin           = onCheckin,
        onReport            = onReport,
        onDelete            = { viewModel.deleteActivity(it) },
        onNotificationClick = onNotificationClick,
        onSettingsClick     = onSettingsClick,
        onLogoutClick       = onLogoutClick,
        onAddPhoneClick     = onAddPhoneClick,
        currentTab          = currentTab,
        onTabChange         = onTabChange,
        onMonthChange       = { viewModel.selectMonth(it) },
        onRetrySync         = { viewModel.retrySync() },
        onDismissSyncFailures = { viewModel.dismissSyncFailures() },
        draftCount          = uiState.draftCount,
        onOpenDrafts        = onOpenDrafts
    )
}

@Composable
private fun HomeScreenContent(
    uiState:             HomeUiState,
    onAddClick:          () -> Unit,
    onCardClick:         (String) -> Unit,
    onCheckin:           (String) -> Unit,
    onReport:            (String) -> Unit,
    onDelete:            (String) -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick:     () -> Unit,
    onLogoutClick:       () -> Unit,
    onAddPhoneClick:     () -> Unit = {},
    currentTab:          Int,
    onTabChange:         (Int) -> Unit,
    onMonthChange:       (YearMonth) -> Unit,
    onRetrySync:         () -> Unit = {},
    onDismissSyncFailures: () -> Unit = {},
    draftCount:          Int = 0,
    onOpenDrafts:        () -> Unit = {}
) {
    Scaffold(
        topBar = {
            HomeTopBar(
                selectedMonth       = uiState.selectedMonth,
                authUser            = uiState.authUser,
                onMonthChange       = onMonthChange,
                onNotificationClick = onNotificationClick,
                onSettingsClick     = onSettingsClick,
                onLogoutClick       = onLogoutClick
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick        = onAddClick,
                containerColor = RedPrimary,
                contentColor   = White,
                shape          = RoundedCornerShape(16.dp)
            ) { Icon(Icons.Default.Add, "สร้างแผน") }
        },
        bottomBar   = { BottomNavBar(currentTab = currentTab, onTabChange = onTabChange) },
        containerColor = BgLight
    ) { padding ->
      Column(Modifier.fillMaxSize().padding(padding)) {
        // ต้องอยู่นอก when เหมือนการ์ดเบอร์โทร — เคสที่ต้องเห็นแถบนี้มากที่สุดคือ "โหลดไม่สำเร็จ
        // จนรายการว่าง" ซึ่งจะไปเข้าสาขา EmptyState ถ้าวางไว้ใน LazyColumn จะไม่มีวันโผล่
        if (uiState.syncFailures.isNotEmpty()) {
            SyncFailedBanner(
                parts     = uiState.syncFailures,
                onRetry   = onRetrySync,
                onDismiss = onDismissSyncFailures,
                modifier  = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
        }
        if (uiState.pendingSummary.isNotEmpty() || uiState.rejectedCount > 0 || uiState.conflictCount > 0 ||
            uiState.syncStatus is com.example.pp68_salestrackingapp.utils.SyncStatus.Running ||
            uiState.syncStatus is com.example.pp68_salestrackingapp.utils.SyncStatus.Queued
        ) {
            val pendingCount = uiState.pendingSummary.sumOf { it.second }
            val message = when {
                uiState.syncStatus is com.example.pp68_salestrackingapp.utils.SyncStatus.Running ->
                    "กำลังส่งข้อมูลเบื้องหลัง คุณใช้งานต่อได้"
                uiState.syncStatus is com.example.pp68_salestrackingapp.utils.SyncStatus.Queued ->
                    "รอส่งข้อมูล $pendingCount รายการ คุณใช้งานต่อได้"
                uiState.rejectedCount + uiState.conflictCount > 0 ->
                    "มี ${uiState.rejectedCount + uiState.conflictCount} รายการที่ต้องตรวจสอบ และ $pendingCount รายการรอส่ง"
                else -> "มีข้อมูล $pendingCount รายการรอส่ง"
            }
            Surface(
                color = Color(0xFFFFF3CD),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, tint = Color(0xFF7A5900))
                    Spacer(Modifier.width(8.dp))
                    Text(message, color = Color(0xFF5F4500), fontSize = 13.sp)
                }
            }
        }
        // อยู่นอก when โดยตั้งใจ — คนที่ยังไม่มีเบอร์มักเป็นบัญชีใหม่ที่ยังไม่มีนัดหมายเลย
        // ถ้าวางไว้ในสาขา LazyColumn การ์ดจะไม่โผล่ให้คนที่ต้องเห็นมากที่สุด
        if (uiState.needsPhoneNumber) {
            PhoneRequiredCard(
                onClick  = onAddPhoneClick,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
            )
        }
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = RedPrimary) }

            uiState.groupedCards.isEmpty() -> EmptyState(Modifier)

            else -> LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ไม่มีปุ่มปิดแบนเนอร์แล้ว เพราะมันไม่ได้ทวงร่างใดร่างหนึ่ง แต่เป็นทางเข้าหน้ารายการ
                // ปิดไปก็ไม่มีประโยชน์ และของเดิมที่จำว่า "ปิดแล้ว" ทำให้ร่างที่ค้างอยู่หายไปจากสายตา
                if (draftCount > 0) {
                    item {
                        com.example.pp68_salestrackingapp.ui.components.DraftBanner(
                            message = "มีฉบับร่างนัดหมาย $draftCount รายการ",
                            actionLabel = "ดูรายการ",
                            onAction = onOpenDrafts,
                            onDismiss = onOpenDrafts,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                uiState.groupedCards.forEach { (dateHeader, cards) ->
                    item {
                        Text(
                            dateHeader,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 12.sp,
                            color      = TextGray,
                            modifier   = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                    }
                    items(cards) { card ->
                        ActivityCard(
                            card      = card,
                            onClick   = { onCardClick(card.activityId) },
                            onCheckin = { onCheckin(card.activityId) },
                            onReport  = { onReport(card.activityId) },
                            onDelete  = { onDelete(card.activityId) }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
      }
    }
}

// ── การ์ดแจ้งว่ายังไม่มีเบอร์โทร ──────────────────────────────
// เดิมเป็น AlertDialog ที่ปิดไม่ได้และไม่บอกอะไรเลยเมื่อบันทึกพลาด ทำให้บัญชีนั้นใช้แอปไม่ได้ถาวร
// การ์ดแบบนี้ยังบังคับให้เห็นทุกครั้งที่เปิดแอป แต่ไม่ขวางงานอื่น และกดแล้วไปหน้ากรอกได้เลย
/**
 * แถบเตือนว่าโหลดข้อมูลบางส่วนไม่สำเร็จตอน login
 *
 * ตั้งใจให้ "บาง" ไม่ใช่ dialog ขวางทาง — ผู้ใช้ยังทำงานส่วนที่โหลดสำเร็จต่อได้ทันที
 * แต่ต้องได้รู้ว่าที่เห็นว่างอยู่นั้นเพราะโหลดไม่สำเร็จ ไม่ใช่เพราะข้อมูลหาย
 */
@Composable
private fun SyncFailedBanner(
    parts: List<String>,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFDECEA)),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.CloudOff, null, tint = Color(0xFFAE2138), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "โหลดข้อมูลไม่ครบ: ${parts.joinToString(", ")}",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                    color      = Color(0xFF7A1226)
                )
                Text(
                    "ข้อมูลในเครื่องยังอยู่ครบ ไม่ได้หายไปไหน",
                    fontSize = 12.sp,
                    color    = Color(0xFF9A3A4A)
                )
            }
            TextButton(onClick = onRetry) {
                Text("ลองใหม่", color = Color(0xFFAE2138), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, "ปิด", tint = Color(0xFF9A3A4A), modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PhoneRequiredCard(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        colors   = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)),
        shape    = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Phone, null, tint = Color(0xFFB26A00), modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "ยังไม่ได้กรอกเบอร์โทรศัพท์",
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    color      = Color(0xFF7A4A00)
                )
                Text(
                    "แตะเพื่อกรอกเบอร์ติดต่อของคุณ",
                    fontSize = 12.sp,
                    color    = Color(0xFF9A6A20)
                )
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFB26A00))
        }
    }
}

// ── Activity Card ─────────────────────────────────────────────
@Composable
fun ActivityCard(
    card:      ActivityCard,
    onClick:   () -> Unit,
    onCheckin: () -> Unit,
    onReport:  () -> Unit,
    onDelete:  () -> Unit
) {
    val typeConf   = typeConfigs[card.activityType]
        ?: TypeConfig(card.activityType.uppercase(), Icons.Default.Event, TextGray)
    val effectiveStatus = com.example.pp68_salestrackingapp.utils.AppointmentPolicy
        .effectiveStatus(card.policyFacts())
    val statusConf = statusConfigs[effectiveStatus]
        ?: StatusConfig(effectiveStatus, TextGray, BgLight, null)

    val hasNote    = !card.weeklyNote.isNullOrBlank() || card.hasResult
    // ลบได้เฉพาะก่อนถึงวันนัด — กฎอยู่ใน AppointmentPolicy ที่เดียวกับที่
    // ActivityRepository.deleteActivity ใช้บล็อกจริง ปุ่มกับการบล็อกจึงไม่มีวันดริฟต์กัน
    val canDelete  = com.example.pp68_salestrackingapp.utils.AppointmentPolicy
        .canDelete(card.policyFacts()) is
        com.example.pp68_salestrackingapp.utils.AppointmentPolicy.Decision.Allowed
    // นัดแบบ call/online ไม่มีขั้นเช็คอิน — สถานะ "planned" จึงพาไปหน้าบันทึกผลตรง ๆ
    // เหมือนกับที่ "checked_in"/"completed" ทำอยู่แล้ว
    val isCallOrOnline = card.activityType == "call" || card.activityType == "online"

    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.DeleteForever,
                    null,
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("ลบแผนนี้?", fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        card.objective ?: "แผนการเข้าพบ",
                        fontWeight = FontWeight.Medium,
                        fontSize   = 14.sp
                    )
                    Text(
                        card.companyName ?: "",
                        fontSize = 13.sp,
                        color    = TextGray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "การลบจะไม่สามารถยกเลิกได้",
                        fontSize = 12.sp,
                        color    = Color(0xFFE53935)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                    shape  = RoundedCornerShape(8.dp)
                ) {
                    Text("ลบ", color = White, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("ยกเลิก", color = TextGray)
                }
            }
        )
    }

    Surface(
        shape           = RoundedCornerShape(12.dp),
        color           = White,
        shadowElevation = 1.dp,
        onClick         = onClick,
        modifier        = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Badge(typeConf.label, typeConf.icon, typeConf.color)

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Badge(statusConf.label, Icons.Default.AccessTime, statusConf.textColor, statusConf.bgColor)

                    if (canDelete) {
                        IconButton(
                            onClick  = { showDeleteDialog = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "ลบแผน",
                                tint     = Color(0xFFE53935),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier            = Modifier.weight(1f)
                ) {
                    card.projectName?.let {
                        InfoRow(Icons.Default.Work, it, fontWeight = FontWeight.SemiBold)
                    }
                    if (!card.plannedTime.isNullOrBlank()) {
                        val startTime = card.plannedTime.take(5).replace(":", ".")
                        val endTime = card.plannedEndTime?.take(5)?.replace(":", ".")
                        val timeText = if (!endTime.isNullOrBlank()) {
                            "$startTime - $endTime"
                        } else {
                            startTime
                        }
                        InfoRow(Icons.Default.AccessTime, timeText, color = BlueBtn, fontWeight = FontWeight.Medium)
                    }
                    card.companyName?.let { InfoRow(Icons.Default.Business, it) }

                    card.objective?.let {
                        InfoRow(Icons.AutoMirrored.Filled.Chat, it, color = TextGray)
                    }
                }

                // ปุ่มเล็กมีให้เฉพาะนัด onsite ที่ยังไม่เช็คอิน (action="checkin") — เคสอื่นทั้งหมด
                // ใช้ปุ่ม "บันทึกผล" เต็มความกว้างด้านล่างแทน
                if (statusConf.action == "checkin" && !isCallOrOnline) {
                    Spacer(Modifier.width(10.dp))
                    ActionButton("Check-in", BlueBtn, onClick = onCheckin)
                }
            }

            val showReportButton = statusConf.action == "report" ||
                    (statusConf.action == "checkin" && isCallOrOnline)
            if (showReportButton) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick  = onReport,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape    = RoundedCornerShape(8.dp),
                    border   = if (hasNote) BorderStroke(1.dp, RedPrimary) else null,
                    colors   = if (hasNote)
                        ButtonDefaults.outlinedButtonColors(contentColor = RedPrimary)
                    else
                        ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text(
                        if (hasNote) "รายละเอียด" else "บันทึกผล",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

// ── ส่วนประกอบย่อยอื่นๆ (Helper UI) ──────────────────────────
@Composable
private fun Badge(label: String, icon: ImageVector, color: Color, bgColor: Color = Color(0xFFF0F0F0)) {
    Surface(shape = RoundedCornerShape(20.dp), color = bgColor) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String, color: Color = TextDark, fontWeight: FontWeight = FontWeight.Normal) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = TextGray, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 13.sp, color = color, fontWeight = fontWeight, maxLines = 1)
    }
}

@Composable
private fun ActionButton(label: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(90.dp).height(36.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = White)
    }
}

@Composable
private fun StatusTag(label: String, color: Color) {
    Surface(color = Color(0xFFF5F5F5), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.EventNote, null, tint = BorderGray, modifier = Modifier.size(64.dp))
            Text("ไม่มีแผนในเดือนนี้", color = TextGray, fontSize = 15.sp)
        }
    }
}

@Composable
private fun HomeTopBar(
    selectedMonth: YearMonth,
    authUser: AuthUser?,
    onMonthChange: (YearMonth) -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    val formatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale("th", "TH"))
    Column(modifier = Modifier.background(White)) {
        AppTopBar(
            title = "Home",
            onNotificationClick = onNotificationClick,
            onSettingsClick = onSettingsClick,
            onLogoutClick = onLogoutClick,
            user = authUser
        )
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Surface(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, BorderGray), color = White, onClick = { showMonthPicker = !showMonthPicker }, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(selectedMonth.format(formatter).replaceFirstChar { it.uppercase() }, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Icon(if (showMonthPicker) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextGray)
                }
            }
        }
        if (showMonthPicker) {
            MonthPicker(current = selectedMonth, onSelect = { onMonthChange(it); showMonthPicker = false })
        }
    }
}

@Composable
private fun MonthPicker(current: YearMonth, onSelect: (YearMonth) -> Unit) {
    var viewedYear by remember { mutableIntStateOf(current.year) }
    val months = (1..12).map { month -> YearMonth.of(viewedYear, month) }
    
    Surface(
        color = White,
        border = BorderStroke(1.dp, BorderGray),
        shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // ส่วนเลือกปี
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewedYear-- }) {
                    Icon(Icons.Default.ChevronLeft, "Previous Year", tint = RedPrimary)
                }
                Text(
                    text = "${viewedYear + 543}", // แสดงเป็น พ.ศ.
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextDark
                )
                IconButton(onClick = { viewedYear++ }) {
                    Icon(Icons.Default.ChevronRight, "Next Year", tint = RedPrimary)
                }
            }

            Spacer(Modifier.height(8.dp))

            // ส่วนเลือกเดือน (Grid 3x4)
            val monthNames = listOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
            
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (col in 0 until 3) {
                            val index = row * 3 + col
                            val m = months[index]
                            val isSelected = m == current
                            
                            Button(
                                onClick = { onSelect(m) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) RedPrimary else Color(0xFFF5F5F5),
                                    contentColor = if (isSelected) White else TextDark
                                ),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(monthNames[index], fontSize = 13.sp, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(8.dp))

            // ปุ่มกลับไปเดือนปัจจุบัน
            TextButton(
                onClick = { onSelect(YearMonth.now()) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("ไปยังเดือนปัจจุบัน", fontSize = 13.sp, color = RedPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    val sampleAuthUser = AuthUser(
        userId = "user123",
        email = "somchai.r@company.com",
        role = "Sales",
        teamId = "team_a"
    )

    val sampleCards = listOf(
        ActivityCard(
            activityId = "1",
            activityType = "onsite",
            projectName = "Project A",
            companyName = "Company A",
            contactName = "John Doe",
            objective = "First Visit",
            planStatus = "planned",
            plannedDate = "2024-01-16",
            plannedTime = "09:00",
            plannedEndTime = "11:00"
        ),
        ActivityCard(
            activityId = "2",
            activityType = "online",
            projectName = "Project B",
            companyName = "Company B",
            contactName = "Jane Smith",
            objective = "Follow up",
            planStatus = "completed",
            plannedDate = "2024-01-15",
            plannedTime = "14:00",
            plannedEndTime = "15:00"
        )
    )

    val groupedCards = sampleCards.groupBy { "16 JAN 2024" }

    val uiState = HomeUiState(
        selectedMonth = YearMonth.of(2024, 1),
        groupedCards = groupedCards,
        authUser = sampleAuthUser
    )

    SalesTrackingTheme {
    


    HomeScreenContent(
            uiState = uiState,
            onAddClick = {},
            onCardClick = {},
            onCheckin = {},
            onReport = {},
            onNotificationClick = {},
            onSettingsClick = {},
            onLogoutClick = {},
            currentTab = 0,
            onTabChange = {},
            onDelete = {},
            onMonthChange = {}
        )
    }
}
