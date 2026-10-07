package com.example.pp68_salestrackingapp.ui.screen.activity

import com.example.pp68_salestrackingapp.utils.AppointmentPolicy
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

// ✅ Helper function สำหรับเปิด Google Maps
fun openMap(context: Context, lat: Double?, lng: Double?, label: String = "Location") {
    if (lat == null || lng == null || lat == 0.0 || lng == 0.0) return
    val uri = Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
    val intent = Intent(Intent.ACTION_VIEW, uri)
    context.startActivity(intent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityDetailScreen(
    activityId: String,
    onBack:     () -> Unit,
    onEdit:     (String) -> Unit = {},
    onCheckin:  (String) -> Unit = {},
    // นำไปหน้าบันทึกผลตรง ๆ — ไม่มี API call ระหว่างทางแล้ว การเปลี่ยนสถานะเป็น completed
    // เกิดขึ้นตอนบันทึกผลสำเร็จ (SalesResultViewModel.save()) ไม่ใช่ตอนกดปุ่มนี้
    onSaveResult: (String) -> Unit = {},
    // นัดที่ขาดไปแล้วบันทึกผลผ่านนัดไม่ได้ (AppointmentPolicy.canCreateResult ปฏิเสธด้วย
    // MISSED_ONSITE_RESULT_NOT_ALLOWED) แต่การเข้าพบที่เกิดขึ้นจริงยังต้องบันทึกได้
    // ทางที่เหลือคือบันทึกผูกกับโครงการตรง ๆ ซึ่งข้ามกติกานี้โดยตั้งใจ
    onSaveStandaloneResult: (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onLogoutClick:       () -> Unit = {},
    viewModel: ActivityDetailViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadActivity(activityId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ActivityDetailContent(
        s            = s,
        onBack       = onBack,
        onEdit       = { onEdit(activityId) },
        onCheckin    = { onCheckin(activityId) },
        onToggleItem = { viewModel.toggleItem(it) },
        onSaveResult = { onSaveResult(activityId) },
        onSaveStandaloneResult = { projectId -> onSaveStandaloneResult(projectId) },
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
    onSaveResult: () -> Unit,
    onSaveStandaloneResult: (String) -> Unit = {},
    onClearError: () -> Unit,
    onNotificationClick: () -> Unit = {},
    onSettingsClick:     () -> Unit = {},
    onLogoutClick:       () -> Unit = {}
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
                // ตัดสินใน ViewModel จากค่าดิบ — ในนี้ plannedTime ถูกแปลงเป็น "02:00 PM" แล้ว
                // ถ้าคิดกติกาตรงนี้จะอ่านบ่ายสองเป็นตีสอง
                val effectiveStatus = s.effectiveStatus
                val editLocked = s.editDenialMessage != null
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(effectiveStatus)
                    Spacer(Modifier.weight(1f))
                    if (editLocked) {
                        Icon(
                            Icons.Default.Lock, "แก้ไขไม่ได้",
                            tint = TextGray, modifier = Modifier.size(20.dp)
                        )
                    } else {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, "แก้ไข", tint = RedPrimary)
                        }
                    }
                }
                s.editDenialMessage?.let {
                    Text(it, fontSize = 12.sp, color = TextGray)
                }
                
                InfoCard(s)

                Text("วัตถุประสงค์/เป้าหมายกิจกรรม", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = TextGray)
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BgField, RoundedCornerShape(12.dp))
                        .border(1.dp, Color.LightGray.copy(0.3f), RoundedCornerShape(12.dp))
                        .padding(8.dp)
                ) {
                    if (s.planItems.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("ยังไม่มีการวางแผนเป้าหมาย", color = TextGray, fontSize = 14.sp)
                        }
                    } else {
                        s.planItems.forEach { item ->
                            // ใช้ effectiveStatus ให้ตรงกับที่ปุ่มด้านล่างใช้ — เดิมไฟล์นี้ใช้สองมาตรฐานปนกัน
                            // นัดที่ขาดไปรวมอยู่ในชุดที่ติ๊กได้โดยตั้งใจ — กติกาเดียวกับที่ไม่ล็อกการบันทึกผล
                            // (แผนที่ขาดต้องบันทึกย้อนหลังได้) เดิมมันทำงานได้เพราะสถานะดิบยังเป็น planned โดยบังเอิญ
                            val isCompleted = effectiveStatus == "completed"
                            val canToggle = effectiveStatus == "planned" ||
                                    effectiveStatus == "checked_in" ||
                                    effectiveStatus == AppointmentPolicy.MISSING

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
                                    val isSelected = s.selectedItemIds.contains(item.masterId)
                                    Checkbox(
                                        checked         = isSelected,
                                        onCheckedChange = { onToggleItem(item.masterId) },
                                        colors = CheckboxDefaults.colors(checkedColor = RedPrimary)
                                    )
                                } else {
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
                                    item.masterDetails?.actName ?: "ไม่ระบุเป้าหมาย",
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

                // ✅ ไม่มีปุ่ม "Finish" อีกต่อไป — เดิมกดแล้วแค่เปลี่ยนสถานะเป็น completed โดยไม่มีข้อมูล
                // อะไรเกิดขึ้นจริง แล้วเด้งกลับ Home ให้แตะการ์ดซ้ำเพื่อไปหน้าบันทึกผล ซ้ำซ้อนโดยใช่เหตุ
                // สถานะ completed ตอนนี้เกิดจากบันทึกผลสำเร็จเท่านั้น (ดู SalesResultViewModel.save())
                when (effectiveStatus) {
                    "planned" -> {
                        val isOnsiteVisit = s.activity?.activityType == "onsite"

                        if (isOnsiteVisit) {
                            Button(
                                onClick  = onCheckin,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.LocationOn, null, tint = White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("เช็คอิน", fontWeight = FontWeight.Bold, color = White)
                            }
                        } else {
                            Button(
                                onClick  = onSaveResult,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                shape    = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("ไปหน้าบันทึกผล", fontWeight = FontWeight.Bold, color = White)
                            }
                        }
                    }

                    "checked_in" -> {
                        Button(
                            onClick  = onSaveResult,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape    = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("ไปหน้าบันทึกผล", fontWeight = FontWeight.Bold, color = White)
                        }
                    }

                    // ขาดนัด = นัดหน้างานที่พ้นวันนัดแล้วและไม่เคยเช็คอิน
                    //
                    // เดิมกิ่งนี้รวมอยู่กับ checked_in แล้วโชว์ปุ่ม "ไปหน้าบันทึกผล" เหมือนกัน
                    // แต่ AppointmentPolicy.canCreateResult ปฏิเสธนัดแบบนี้เสมอด้วย
                    // MISSED_ONSITE_RESULT_NOT_ALLOWED และ MISSING เกิดได้เฉพาะนัด onsite
                    // (ประเภทอื่น requiresCheckIn เป็น false จึงไม่มีวันเป็น MISSING)
                    // ปุ่มนั้นจึงตันเสมอ 100% ผู้ใช้กรอกสรุปการเข้าพบ แนบรูป แล้วค่อยโดนปฏิเสธ
                    // ตอนกดบันทึก — เสียเวลากรอกฟรีทั้งฟอร์ม
                    //
                    // การเข้าพบที่เกิดขึ้นจริงยังต้องบันทึกได้ ทางที่เหลือคือบันทึกผูกกับโครงการ
                    // ตรง ๆ (ResultMode.STANDALONE) ซึ่ง SalesResultViewModel.save() ข้ามกติกา
                    // ของนัดโดยตั้งใจ จึงชี้ทางไปตรงนั้นแทนการซ่อนปุ่มเฉย ๆ
                    AppointmentPolicy.MISSING -> {
                        val linkedProjectId = s.activity?.projectId?.takeIf { it.isNotBlank() }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFC62828).copy(0.08f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "ขาดนัด — ไม่มีการเช็คอินภายในวันนัด",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFFC62828)
                            )
                            Text(
                                if (linkedProjectId != null) {
                                    "นัดนี้บันทึกผลผ่านนัดหมายไม่ได้แล้ว หากเข้าพบจริง " +
                                        "ให้บันทึกการเข้าพบผูกกับโครงการแทน"
                                } else {
                                    "นัดนี้บันทึกผลผ่านนัดหมายไม่ได้แล้ว และยังไม่ได้ผูกกับโครงการใด " +
                                        "หากเข้าพบจริง ให้เปิดหน้าโครงการที่เกี่ยวข้องแล้วบันทึกการเข้าพบจากตรงนั้น"
                                },
                                fontSize = 13.sp,
                                color = TextGray
                            )
                            if (linkedProjectId != null) {
                                Button(
                                    onClick  = { onSaveStandaloneResult(linkedProjectId) },
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    shape    = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, null, tint = White, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("บันทึกการเข้าพบที่โครงการ", fontWeight = FontWeight.Bold, color = White)
                                }
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
        "planned" -> "planned" to Color(0xFF1976D2)
        "checked_in" -> "checked_in" to Color(0xFF2E7D32)
        "completed" -> "completed" to Color(0xFF546E7A)
        AppointmentPolicy.MISSING -> "ขาดนัด" to Color(0xFFC62828)
        else -> status.uppercase() to TextGray
    }
    Surface(color = color.copy(0.1f), shape = RoundedCornerShape(4.dp)) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun InfoCard(s: ActivityDetailUiState) {
    val act = s.activity ?: return
    val context = LocalContext.current

    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = BgField), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(RedPrimary.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Topic, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("หัวข้อกิจกรรม", fontSize = 10.sp, color = TextGray, fontWeight = FontWeight.Bold)
                    Text(act.detail ?: "ไม่ระบุหัวข้อ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            HorizontalDivider(color = Color.LightGray.copy(0.3f))
            
            DetailRow(Icons.Default.Person, "ผู้ติดต่อ", act.contactName ?: "N/A")
            DetailRow(Icons.Default.Business, "บริษัท", act.companyName ?: "N/A")
            DetailRow(Icons.Default.Work, "โครงการ", act.projectName ?: "N/A")
            // จัดรูปแบบวันที่ตอนแสดงผลเท่านั้น — ค่าใน state ต้องคง ISO ไว้ให้กติกา W6 ข้างบน
            // (effective/isEditLocked) parse ได้ ไม่งั้นทั้งสองข้อพังเงียบ ๆ
            DetailRow(Icons.Default.CalendarToday, "วันที่", formatDateForDisplay(act.activityDate))
            
            val timeRange = if (!act.plannedTime.isNullOrBlank()) {
                if (!act.plannedEndTime.isNullOrBlank()) "${act.plannedTime} - ${act.plannedEndTime}"
                else act.plannedTime
            } else "N/A"
            DetailRow(Icons.Default.AccessTime, "เวลา", timeRange)
            
            DetailRow(Icons.Default.Category, "ประเภท", act.activityType)

            // ✅ เพิ่มลิงก์แผนที่
            if (act.plannedLat != null && act.plannedLong != null && act.plannedLat != 0.0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { openMap(context, act.plannedLat, act.plannedLong, act.companyName ?: "Activity") }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Map, null, tint = RedPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ดูตำแหน่งบนแผนที่", color = RedPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

// แปลงวันที่ ISO เป็นรูปแบบที่อ่านง่ายเฉพาะตอนแสดงผล — ค่าที่เก็บใน state ต้องคงเป็น ISO เสมอ
// เพราะกติกา W6 (AppointmentStatus) parse จากค่านั้นโดยตรง
private fun formatDateForDisplay(isoDate: String?): String {
    if (isoDate.isNullOrBlank()) return "N/A"
    return runCatching {
        java.time.LocalDate.parse(isoDate.take(10))
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("th", "TH")))
    }.getOrDefault(isoDate)
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
        status = "planned",
        projectName = "Alpha Project",
        companyName = "Example Corp",
        contactName = "John Doe",
        plannedTime = "10:00",
        plannedEndTime = "11:30"
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
            onSaveResult = {},
            onClearError = {}
        )
    }
}
