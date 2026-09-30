package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.*
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.project.*

// ═══════════════════════════════════════════════════════════════
@Composable
fun AddProjectScreen(
    projectId: String? = null,
    onBack:  () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddProjectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        if (projectId != null) {
            viewModel.onEvent(AddProjectEvent.LoadProject(projectId))
        } else {
            viewModel.onEvent(AddProjectEvent.CheckDraft)
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

    AddProjectContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = attemptBack,
        lossReasonOptions = viewModel.lossReasonOptions,
        dealPositionOptions = viewModel.dealPositionOptions,
        previousSolutionOptions = viewModel.previousSolutionOptions,
        counterpartyTypeOptions = viewModel.counterpartyTypeOptions,
        responseSpeedOptions = viewModel.responseSpeedOptions
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AddProjectContent(
    uiState: AddProjectUiState,
    onEvent: (AddProjectEvent) -> Unit,
    onBack: () -> Unit,
    lossReasonOptions: List<String> = emptyList(),
    dealPositionOptions: List<String> = emptyList(),
    previousSolutionOptions: List<String> = emptyList(),
    counterpartyTypeOptions: List<String> = emptyList(),
    responseSpeedOptions: List<String> = emptyList()
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp, color = AppColors.BgWhite) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextPrimary)
                    }
                    Text(
                        if (uiState.projectId != null) "Edit Project" else "Create Project",
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Primary)
                }
            }
        },
        containerColor = AppColors.BgWhite
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
                    onAction = { onEvent(AddProjectEvent.RestoreDraft) },
                    onDismiss = { onEvent(AddProjectEvent.DismissDraftPrompt) }
                )
            }
            // ── Project Number ────────────────────────────────
            FormField("หมายเลขโครงการ") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    color    = Color(0xFFF0F0F0),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Border)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Tag, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                        Text(
                            text = uiState.displayProjectId,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (uiState.displayProjectId.contains("รอกด"))
                                AppColors.TextSecondary else AppColors.TextPrimary
                        )
                        Spacer(Modifier.weight(1f))
                        Surface(color = AppColors.Primary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                            Text(
                                "AUTO",
                                fontSize   = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color      = AppColors.Primary,
                                modifier   = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── Project Name * ────────────────────────────────
            FormField("ชื่อโครงการ", required = true) {
                FormTextField(
                    value         = uiState.projectName,
                    onValueChange = { onEvent(AddProjectEvent.ProjectNameChanged(it)) },
                    placeholder   = "ระบุชื่อโครงการ",
                    isError       = uiState.projectNameError != null,
                    errorMsg      = uiState.projectNameError
                )
            }

            // ── Project Status * ──────────────────────────────
            // อยู่ถัดจากชื่อโครงการเพราะเป็นอีกฟิลด์ที่บังคับกรอก — ให้สองฟิลด์ที่บังคับอยู่ติดกันบนสุด
            val statusList = com.example.pp68_salestrackingapp.utils.ProjectStages.SELECTABLE
            FormField("สถานะโครงการ", required = true) {
                DropdownField(
                    value       = uiState.projectStatus ?: "",
                    placeholder = "เลือกสถานะ",
                    options     = statusList,
                    isError     = uiState.statusError != null,
                    errorMsg    = uiState.statusError,
                    onSelect    = { idx ->
                        onEvent(AddProjectEvent.StatusChanged(statusList[idx]))
                    },
                    displayLabel = { com.example.pp68_salestrackingapp.utils.ProjectStages.labelFor(it) }
                )
            }

            // ── Loss Reason (แสดงเมื่อเป็น Lost หรือ Failed) ──────────────────
            // ต้องอยู่ติดกับฟิลด์สถานะเสมอ เพราะมันโผล่มาตามค่าที่เลือกในฟิลด์นั้น
            AnimatedVisibility(visible = uiState.projectStatus == "Lost" || uiState.projectStatus == "Failed") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    FormField("เหตุผลที่ไม่ได้งาน", required = true) {
                        DropdownField(
                            value       = uiState.lossReason,
                            placeholder = "เลือกเหตุผล",
                            options     = lossReasonOptions,
                            isError     = uiState.lossReasonError != null,
                            errorMsg    = uiState.lossReasonError,
                            onSelect    = { idx ->
                                onEvent(AddProjectEvent.LossReasonChanged(lossReasonOptions[idx]))
                            },
                            displayLabel = { com.example.pp68_salestrackingapp.utils.LossReasons.labelFor(it) }
                        )
                    }

                    if (uiState.lossReason == "อื่น ๆ") {
                        FormField("ระบุเหตุผลอื่น ๆ", required = true) {
                            FormTextField(
                                value         = uiState.otherLossReason,
                                onValueChange = { onEvent(AddProjectEvent.OtherLossReasonChanged(it)) },
                                placeholder   = "กรอกเหตุผลที่ไม่ได้งาน",
                                isError       = uiState.lossReasonError != null
                            )
                        }
                    }
                }
            }

            // ── Customer/Company ────────────────────────────
            // Customer/Company
            FormField("ลูกค้า/บริษัท") {
                if (uiState.isLoadingCustomers) LoadingFieldProject()
                else Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SearchableDropdownField(
                                value       = uiState.selectedCustomerName ?: "",
                                placeholder = "เลือกลูกค้า",
                                options     = uiState.customerOptions.map { it.second },
                                onSelect    = { name ->
                                    val found = uiState.customerOptions.firstOrNull { it.second == name }
                                    found?.let { onEvent(AddProjectEvent.CustomerSelected(it.first, it.second)) }
                                },
                                onClear     = { onEvent(AddProjectEvent.CustomerSelected("", "")) }
                            )
                        }
                        TextButton(
                            onClick = { onEvent(AddProjectEvent.ToggleQuickAddCustomer(true)) },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add Customer", modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("สร้างลูกค้าใหม่", fontSize = 12.sp)
                        }
                    }
                    if (uiState.customerError != null)
                        Text(uiState.customerError, color = AppColors.Error, fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                }
            }

            // ── Contact Person (กรองตาม customer) ────────────
            FormField("ผู้ติดต่อ") {
                // โชว์เมื่อเลือกบริษัทแล้วเท่านั้น — ผู้ติดต่อต้องมีบริษัทสังกัดเสมอ
                if (uiState.selectedCustomerId != null) {
                    TextButton(onClick = { onEvent(AddProjectEvent.ToggleQuickAddContact(true)) }) {
                        Text("+ สร้างผู้ติดต่อด่วน", color = AppColors.Primary, fontSize = 13.sp)
                    }
                }
                if (uiState.isLoadingContacts) {
                    LoadingFieldProject()
                } else if (uiState.contactOptions.isNotEmpty()) {
                    MemberChipGrid(
                        options     = uiState.contactOptions,
                        selectedIds = uiState.selectedContactIds,
                        onToggle    = { onEvent(AddProjectEvent.ContactToggled(it)) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
                            .background(AppColors.BgGray)
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (uiState.selectedCustomerId == null) "เลือกบริษัทลูกค้าก่อน" else "ไม่พบรายชื่อผู้ติดต่อ",
                            color = AppColors.TextHint, fontSize = 14.sp
                        )
                    }
                }
            }

            // ── Billing Branch (ไม่บังคับ) ──────────────────────
            FormField("สาขาที่เปิดบิล", required = false) {
                if (uiState.isLoadingBillingBranches) LoadingFieldProject()
                else DropdownField(
                    value       = uiState.selectedBillingBranchName ?: "",
                    placeholder = "เลือกสาขาที่เปิดบิล",
                    options     = uiState.billingBranchOptions.map { it.second },
                    isError     = uiState.billingBranchError != null,
                    errorMsg    = uiState.billingBranchError,
                    onSelect    = { idx ->
                        onEvent(AddProjectEvent.BillingBranchSelected(
                            uiState.billingBranchOptions[idx].first,
                            uiState.billingBranchOptions[idx].second
                        ))
                    }
                )
            }

            // ── Branch/Team ────────────────────────────────────
            FormField("สาขาที่รับผิดชอบ") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.isLoadingTeams) LoadingFieldProject()
                    else DropdownField(
                        value       = uiState.selectedTeamName ?: "",
                        placeholder = "เลือกสาขา",
                        options     = uiState.teamOptions.map { it.second },
                        onSelect    = { idx ->
                            onEvent(AddProjectEvent.TeamSelected(
                                uiState.teamOptions[idx].first,
                                uiState.teamOptions[idx].second
                            ))
                        }
                    )


                }
            }

            // ── Expected Value ────────────────────────────────
            FormField("มูลค่าที่คาดหวัง") {
                FormTextField(
                    value         = uiState.expectedValue,
                    onValueChange = { onEvent(AddProjectEvent.ExpectedValueChanged(it)) },
                    placeholder   = "เช่น 500,000",
                    leadingIcon   = Icons.Default.AttachMoney,
                    keyboardType  = KeyboardType.Number
                )
            }

            // ── Start Date ────────────────────────────────────
            FormField("วันที่เริ่มโครงการ") {
                DatePickerField(
                    selectedDate   = uiState.startDate,
                    placeholder    = "เลือกวันที่",
                    onDateSelected = { onEvent(AddProjectEvent.StartDateChanged(it)) }
                )
            }

            // ── Closing Date ──────────────────────────────────
            FormField("วันที่คาดว่าจะปิด") {
                DatePickerField(
                    selectedDate   = uiState.closeDate,
                    placeholder    = "เลือกวันที่",
                    onDateSelected = { onEvent(AddProjectEvent.CloseDateChanged(it)) }
                )
            }

            // ── ปัจจัยของดีล (W6-2) — แก้ไขได้ที่นี่เท่านั้น หน้าบันทึกผลแค่ prefill มาโชว์ ──
            FormField("ตำแหน่งของดีล") {
                DropdownField(
                    value       = uiState.dealPosition,
                    placeholder = "เลือกตำแหน่งของดีล",
                    options     = dealPositionOptions,
                    onSelect    = { idx -> onEvent(AddProjectEvent.DealPositionChanged(dealPositionOptions[idx])) }
                )
            }
            FormField("Solution เดิมของลูกค้า") {
                DropdownField(
                    value       = uiState.previousSolution,
                    placeholder = "เลือก Solution เดิม",
                    options     = previousSolutionOptions,
                    onSelect    = { idx -> onEvent(AddProjectEvent.PreviousSolutionChanged(previousSolutionOptions[idx])) }
                )
            }
            FormField("ประเภทคู่สัญญา") {
                DropdownField(
                    value       = uiState.counterpartyType,
                    placeholder = "เลือกประเภทคู่สัญญา",
                    options     = counterpartyTypeOptions,
                    onSelect    = { idx -> onEvent(AddProjectEvent.CounterpartyTypeChanged(counterpartyTypeOptions[idx])) }
                )
            }
            FormField("ความรวดเร็วในการตอบรับ") {
                DropdownField(
                    value       = uiState.responseSpeed,
                    placeholder = "เลือกความรวดเร็วในการตอบรับ",
                    options     = responseSpeedOptions,
                    onSelect    = { idx -> onEvent(AddProjectEvent.ResponseSpeedChanged(responseSpeedOptions[idx])) }
                )
            }

            // ── Site Location + Google Maps ──────────────────
            FormField("สถานที่ตั้งโครงการ") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ใช้ MapPickerField จาก MapComponents.kt ที่มีระบบค้นหา
                    MapPickerField(
                        lat = uiState.siteLat,
                        lng = uiState.siteLong,
                        onLocationPicked = { lat, lng ->
                            onEvent(AddProjectEvent.LocationPicked(lat, lng))
                        }
                    )
                }
            }

            uiState.saveError?.let {
                Text(it, color = AppColors.Error, fontSize = 13.sp)
            }

            Button(
                onClick  = { onEvent(AddProjectEvent.Save) },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (uiState.projectId != null) "บันทึกการเปลี่ยนแปลงโครงการ" else "สร้างโครงการ", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // Quick Add Customer Modal
        if (uiState.isQuickAddCustomerOpen) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { onEvent(AddProjectEvent.ToggleQuickAddCustomer(false)) },
                containerColor = AppColors.BgWhite
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ✅ saveQuickCustomer() ตั้ง isLead=true เสมอ — ไม่ใช่การสร้างลูกค้าจริง ต้องบอกให้ชัด
                    Text("สร้าง Lead ใหม่ (สร้างด่วน)", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AppColors.Primary)
                    
                    FormField("ชื่อบริษัท/ชื่อลูกค้า", required = true) {
                        FormTextField(
                            value = uiState.quickAddCompanyName,
                            onValueChange = { onEvent(AddProjectEvent.QuickAddCustomerChanged(it, uiState.quickAddCustType)) },
                            placeholder = "ระบุชื่อบริษัท หรือชื่อลูกค้า"
                        )
                    }

                    FormField("ประเภทลูกค้า", required = true) {
                        val types = listOf("Owner", "Developer", "Main Contractor", "Sub Contractor", "Installer", "Architect", "Interior Designer", "Consultant", "Industrial", "Wholesale", "Factory")
                        DropdownField(
                            value = uiState.quickAddCustType,
                            placeholder = "เลือกประเภท",
                            options = types,
                            onSelect = { idx -> onEvent(AddProjectEvent.QuickAddCustomerChanged(uiState.quickAddCompanyName, types[idx])) }
                        )
                    }

                    Button(
                        onClick = { onEvent(AddProjectEvent.SaveQuickAddCustomer) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !uiState.isSavingQuickCust && uiState.quickAddCompanyName.isNotBlank() && uiState.quickAddCustType.isNotBlank()
                    ) {
                        if (uiState.isSavingQuickCust) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("บันทึก Lead ใหม่", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }

        if (uiState.isQuickAddContactOpen) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { onEvent(AddProjectEvent.ToggleQuickAddContact(false)) },
                containerColor = AppColors.BgWhite
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("สร้างผู้ติดต่อใหม่", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Primary)
                    FormField("ชื่อ-นามสกุล", required = true) {
                        FormTextField(
                            value = uiState.quickAddContactName,
                            onValueChange = { onEvent(AddProjectEvent.QuickAddContactChanged(it, uiState.quickAddContactPhone)) },
                            placeholder = "ระบุชื่อผู้ติดต่อ"
                        )
                    }
                    FormField("เบอร์โทรศัพท์", required = true) {
                        FormTextField(
                            value = uiState.quickAddContactPhone,
                            onValueChange = { onEvent(AddProjectEvent.QuickAddContactChanged(uiState.quickAddContactName, it)) },
                            placeholder = "เช่น 06x-xxx-xxxx",
                            keyboardType = KeyboardType.Phone
                        )
                    }
                    uiState.quickAddContactError?.let {
                        Text(it, color = AppColors.Error, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { onEvent(AddProjectEvent.SaveQuickAddContact) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !uiState.isSavingQuickContact &&
                            uiState.quickAddContactName.isNotBlank() && uiState.quickAddContactPhone.isNotBlank()
                    ) {
                        if (uiState.isSavingQuickContact) {
                            CircularProgressIndicator(color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(24.dp))
                        } else {
                            Text("บันทึกผู้ติดต่อ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

// ── Member chip grid ────────────────────────────────────────
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemberChipGrid(
    options:     List<Pair<String, String>>,
    selectedIds: Set<String>,
    onToggle:    (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(12.dp)
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (id, name) ->
                val selected = id in selectedIds
                FilterChip(
                    modifier = Modifier.widthIn(max = 150.dp), // จำกัดความกว้างเพื่อไม่ให้ตกขอบ
                    selected = selected,
                    onClick  = { onToggle(id) },
                    label    = { 
                        Text(
                            text = name, 
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 2, // ยอมให้ขึ้นบรรทัดใหม่ได้ 2 บรรทัด
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start
                        ) 
                    },
                    colors   = FilterChipDefaults.filterChipColors(
                        selectedContainerColor   = AppColors.Primary,
                        selectedLabelColor       = Color.White,
                        selectedLeadingIconColor = Color.White,
                        containerColor           = Color.White,
                        labelColor               = AppColors.TextPrimary
                    ),
                    leadingIcon = if (selected) {
                        { Icon(Icons.Default.Check, null,
                            modifier = Modifier.size(14.dp)) }
                    } else null,
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = AppColors.Border,
                        selectedBorderColor = AppColors.Primary,
                        borderWidth = 1.dp
                    )
                )
            }
        }
    }
}

// ── Loading state for a field ───────────────────────────────
@Composable
private fun LoadingFieldProject() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
            .background(AppColors.BgGray),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                color = AppColors.Primary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("กำลังโหลด...", color = AppColors.TextSecondary, fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddProjectScreenPreview() {
    SalesTrackingTheme {
        AddProjectContent(
            uiState = AddProjectUiState(
                projectId = "PRJ-2023-001",
                displayProjectId = "PRJ-2023-001",
                projectName = "New Office Construction",
                branch = "Bangkok",
                customerOptions = listOf("1" to "ACME Corp", "2" to "Globex"),
                selectedCustomerName = "ACME Corp",
                selectedCustomerId = "1",
                contactOptions = listOf("c1" to "John Smith"),
                selectedContactIds = setOf("c1"),
                expectedValue = "1500000",
                startDate = "2023-11-01",
                closeDate = "2024-05-01",
                projectStatus = "New Project",
                teamOptions = listOf("t1" to "Sales Team Alpha"),
                selectedTeamId = "t1",
                selectedTeamName = "Sales Team Alpha"
            ),
            onEvent = {},
            onBack = {}
        )
    }
}
