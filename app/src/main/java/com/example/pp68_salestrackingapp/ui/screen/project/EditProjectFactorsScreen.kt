package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.DatePickerField
import com.example.pp68_salestrackingapp.ui.components.DropdownField
import com.example.pp68_salestrackingapp.ui.components.FormField
import com.example.pp68_salestrackingapp.ui.components.FormTextField
import com.example.pp68_salestrackingapp.ui.viewmodels.project.EditProjectFactorsViewModel

private val RedPrimary = Color(0xFFD32F2F)
private val TextDark   = Color(0xFF212121)
private val TextGray   = Color(0xFF757575)
private val BorderGray = Color(0xFFE0E0E0)
private val White      = Color(0xFFFFFFFF)

// หน้าจอแก้ไข "ปัจจัย" ของโครงการโดยเฉพาะ (ข้อ 4-9 ในหน้าบันทึกผล) ไม่มีฟิลด์อื่นของโครงการเลย
// ต่างจาก EditProjectScreen ที่เป็นฟอร์มเต็ม — ที่นี่แก้ได้เฉพาะปัจจัย และมีประวัติการแก้ไขให้ดู
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProjectFactorsScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: EditProjectFactorsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) { viewModel.load(projectId) }

    // บันทึกสำเร็จแล้วเด้งกลับหน้าเดิมทันที (หน้าที่เรียกมาจะ refresh ค่าที่แก้เอง)
    LaunchedEffect(state.isSaved) { if (state.isSaved) onBack() }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "แก้ไขปัจจัยของดีล",
                        fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = TextDark
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { viewModel.save() }, enabled = !state.isSaving && !state.isLoading) {
                        Text("บันทึก", color = RedPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        },
        containerColor = White
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("โครงการ: ${state.projectName}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
            Text(
                "ค่าเหล่านี้เป็นของโครงการ — แก้ที่นี่แล้วมีผลกับทุกที่ที่แสดงค่านี้",
                fontSize = 12.sp, color = TextGray
            )

            FormField(label = "4. สถานะการแข่งขันของดีล", required = true) {
                DropdownField(
                    value = state.dealPosition,
                    placeholder = "เลือกสถานะการแข่งขัน",
                    options = viewModel.dealPositionOptions,
                    onSelect = { idx -> viewModel.onDealPositionChange(viewModel.dealPositionOptions[idx]) }
                )
            }

            FormField(label = "5. โซลูชันเดิมของลูกค้า", required = true) {
                DropdownField(
                    value = state.previousSolution,
                    placeholder = "เลือกโซลูชันเดิม",
                    options = viewModel.previousSolutionOptions,
                    onSelect = { idx -> viewModel.onPreviousSolutionChange(viewModel.previousSolutionOptions[idx]) }
                )
            }

            FormField(label = "6. ลักษณะคู่สัญญา", required = true) {
                DropdownField(
                    value = state.counterpartyType,
                    placeholder = "เลือกลักษณะคู่สัญญา",
                    options = viewModel.counterpartyTypeOptions,
                    onSelect = { idx -> viewModel.onCounterpartyTypeChange(viewModel.counterpartyTypeOptions[idx]) }
                )
            }

            FormField(label = "7. ความเร็วในการตอบสนองของลูกค้า", required = true) {
                DropdownField(
                    value = state.responseSpeed,
                    placeholder = "เลือกความเร็วการตอบสนอง",
                    options = viewModel.responseSpeedOptions,
                    onSelect = { idx -> viewModel.onResponseSpeedChange(viewModel.responseSpeedOptions[idx]) }
                )
            }

            HorizontalDivider(color = BorderGray)

            FormField(label = "8. การส่งใบเสนอราคา") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ส่งใบเสนอราคาแล้ว", fontSize = 14.sp, color = TextDark)
                        Switch(
                            checked = state.isProposalSent,
                            onCheckedChange = viewModel::onProposalSentToggle,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = White, checkedTrackColor = RedPrimary
                            )
                        )
                    }
                    if (state.isProposalSent) {
                        DatePickerField(
                            selectedDate = state.proposalDate,
                            placeholder = "เลือกวันที่ส่งใบเสนอราคา",
                            onDateSelected = viewModel::onProposalDateChange
                        )
                    }
                }
            }

            FormField(label = "9. จำนวนคู่แข่ง") {
                FormTextField(
                    value = state.competitorCount,
                    onValueChange = viewModel::onCompetitorCountChange,
                    placeholder = "0",
                    keyboardType = KeyboardType.Number
                )
            }

            state.error?.let {
                Text(it, color = RedPrimary, fontSize = 13.sp)
            }

            HorizontalDivider(color = BorderGray)

            // ประวัติการแก้ไข — ปัจจัยพวกนี้เขียนทับค่าเดิม ไม่ได้เก็บเป็นเวอร์ชันเหมือนบันทึกผล
            // จึงต้องมี log แยกให้ย้อนดูได้ว่าค่าเคยเปลี่ยนจากอะไรเป็นอะไรเมื่อไหร่
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, null, tint = TextGray, modifier = Modifier.size(18.dp))
                Text("ประวัติการแก้ไข", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextDark)
            }

            when {
                state.historyError != null ->
                    Text(state.historyError!!, fontSize = 13.sp, color = TextGray)
                state.history.isEmpty() ->
                    Text("ยังไม่มีประวัติการแก้ไข", fontSize = 13.sp, color = TextGray)
                else -> state.history.forEach { log ->
                    FactorLogRow(
                        fieldLabel = factorFieldLabel(log.fieldKey),
                        oldValue = factorDisplayValue(log.fieldKey, log.oldValue),
                        newValue = factorDisplayValue(log.fieldKey, log.newValue),
                        changedBy = log.changedBy,
                        changedAt = log.changedAt
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun FactorLogRow(
    fieldLabel: String,
    oldValue: String,
    newValue: String,
    changedBy: String?,
    changedAt: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = White,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(fieldLabel, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextDark)
            Text("$oldValue  →  $newValue", fontSize = 13.sp, color = TextDark)
            Text(
                listOfNotNull(formatLogTime(changedAt), changedBy?.takeIf { it.isNotBlank() })
                    .joinToString(" • "),
                fontSize = 11.sp, color = TextGray
            )
        }
    }
}

// field_key จาก DB -> ชื่อหัวข้อที่ผู้ใช้เห็นในฟอร์ม (เลขข้อตรงกับหน้าบันทึกผล)
private fun factorFieldLabel(fieldKey: String): String = when (fieldKey) {
    "deal_position"     -> "4. สถานะการแข่งขันของดีล"
    "current_solution"  -> "5. โซลูชันเดิมของลูกค้า"
    "counterparty_type" -> "6. ลักษณะคู่สัญญา"
    "response_speed"    -> "7. ความเร็วในการตอบสนอง"
    "is_proposal_sent"  -> "8. การส่งใบเสนอราคา"
    "proposal_date"     -> "8. วันที่ส่งใบเสนอราคา"
    "competitor_count"  -> "9. จำนวนคู่แข่ง"
    else                -> fieldKey
}

// log เก็บ "รหัส" ของตัวเลือก (และ true/false ของสวิตช์) ต้องแปลงกลับเป็นข้อความที่อ่านรู้เรื่อง
private fun factorDisplayValue(fieldKey: String, raw: String?): String {
    if (raw.isNullOrBlank()) return "ไม่ระบุ"
    val df = com.example.pp68_salestrackingapp.utils.DealFactors
    return when (fieldKey) {
        "deal_position"     -> df.codeToLabel(df.DEAL_POSITION)[raw] ?: raw
        "current_solution"  -> df.codeToLabel(df.PREVIOUS_SOLUTION)[raw] ?: raw
        "counterparty_type" -> df.codeToLabel(df.COUNTERPARTY_TYPE)[raw] ?: raw
        "response_speed"    -> df.codeToLabel(df.RESPONSE_SPEED)[raw] ?: raw
        "is_proposal_sent"  -> if (raw.equals("true", ignoreCase = true)) "ส่งแล้ว" else "ยังไม่ส่ง"
        "competitor_count"  -> "$raw ราย"
        else                -> raw
    }
}

// changed_at เป็น timestamptz ของ Postgres — โชว์แบบสั้นอ่านง่าย ถ้า parse ไม่ได้ก็โชว์ดิบไปเลย
private fun formatLogTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", java.util.Locale("th", "TH")))
}.getOrElse {
    runCatching {
        java.time.OffsetDateTime.parse(iso)
            .atZoneSameInstant(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", java.util.Locale("th", "TH")))
    }.getOrElse { iso }
}
