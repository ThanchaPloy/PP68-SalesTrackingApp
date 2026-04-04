package com.example.pp68_salestrackingapp.ui.screen.activity

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pp68_salestrackingapp.ui.components.DatePickerField
import com.example.pp68_salestrackingapp.ui.components.DropdownField
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SalesResultUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SalesResultViewModel

private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFAE2138)
private val BgLight    = Color(0xFFF5F5F5)
private val BorderGray = Color(0xFFE8E8E8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesResultScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: SalesResultViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    

    LaunchedEffect(s.isSaved) {
        if (s.isSaved) onSaved()
    }

    LaunchedEffect(s.error) {
        s.error?.let { snackbarHostState.showSnackbar(it) }
    }

    SalesResultContent(
        s                               = s,
        snackbarHostState               = snackbarHostState,
        onBack                          = onBack,
        onReportDateChanged             = viewModel::onReportDateChanged,
        onStatusToggle                  = viewModel::onStatusToggle,
        onNewStatusSelected             = viewModel::onNewStatusSelected,
        onOpportunitySelected           = viewModel::onOpportunitySelected,
        onDealPositionChanged           = viewModel::onDealPositionChanged,
        onPreviousSolutionChanged       = viewModel::onPreviousSolutionChanged,
        onCounterpartyMultiplierChanged = viewModel::onCounterpartyMultiplierChanged,
        onResponseSpeedChanged          = viewModel::onResponseSpeedChanged,
        onProposalToggle                = viewModel::onProposalToggle,
        onProposalDateChanged           = viewModel::onProposalDateChanged,
        onCompetitorCountChanged        = viewModel::onCompetitorCountChanged,
        onSummaryChanged                = viewModel::onSummaryChanged,
        onPhotoPicked                   = { uri -> viewModel.onPhotoPicked(context, uri) },
        onUploadPhoto                   = { viewModel.uploadPhoto(context) },
        onSave                          = viewModel::save,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SalesResultContent(
    s: SalesResultUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onReportDateChanged: (String) -> Unit,
    onStatusToggle: (Boolean) -> Unit,
    onNewStatusSelected: (String) -> Unit,
    onOpportunitySelected: (String) -> Unit,
    onDealPositionChanged: (String) -> Unit,
    onPreviousSolutionChanged: (String) -> Unit,
    onCounterpartyMultiplierChanged: (String) -> Unit,
    onResponseSpeedChanged: (String) -> Unit,
    onProposalToggle: (Boolean) -> Unit,
    onProposalDateChanged: (String) -> Unit,
    onCompetitorCountChanged: (Int) -> Unit,
    onSummaryChanged: (String) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onUploadPhoto: () -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("บันทึกผลการขาย", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = White)
            )
        },
        containerColor = BgLight
    ) { padding ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // วันที่บันทึกผล
                SectionCard(title = "วันที่บันทึกผล", icon = Icons.Default.CalendarToday) {
                    DatePickerField(
                        selectedDate  = s.reportDate,
                        placeholder   = "เลือกวันที่บันทึกผล",
                        onDateSelected = onReportDateChanged
                    )
                }

                // 1. สถานะโครงการ
                SectionCard(title = "1. สถานะโครงการ", icon = Icons.Default.Info) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("สถานะปัจจุบัน: ", fontSize = 14.sp, color = TextGray)
                            Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFEEEEEE)) {
                                Text(
                                    text = s.currentStatus.ifBlank { "ไม่ระบุ" },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("มีการอัปเดตสถานะ", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = s.isStatusUpdateEnabled,
                                onCheckedChange = onStatusToggle,
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = RedPrimary)
                            )
                        }
                        if (s.isStatusUpdateEnabled) {
                            val statusList = listOf(
                                "Lead",
                                "New Project",
                                "Quotation",
                                "Bidding",
                                "Decision Making",
                                "Assured",
                                "PO",
                                "Lost",
                                "Failed"
                            )
                            DropdownField(
                                value       = s.newStatus,
                                placeholder = "เลือกสถานะใหม่",
                                options     = statusList,
                                onSelect    = { onNewStatusSelected(statusList[it]) }
                            )
                        }
                    }
                }

                // 2. โอกาสในการสำเร็จ
                SectionCard(title = "2. โอกาสในการสำเร็จ", icon = Icons.AutoMirrored.Filled.TrendingUp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OpportunityButton("สูง (HOT) 🔥",  s.opportunityScore == "สูง (HOT)",  Modifier.weight(1f)) { onOpportunitySelected("สูง (HOT)") }
                        OpportunityButton("กลาง (WARM) ☀️", s.opportunityScore == "กลาง (WARM)", Modifier.weight(1f)) { onOpportunitySelected("กลาง (WARM)") }
                        OpportunityButton("ต่ำ (COLD) ❄️", s.opportunityScore == "ต่ำ (COLD)", Modifier.weight(1f)) { onOpportunitySelected("ต่ำ (COLD)") }
                    }
                }

                // 3. สรุปการเข้าพบ
                SectionCard(title = "3. สรุปการเข้าพบ", icon = Icons.AutoMirrored.Filled.Notes) {
                    OutlinedTextField(
                        value         = s.visitSummary,
                        onValueChange = onSummaryChanged,
                        placeholder   = { Text("เขียนสรุปรายละเอียดการสนทนา...", fontSize = 14.sp) },
                        modifier      = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        shape         = RoundedCornerShape(12.dp)
                    )
                }

                // 4. ตำแหน่งของดีล (Deal Position)
                SectionCard(title = "4. ตำแหน่งของดีล (Deal Position)", icon = Icons.Default.Place) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก",
                            "ลูกค้าเลือกเราเป็นตัวหลัก คู่แข่งอื่นเป็นแค่ backup",
                            "ถูกเชิญมาเพื่อ benchmark ราคา โอกาสต่ำ"
                        ).forEach { opt ->
                            SelectOption(opt, s.dealPosition == opt) { onDealPositionChanged(opt) }
                        }
                    }
                }

                // 5. Solution เดิมของลูกค้า
                SectionCard(title = "5. Solution เดิมของลูกค้า", icon = Icons.Default.History) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "ไม่มี Solution เดิม",
                            "มีระบบเดิมที่ไม่ใช่คู่แข่ง",
                            "ใช้คู่แข่งอยู่และไม่มีปัญหา"
                        ).forEach { opt ->
                            SelectOption(opt, s.previousSolution == opt) { onPreviousSolutionChanged(opt) }
                        }
                    }
                }

                // 6. ประเภทคู่สัญญา (Counterparty Type)
                SectionCard(title = "6. ประเภทคู่สัญญา (Counterparty Type)", icon = Icons.Default.Groups) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(
                            "ดีลกับ Main Contractor โดยตรง",
                            "ดีลผ่าน Installer — Main Contractor ได้งานแล้ว",
                            "ดีลผ่าน Installer — Main Contractor ยังไม่ได้งาน"
                        ).forEach { opt ->
                            SelectOption(opt, s.counterpartyMultiplier == opt) { onCounterpartyMultiplierChanged(opt) }
                        }
                    }
                }

                // 7. ความรวดเร็วในการตอบรับ
                SectionCard(title = "7. ความรวดเร็วในการตอบรับ", icon = Icons.Default.Speed) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("เร็ว", "ปกติ", "ช้าหรือเงียบ").forEach { opt ->
                            SelectOption(opt, s.responseSpeed == opt) { onResponseSpeedChanged(opt) }
                        }
                    }
                }

                // 8. การส่งใบเสนอราคา (Proposal)
                SectionCard(title = "8. การส่งใบเสนอราคา (Proposal)", icon = Icons.Default.Description) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ส่งใบเสนอราคาแล้ว", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = s.isProposalSent,
                                onCheckedChange = onProposalToggle,
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = RedPrimary)
                            )
                        }
                        if (s.isProposalSent) {
                            DatePickerField(
                                selectedDate  = s.proposalDate ?: "",
                                placeholder   = "ระบุวันที่ส่งใบเสนอราคา",
                                onDateSelected = onProposalDateChanged
                            )
                        }
                    }
                }

                // 9. จำนวนคู่แข่ง
                SectionCard(title = "9. จำนวนคู่แข่ง", icon = Icons.Default.CompareArrows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("จำนวนคู่แข่งที่ทราบ", fontSize = 15.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconButton(onClick = { onCompetitorCountChanged(-1) }) {
                                Icon(Icons.Default.RemoveCircleOutline, null, tint = RedPrimary)
                            }
                            Text("${s.competitorCount}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { onCompetitorCountChanged(1) }) {
                                Icon(Icons.Default.AddCircleOutline, null, tint = RedPrimary)
                            }
                        }
                    }
                }

                // ส่วนอัปโหลดรูป
                SectionCard(title = "10. แนบรูปภาพหลักฐาน", icon = Icons.Default.PhotoCamera) {
                    PhotoUploadSection(
                        photoUri = s.photoUri,
                        photoUrl = s.photoUrl,
                        isUploading = s.isUploadingPhoto,
                        onPhotoPicked = onPhotoPicked,
                        onUpload = onUploadPhoto
                    )
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    enabled = !s.isSaving
                ) {
                    if (s.isSaving) {
                        CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("บันทึกข้อมูล", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
            }
            content()
        }
    }
}

@Composable
private fun OpportunityButton(
    text: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) RedPrimary else White,
        border = BorderStroke(1.dp, if (isSelected) RedPrimary else BorderGray)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) White else TextDark
            )
        }
    }
}

@Composable
private fun SelectOption(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = RedPrimary)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp, color = if (isSelected) TextDark else TextGray)
    }
}

@Composable
private fun PhotoUploadSection(
    photoUri: Uri?,
    photoUrl: String?,
    isUploading: Boolean,
    onPhotoPicked: (Uri) -> Unit,
    onUpload: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { onPhotoPicked(it) }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (photoUrl != null || photoUri != null) {
            AsyncImage(
                model = photoUrl ?: photoUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(12.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("เลือกรูป")
            }

            if (photoUri != null && photoUrl == null) {
                Button(
                    onClick = onUpload,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    enabled = !isUploading
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = White, modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Default.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("อัปโหลด")
                    }
                }
            }
        }
    }
}
