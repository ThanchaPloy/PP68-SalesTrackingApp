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
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

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

    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocationPermission) {
            fetchLocation(fusedLocationClient) { lat, lng ->
                onEvent(CreateAppointmentEvent.LocationPicked(lat, lng))
            }
        }
    }

    LaunchedEffect(state.activityType, activityId) {
        if (activityId == null && state.activityType == "onsite" && state.lat == null && state.lng == null) {
            if (hasLocationPermission) {
                fetchLocation(fusedLocationClient) { lat, lng ->
                    onEvent(CreateAppointmentEvent.LocationPicked(lat, lng))
                }
            } else {
                permissionLauncher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

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
                        Text(if (activityId == null) "บันทึก" else "บันทึกการเปลี่ยนแปลง", color = RedPrimary,
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

            FormField(label = "หัวข้อกิจกรรม", required = true) {
                FormTextField(
                    value         = state.titleTopic,
                    onValueChange = { onEvent(CreateAppointmentEvent.TitleChanged(it)) },
                    placeholder   = "ระบุหัวข้อกิจกรรม",
                    leadingIcon   = Icons.AutoMirrored.Filled.Label
                )
            }

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

            FormField(label = "บริษัท") {
                if (state.selectedProjectId != null) {
                    FormTextField(
                        value         = state.selectedCompanyName ?: "",
                        onValueChange = {},
                        placeholder   = "กำลังโหลด...",
                        leadingIcon   = Icons.Default.Business,
                        readOnly      = true
                    )
                } else if (state.isLoadingCompanies) {
                    CircularProgressIndicator(color = RedPrimary, modifier = Modifier.size(24.dp))
                } else {
                    SearchableDropdownField(
                        value       = state.selectedCompanyName ?: "",
                        placeholder = "ค้นหาชื่อบริษัท...",
                        options     = state.companyOptions.map { it.second },
                        onSelect    = { name ->
                            state.companyOptions.firstOrNull { it.second == name }?.let { (id, n) ->
                                onEvent(CreateAppointmentEvent.CompanySelected(id, n))
                            }
                        },
                        onClear     = { onEvent(CreateAppointmentEvent.CompanySelected("", "")) }
                    )
                }
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
                        
                        // ✅ ไม่โชว์รายชื่อทั้งหมดจากฐานข้อมูล ต้องพิมพ์ค้นหาก่อนถึงจะขึ้น (ยกเว้นชื่อที่เลือกไว้แล้ว)
                        val filteredContacts = if (state.contactSearchQuery.isBlank()) {
                            state.allContactOptions.filter { it.id in state.selectedContactIds }
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
                        } else if (state.contactSearchQuery.isNotBlank()) {
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

            FormField(label = "กำหนดการ", required = true) {
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
                                val displayStart = state.startTime?.take(5) ?: "เริ่ม"
                                Text(displayStart, fontSize = 14.sp, color = if (state.startTime == null) TextGray else TextDark)
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
                                val displayEnd = state.endTime?.take(5) ?: "สิ้นสุด"
                                Text(displayEnd, fontSize = 14.sp, color = if (state.endTime == null) TextGray else TextDark)
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

            if (state.activityType == "onsite") {
                FormField(label = "สถานที่") {
                    GoogleMapPickerField(
                        lat = state.lat,
                        lng = state.lng,
                        onLocationPicked = { lat, lng ->
                            onEvent(CreateAppointmentEvent.LocationPicked(lat, lng))
                        },
                        onResetToCurrentLocation = {
                            if (hasLocationPermission) {
                                fetchLocation(fusedLocationClient) { lat, lng ->
                                    onEvent(CreateAppointmentEvent.LocationPicked(lat, lng))
                                }
                            } else {
                                permissionLauncher.launch(arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                ))
                            }
                        },
                        onClearLocation = {
                            onEvent(CreateAppointmentEvent.LocationPicked(null, null))
                        }
                    )
                }
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
                    Text(if (activityId == null) "สร้างการนัดหมาย" else "บันทึกการเปลี่ยนแปลงแผนงาน", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = White)
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (state.showStartTimePicker) {
        val (h, m) = parseTimeStringToHourMinute(state.startTime)
        TimePickerDialogWrapper(
            initialHour = h,
            initialMinute = m,
            onConfirm = { hour, minute ->
                onEvent(CreateAppointmentEvent.StartTimeSelected(
                    "%02d:%02d:00".format(hour, minute)
                ))
            },
            onDismiss = { onEvent(CreateAppointmentEvent.DismissTimePicker) }
        )
    }

    if (state.showEndTimePicker) {
        val (h, m) = parseTimeStringToHourMinute(state.endTime)
        TimePickerDialogWrapper(
            initialHour = h,
            initialMinute = m,
            onConfirm = { hour, minute ->
                onEvent(CreateAppointmentEvent.EndTimeSelected(
                    "%02d:%02d:00".format(hour, minute)
                ))
            },
            onDismiss = { onEvent(CreateAppointmentEvent.DismissTimePicker) }
        )
    }
}

@SuppressLint("MissingPermission")
private fun fetchLocation(
    fusedLocationClient: com.google.android.gms.location.FusedLocationProviderClient,
    onResult: (Double, Double) -> Unit
) {
    fusedLocationClient.getCurrentLocation(
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
        com.google.android.gms.tasks.CancellationTokenSource().token
    ).addOnSuccessListener { location ->
        location?.let {
            onResult(it.latitude, it.longitude)
        }
    }
}

fun parseTimeStringToHourMinute(timeStr: String?): Pair<Int, Int> {
    if (timeStr.isNullOrBlank()) return Pair(12, 0)
    return try {
        if (timeStr.length >= 5 && timeStr[2] == ':') {
            val h = timeStr.substring(0, 2).toIntOrNull() ?: 12
            val m = timeStr.substring(3, 5).toIntOrNull() ?: 0
            if (timeStr.contains("PM", ignoreCase = true) && h < 12) {
                Pair(h + 12, m)
            } else if (timeStr.contains("AM", ignoreCase = true) && h == 12) {
                Pair(0, m)
            } else {
                Pair(h, m)
            }
        } else {
            Pair(12, 0)
        }
    } catch (e: Exception) {
        Pair(12, 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogWrapper(
    initialHour: Int = 12,
    initialMinute: Int = 0,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
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
