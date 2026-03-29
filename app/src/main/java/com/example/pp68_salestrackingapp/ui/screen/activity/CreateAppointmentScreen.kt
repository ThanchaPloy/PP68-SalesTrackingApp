package com.example.pp68_salestrackingapp.ui.screen.activity

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.ActivityMaster
import com.example.pp68_salestrackingapp.ui.components.GoogleMapPickerField
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.CreateAppointmentViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.CreateAppointmentEvent
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.CreateAppointmentUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ProjectOption
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ContactOption

// ── Colors ────────────────────────────────────────────────────
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFCC1D1D)
private val BgField    = Color(0xFFF8F8F8)
private val BorderGray = Color(0xFFE8E8E8)
private val ErrorRed   = Color(0xFFE53935)
private val LabelGray  = Color(0xFF666666)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreateAppointmentScreen(
    activityId: String? = null,
    projectId: String? = null,
    onBack:    () -> Unit,
    onSaved:   () -> Unit,
    viewModel: CreateAppointmentViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()
    
    LaunchedEffect(activityId, projectId) {
        if (activityId != null) {
            viewModel.onEvent(CreateAppointmentEvent.LoadActivity(activityId))
        } else if (projectId != null) {
            // If we came from a project, auto-select it
            viewModel.onEvent(CreateAppointmentEvent.LoadInitialProject(projectId))
        }
    }

    LaunchedEffect(s.isSaved) { if (s.isSaved) onSaved() }

    CreateAppointmentContent(
        activityId = activityId,
        state = s,
        onEvent = viewModel::onEvent,
        onBack = onBack
    )
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CreateAppointmentContent(
    activityId: String?,
    state: CreateAppointmentUiState,
    onEvent: (CreateAppointmentEvent) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 1.dp, color = White) {
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
                            fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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

            FieldSection("PROJECT SELECTION") {
                AppDropdown(
                    value        = state.selectedProjectName ?: "",
                    placeholder  = "Select Project",
                    icon         = Icons.Default.Work,
                    options      = state.projectOptions.map { it.name },
                    isLoading    = state.isLoadingProjects,
                    isError      = state.projectError != null,
                    errorMsg     = state.projectError,
                    onSelect     = { idx ->
                        val opt = state.projectOptions[idx]
                        onEvent(CreateAppointmentEvent.ProjectSelected(
                            opt.id,
                            opt.name,
                            opt.status
                        ))
                    }
                )
            }

            FieldSection("TITLE TOPIC") {
                AppTextField(
                    value         = state.titleTopic,
                    onValueChange = { onEvent(CreateAppointmentEvent.TitleChanged(it)) },
                    placeholder   = "enter title of activity",
                    icon          = Icons.AutoMirrored.Filled.Label
                )
            }

            FieldSection("CONTACT PERSON") {
                if (state.selectedProjectId == null) {
                    AppDropdownDisabled("เลือก Project ก่อน")
                } else if (state.isLoadingContacts) {
                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                } else if (state.contactOptions.isEmpty()) {
                    Text("ไม่มีรายชื่อผู้ติดต่อ", color = TextGray, fontSize = 13.sp)
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

            FieldSection("ACTIVITY TYPE") {
                ActivityTypeToggle(
                    selected = state.activityType,
                    onSelect = { onEvent(CreateAppointmentEvent.TypeChanged(it)) }
                )
            }

            FieldSection("SCHEDULE") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateRow(
                        selectedDate   = state.plannedDate,
                        onDateSelected = { onEvent(CreateAppointmentEvent.DateChanged(it)) }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TimeBox(
                            label    = "START",
                            time     = state.startTime ?: "09:00 AM",
                            modifier = Modifier.weight(1f),
                            onClick  = { onEvent(CreateAppointmentEvent.ShowStartTimePicker) }
                        )
                        TimeBox(
                            label    = "END",
                            time     = state.endTime ?: "10:30 AM",
                            modifier = Modifier.weight(1f),
                            onClick  = { onEvent(CreateAppointmentEvent.ShowEndTimePicker) }
                        )
                    }
                }
            }

            FieldSection(
                label = "OBJECTIVE",
                trailing = {
                    if (state.selectedMasterIds.isNotEmpty()) {
                        Badge(containerColor = RedPrimary) {
                            Text("${state.selectedMasterIds.size}", color = White)
                        }
                    }
                }
            ) {
                when {
                    // ✅ ยังไม่เลือก project
                    state.selectedProjectId == null -> {
                        AppDropdownDisabled("เลือก Project ก่อน")
                    }

                    // กำลังโหลด
                    state.isLoadingMasters -> {
                        LoadingField()
                    }

                    // ✅ เลือก project แล้วแต่ไม่มี objective ในสเตจนี้
                    state.masterOptions.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ไม่มีกิจกรรมในสเตจนี้",
                                color    = TextGray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // ✅ แสดง checklist ที่ filter แล้ว
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                                .background(BgField)
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
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
                            if (state.masterError != null) {
                                Text(
                                    state.masterError,
                                    color    = ErrorRed,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 12.dp)
                                )
                            }
                        }
                    }
                }
            }

            FieldSection("LOCATION") {
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
                    Text(if (activityId == null) "Create Appointment" else "Save Changes", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = White)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.showStartTimePicker) {
        TimePickerDialog(
            onConfirm = { h, m ->
                onEvent(CreateAppointmentEvent.StartTimeSelected(
                    "%02d:%02d %s".format(if (h > 12) h - 12 else if (h == 0) 12 else h, m, if (h >= 12) "PM" else "AM")
                ))
            },
            onDismiss = { onEvent(CreateAppointmentEvent.DismissTimePicker) }
        )
    }
    if (state.showEndTimePicker) {
        TimePickerDialog(
            onConfirm = { h, m ->
                onEvent(CreateAppointmentEvent.EndTimeSelected(
                    "%02d:%02d %s".format(if (h > 12) h - 12 else if (h == 0) 12 else h, m, if (h >= 12) "PM" else "AM")
                ))
            },
            onDismiss = { onEvent(CreateAppointmentEvent.DismissTimePicker) }
        )
    }
}

@Composable
private fun FieldSection(
    label: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                color = LabelGray, letterSpacing = 0.8.sp)
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun AppTextField(
    value: String, onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextGray, fontSize = 14.sp) },
        leadingIcon = { Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(20.dp)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor    = BorderGray,
            focusedBorderColor      = RedPrimary,
            unfocusedContainerColor = BgField,
            focusedContainerColor   = White,
            unfocusedTextColor      = TextDark,
            focusedTextColor        = TextDark,
            cursorColor             = RedPrimary
        ),
        singleLine = true
    )
}

@Composable
private fun AppDropdown(
    value: String, placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    options: List<String>,
    isLoading: Boolean = false,
    isError: Boolean = false,
    errorMsg: String? = null,
    onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (isError) ErrorRed else BorderGray,
                    RoundedCornerShape(10.dp)
                )
                .background(BgField)
                .clickable { if (!isLoading) expanded = true }
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
                Text(
                    text     = value.ifBlank { placeholder },
                    color    = if (value.isBlank()) TextGray else TextDark,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (isLoading) {
                    CircularProgressIndicator(
                        color = RedPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = TextGray)
                }
            }
        }
        errorMsg?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
        DropdownMenu(
            expanded         = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { idx, option ->
                DropdownMenuItem(
                    text    = { Text(option, fontSize = 14.sp) },
                    onClick = { onSelect(idx); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun AppDropdownDisabled(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(Color(0xFFF0F0F0))
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(message, color = TextGray, fontSize = 14.sp)
    }
}

@Composable
private fun ActivityTypeToggle(
    selected: String,
    onSelect: (String) -> Unit
) {
    val types = listOf(
        "onsite" to Pair(Icons.Default.Store,    "Onsite"),
        "online" to Pair(Icons.Default.Videocam, "Online"),
        "call"   to Pair(Icons.Default.Call,     "Call")
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(BgField)
    ) {
        types.forEachIndexed { idx, (type, pair) ->
            val (icon, label) = pair
            val isSelected = selected == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(
                        when (idx) {
                            0    -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                            types.size - 1 -> RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    )
                    .background(if (isSelected) White else Color.Transparent)
                    .border(
                        width = if (isSelected) 1.dp else 0.dp,
                        color = if (isSelected) BorderGray else Color.Transparent,
                        shape = when (idx) {
                            0    -> RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)
                            types.size - 1 -> RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)
                            else -> RoundedCornerShape(0.dp)
                        }
                    )
                    .clickable { onSelect(type) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(icon, null,
                        tint     = if (isSelected) RedPrimary else TextGray,
                        modifier = Modifier.size(16.dp))
                    Text(label, fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (isSelected) TextDark else TextGray)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRow(selectedDate: String?, onDateSelected: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(BgField)
            .clickable { showPicker = true }
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.CalendarMonth, null,
                tint = RedPrimary, modifier = Modifier.size(20.dp))
            Text(
                text     = selectedDate ?: "Select date",
                color    = if (selectedDate == null) TextGray else TextDark,
                fontSize = 14.sp, modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.KeyboardArrowDown, null, tint = TextGray)
        }
    }
    if (showPicker) {
        val state = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val date = java.text.SimpleDateFormat(
                            "MMM dd, yyyy", java.util.Locale.ENGLISH
                        ).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                            .format(java.util.Date(millis))
                        onDateSelected(date)
                    }
                    showPicker = false
                }) { Text("ยืนยัน", color = RedPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("ยกเลิก", color = TextGray)
                }
            }
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(
                selectedDayContainerColor = RedPrimary,
                todayDateBorderColor      = RedPrimary
            ))
        }
    }
}

@Composable
private fun TimeBox(label: String, time: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(BgField)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.AccessTime, null,
                    tint = RedPrimary, modifier = Modifier.size(14.dp))
                Text(label, fontSize = 10.sp,
                    color = TextGray, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
            Text(time, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                Text("ยืนยัน", color = RedPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = TextGray) }
        },
        text = { TimePicker(state = state) }
    )
}

@Composable
private fun LoadingField() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(BgField),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                color = RedPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("กำลังโหลด...", color = TextGray, fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CreateAppointmentScreenPreview() {
    SalesTrackingTheme {
        CreateAppointmentContent(
            activityId = null,
            state = CreateAppointmentUiState(
                projectOptions = listOf(
                    ProjectOption("1", "Project Alpha", "Active"),
                    ProjectOption("2", "Project Beta", "Pending")
                ),
                contactOptions = listOf(
                    ContactOption("c1", "John Doe"),
                    ContactOption("c2", "Jane Smith")
                ),
                masterOptions = listOf(
                    ActivityMaster(1, "Requirement Gathering"),
                    ActivityMaster(2, "Product Presentation"),
                    ActivityMaster(3, "Contract Signing")
                ),
                selectedProjectId = "1",
                selectedProjectName = "Project Alpha",
                plannedDate = "Oct 25, 2023",
                startTime = "10:00 AM",
                endTime = "11:30 AM"
            ),
            onEvent = {},
            onBack = {}
        )
    }

}
