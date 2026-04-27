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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.*
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

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
        }
    }

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onSaved() }

    AddProjectContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        lossReasonOptions = viewModel.lossReasonOptions
    )
}

@Composable
fun AddProjectContent(
    uiState: AddProjectUiState,
    onEvent: (AddProjectEvent) -> Unit,
    onBack: () -> Unit,
    lossReasonOptions: List<String> = emptyList()
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
                            text = uiState.generatedProjectNumber,
                            fontSize   = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color      = if (uiState.generatedProjectNumber.contains("รอกด"))
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

            // ── Customer/Company * ────────────────────────────
            FormField("ลูกค้า/บริษัท", required = true) {
                if (uiState.isLoadingCustomers) LoadingFieldProject()
                else DropdownField(
                    value       = uiState.selectedCustomerName ?: "",
                    placeholder = "เลือกลูกค้า",
                    options     = uiState.customerOptions.map { it.second },
                    isError     = uiState.customerError != null,
                    errorMsg    = uiState.customerError,
                    onSelect    = { idx ->
                        onEvent(AddProjectEvent.CustomerSelected(
                            uiState.customerOptions[idx].first,
                            uiState.customerOptions[idx].second
                        ))
                    }
                )
            }

            // ── Contact Person (กรองตาม customer) ────────────
            FormField("ผู้ติดต่อ") {
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

            // ── Billing Branch * ──────────────────────────────
            FormField("สาขาที่เปิดบิล", required = true) {
                if (uiState.isLoadingTeams) LoadingFieldProject()
                else DropdownField(
                    value       = uiState.selectedBillingBranchName ?: "",
                    placeholder = "เลือกสาขาที่เปิดบิล",
                    options     = uiState.teamOptions.map { it.second },
                    isError     = uiState.billingBranchError != null,
                    errorMsg    = uiState.billingBranchError,
                    onSelect    = { idx ->
                        onEvent(AddProjectEvent.BillingBranchSelected(
                            uiState.teamOptions[idx].first,
                            uiState.teamOptions[idx].second
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

                    if (uiState.selectedTeamId != null) {
                        if (uiState.isLoadingMembers) {
                            LoadingFieldProject()
                        } else if (uiState.teamMemberOptions.isNotEmpty()) {
                            Text("เลือกสมาชิกที่รับผิดชอบ",
                                fontSize = 12.sp, color = AppColors.TextSecondary)
                            MemberChipGrid(
                                options     = uiState.teamMemberOptions,
                                selectedIds = uiState.selectedMemberIds,
                                onToggle    = { onEvent(AddProjectEvent.MemberToggled(it)) }
                            )
                        }
                    }
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

            // ── Project Status * ──────────────────────────────
            val statusList = listOf(
                "Lead", "New Project", "Quotation", "Bidding",
                "Make a Decision", "Assured", "PO", "Lost", "Failed"
            )
            FormField("สถานะโครงการ", required = true) {
                DropdownField(
                    value       = uiState.projectStatus ?: "",
                    placeholder = "เลือกสถานะ",
                    options     = statusList,
                    isError     = uiState.statusError != null,
                    errorMsg    = uiState.statusError,
                    onSelect    = { idx ->
                        onEvent(AddProjectEvent.StatusChanged(statusList[idx]))
                    }
                )
            }

            // ── Loss Reason (แสดงเมื่อเป็น Lost หรือ Failed) ──────────────────
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
                            }
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

            // ── Site Location + Google Maps ──────────────────
            FormField("สถานที่ตั้งโครงการ") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ใช้ GoogleMapPickerField จาก MapComponents.kt ที่มีระบบค้นหา
                    GoogleMapPickerField(
                        lat = uiState.siteLat ?: 13.7563,
                        lng = uiState.siteLong ?: 100.5018,
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
                    Text(if (uiState.projectId != null) "อัปเดตโครงการ" else "สร้างโครงการ", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

// ── Member chip grid ────────────────────────────────────────
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
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            options.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (id, name) ->
                        val selected = id in selectedIds
                        FilterChip(
                            selected = selected,
                            onClick  = { onToggle(id) },
                            label    = { Text(name, fontSize = 12.sp, maxLines = 1) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor   = AppColors.Primary,
                                selectedLabelColor       = Color.White,
                                selectedLeadingIconColor = Color.White
                            ),
                            leadingIcon = if (selected) {
                                { Icon(Icons.Default.Check, null,
                                    modifier = Modifier.size(14.dp)) }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

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
                projectNumber = "PRJ-2023-001",
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
                selectedTeamName = "Sales Team Alpha",
                teamMemberOptions = listOf("m1" to "Alice", "m2" to "Bob", "m3" to "Charlie"),
                selectedMemberIds = setOf("m1", "m2")
            ),
            onEvent = {},
            onBack = {}
        )
    }
}
