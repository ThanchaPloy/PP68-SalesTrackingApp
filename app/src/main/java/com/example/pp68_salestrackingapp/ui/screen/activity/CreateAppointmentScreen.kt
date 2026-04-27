package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.*
import com.example.pp68_salestrackingapp.ui.theme.*
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.CreateAppointmentEvent
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.CreateAppointmentViewModel

private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFAE2138)
private val BorderGray = Color(0xFFE8E8E8)
private val BgField    = Color(0xFFF9F9F9)
private val ErrorRed   = Color(0xFFD32F2F)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateAppointmentScreen(
    activityId: String? = null,
    projectId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: CreateAppointmentViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val onEvent = viewModel::onEvent

    LaunchedEffect(activityId, projectId) {
        if (activityId != null) {
            onEvent(CreateAppointmentEvent.LoadActivity(activityId))
        } else if (projectId != null) {
            onEvent(CreateAppointmentEvent.LoadInitialProject(projectId))
        }
    }

    if (state.isSaved) {
        LaunchedEffect(Unit) { onSaved() }
    }

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
                    Text(if (activityId == null) "New Appointment" else "Edit Appointment",
                        fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = TextDark)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onEvent(CreateAppointmentEvent.Save) }) {
                        Text("Save", color = RedPrimary,
                            fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
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
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            FormField(label = "เลือกโครงการ (ไม่ระบุได้)") {
                val options = state.projectOptions.map { it.name }.toMutableList()
                options.add(0, "ไม่ระบุโครงการ")
                
                DropdownField(
                    value        = if (state.selectedProjectId == null) "ไม่ระบุโครงการ" else (state.selectedProjectName ?: ""),
                    placeholder  = "เลือกโครงการ",
                    options      = options,
                    isError      = state.projectError != null,
                    errorMsg     = state.projectError,
                    onSelect     = { idx ->
                        if (idx == 0) {
                            onEvent(CreateAppointmentEvent.ProjectSelected(null, null, null))
                        } else {
                            val opt = state.projectOptions[idx - 1]
                            onEvent(CreateAppointmentEvent.ProjectSelected(
                                opt.id,
                                opt.name,
                                opt.status
                            ))
                        }
                    }
                )
            }

            FormField(label = "หัวข้อกิจกรรม") {
                FormTextField(
                    value         = state.titleTopic,
                    onValueChange = { onEvent(CreateAppointmentEvent.TitleChanged(it)) },
                    placeholder   = "ระบุหัวข้อกิจกรรม",
                    leadingIcon   = Icons.AutoMirrored.Filled.Label
                )
            }

            FormField(label = "ผู้ติดต่อ") {
                if (state.selectedProjectId == null) {
                    // ✅ กรณีไม่เลือกโครงการ: แสดงช่องค้นหาและ Dropdown รวม
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormTextField(
                            value = state.contactSearchQuery,
                            onValueChange = { onEvent(CreateAppointmentEvent.ContactSearchQueryChanged(it)) },
                            placeholder = "ค้นหาชื่อผู้ติดต่อ...",
                            leadingIcon = Icons.Default.Search
                        )
                        
                        val filteredContacts = if (state.contactSearchQuery.isBlank()) {
                            state.allContactOptions
                        } else {
                            state.allContactOptions.filter { it.name.contains(state.contactSearchQuery, ignoreCase = true) }
                        }
                        
                        if (filteredContacts.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                filteredContacts.take(15).forEach { contact ->
                                    val isSelected = state.selectedContactIds.contains(contact.id)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onEvent(CreateAppointmentEvent.ContactToggled(contact.id)) },
                                        label = { Text(contact.name, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = RedPrimary.copy(alpha = 0.1f),
                                            selectedLabelColor = RedPrimary
                                        )
                                    )
                                }
                            }
                        } else {
                            Text("ไม่พบรายชื่อผู้ติดต่อ", color = TextGray, fontSize = 12.sp)
                        }
                    }
                } else if (state.isLoadingContacts) {
                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                } else if (state.contactOptions.isEmpty()) {
                    Text("ไม่มีรายชื่อผู้ติดต่อในโครงการนี้", color = TextGray, fontSize = 13.sp)
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.contactOptions.forEach { contact ->
                            val isSelected = state.selectedContactIds.contains(contact.id)
                            FilterChip(
                                selected = isSelected,
                                onClick = { onEvent(CreateAppointmentEvent.ContactToggled(contact.id)) },
                                label = { Text(contact.name, fontSize = 12.sp) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = RedPrimary.copy(alpha = 0.1f),
                                    selectedLabelColor = RedPrimary,
                                    selectedLeadingIconColor = RedPrimary
                                )
                            )
                        }
                    }
                }
            }

            FormField(label = "ประเภทกิจกรรม") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    listOf("onsite" to "On-site", "online" to "Online", "call" to "Call").forEach { (valStr, label) ->
                        val isSelected = state.activityType == valStr
                        Surface(
                            onClick = { onEvent(CreateAppointmentEvent.TypeChanged(valStr)) },
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) RedPrimary else White,
                            border = if (isSelected) null else BorderStroke(1.dp, BorderGray)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(label, color = if (isSelected) White else TextDark, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            FormField(label = "กำหนดการ") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DatePickerField(
                        selectedDate   = state.plannedDate,
                        placeholder    = "เลือกวันที่นัดหมาย",
                        onDateSelected = { onEvent(CreateAppointmentEvent.DateChanged(it)) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { onEvent(CreateAppointmentEvent.ShowStartTimePicker) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGray),
                            color = White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(state.startTime ?: "เริ่ม", fontSize = 14.sp, color = if (state.startTime == null) TextGray else TextDark)
                                Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp), tint = TextGray)
                            }
                        }
                        Surface(
                            onClick = { onEvent(CreateAppointmentEvent.ShowEndTimePicker) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, BorderGray),
                            color = White
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(state.endTime ?: "สิ้นสุด", fontSize = 14.sp, color = if (state.endTime == null) TextGray else TextDark)
                                Icon(Icons.Default.AccessTime, null, modifier = Modifier.size(18.dp), tint = TextGray)
                            }
                        }
                    }
                }
            }

            FormField(
                label = "วัตถุประสงค์/เป้าหมายกิจกรรม"
            ) {
                if (state.isLoadingMasters) {
                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                            .background(BgField)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Standard options
                        for (master in state.masterOptions) {
                            val isSelected = state.selectedMasterIds.contains(master.masterId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onEvent(CreateAppointmentEvent.MasterToggled(master.masterId)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked         = isSelected,
                                    onCheckedChange = {
                                        onEvent(CreateAppointmentEvent.MasterToggled(master.masterId))
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = RedPrimary)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text       = master.actName,
                                    fontSize   = 14.sp,
                                    color      = if (isSelected) TextDark else TextGray,
                                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }

                        // "Other" option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEvent(CreateAppointmentEvent.OtherToggled) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked         = state.isOtherSelected,
                                onCheckedChange = { onEvent(CreateAppointmentEvent.OtherToggled) },
                                colors = CheckboxDefaults.colors(checkedColor = RedPrimary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text       = "อื่นๆ",
                                fontSize   = 14.sp,
                                color      = if (state.isOtherSelected) TextDark else TextGray,
                                fontWeight = if (state.isOtherSelected) FontWeight.Medium else FontWeight.Normal
                            )
                        }

                        if (state.isOtherSelected) {
                            OutlinedTextField(
                                value = state.otherObjectiveText,
                                onValueChange = { onEvent(CreateAppointmentEvent.OtherObjectiveTextChanged(it)) },
                                placeholder = { Text("ระบุวัตถุประสงค์อื่นๆ", fontSize = 13.sp) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = RedPrimary,
                                    unfocusedBorderColor = BorderGray
                                )
                            )
                        }

                        state.masterError?.let {
                            Text(
                                it,
                                color    = ErrorRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }
                    }
                }
            }

            FormField(label = "สถานที่") {
                GoogleMapPickerField(
                    lat = state.lat ?: 13.7563,
                    lng = state.lng ?: 100.5018,
                    onLocationPicked = { lat, lng ->
                        onEvent(CreateAppointmentEvent.LocationPicked(lat, lng))
                    }
                )
            }

            state.saveError?.let {
                Text(it, color = ErrorRed, fontSize = 13.sp)
            }

            Button(
                onClick  = { onEvent(CreateAppointmentEvent.Save) },
                enabled  = !state.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(if (activityId == null) Icons.Default.Add else Icons.Default.Save, null, tint = White,
                        modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (activityId == null) "สร้างการนัดหมาย" else "บันทึกการแก้ไข", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = White)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.showStartTimePicker) {
        TimePickerDialogWrapper(
            onConfirm = { h, m ->
                onEvent(CreateAppointmentEvent.StartTimeSelected(
                    "%02d:%02d %s".format(if (h > 12) h - 12 else if (h == 0) 12 else h, m, if (h >= 12) "PM" else "AM")
                ))
            },
            onDismiss = { onEvent(CreateAppointmentEvent.DismissTimePicker) }
        )
    }

    if (state.showEndTimePicker) {
        TimePickerDialogWrapper(
            onConfirm = { h, m ->
                onEvent(CreateAppointmentEvent.EndTimeSelected(
                    "%02d:%02d %s".format(if (h > 12) h - 12 else if (h == 0) 12 else h, m, if (h >= 12) "PM" else "AM")
                ))
            },
            onDismiss = { onEvent(CreateAppointmentEvent.DismissTimePicker) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogWrapper(
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("OK", color = RedPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextGray)
            }
        },
        text = {
            TimePicker(state = state)
        }
    )
}
