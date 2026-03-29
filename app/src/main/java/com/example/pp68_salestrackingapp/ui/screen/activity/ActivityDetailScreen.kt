package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
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
import com.example.pp68_salestrackingapp.data.model.MasterActDto
import com.example.pp68_salestrackingapp.data.model.PlanItemDto
import com.example.pp68_salestrackingapp.data.model.SalesActivity
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityDetailViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ActivityDetailUiState

private val RedPrimary = Color(0xFFCC1D1D)
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val BgField    = Color(0xFFF8F8F8)
private val White      = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    activityId: String,
    onBack:     () -> Unit,
    onEdit:     (String) -> Unit = {},
    onCheckin:  (String) -> Unit = {},
    onFinish:   () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onLogoutClick:       () -> Unit = {},
    viewModel: ActivityDetailViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(activityId) {
        viewModel.loadActivity(activityId)
    }

    LaunchedEffect(s.isFinished) {
        if (s.isFinished) {
            onFinish()
        }
    }

    ActivityDetailContent(
        s            = s,
        onBack       = onBack,
        onEdit       = { onEdit(activityId) },
        onCheckin    = { onCheckin(activityId) },
        onToggleItem = { viewModel.toggleItem(it) },
        onFinish     = { viewModel.finishActivity() },
        onClearError = { viewModel.clearError() },
        onNotificationClick = onNotificationClick,
        onSettingsClick     = onSettingsClick,
        onLogoutClick       = onLogoutClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailContent(
    s: ActivityDetailUiState,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onCheckin: () -> Unit,
    onToggleItem: (Int) -> Unit,
    onFinish: () -> Unit,
    onClearError: () -> Unit,
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(s.error) {
        s.error?.let {
            snackbarHostState.showSnackbar(it)
            onClearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = "Activity Details",
                onBackClick = onBack,
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick
            )
        }
    ) { padding ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(s.activity?.status ?: "planned")
                    Spacer(Modifier.weight(1f))
                    if (s.activity?.status != "completed") {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "Edit", tint = RedPrimary)
                        }
                    }
                }
                
                InfoCard(s)

                Text("ACTIVITY OBJECTIVES", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextGray)
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgField, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.LightGray.copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    if (s.planItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("No objectives planned", color = TextGray, fontSize = 14.sp)
                        }
                    } else {
                        s.planItems.forEach { item ->
                            val isCompleted = s.activity?.status == "completed"
                            // ✅ แก้: ให้ tick ได้ทั้ง planned, checked_in ไม่ใช่แค่ checked_in
                            val canToggle = s.activity?.status == "planned" ||
                                    s.activity?.status == "checked_in"

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(
                                        if (canToggle) Modifier.clickable { onToggleItem(item.masterId) }
                                        else Modifier
                                    )
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (canToggle) {
                                    // ✅ planned และ checked_in → tick ได้
                                    val isSelected = s.selectedItemIds.contains(item.masterId)
                                    Checkbox(
                                        checked         = isSelected,
                                        onCheckedChange = { onToggleItem(item.masterId) },
                                        colors = CheckboxDefaults.colors(checkedColor = RedPrimary)
                                    )
                                } else {
                                    // completed → แสดงผลอย่างเดียว
                                    Icon(
                                        if (item.isDone) Icons.Default.CheckCircle
                                        else Icons.Default.RadioButtonUnchecked,
                                        null,
                                        tint = if (item.isDone) Color(0xFF2E7D32) else TextGray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    item.masterDetails?.actName ?: "Unknown Objective",
                                    color = when {
                                        isCompleted && item.isDone -> Color(0xFF2E7D32)
                                        canToggle && s.selectedItemIds.contains(item.masterId) -> TextDark
                                        else -> TextGray
                                    },
                                    fontSize   = 14.sp,
                                    fontWeight = if (s.selectedItemIds.contains(item.masterId))
                                        FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                when (s.activity?.status) {
                    "planned" -> {
                        val isOnsiteVisit = s.activity.activityType == "onsite"

                        if (isOnsiteVisit) {
                            // ✅ onsite เท่านั้น → Check-in (GPS)
                            Button(
                                onClick  = onCheckin,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Check-in", fontWeight = FontWeight.Bold, color = White)
                            }
                        } else {
                            // ✅ online / call → Finish ได้เลย ไม่ต้อง check-in
                            Button(
                                onClick  = onFinish,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape    = RoundedCornerShape(12.dp),
                                enabled  = !s.isFinishing
                            ) {
                                if (s.isFinishing) {
                                    CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp))
                                } else {
                                    Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Finish Activity", fontWeight = FontWeight.Bold, color = White)
                                }
                            }
                        }
                    }

                    "checked_in" -> {
                        // ✅ checked_in → Finish Activity
                        Button(
                            onClick  = onFinish,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape    = RoundedCornerShape(12.dp),
                            enabled  = !s.isFinishing
                        ) {
                            if (s.isFinishing) {
                                CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Finish Activity", fontWeight = FontWeight.Bold, color = White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (label, color) = when (status) {
        "planned" -> "PLANNED" to Color(0xFF1976D2)
        "checked_in" -> "CHECKED IN" to Color(0xFF2E7D32)
        "completed" -> "COMPLETED" to Color(0xFF546E7A)
        else -> status.uppercase() to TextGray
    }
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun InfoCard(s: ActivityDetailUiState) {
    val act = s.activity ?: return
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgField), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(RedPrimary.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Work, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("ACTIVITY ID", fontSize = 10.sp, color = TextGray, fontWeight = FontWeight.Bold)
                    Text(act.activityId, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            HorizontalDivider(color = Color.LightGray.copy(0.3f))
            DetailRow(Icons.AutoMirrored.Filled.Label, "Detail", act.detail ?: "No Detail")
            DetailRow(Icons.Default.CalendarMonth, "Date", act.activityDate)
            DetailRow(Icons.Default.AccessTime, "Type", act.activityType)
        }
    }
}

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = TextGray, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: ", fontSize = 13.sp, color = TextGray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextDark)
    }
}

@Preview(showBackground = true)
@Composable
fun ActivityDetailScreenPreview() {
    val sampleActivity = SalesActivity(
        activityId = "ACT001",
        projectId = "PRJ001",
        customerId = "CUST001",
        userId = "USER001",
        activityType = "Meeting",
        activityDate = "2023-10-27",
        detail = "Project Kick-off Meeting",
        status = "planned"
    )
    
    val samplePlanItems = listOf(
        PlanItemDto(masterId = 1, masterDetails = MasterActDto("Introduce Team"), isDone = false),
        PlanItemDto(masterId = 2, masterDetails = MasterActDto("Discuss Scope"), isDone = false),
        PlanItemDto(masterId = 3, masterDetails = MasterActDto("Timeline Review"), isDone = false)
    )

    val uiState = ActivityDetailUiState(
        isLoading = false,
        activity = sampleActivity,
        planItems = samplePlanItems
    )

    SalesTrackingTheme {
        ActivityDetailContent(
            s = uiState,
            onBack = {},
            onEdit = {},
            onCheckin = {},
            onToggleItem = {},
            onFinish = {},
            onClearError = {}
        )
    }
}
