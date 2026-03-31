package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.notification.NotificationViewModel

// ── Colors ────────────────────────────────────────────────────
private val RedPrimary   = Color(0xFFCC1D1D)
private val TextDark     = Color(0xFF1A1A1A)
private val TextGray     = Color(0xFF888888)
private val BgLight      = Color(0xFFF8F9FA)
private val OrangeIcon   = Color(0xFFE65100)
private val OrangeBg     = Color(0xFFFFF3E0)
private val GreenIcon    = Color(0xFF2E7D32)
private val GreenBg      = Color(0xFFE8F5E9)
private val BlueIcon      = Color(0xFF1976D2)
private val BlueBg        = Color(0xFFE3F2FD)
private val White         = Color.White

// ── Data Models ───────────────────────────────────────────────
enum class NotiType { REMINDER, FINISHED, REPORT_WEEKLY, REPORT_MONTHLY }
enum class NotiAction { CHECK_IN, REPORT, VIEW_WEEKLY, VIEW_MONTHLY }

data class NotificationItem(
    val id: String,
    val type: NotiType,
    val title: String,
    val timeLabel: String,
    val subtitle: String,
    val location: String,
    val action: NotiAction,
    val isToday: Boolean
)

@Composable
fun NotificationScreen(
    onBackClick: () -> Unit,
    viewModel: NotificationViewModel = hiltViewModel(),
    onReport: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onWeeklyReport: () -> Unit,
    onMonthlyReport: () -> Unit,
    onViewDetails: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Refactored to pass state to a stateless Composable
    NotificationContent(
        notificationList = uiState.notifications,
        onBackClick = onBackClick,
        onReport = onReport,
        onCheckIn = onCheckIn,
        onWeeklyReport = onWeeklyReport,
        onMonthlyReport = onMonthlyReport,
        onViewDetails = onViewDetails
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationContent(
    notificationList: List<NotificationItem>,
    onBackClick: () -> Unit,
    onReport: (String) -> Unit,
    onCheckIn: (String) -> Unit,
    onWeeklyReport: () -> Unit,
    onMonthlyReport: () -> Unit,
    onViewDetails: (String) -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.White
                )
            )
        },
        containerColor = BgLight
    ) { padding ->
        if (notificationList.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("ไม่มีการแจ้งเตือน", color = TextGray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                val todayItems = notificationList.filter { it.isToday }
                val earlierItems = notificationList.filter { !it.isToday }

                if (todayItems.isNotEmpty()) {
                    item { SectionHeader("TODAY") }
                    items(todayItems) { item ->
                        NotificationCard(
                            item = item,
                            onCheckIn = onCheckIn,
                            onReport = onReport,
                            onWeeklyReport = onWeeklyReport,
                            onMonthlyReport = onMonthlyReport,
                            onViewDetails = onViewDetails
                        )
                    }
                }

                if (earlierItems.isNotEmpty()) {
                    item { SectionHeader("EARLIER") }
                    items(earlierItems) { item ->
                        NotificationCard(
                            item = item,
                            onCheckIn = onCheckIn,
                            onReport = onReport,
                            onWeeklyReport = onWeeklyReport,
                            onMonthlyReport = onMonthlyReport,
                            onViewDetails = onViewDetails
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextGray,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun NotificationCard(
    item: NotificationItem,
    onCheckIn: (String) -> Unit,
    onReport: (String) -> Unit,
    onWeeklyReport: () -> Unit,
    onMonthlyReport: () -> Unit,
    onViewDetails: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Icon selection
                val (iconBoxColor, iconColor, icon) = when (item.type) {
                    NotiType.REMINDER -> Triple(OrangeBg, OrangeIcon, Icons.Default.AccessTime)
                    NotiType.FINISHED -> Triple(GreenBg, GreenIcon, Icons.Default.CheckCircle)
                    NotiType.REPORT_WEEKLY -> Triple(BlueBg, BlueIcon, Icons.AutoMirrored.Filled.Assignment)
                    NotiType.REPORT_MONTHLY -> Triple(BlueBg, BlueIcon, Icons.AutoMirrored.Filled.EventNote)
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBoxColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(24.dp))
                }

                Spacer(Modifier.width(12.dp))

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = item.title,
                            color = TextDark,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        if (item.timeLabel.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = when {
                                    item.timeLabel.contains("นาที") &&
                                            !item.timeLabel.contains("ชั่วโมง") -> Color(0xFFFFEBEE)
                                    item.timeLabel.contains("เลย") -> Color(0xFFEEEEEE)
                                    else -> Color(0xFFFFF3E0)
                                }
                            ) {
                                Text(
                                    text = item.timeLabel,
                                    color = when {
                                        item.timeLabel.contains("นาที") &&
                                                !item.timeLabel.contains("ชั่วโมง") -> RedPrimary
                                        item.timeLabel.contains("เลย") -> TextGray
                                        else -> OrangeIcon
                                    },
                                    fontSize   = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Text(text = item.subtitle, color = TextGray, fontSize = 13.sp)
                    if (item.location.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(3.dp).background(TextGray, CircleShape))
                            Spacer(Modifier.width(4.dp))
                            Text(text = item.location, color = TextGray, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = Color(0xFFF1F3F4))
            Spacer(Modifier.height(16.dp))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        when (item.action) {
                            NotiAction.CHECK_IN -> onCheckIn(item.id)
                            NotiAction.REPORT -> onReport(item.id)
                            NotiAction.VIEW_WEEKLY -> onWeeklyReport()
                            NotiAction.VIEW_MONTHLY -> onMonthlyReport()
                        }
                    },
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    val btnText = when (item.action) {
                        NotiAction.CHECK_IN -> "Check-in"
                        NotiAction.REPORT -> "บันทึกผล"
                        NotiAction.VIEW_WEEKLY -> "Weekly Report"
                        NotiAction.VIEW_MONTHLY -> "Monthly Report"
                    }
                    Text(text = btnText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                
                if (item.type != NotiType.REPORT_WEEKLY && item.type != NotiType.REPORT_MONTHLY) {
                    OutlinedButton(
                        onClick = { onViewDetails(item.id) },
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0))
                    ) {
                        Text("View Details", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationScreenPreview() {
    SalesTrackingTheme {
        // Use the stateless NotificationContent for the Preview to avoid ViewModel instantiation issues
        NotificationContent(
            notificationList = listOf(
                NotificationItem(
                    id = "1",
                    type = NotiType.REMINDER,
                    title = "Meeting with Client",
                    timeLabel = "อีก 10 นาที",
                    subtitle = "Project Alpha",
                    location = "Office A",
                    action = NotiAction.CHECK_IN,
                    isToday = true
                ),
                NotificationItem(
                    id = "2",
                    type = NotiType.REPORT_WEEKLY,
                    title = "Weekly Report",
                    timeLabel = "Action required",
                    subtitle = "Due today",
                    location = "",
                    action = NotiAction.VIEW_WEEKLY,
                    isToday = true
                )
            ),
            onBackClick = {},
            onReport = {},
            onCheckIn = {},
            onWeeklyReport = {},
            onMonthlyReport = {},
            onViewDetails = {}
        )
    }
}
