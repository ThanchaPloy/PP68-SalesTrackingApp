package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FormField(
    label: String,
    required: Boolean = false,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.TextPrimary)
            if (required)
                Text(" *", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Error)
        }
        content()
    }
}

@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    errorMsg: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    readOnly: Boolean = false
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = AppColors.TextHint, fontSize = 14.sp) },
            leadingIcon = leadingIcon?.let {
                { Icon(it, null, tint = AppColors.TextHint, modifier = Modifier.size(18.dp)) }
            },
            trailingIcon = trailingIcon,
            isError = isError,
            singleLine = singleLine,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = AppColors.Border,
                focusedBorderColor = AppColors.Primary,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                unfocusedTextColor = AppColors.TextPrimary,
                focusedTextColor = AppColors.TextPrimary,
                cursorColor = AppColors.Primary,
                errorBorderColor = AppColors.Error
            )
        )
        if (isError && errorMsg != null) {
            Text(
                errorMsg, color = AppColors.Error, fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun DropdownField(
    value: String,
    placeholder: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMsg: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .border(
                    1.dp,
                    if (isError) AppColors.Error else AppColors.Border,
                    RoundedCornerShape(10.dp)
                )
                .background(if (enabled) Color.White else Color(0xFFF5F5F5))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    color = if (!enabled || value.isBlank()) AppColors.TextHint else AppColors.TextPrimary,
                    fontSize = 14.sp
                )
                Icon(
                    Icons.Default.KeyboardArrowDown, null,
                    tint = AppColors.TextHint, modifier = Modifier.size(20.dp)
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color.White)
        ) {
            options.forEachIndexed { idx, option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp, color = AppColors.TextPrimary) },
                    onClick = {
                        onSelect(idx)
                        expanded = false
                    }
                )
            }
        }
        if (isError && errorMsg != null) {
            Text(
                errorMsg, color = AppColors.Error, fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdownField(
    value: String,
    placeholder: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit = {},
    enabled: Boolean = true
) {
    var query by remember(value) { mutableStateOf(value) }
    var expanded by remember { mutableStateOf(false) }

    val filtered = remember(query, options) {
        val q = query.trim()
        // ponytail: was take(80) on the unfiltered list — Thai text sorts after every
        // Latin/digit string (higher Unicode code points), so any list with 80+ Latin-named
        // entries (e.g. brand codes like "3DI", "555") hid every Thai-named option until
        // the user typed something. 300 comfortably covers categorical lists (brands,
        // groups, units); genuinely huge lists (customers) still get bounded.
        if (q.isBlank()) options.take(300)
        else options.filter { it.contains(q, ignoreCase = true) }.take(300)
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; expanded = true },
            placeholder = { Text(placeholder, color = AppColors.TextHint, fontSize = 14.sp) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = ""; onClear(); expanded = false }) {
                        Icon(Icons.Default.Close, null, tint = AppColors.TextHint, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Icon(Icons.Default.KeyboardArrowDown, null, tint = AppColors.TextHint, modifier = Modifier.size(20.dp))
                }
            },
            singleLine = true,
            enabled = enabled,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = AppColors.Border,
                focusedBorderColor = AppColors.Primary,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                unfocusedTextColor = AppColors.TextPrimary,
                focusedTextColor = AppColors.TextPrimary,
                cursorColor = AppColors.Primary,
                disabledContainerColor = Color(0xFFF5F5F5),
                disabledTextColor = AppColors.TextPrimary
            )
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 300.dp)
                .background(Color.White)
        ) {
            filtered.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, fontSize = 14.sp, color = AppColors.TextPrimary) },
                    onClick = { query = option; onSelect(option); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    selectedDate: String?,
    placeholder: String,
    onDateSelected: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, AppColors.Border, RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable { showPicker = true }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CalendarMonth, null,
                    tint = AppColors.TextHint, modifier = Modifier.size(18.dp)
                )
                val displayDate = remember(selectedDate) {
                    if (selectedDate.isNullOrBlank()) null
                    else try {
                        val localDate = java.time.LocalDate.parse(selectedDate.take(10))
                        val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("th", "TH"))
                        localDate.format(formatter)
                    } catch (e: Exception) { selectedDate }
                }
                Text(
                    text = displayDate ?: placeholder,
                    color = if (displayDate == null) AppColors.TextHint else AppColors.TextPrimary,
                    fontSize = 14.sp
                )
            }
            if (selectedDate != null) {
                IconButton(
                    onClick = { onDateSelected("") },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        Icons.Default.Close, null,
                        tint = AppColors.TextHint, modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showPicker) {
        val initialMillis = remember(selectedDate) {
            selectedDate?.let { s ->
                try {
                    val dateStr = if (s.length >= 10) s.take(10) else s
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                        .parse(dateStr)?.time
                } catch (_: Exception) { null }
            } ?: System.currentTimeMillis()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = SimpleDateFormat(
                            "yyyy-MM-dd", Locale.getDefault()
                        ).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }.format(Date(millis))
                        onDateSelected(date)
                    }
                    showPicker = false
                }) { Text("ยืนยัน", color = AppColors.Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text("ยกเลิก", color = AppColors.TextSecondary)
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = AppColors.Primary,
                    todayDateBorderColor = AppColors.Primary
                )
            )
        }
    }
}
