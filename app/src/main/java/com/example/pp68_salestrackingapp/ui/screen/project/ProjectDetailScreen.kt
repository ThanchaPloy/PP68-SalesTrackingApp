package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.ProjectProgressBar
import com.example.pp68_salestrackingapp.ui.screen.auth.BorderGray
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

// นำเข้า ViewModel และ Data Classes
import com.example.pp68_salestrackingapp.ui.viewmodels.ProjectDetailViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.ProjectDetailUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.TaskItem
import com.example.pp68_salestrackingapp.ui.viewmodels.TeamMember
import com.example.pp68_salestrackingapp.ui.viewmodels.HistoryItem

// ── Colors ────────────────────────────────────────────────────
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFCC1D1D)
private val RedDark    = Color(0xFF8B0000)
private val BgPink     = Color(0xFFFFF5F5)
private val BgLight    = Color(0xFFF5F5F5)
private val BlueBtn    = Color(0xFF1976D2)

// avatar colors for team members
private val avatarColors = listOf(
    Color(0xFF26C6DA), Color(0xFF66BB6A),
    Color(0xFF3F51B5), Color(0xFF8BC34A),
    Color(0xFFFF7043), Color(0xFFAB47BC)
)

// ═══════════════════════════════════════════════════════════════
@Composable
fun ProjectDetailScreen(
    onBack:              () -> Unit,
    onEditProject:       (String) -> Unit = {},
    onCreateActivity:    (String) -> Unit = {},
    onSalesResultClick: (String) -> Unit,
    onInventoryClick:    (String) -> Unit = {},
    onRecordResult:      (projectId: String?, activityId: String?) -> Unit = { _, _ -> },
    onActivityClick:     (String) -> Unit = {},
    onCheckin:           (String) -> Unit = {},
    onFinish:            (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onLogoutClick:       () -> Unit = {},
    viewModel:           ProjectDetailViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(s.deleteSuccess) {
        if (s.deleteSuccess) {
            onBack()
        }
    }

    ProjectDetailContent(
        s = s,
        onBack = onBack,
        onEditProject = onEditProject,
        onCreateActivity = onCreateActivity,
        onInventoryClick = onInventoryClick,
        onSalesResultClick = onSalesResultClick,
        onRecordResult = onRecordResult,
        onActivityClick = onActivityClick,
        onCheckin = onCheckin,
        onFinish = onFinish,
        onDeleteProject = { viewModel.deleteProject() },
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onLogoutClick = {
            viewModel.logout()
            onLogoutClick()
        }
    )
}

@Composable
fun ProjectDetailContent(
    s: ProjectDetailUiState,
    onBack: () -> Unit,
    onEditProject: (String) -> Unit,
    onCreateActivity: (String) -> Unit,
    onInventoryClick: (String) -> Unit,
    onSalesResultClick: (String) -> Unit,
    onRecordResult: (String?, String?) -> Unit,
    onActivityClick: (String) -> Unit,
    onCheckin: (String) -> Unit = {},
    onFinish: (String) -> Unit = {},
    onDeleteProject: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onLogoutClick:       () -> Unit = {}
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("ลบโครงการ?", fontWeight = FontWeight.Bold) },
            text = { Text("คุณต้องการลบโครงการ ${s.project?.projectName} หรือไม่? การลบนี้ไม่สามารถย้อนกลับได้") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteProject()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                ) {
                    Text("ลบ", color = White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("ยกเลิก", color = TextGray)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Project",
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // FAB 1 — Inventory/Products
                FloatingActionButton(
                    onClick        = { s.project?.let { onInventoryClick(it.projectId) } },
                    containerColor = RedDark,
                    contentColor   = White,
                    shape          = CircleShape,
                    modifier       = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, "สินค้าในโครงการ",
                        modifier = Modifier.size(22.dp))
                }
                
                // FAB 2 — สร้างกิจกรรม (บวก)
                FloatingActionButton(
                    onClick        = { s.project?.let { onCreateActivity(it.projectId) } },
                    containerColor = RedDark,
                    contentColor   = White,
                    shape          = CircleShape,
                    modifier       = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.AddHome, "สร้างกิจกรรม",
                        modifier = Modifier.size(22.dp))
                }

                // FAB 3 — บันทึกผลการขาย (สมุดโน้ต)
                FloatingActionButton(
                    onClick        = { s.project?.let { onSalesResultClick(it.projectId) } },
                    containerColor = RedDark,
                    contentColor   = White,
                    shape          = CircleShape,
                    modifier       = Modifier.size(52.dp)
                ) {
                    Icon(Icons.Default.Assignment, "บันทึกผลการขาย",
                        modifier = Modifier.size(22.dp))
                }
            }
        },
        containerColor = BgLight
    ) { padding ->
        when {
            s.isLoading || s.isDeleting -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = RedPrimary) }

            s.project == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { Text("ไม่พบข้อมูลโครงการ", color = TextGray) }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                // ── 0. Back Button Row ──
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("กลับ", fontSize = 14.sp, color = TextDark)
                    }
                }

                // ── 1. Header card ──────────────────────────────
                item {
                    ProjectHeaderCard(
                        project     = s.project,
                        companyName = s.companyName,
                        onEdit      = { onEditProject(s.project.projectId) },
                        onDelete    = { showDeleteDialog = true }
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ── 2. Upcoming Tasks ───────────────────────────
                item {
                    SectionHeader(
                        icon  = Icons.AutoMirrored.Filled.Assignment,
                        title = "แผนงานที่กำลังจะมาถึง",
                        sub   = "กิจกรรมที่วางแผนไว้ในอนาคต"
                    )
                }
                
                if (s.upcomingTasks.isEmpty()) {
                    item {
                        EmptySection("ไม่มีแผนงานที่กำลังจะมาถึง",
                            modifier = Modifier.padding(horizontal = 16.dp))
                    }
                } else {
                    val upcomingSorted = s.upcomingTasks.sortedBy { it.plannedDate }
                    items(upcomingSorted) { task ->
                        TimelineTaskRow(
                            task = task,
                            onClick = { onActivityClick(task.activityId) },
                            onCheckin = { onCheckin(task.activityId) },
                            onFinish = { onFinish(task.activityId) },
                            isLast = task == upcomingSorted.last()
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }

                // ── 3. Sales Team ───────────────────────────────
                item {
                    SectionHeader(
                        icon  = Icons.Default.People,
                        title = "ทีมขาย",
                        sub   = null
                    )
                    SalesTeamRow(members = s.teamMembers)
                    Spacer(Modifier.height(16.dp))
                }

                // ── 4. History ──────────────────────────────────
                item {
                    SectionHeader(
                        icon  = Icons.Default.History,
                        title = "ประวัติกิจกรรม",
                        sub   = "กิจกรรมที่ดำเนินการเสร็จสิ้นแล้ว"
                    )
                }
                
                if (s.history.isEmpty()) {
                    item {
                        EmptySection("ยังไม่มีประวัติกิจกรรม",
                            modifier = Modifier.padding(horizontal = 16.dp))
                    }
                } else {
                    val historySorted = s.history.sortedByDescending { it.plannedDate }
                    items(historySorted) { item ->
                        TimelineHistoryRow(
                            item = item,
                            onClick = { onActivityClick(item.activityId) },
                            onFinish = { onFinish(item.activityId) },
                            onRecordResult = { onRecordResult(null, item.activityId) },
                            isLast = item == historySorted.last()
                        )
                    }
                }
            }
        }
    }
}

// ── Timeline Row for Upcoming ─────────────────────────────────
@Composable
private fun TimelineTaskRow(
    task: TaskItem, 
    onClick: () -> Unit, 
    onCheckin: () -> Unit,
    onFinish: () -> Unit,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Timeline Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1976D2))
                    .border(2.dp, White, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(Color.LightGray.copy(0.5f))
                )
            } else {
                Spacer(Modifier.height(24.dp))
            }
        }
        
        Spacer(Modifier.width(8.dp))
        
        TaskCard(task = task, onClick = onClick, onCheckin = onCheckin, onFinish = onFinish)
    }
}

// ── Timeline Row for History ──────────────────────────────────
@Composable
private fun TimelineHistoryRow(
    item: HistoryItem, 
    onClick: () -> Unit, 
    onFinish: () -> Unit,
    onRecordResult: () -> Unit,
    isLast: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Timeline Column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (item.planStatus.lowercase() == "checked_in") Color(0xFF2E7D32) else RedPrimary)
                    .border(2.dp, White, CircleShape)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(Color.LightGray.copy(0.5f))
                )
            } else {
                Spacer(Modifier.height(24.dp))
            }
        }
        
        Spacer(Modifier.width(8.dp))
        
        HistoryCard(item = item, onClick = onClick, onFinish = onFinish, onRecordResult = onRecordResult)
    }
}

// ── Header card ───────────────────────────────────────────────
@Composable
private fun ProjectHeaderCard(
    project:     Project,
    companyName: String,
    onEdit:      () -> Unit,
    onDelete:    () -> Unit
) {
    Surface(
        color    = White,
        modifier = Modifier.fillMaxWidth(),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        project.projectName,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 20.sp,
                        color      = TextDark
                    )
                    Text(
                        "อ้างอิง: ${project.projectNumber}",
                        fontSize = 12.sp,
                        color    = TextGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Business, null,
                            tint     = TextGray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(companyName, fontSize = 14.sp, color = TextDark)
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "แก้ไข", tint = RedPrimary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "ลบ", tint = RedPrimary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("สถานะ", fontSize = 12.sp, color = TextGray)
                    ProjectStatusBadge(status = project.projectStatus ?: "ไม่ระบุ")
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("มูลค่าที่คาดหวัง", fontSize = 12.sp, color = TextGray)
                    Text(
                        "฿ ${project.expectedValue ?: 0.0}",
                        fontWeight = FontWeight.Bold,
                        color      = RedPrimary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("วันที่เริ่ม", fontSize = 12.sp, color = TextGray)
                    Text(
                        project.startDate ?: "-",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextDark
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("วันที่คาดว่าจะปิด", fontSize = 12.sp, color = TextGray)
                    Text(
                        project.closingDate ?: "-",
                        fontSize   = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color      = TextDark
                    )
                }
            }

            // ✅ เพิ่ม Progress Bar หรือ Loss Reason
            if (project.projectStatus !in listOf("Lost", "Failed")) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFFFFFFF).copy(alpha = 0.5f))
                Spacer(Modifier.height(12.dp))
                Text(
                    "ความคืบหน้าการขาย",
                    fontSize = 12.sp,
                    color    = TextGray
                )
                Spacer(Modifier.height(6.dp))
                ProjectProgressBar(
                    progressPct = project.progressPct,
                    status      = project.projectStatus,
                    modifier    = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Cancel, null,
                                tint     = Color(0xFF991B1B),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (project.projectStatus == "Lost")
                                    "โครงการนี้ไม่ได้รับงาน" else "โครงการนี้ถูกยกเลิก",
                                fontSize   = 13.sp,
                                color      = Color(0xFF991B1B),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        
                        // แสดงสาเหตุ (Loss Reason)
                        if (!project.lossReason.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(start = 24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Info, null,
                                    tint = Color(0xFF991B1B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "สาเหตุ: ${project.lossReason}",
                                    fontSize = 12.sp,
                                    color = Color(0xFF991B1B),
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectStatusBadge(status: String) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, sub: String?) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
        }
        sub?.let {
            Text(it, fontSize = 12.sp, color = TextGray)
        }
    }
}

@Composable
private fun EmptySection(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = White,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(text, color = TextGray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun TaskCard(task: TaskItem, onClick: () -> Unit, onCheckin: () -> Unit, onFinish: () -> Unit) {
    Surface(
        onClick = onClick,
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(task.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                Text(task.plannedDate ?: "-", fontSize = 12.sp, color = TextGray)
            }
            task.description?.let {
                Text(it, fontSize = 13.sp, color = TextGray, modifier = Modifier.padding(top = 4.dp))
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCheckin,
                    colors = ButtonDefaults.buttonColors(containerColor = BlueBtn),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("เช็คอิน", fontSize = 11.sp)
                }
                
                OutlinedButton(
                    onClick = onFinish,
                    border = androidx.compose.foundation.BorderStroke(1.dp, BlueBtn),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("เสร็จสิ้น", fontSize = 11.sp, color = BlueBtn)
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(item: HistoryItem, onClick: () -> Unit, onFinish: () -> Unit, onRecordResult: () -> Unit) {
    Surface(
        onClick = onClick,
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
                    Text(item.plannedDate ?: "-", fontSize = 12.sp, color = TextGray)
                }
                
                Surface(
                    color = if(item.planStatus.lowercase() == "completed") Color(0xFFE8F5E9) else Color(0xFFE3F2FD),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if(item.planStatus.lowercase() == "completed") "เสร็จสิ้น" else item.planStatus.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if(item.planStatus.lowercase() == "completed") Color(0xFF2E7D32) else Color(0xFF1976D2)
                    )
                }
            }

            Row(
                modifier = Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when(item.activityType.lowercase()){
                        "call" -> Icons.Default.Phone
                        "email" -> Icons.Default.Email
                        else -> Icons.Default.Person
                    },
                    null, tint = TextGray, modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(item.contactName ?: "ไม่ระบุชื่อผู้ติดต่อ", fontSize = 13.sp, color = TextGray)
            }
            
            Spacer(Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.planStatus.lowercase() != "completed") {
                    Button(
                        onClick = onFinish,
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("เสร็จสมบูรณ์", fontSize = 11.sp)
                    }
                }
                
                TextButton(onClick = onRecordResult) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(14.dp), tint = BlueBtn)
                    Spacer(Modifier.width(4.dp))
                    Text("ดูรายงาน", fontSize = 12.sp, color = BlueBtn)
                }
            }
        }
    }
}

@Composable
private fun SalesTeamRow(members: List<TeamMember>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy((-8).dp) // Overlap effect
    ) {
        members.take(5).forEachIndexed { idx, member ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(avatarColors[idx % avatarColors.size])
                    .border(2.dp, White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    member.fullName.take(1).uppercase(),
                    color = White, fontWeight = FontWeight.Bold
                )
            }
        }
        if (members.size > 5) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .border(2.dp, White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+${members.size - 5}", fontSize = 12.sp, color = TextDark)
            }
        }
        
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.Center, modifier = Modifier.height(40.dp)) {
            Text("${members.size} สมาชิก", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("ได้รับมอบหมายในโครงการนี้", fontSize = 11.sp, color = TextGray)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectDetailPreview() {
    SalesTrackingTheme {
        ProjectDetailContent(
            s = ProjectDetailUiState(
                project = Project(
                    projectId = "P1",
                    projectNumber = "PRJ-2023-001",
                    projectName = "Building A Renovation",
                    custId = "C1",
                    branchId = "B1",
                    projectStatus = "In Progress",
                    expectedValue = 2500000.0,
                    opportunityScore = "High",
                    startDate = "2023-01-01",
                    closingDate = "2023-12-31"
                ),
                companyName = "Global Tech Solution Co., Ltd.",
                upcomingTasks = listOf(
                    TaskItem("T1", "Site Survey", "Check building structure", "2024-06-20"),
                    TaskItem("T2", "Quotation Draft", "Prepare document for client", "2024-06-25")
                ),
                teamMembers = listOf(
                    TeamMember("U1", "John Doe"),
                    TeamMember("U2", "Jane Smith"),
                    TeamMember("U3", "Bob Wilson")
                ),
                history = listOf(
                    HistoryItem("H1", "First Meeting", "Initial requirement discussion", "2024-05-10", "completed", "meeting", "Khun Somchai"),
                    HistoryItem("H2", "Follow-up Call", "Discussing the draft proposal", "2024-05-15", "completed", "call", "Khun Somchai")
                )
            ),
            onBack = {},
            onEditProject = {},
            onCreateActivity = {},
            onInventoryClick = {},
            onSalesResultClick = {},
            onRecordResult = { _, _ -> },
            onActivityClick = {}
        )
    }
}
