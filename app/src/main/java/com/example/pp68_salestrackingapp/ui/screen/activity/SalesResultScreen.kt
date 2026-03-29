package com.example.pp68_salestrackingapp.ui.screen.activity

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.DatePickerField
import com.example.pp68_salestrackingapp.ui.components.DropdownField
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SalesResultViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SalesResultUiState

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

    LaunchedEffect(s.isSaved) {
        if (s.isSaved) onSaved()
    }

    LaunchedEffect(s.error) {
        s.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    SalesResultContent(
        s = s,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onReportDateChanged = viewModel::onReportDateChanged,
        onStatusToggle = viewModel::onStatusToggle,
        onNewStatusSelected = viewModel::onNewStatusSelected,
        onOpportunitySelected = viewModel::onOpportunitySelected,
        onDealPositionChanged = viewModel::onDealPositionChanged,
        onPreviousSolutionChanged = viewModel::onPreviousSolutionChanged,
        onCounterpartyMultiplierChanged = viewModel::onCounterpartyMultiplierChanged,
        onResponseSpeedChanged = viewModel::onResponseSpeedChanged,
        onProposalToggle = viewModel::onProposalToggle,
        onProposalDateChanged = viewModel::onProposalDateChanged,
        onCompetitorCountChanged = viewModel::onCompetitorCountChanged,
        onSummaryChanged = viewModel::onSummaryChanged,
        onSave = viewModel::save
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
                // 0️⃣ วันที่บันทึกผล
                SectionCard(title = "วันที่บันทึกผล", icon = Icons.Default.CalendarToday) {
                    DatePickerField(
                        selectedDate = s.reportDate,
                        placeholder = "เลือกวันที่บันทึกผล",
                        onDateSelected = onReportDateChanged
                    )
                }

                // 1️⃣ สถานะโครงการ
                SectionCard(title = "1. สถานะโครงการ", icon = Icons.Default.Info) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("สถานะปัจจุบัน: ", fontSize = 14.sp, color = TextGray)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFEEEEEE)
                            ) {
                                Text(
                                    text = s.currentStatus.ifBlank { "N/A" },
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
                                onCheckedChange = { onStatusToggle(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = RedPrimary)
                            )
                        }

                        if (s.isStatusUpdateEnabled) {
                            val statusList = listOf(
                                "Lead", "New Project", "Quotation", "Bidding",
                                "Make a Decision", "Assured", "PO", "Completed", "Lost", "Failed"
                            )
                            DropdownField(
                                value = s.newStatus,
                                placeholder = "เลือกสถานะใหม่",
                                options = statusList,
                                onSelect = { onNewStatusSelected(statusList[it]) }
                            )
                        }
                    }
                }

                // 2️⃣ โอกาสในการสำเร็จ
                SectionCard(title = "2. โอกาสในการสำเร็จ", icon = Icons.AutoMirrored.Filled.TrendingUp) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OpportunityButton("HOT 🔥", s.opportunityScore == "HOT", Modifier.weight(1f)) {
                            onOpportunitySelected("HOT")
                        }
                        OpportunityButton("WARM ☀️", s.opportunityScore == "WARM", Modifier.weight(1f)) {
                            onOpportunitySelected("WARM")
                        }
                        OpportunityButton("COLD ❄️", s.opportunityScore == "COLD", Modifier.weight(1f)) {
                            onOpportunitySelected("COLD")
                        }
                    }
                }

                // 3️⃣ สรุปการเข้าพบ
                SectionCard(title = "3. สรุปการเข้าพบ", icon = Icons.AutoMirrored.Filled.Notes) {
                    OutlinedTextField(
                        value = s.visitSummary,
                        onValueChange = { onSummaryChanged(it) },
                        placeholder = { Text("เขียนสรุปรายละเอียดการสนทนา...", fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                // 4️⃣ Deal Position
                SectionCard(title = "4. Deal Position", icon = Icons.Default.Place) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val options = listOf(
                            "ลูกค้าใช้เราอยู่แล้ว การต่อสัญญามีโอกาสสูงมาก",
                            "ลูกค้าเลือกเราเป็นตัวหลัก คู่แข่งอื่นเป็นแค่ backup",
                            "ถูกเชิญมาเพื่อ benchmark ราคา โอกาสต่ำ"
                        )
                        options.forEach { opt ->
                            SelectOption(opt, s.dealPosition == opt) { onDealPositionChanged(opt) }
                        }
                    }
                }

                // 5️⃣ Solution เดิมของลูกค้า
                SectionCard(title = "5. Solution เดิมของลูกค้า", icon = Icons.Default.History) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val options = listOf(
                            "ไม่มี Solution เดิม",
                            "มีระบบเดิมที่ไม่ใช่คู่แข่ง",
                            "ใช้คู่แข่งอยู่และไม่มีปัญหา"
                        )
                        options.forEach { opt ->
                            SelectOption(opt, s.previousSolution == opt) { onPreviousSolutionChanged(opt) }
                        }
                    }
                }

                // 6️⃣ Counterparty Multiplier
                SectionCard(title = "6. Counterparty Multiplier", icon = Icons.Default.Groups) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val options = listOf(
                            "ดีลกับ Main Contractor โดยตรง",
                            "ดีลผ่าน Installer — Main Contractor ได้งานแล้ว",
                            "ดีลผ่าน Installer — Main Contractor ยังไม่ได้งาน"
                        )
                        options.forEach { opt ->
                            SelectOption(opt, s.counterpartyMultiplier == opt) { onCounterpartyMultiplierChanged(opt) }
                        }
                    }
                }

                // 7️⃣ ความเร็วในการตอบกลับ
                SectionCard(title = "7. ความเร็วในการตอบกลับ", icon = Icons.Default.Speed) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val options = listOf("เร็ว", "ปกติ", "ช้าหรือเงียบ")
                        options.forEach { opt ->
                            SelectOption(opt, s.responseSpeed == opt) { onResponseSpeedChanged(opt) }
                        }
                    }
                }

                // 8️⃣ การส่ง Proposal
                SectionCard(title = "8. การส่ง Proposal", icon = Icons.Default.Description) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("ส่ง Proposal แล้ว", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = s.isProposalSent,
                                onCheckedChange = { onProposalToggle(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = RedPrimary)
                            )
                        }

                        if (s.isProposalSent) {
                            DatePickerField(
                                selectedDate = s.proposalDate,
                                placeholder = "วันที่ส่ง Proposal",
                                onDateSelected = { onProposalDateChanged(it) }
                            )
                        }
                    }
                }

                // 9️⃣ จำนวนคู่แข่ง
                SectionCard(title = "9. จำนวนคู่แข่ง (ถ้ามี)", icon = Icons.Default.Compare) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { onCompetitorCountChanged(-1) },
                            modifier = Modifier.border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Remove, null, tint = RedPrimary)
                        }
                        
                        Text(
                            text = "${s.competitorCount}",
                            modifier = Modifier.padding(horizontal = 32.dp),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = { onCompetitorCountChanged(1) },
                            modifier = Modifier.border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Add, null, tint = RedPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { onSave() },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RedPrimary),
                    enabled = !s.isSaving
                ) {
                    if (s.isSaving) {
                        CircularProgressIndicator(color = White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("บันทึกข้อมูล", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = TextDark)
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun OpportunityButton(label: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) RedPrimary else BgLight,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (isSelected) White else TextGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SelectOption(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = RedPrimary)
        )
        Text(label, fontSize = 13.sp, color = TextDark, lineHeight = 18.sp)
    }
}

@Preview(showBackground = true)
@Composable
fun SalesResultScreenPreview() {
    SalesTrackingTheme {
        SalesResultContent(
            s = SalesResultUiState(
                currentStatus = "Quotation",
                isStatusUpdateEnabled = true,
                newStatus = "Bidding",
                opportunityScore = "HOT",
                dealPosition = "ลูกค้าเลือกเราเป็นตัวหลัก คู่แข่งอื่นเป็นแค่ backup",
                previousSolution = "ไม่มี Solution เดิม",
                counterpartyMultiplier = "ดีลกับ Main Contractor โดยตรง",
                responseSpeed = "เร็ว",
                isProposalSent = true,
                proposalDate = "2023-10-27",
                competitorCount = 2,
                visitSummary = "Met with the CTO and discussed the project timeline."
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
            onSummaryChanged = {},
            onSave = {}
        )
    }
}
