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
                .background(Color.White)
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    color = if (value.isBlank()) AppColors.TextHint else AppColors.TextPrimary,
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
                Text(
                    text = selectedDate ?: placeholder,
                    color = if (selectedDate == null) AppColors.TextHint else AppColors.TextPrimary,
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
        val datePickerState = rememberDatePickerState()
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
