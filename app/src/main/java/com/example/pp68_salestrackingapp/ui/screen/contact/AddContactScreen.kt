package com.example.pp68_salestrackingapp.ui.screen.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.*
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.contact.AddContactViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.contact.AddContactUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.contact.AddContactEvent

// ─── Colors ───────────────────────────────────────────────────
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFCC1D1D)
private val BgLight    = Color(0xFFF5F5F5)
private val ErrorRed   = Color(0xFFE53935)
private val BorderGray = Color(0xFFE0E0E0)


// ═══════════════════════════════════════════════════════════════
// SCREEN ENTRY POINT
// ═══════════════════════════════════════════════════════════════
@Composable
fun AddContactScreen(
    contactId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddContactViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(contactId) {
        if (contactId != null) {
            viewModel.onEvent(AddContactEvent.LoadContact(contactId))
        } else {
            viewModel.onEvent(AddContactEvent.CheckDraft)
        }
    }

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onSaved() }

    var showDiscardDialog by remember { mutableStateOf(false) }
    val attemptBack: () -> Unit = {
        if (viewModel.isDirty()) showDiscardDialog = true else onBack()
    }
    androidx.activity.compose.BackHandler(onBack = attemptBack)

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onSaveDraft = { viewModel.saveDraft(); showDiscardDialog = false; onBack() },
            onDiscard   = { viewModel.discardDraft(); showDiscardDialog = false; onBack() },
            onDismiss   = { showDiscardDialog = false }
        )
    }

    AddContactContent(
        uiState = uiState,
        onBack  = attemptBack,
        onSaved = onSaved,
        onEvent = viewModel::onEvent
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddContactContent(
    uiState: AddContactUiState,
    onBack:  () -> Unit,
    onSaved: () -> Unit,
    onEvent: (AddContactEvent) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp, color = White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextDark)
                    }
                    Text(
                        if (uiState.contactId != null) "Edit Contact Person" else "Add Contact Person",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = RedPrimary
                    )
                }
            }
        },
        containerColor = White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.draftAvailable) {
                DraftBanner(
                    message = "พบฉบับร่างที่บันทึกไว้ก่อนหน้านี้",
                    actionLabel = "กู้คืน",
                    onAction = { onEvent(AddContactEvent.RestoreDraft) },
                    onDismiss = { onEvent(AddContactEvent.DismissDraftPrompt) }
                )
            }

            // ── Select Company * ──────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onEvent(AddContactEvent.ToggleQuickAddCompany(true)) }) {
                    Icon(Icons.Default.AddBusiness, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("สร้างบริษัทด่วน", color = AppColors.Primary, fontSize = 13.sp)
                }
            }
            FormField("เลือกบริษัท", required = true) {
                if (uiState.isLoadingCompanies) {
                    LoadingField()
                } else {
                    Column {
                        SearchableIdDropdownField(
                            value       = uiState.selectedCompanyName ?: "",
                            placeholder = "เลือกบริษัทลูกค้า",
                            options     = uiState.companyOptions,
                            onSelect    = { id, name -> onEvent(AddContactEvent.CompanySelected(id, name)) },
                            onClear     = { onEvent(AddContactEvent.CompanySelected("", "")) }
                        )
                        if (uiState.companyError != null)
                            Text(uiState.companyError, color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
            }

            // ── Select Project (กรองตาม company) ─────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                // เปิดให้กดได้เฉพาะตอนเลือกบริษัทแล้ว — โครงการต้องสังกัดบริษัทเสมอ
                TextButton(
                    onClick = { onEvent(AddContactEvent.ToggleQuickAddProject(true)) },
                    enabled = uiState.selectedCompanyId != null
                ) {
                    Icon(Icons.Default.CreateNewFolder, null,
                        tint = if (uiState.selectedCompanyId != null) AppColors.Primary else AppColors.TextHint,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("สร้างโครงการด่วน",
                        color = if (uiState.selectedCompanyId != null) AppColors.Primary else AppColors.TextHint,
                        fontSize = 13.sp)
                }
            }
            FormField("เลือกโครงการ (ไม่บังคับ)") {
                if (uiState.isLoadingProjects) {
                    LoadingField()
                } else {
                    DropdownField(
                        value       = uiState.selectedProjectName ?: "",
                        placeholder = if (uiState.selectedCompanyId == null)
                            "เลือกบริษัทก่อน" else "เลือกโครงการ",
                        options     = uiState.projectOptions.map { it.second },
                        onSelect    = { idx ->
                            onEvent(AddContactEvent.ProjectSelected(
                                uiState.projectOptions[idx].first,
                                uiState.projectOptions[idx].second
                            ))
                        }
                    )
                }
            }

            // ── Full Name * ───────────────────────────────────
            FormField("ชื่อ-นามสกุล", required = true) {
                FormTextField(
                    value         = uiState.fullName,
                    onValueChange = { onEvent(AddContactEvent.FullNameChanged(it)) },
                    placeholder   = "กรอกชื่อ-นามสกุล",
                    leadingIcon   = Icons.Default.Person,
                    isError       = uiState.fullNameError != null,
                    errorMsg      = uiState.fullNameError
                )
            }

            // ── Nickname (Optional) ───────────────────────────
            FormField("ชื่อเล่น (ไม่บังคับ)") {
                FormTextField(
                    value         = uiState.nickname,
                    onValueChange = { onEvent(AddContactEvent.NicknameChanged(it)) },
                    placeholder   = "กรอกชื่อเล่น"
                )
            }

            // ── Position ──────────────────────────────────────
            FormField("ตำแหน่ง") {
                FormTextField(
                    value         = uiState.position,
                    onValueChange = { onEvent(AddContactEvent.PositionChanged(it)) },
                    placeholder   = "เช่น ผู้จัดการฝ่ายขาย, CEO",
                    leadingIcon   = Icons.Default.Work
                )
            }

            // ── Mobile Number ─────────────────────────────────
            FormField("เบอร์โทรศัพท์มือถือ", required = true) {
                FormTextField(
                    value         = uiState.phoneNum,
                    onValueChange = { onEvent(AddContactEvent.PhoneChanged(it)) },
                    placeholder   = "เช่น 06x-xxx-xxxx",
                    leadingIcon   = Icons.Default.Phone,
                    keyboardType  = KeyboardType.Phone,
                    isError       = uiState.phoneError != null,
                    errorMsg      = uiState.phoneError
                )
            }

            // ── Email ─────────────────────────────────────────
            FormField("อีเมล") {
                FormTextField(
                    value         = uiState.email,
                    onValueChange = { onEvent(AddContactEvent.EmailChanged(it)) },
                    placeholder   = "กรอกอีเมล",
                    leadingIcon   = Icons.Default.Email,
                    keyboardType  = KeyboardType.Email,
                    isError       = uiState.emailError != null,
                    errorMsg      = uiState.emailError
                )
            }

            // ── Line ID (Optional) ────────────────────────────
            FormField("Line ID (ไม่บังคับ)") {
                FormTextField(
                    value         = uiState.lineId,
                    onValueChange = { onEvent(AddContactEvent.LineIdChanged(it)) },
                    placeholder   = "กรอก Line ID",
                    leadingIcon   = Icons.Default.Chat
                )
            }

            // ── Is Decision Maker Checkbox ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                    .clickable { onEvent(AddContactEvent.IsDecisionMakerToggled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked         = uiState.isDecisionMaker,
                    onCheckedChange = { onEvent(AddContactEvent.IsDecisionMakerToggled) },
                    colors = CheckboxDefaults.colors(
                        checkedColor   = RedPrimary,
                        uncheckedColor = TextGray
                    )
                )
                Column {
                    Text("เป็นผู้ตัดสินใจ?", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(
                        if (uiState.isDecisionMaker) "ผู้ติดต่อนี้เป็นผู้ตัดสินใจหลัก"
                        else "ผู้ติดต่อนี้ไม่ใช่ผู้ตัดสินใจหลัก",
                        fontSize = 12.sp, color = TextGray
                    )
                }
            }

            // ── Is Active Checkbox ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                    .clickable { onEvent(AddContactEvent.IsActiveToggled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked         = uiState.isActive,
                    onCheckedChange = { onEvent(AddContactEvent.IsActiveToggled) },
                    colors = CheckboxDefaults.colors(
                        checkedColor   = RedPrimary,
                        uncheckedColor = TextGray
                    )
                )
                Column {
                    Text("ใช้งานอยู่?", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(
                        if (uiState.isActive) "ผู้ติดต่อนี้ยังใช้งานอยู่"
                        else "ผู้ติดต่อนี้ไม่ได้ใช้งานแล้ว",
                        fontSize = 12.sp, color = TextGray
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Error ─────────────────────────────────────────
            uiState.saveError?.let {
                Text(it, color = ErrorRed, fontSize = 13.sp)
            }

            // ── Save Button ───────────────────────────────────
            Button(
                onClick  = { onEvent(AddContactEvent.Save) },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                } else {
                    Text(if (uiState.contactId != null) "บันทึกการเปลี่ยนแปลงผู้ติดต่อ" else "บันทึกข้อมูลผู้ติดต่อ", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = White)
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // ── สร้างบริษัทด่วน ─────────────────────────────────
        if (uiState.isQuickAddCompanyOpen) {
            ModalBottomSheet(
                onDismissRequest = { onEvent(AddContactEvent.ToggleQuickAddCompany(false)) },
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ตั้ง isLead = true เสมอเหมือนหน้าอื่น — ยังไม่ผ่านการคัดกรองเป็นลูกค้าจริง
                    Text("สร้าง Lead ใหม่ (สร้างด่วน)", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = AppColors.Primary)

                    FormField("ชื่อบริษัท/ชื่อลูกค้า", required = true) {
                        FormTextField(
                            value = uiState.quickAddCompanyName,
                            onValueChange = { onEvent(AddContactEvent.QuickAddCompanyChanged(it, uiState.quickAddCustType)) },
                            placeholder = "ระบุชื่อบริษัท หรือชื่อลูกค้า"
                        )
                    }
                    FormField("ประเภทลูกค้า", required = true) {
                        val types = listOf("Owner", "Developer", "Main Contractor", "Sub Contractor",
                            "Installer", "Architect", "Interior Designer", "Consultant",
                            "Industrial", "Wholesale", "Factory")
                        DropdownField(
                            value = uiState.quickAddCustType,
                            placeholder = "เลือกประเภท",
                            options = types,
                            onSelect = { idx -> onEvent(AddContactEvent.QuickAddCompanyChanged(uiState.quickAddCompanyName, types[idx])) }
                        )
                    }
                    uiState.quickAddCompanyError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onEvent(AddContactEvent.SaveQuickAddCompany) },
                        enabled = !uiState.isSavingQuickCompany,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        if (uiState.isSavingQuickCompany) {
                            CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("สร้างและเลือกบริษัทนี้", color = White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ── สร้างโครงการด่วน ────────────────────────────────
        if (uiState.isQuickAddProjectOpen) {
            ModalBottomSheet(
                onDismissRequest = { onEvent(AddContactEvent.ToggleQuickAddProject(false)) },
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("สร้างโครงการใหม่ (สร้างด่วน)", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                        color = AppColors.Primary)
                    Text("จะถูกผูกกับบริษัท: ${uiState.selectedCompanyName ?: "-"}",
                        fontSize = 13.sp, color = AppColors.TextSecondary)

                    FormField("ชื่อโครงการ", required = true) {
                        FormTextField(
                            value = uiState.quickAddProjectName,
                            onValueChange = { onEvent(AddContactEvent.QuickAddProjectNameChanged(it)) },
                            placeholder = "ระบุชื่อโครงการ"
                        )
                    }
                    FormField("สถานะโครงการ", required = true) {
                        val stages = com.example.pp68_salestrackingapp.utils.ProjectStages.SELECTABLE
                        DropdownField(
                            value = uiState.quickAddProjectStatus,
                            placeholder = "เลือกสถานะ",
                            options = stages,
                            onSelect = { idx -> onEvent(AddContactEvent.QuickAddProjectStatusChanged(stages[idx])) },
                            // เก็บเป็นรหัสแต่โชว์ป้ายจาก master data เหมือนทุกที่ในแอป
                            displayLabel = { com.example.pp68_salestrackingapp.utils.ProjectStages.labelFor(it) }
                        )
                    }
                    uiState.quickAddProjectError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onEvent(AddContactEvent.SaveQuickAddProject) },
                        enabled = !uiState.isSavingQuickProject,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
                    ) {
                        if (uiState.isSavingQuickProject) {
                            CircularProgressIndicator(color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("สร้างและเลือกโครงการนี้", color = White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

// ─── Loading placeholder ──────────────────────────────────────
@Composable
private fun LoadingField() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(Color(0xFFF9F9F9)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                color = RedPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp
            )
            Text("กำลังโหลด...", color = TextGray, fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddContactScreenPreview() {
    SalesTrackingTheme {
        AddContactContent(
            uiState = AddContactUiState(
                fullName = "John Doe",
                companyOptions = listOf("1" to "Example Corp", "2" to "Mock Ltd"),
                selectedCompanyName = "Example Corp"
            ),
            onBack = {},
            onSaved = {},
            onEvent = {}
        )
    }
}
