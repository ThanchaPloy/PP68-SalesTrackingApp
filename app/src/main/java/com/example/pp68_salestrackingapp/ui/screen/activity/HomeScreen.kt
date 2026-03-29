package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.background
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
private val statusConfigs = mapOf(
    "planned"    to StatusConfig("กำลังดำเนินการ", GreenStatus,  Color(0xFFE8F5E9), "checkin"),
    "checked_in" to StatusConfig("กำลังดำเนินการ", GreenStatus,  Color(0xFFE8F5E9), "finish"),
    "completed"  to StatusConfig("เสร็จสิ้น",        GrayStatus, Color(0xFFECEFF1), "report"),
    "cancelled"  to StatusConfig("รอรายงานผล",    OrangeStatus, Color(0xFFFFF3E0), "report")
)

@Composable
fun HomeScreen(
    onAddClick:          () -> Unit,
    onCardClick:         (String) -> Unit,
    onCheckin:           (String) -> Unit,
    onFinish:            (String) -> Unit,
    onReport:            (String) -> Unit,
    onNotificationClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onLogoutClick:       () -> Unit = {},
    currentTab:          Int,
    onTabChange:         (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeScreenContent(
        uiState             = uiState,
        onAddClick          = onAddClick,
        onCardClick         = onCardClick,
        onCheckin           = onCheckin,
        onFinish            = onFinish,
        onReport            = onReport,
        onDelete            = { viewModel.deleteActivity(it) },
        onNotificationClick = onNotificationClick,
        onSettingsClick     = onSettingsClick,
        onLogoutClick       = { viewModel.logout(); onLogoutClick() },
        currentTab          = currentTab,
        onTabChange         = onTabChange,
        onMonthChange       = { viewModel.selectMonth(it) }
    )
}

@Composable
private fun HomeScreenContent(
    uiState:             HomeUiState,
    onAddClick:          () -> Unit,
    onCardClick:         (String) -> Unit,
    onCheckin:           (String) -> Unit,
    onFinish:            (String) -> Unit,
    onReport:            (String) -> Unit,
    onDelete:            (String) -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick:     () -> Unit,
    onLogoutClick:       () -> Unit,
    currentTab:          Int,
    onTabChange:         (Int) -> Unit,
    onMonthChange:       (YearMonth) -> Unit
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
        when {
            uiState.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = RedPrimary) }

            uiState.groupedCards.isEmpty() -> EmptyState(Modifier.padding(padding))

            else -> LazyColumn(
                modifier            = Modifier.fillMaxSize().padding(padding),
                contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
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
                            onFinish  = { onFinish(card.activityId) },
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

// ── Activity Card ─────────────────────────────────────────────
@Composable
fun ActivityCard(
    card:      ActivityCard,
    onClick:   () -> Unit,
    onCheckin: () -> Unit,
    onFinish:  () -> Unit,
    onReport:  () -> Unit,
    onDelete:  () -> Unit
) {
    val typeConf   = typeConfigs[card.activityType]
        ?: TypeConfig(card.activityType.uppercase(), Icons.Default.Event, TextGray)
    val statusConf = statusConfigs[card.planStatus]
        ?: StatusConfig(card.planStatus, TextGray, BgLight, null)

    val hasNote    = !card.weeklyNote.isNullOrBlank()
    val canDelete  = card.planStatus == "planned"

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
                    card.companyName?.let { InfoRow(Icons.Default.Business, it) }
                    card.contactName?.let { InfoRow(Icons.Default.Person, it) }
                    card.objective?.let {
                        InfoRow(Icons.AutoMirrored.Filled.Chat, it, color = TextGray)
                    }
                }

                statusConf.action?.let { action ->
                    Spacer(Modifier.width(10.dp))
                    val isCallOrOnline = card.activityType == "call" || card.activityType == "online"
                    when (action) {
                        "checkin" -> {
                            if (isCallOrOnline) ActionButton("Finish", BlueBtn, onClick = onFinish)
                            else ActionButton("Check-in", BlueBtn, onClick = onCheckin)
                        }
                        "finish"  -> ActionButton("Finish", BlueBtn, onClick = onFinish)
                    }
                }
            }

            if (statusConf.action == "report") {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick  = onReport,
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape    = RoundedCornerShape(8.dp),
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
            Surface(shape = RoundedCornerShape(8.dp), border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray), color = White, onClick = { showMonthPicker = !showMonthPicker }, modifier = Modifier.fillMaxWidth()) {
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
    val months = (-6..6).map { current.plusMonths(it.toLong()) }
    Surface(color = White, border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column {
            months.forEach { m ->
                TextButton(onClick = { onSelect(m) }, modifier = Modifier.fillMaxWidth()) {
                    Text(m.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("th", "TH"))).replaceFirstChar { it.uppercase() }, color = if (m == current) RedPrimary else TextDark)
                }
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
            onFinish = {},
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
