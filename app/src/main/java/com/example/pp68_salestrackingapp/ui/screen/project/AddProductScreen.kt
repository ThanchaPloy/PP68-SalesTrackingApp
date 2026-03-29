package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import com.example.pp68_salestrackingapp.ui.components.DatePickerField
import com.example.pp68_salestrackingapp.ui.components.DropdownField
import com.example.pp68_salestrackingapp.ui.components.FormTextField
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

@Composable
fun AddProductScreen(
    projectId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddProductViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(s.isSaved) {
        if (s.isSaved) onSaved()
    }

    AddProductContent(
        s = s,
        onBack = onBack,
        onBrandSelected = { viewModel.onBrandSelected(it) },
        onNameSelected = { viewModel.onNameSelected(it) },
        onQuantityChange = { viewModel.onQuantityChange(it) },
        onDateSelected = { viewModel.onDateSelected(it) },
        onSave = { viewModel.save() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductContent(
    s: AddProductUiState,
    onBack: () -> Unit,
    onBrandSelected: (String) -> Unit,
    onNameSelected: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onSave: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Add Product", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Inventory, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "Close", tint = AppColors.TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Inventory, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                Text("PRODUCT INFO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
            }

            // Form Fields
            val brands = s.products.map { it.brand }.distinct().filter { it.isNotBlank() }
            FormField(label = "Product Brand") {
                DropdownField(
                    value = s.selectedBrand,
                    placeholder = if (s.isLoading) "Loading..." else "เลือกแบรนด์สินค้า",
                    options = brands,
                    onSelect = { onBrandSelected(brands[it]) }
                )
            }

            FormField(label = "Product Name") {
                DropdownField(
                    value = s.selectedProductName,
                    placeholder = if (s.selectedBrand.isBlank()) "กรุณาเลือกแบรนด์ก่อน" else "เลือกชื่อสินค้า",
                    options = s.filteredNames,
                    onSelect = { onNameSelected(s.filteredNames[it]) }
                )
            }

            FormField(label = "Product Group") {
                FormTextField(
                    value = s.selectedGroup,
                    onValueChange = {},
                    placeholder = "กลุ่มของสินค้า",
                    readOnly = true
                )
            }

            FormField(label = "Product Subgroup") {
                FormTextField(
                    value = s.selectedSubgroup,
                    onValueChange = {},
                    placeholder = "ประเภทย่อยของสินค้า",
                    readOnly = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormField(label = "QTY", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value = s.quantity,
                        onValueChange = onQuantityChange,
                        placeholder = "จำนวนสินค้า",
                        keyboardType = KeyboardType.Number
                    )
                }
                FormField(label = "Unit", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value = s.unit,
                        onValueChange = {},
                        placeholder = "หน่วย",
                        readOnly = true
                    )
                }
            }

            FormField(label = "Customer Wanted Date") {
                DatePickerField(
                    selectedDate = s.wantedDate,
                    placeholder = "Select Delivery Date",
                    onDateSelected = onDateSelected
                )
            }

            Spacer(Modifier.height(20.dp))

            // Action Buttons
            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                enabled = !s.isSaving && s.selectedProductName.isNotBlank() && s.quantity.isNotBlank()
            ) {
                if (s.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                        Text("Save Product", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            TextButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel", color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
            }

            if (s.error != null) {
                Text(
                    s.error!!,
                    color = AppColors.Error,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        content()
    }
}
