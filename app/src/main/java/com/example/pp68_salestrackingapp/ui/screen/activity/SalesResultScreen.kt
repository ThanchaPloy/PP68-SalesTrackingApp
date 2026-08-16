package com.example.pp68_salestrackingapp.ui.screen.activity

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.utils.formatPhotoUrl
import coil.compose.AsyncImage
import com.example.pp68_salestrackingapp.ui.components.DatePickerField
import com.example.pp68_salestrackingapp.ui.components.DropdownField
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ResultPhoto
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SalesResultUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SalesResultViewModel
import java.io.File

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
    onViewHistory: (String) -> Unit = {},
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
        onDmToggle                      = viewModel::onDmToggle,
        onSummaryChanged                = viewModel::onSummaryChanged,
        onLossReasonChanged             = viewModel::onLossReasonChanged,
        onOtherLossReasonChanged        = viewModel::onOtherLossReasonChanged,
        lossReasonOptions               = viewModel.lossReasonOptions,
        onPhotoCaptured                 = { uri -> viewModel.onPhotoCaptured(context, uri) },
        onPhotosPicked                  = { uris -> viewModel.onPhotosPicked(context, uris) },
        onRemovePhoto                   = viewModel::onRemovePhoto,
        onSave                          = viewModel::save,
        onViewHistory                   = { s.resultGroupId?.let(onViewHistory) },
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
    onDmToggle: (Boolean) -> Unit,
    onSummaryChanged: (String) -> Unit,
    onLossReasonChanged: (String) -> Unit,
    onOtherLossReasonChanged: (String) -> Unit,
    lossReasonOptions: List<String>,
    onPhotoCaptured: (Uri) -> Unit,
    onPhotosPicked: (List<Uri>) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onSave: () -> Unit,
    onViewHistory: () -> Unit = {}
) {
    var expandSolution by remember { mutableStateOf(false) }
    var expandContract by remember { mutableStateOf(false) }
    var expandProposal by remember { mutableStateOf(false) }

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
                actions = {
                    if (s.resultGroupId != null) {
                        IconButton(onClick = onViewHistory) {
                            Icon(Icons.Default.History, "ดูประวัติการแก้ไข", tint = RedPrimary)
                        }
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
                if (s.isReadOnlyVersion) {
                    Surface(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Info, null, tint = Color(0xFFE65100), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "กำลังดูเวอร์ชันเก่า (version ${s.version}) — ไม่สามารถแก้ไขได้",
                                fontSize = 13.sp, color = Color(0xFFE65100), fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                SectionCard(title = "วันที่บันทึกผล", icon = Icons.Default.CalendarToday) {
                    DatePickerField(
                        selectedDate  = s.reportDate,
                        placeholder   = "เลือกวันที่บันทึกผล",
                        onDateSelected = onReportDateChanged
                    )
                }

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
                                "Lead", "New Project", "Quotation", "Bidding",
                                "Make a Decision", "Assured", "PO", "Lost", "Failed"
                            )
                            DropdownField(
                                value       = s.newStatus,
                                placeholder = "เลือกสถานะใหม่",
                                options     = statusList,
                                onSelect    = { onNewStatusSelected(statusList[it]) }
                            )
                        }

                        // ✅ สาเหตุที่ไม่ได้งาน (แสดงเมื่อเป็น Lost หรือ Failed)
                        AnimatedVisibility(
                            visible = s.isStatusUpdateEnabled && (s.newStatus == "Lost" || s.newStatus == "Failed")
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("เหตุผลที่ไม่ได้งาน *", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = RedPrimary)
                                DropdownField(
                                    value       = s.lossReason,
                                    placeholder = "เลือกเหตุผล",
                                    options     = lossReasonOptions,
                                    isError     = s.lossReasonError != null,
                                    errorMsg    = s.lossReasonError,
                                    onSelect    = { onLossReasonChanged(lossReasonOptions[it]) }
                                )

                                if (s.lossReason == "อื่น ๆ") {
                                    OutlinedTextField(
                                        value         = s.otherLossReason,
                                        onValueChange = onOtherLossReasonChanged,
                                        placeholder   = { Text("ระบุเหตุผลอื่น ๆ...", fontSize = 14.sp) },
                                        modifier      = Modifier.fillMaxWidth(),
                                        shape         = RoundedCornerShape(10.dp),
                                        isError       = s.lossReasonError != null,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = RedPrimary,
                                            unfocusedBorderColor = BorderGray
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                SectionCard(title = "2. โอกาสในการสำเร็จ", icon = Icons.AutoMirrored.Filled.TrendingUp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OpportunityButton("HOT🔥",  s.opportunityScore == "สูง (HOT)",  Modifier.weight(1f)) { onOpportunitySelected("สูง (HOT)") }
                        OpportunityButton("WARM☀️", s.opportunityScore == "กลาง (WARM)", Modifier.weight(1f)) { onOpportunitySelected("กลาง (WARM)") }
                        OpportunityButton("COLD❄️", s.opportunityScore == "ต่ำ (COLD)", Modifier.weight(1f)) { onOpportunitySelected("ต่ำ (COLD)") }
                    }
                }

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

                SectionCard(title = "รูปภาพยืนยันการเข้าพบ", icon = Icons.Default.PhotoCamera) {
                    PhotoUploadSection(
                        photos = s.photos,
                        onPhotoCaptured = onPhotoCaptured,
                        onPhotosPicked = onPhotosPicked,
                        onRemovePhoto = onRemovePhoto
                    )
                }

                SectionCard(title = "ผู้มีอำนาจตัดสินใจ (DM)", icon = Icons.Default.Person) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ได้พบ/ดีลกับ DM โดยตรง", fontSize = 15.sp)
                        Switch(
                            checked = s.dmInvolved,
                            onCheckedChange = onDmToggle,
                            colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = RedPrimary)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = BorderGray)
                Text("วิเคราะห์ข้อมูลเพิ่มเติม(ถ้ามี)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = RedPrimary)

                CollapsibleSection(
                    title = "Tab Solution & ตำแหน่งดีล",
                    icon = Icons.Default.SettingsSuggest,
                    isExpanded = expandSolution,
                    onToggle = { expandSolution = !expandSolution }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionCard(title = "4. ตำแหน่งของดีล", icon = Icons.Default.Place) {
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
                    }
                }

                CollapsibleSection(
                    title = "Tap สัญญา & การตอบรับ",
                    icon = Icons.Default.Handshake,
                    isExpanded = expandContract,
                    onToggle = { expandContract = !expandContract }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionCard(title = "6. ประเภทคู่สัญญา", icon = Icons.Default.Groups) {
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
                        SectionCard(title = "7. ความรวดเร็วในการตอบรับ", icon = Icons.Default.Speed) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("เร็ว", "ปกติ", "ช้าหรือเงียบ").forEach { opt ->
                                    SelectOption(opt, s.responseSpeed == opt) { onResponseSpeedChanged(opt) }
                                }
                            }
                        }
                    }
                }

                CollapsibleSection(
                    title = "Tab ใบเสนอราคา & คู่แข่ง",
                    icon = Icons.Default.Description,
                    isExpanded = expandProposal,
                    onToggle = { expandProposal = !expandProposal }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionCard(title = "8. การส่งใบเสนอราคา", icon = Icons.Default.Description) {
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
                        SectionCard(title = "9. จำนวนคู่แข่ง", icon = Icons.AutoMirrored.Filled.CompareArrows) {
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
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    enabled = !s.isSaving && !s.isReadOnlyVersion
                ) {
                    if (s.isSaving) {
                        CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            if (s.resultId != null) "บันทึกการเปลี่ยนแปลงผลการขาย" else "บันทึกข้อมูล",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
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
private fun CollapsibleSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column {
        Surface(
            onClick = onToggle,
            color = Color(0xFFF0F0F0),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextDark, modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextGray
                )
            }
        }
        AnimatedVisibility(visible = isExpanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                content()
            }
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

private const val MAX_RESULT_PHOTOS = 5

private fun createCameraCaptureUri(context: android.content.Context): Uri {
    val file = File.createTempFile("visit_photo_", ".jpg", context.cacheDir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

@Composable
private fun PhotoUploadSection(
    photos: List<ResultPhoto>,
    onPhotoCaptured: (Uri) -> Unit,
    onPhotosPicked: (List<Uri>) -> Unit,
    onRemovePhoto: (Int) -> Unit
) {
    val context = LocalContext.current
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) onPhotoCaptured(uri)
        pendingCaptureUri = null
    }

    fun launchCamera() {
        val uri = createCameraCaptureUri(context)
        pendingCaptureUri = uri
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCamera() }

    fun requestCameraCapture() {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) launchCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(MAX_RESULT_PHOTOS)
    ) { uris ->
        if (uris.isNotEmpty()) onPhotosPicked(uris)
    }

    fun launchGallery() {
        galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "ถ่ายรูปบันทึกการเข้าพบได้สูงสุด $MAX_RESULT_PHOTOS รูป (ต้องถ่ายจากกล้องในแอปเท่านั้น)",
            fontSize = 12.sp,
            color = TextGray
        )
        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            itemsIndexed(photos) { index, photo ->
                Box(modifier = Modifier.size(96.dp)) {
                    AsyncImage(
                        model = photo.url?.let { formatPhotoUrl(it) } ?: photo.localUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, BorderGray, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                    if (photo.isUploading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                    }
                    IconButton(
                        onClick = { onRemovePhoto(index) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(22.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "ลบรูป", tint = White, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (photos.size < MAX_RESULT_PHOTOS) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                                .clickable { requestCameraCapture() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoCamera, null, tint = TextGray, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("ถ่ายรูป", fontSize = 11.sp, color = TextGray)
                            }
                        }
                    }
                }
                
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                                .clickable { launchGallery() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.PhotoLibrary, null, tint = TextGray, modifier = Modifier.size(26.dp))
                                Spacer(Modifier.height(4.dp))
                                Text("เลือกจากคลัง", fontSize = 11.sp, color = TextGray)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${photos.size}/$MAX_RESULT_PHOTOS รูป", fontSize = 11.sp, color = TextGray)

        val cover = photos.firstOrNull()
        if (cover != null && (cover.takenAt != null || cover.lat != null || cover.deviceModel != null)) {
            Spacer(Modifier.height(12.dp))
            Surface(
                color    = Color(0xFFF5F5F5),
                shape    = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cover.takenAt?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(14.dp), tint = TextGray)
                            Text("ถ่ายเมื่อ: $it", fontSize = 12.sp, color = TextGray)
                        }
                    }
                    if (cover.lat != null && cover.lng != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val iconTint = when (cover.isLocationValid) {
                                true  -> Color(0xFF2E7D32)
                                false -> Color(0xFFC62828)
                                null  -> TextGray
                            }
                            Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(14.dp), tint = iconTint)
                            Text(
                                text = when (cover.isLocationValid) {
                                    true  -> "พิกัดตรงกับสถานที่นัด ✅"
                                    false -> "พิกัดไม่ตรงกับสถานที่นัด ⚠️"
                                    null  -> "พิกัด: %.4f, %.4f".format(cover.lat, cover.lng)
                                },
                                fontSize = 12.sp,
                                color = when (cover.isLocationValid) {
                                    true  -> Color(0xFF2E7D32)
                                    false -> Color(0xFFC62828)
                                    null  -> TextGray
                                }
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.LocationOff, null, modifier = Modifier.size(14.dp), tint = TextGray)
                            Text("ไม่พบพิกัดในรูป", fontSize = 12.sp, color = TextGray)
                        }
                    }
                    cover.deviceModel?.let {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.PhoneAndroid, null, modifier = Modifier.size(14.dp), tint = TextGray)
                            Text("อุปกรณ์: $it", fontSize = 12.sp, color = TextGray)
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SalesResultScreenPreview() {
    SalesTrackingTheme {
        SalesResultContent(
            s = SalesResultUiState(
                currentStatus = "Lead",
                reportDate = "2023-11-01",
                isStatusUpdateEnabled = true,
                newStatus = "Quotation",
                opportunityScore = "สูง (HOT)",
                visitSummary = "ลูกค้าสนใจสินค้ามาก ต้องการใบเสนอราคาด่วน",
                competitorCount = 2,
                dmInvolved = true
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onBack = {},
            onReportDateChanged = {},
            onStatusToggle = {},
            onNewStatusSelected = {},
            onOpportunitySelected = {},
            onDealPositionChanged = {},
            onPreviousSolutionChanged = {},
            onCounterpartyMultiplierChanged = {},
            onResponseSpeedChanged = {},
            onProposalToggle = {},
            onProposalDateChanged = {},
            onCompetitorCountChanged = {},
            onDmToggle = {},
            onSummaryChanged = {},
            onLossReasonChanged = {},
            onOtherLossReasonChanged = {},
            lossReasonOptions = listOf("ผลิตไม่ได้/ผลิตไม่ทัน", "เทคโนโลยีไม่ผ่าน", "สู้ราคาไม่ไหว", "อื่น ๆ"),
            onPhotoCaptured = {},
            onPhotosPicked = {},
            onRemovePhoto = {},
            onSave = {}
        )
    }
}
