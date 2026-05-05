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
import com.example.pp68_salestrackingapp.data.repository.ProductSimpleDto
import com.example.pp68_salestrackingapp.ui.components.DatePickerField
import com.example.pp68_salestrackingapp.ui.components.DropdownField
import com.example.pp68_salestrackingapp.ui.components.FormTextField
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.project.AddProductUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.project.AddProductViewModel

@Composable
fun AddProductScreen(
    projectId: String,
    onBack:    () -> Unit,
    onSaved:   () -> Unit,
    viewModel: AddProductViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(s.isSaved) {
        if (s.isSaved) onSaved()
    }

    AddProductContent(
        s                        = s,
        onBack                   = onBack,
        onBrandSelected          = { viewModel.onBrandSelected(it) },
        onNameSelected           = { viewModel.onNameSelected(it) },
        onQuantityChange         = { viewModel.onQuantityChange(it) },
        onDateSelected           = { viewModel.onDateSelected(it) },
        onShippingBranchSelected = { id, name -> viewModel.onShippingBranchSelected(id, name) },
        onSave                   = { viewModel.save() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductContent(
    s:                        AddProductUiState,
    onBack:                   () -> Unit,
    onBrandSelected:          (String) -> Unit,
    onNameSelected:           (String) -> Unit,
    onQuantityChange:         (String) -> Unit,
    onDateSelected:           (String) -> Unit,
    onShippingBranchSelected: (String, String) -> Unit,
    onSave:                   () -> Unit
) {
    val titleText = if (s.isEditMode) "แก้ไขสินค้า" else "เพิ่มสินค้า"
    val saveText  = if (s.isEditMode) "บันทึกการแก้ไข" else "บันทึกสินค้า"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(titleText, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (s.isEditMode) Icons.Default.Edit else Icons.Default.Inventory,
                            null, tint = Color.White, modifier = Modifier.size(20.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Close, "ปิด", tint = AppColors.TextSecondary)
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
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Inventory, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                Text("ข้อมูลสินค้า", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
            }

            val brands = s.products.map { it.brand }.distinct().filter { it.isNotBlank() }

            // ── Edit mode: แสดง brand/name เป็น readonly TextField แทน Dropdown ──
            if (s.isEditMode) {
                FormField("แบรนด์สินค้า") {
                    FormTextField(
                        value         = s.selectedBrand,
                        onValueChange = {},
                        placeholder   = "แบรนด์สินค้า",
                        readOnly      = true
                    )
                }
                FormField("ชื่อสินค้า") {
                    FormTextField(
                        value         = s.selectedProductName,
                        onValueChange = {},
                        placeholder   = "ชื่อสินค้า",
                        readOnly      = true
                    )
                }
            } else {
                FormField("แบรนด์สินค้า") {
                    DropdownField(
                        value       = s.selectedBrand,
                        placeholder = if (s.isLoading) "กำลังโหลด..." else "เลือกแบรนด์สินค้า",
                        options     = brands,
                        onSelect    = { onBrandSelected(brands[it]) }
                    )
                }
                FormField("ชื่อสินค้า") {
                    DropdownField(
                        value       = s.selectedProductName,
                        placeholder = if (s.selectedBrand.isBlank()) "กรุณาเลือกแบรนด์ก่อน" else "เลือกชื่อสินค้า",
                        options     = s.filteredNames,
                        onSelect    = { onNameSelected(s.filteredNames[it]) }
                    )
                }
            }

            FormField("กลุ่มสินค้า") {
                FormTextField(value = s.selectedGroup, onValueChange = {}, placeholder = "กลุ่มของสินค้า", readOnly = true)
            }
            FormField("กลุ่มย่อยสินค้า") {
                FormTextField(value = s.selectedSubgroup, onValueChange = {}, placeholder = "ประเภทย่อยของสินค้า", readOnly = true)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField("สี", modifier = Modifier.weight(1f)) {
                    FormTextField(value = s.color ?: "-", onValueChange = {}, placeholder = "สี", readOnly = true)
                }
                FormField("ความหนา", modifier = Modifier.weight(1f)) {
                    FormTextField(value = s.thickness ?: "-", onValueChange = {}, placeholder = "หนา", readOnly = true)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField("กว้าง", modifier = Modifier.weight(1f)) {
                    FormTextField(value = s.width ?: "-", onValueChange = {}, placeholder = "กว้าง", readOnly = true)
                }
                FormField("ยาว", modifier = Modifier.weight(1f)) {
                    FormTextField(value = s.length ?: "-", onValueChange = {}, placeholder = "ยาว", readOnly = true)
                }
                FormField("หน่วยวัด", modifier = Modifier.weight(1f)) {
                    FormTextField(value = s.dimensionUnit ?: "-", onValueChange = {}, placeholder = "หน่วย", readOnly = true)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FormField("จำนวน", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value         = s.quantity,
                        onValueChange = onQuantityChange,
                        placeholder   = "จำนวนสินค้า",
                        keyboardType  = KeyboardType.Number
                    )
                }
                FormField("หน่วยนับ", modifier = Modifier.weight(1f)) {
                    FormTextField(value = s.unit, onValueChange = {}, placeholder = "หน่วย", readOnly = true)
                }
            }

            FormField("วันที่ลูกค้าต้องการ") {
                DatePickerField(
                    selectedDate   = s.wantedDate,
                    placeholder    = "เลือกวันที่ส่งมอบ",
                    onDateSelected = onDateSelected
                )
            }

            FormField("สาขาที่ออกของ") {
                DropdownField(
                    value       = s.selectedShippingBranchName ?: "",
                    placeholder = if (s.isLoadingBranches) "กำลังโหลด..." else "เลือกสาขาที่ออกของ",
                    options     = s.shippingBranchOptions.map { it.second },
                    onSelect    = { idx ->
                        val item = s.shippingBranchOptions[idx]
                        onShippingBranchSelected(item.first, item.second)
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick  = onSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                enabled  = !s.isSaving &&
                        s.selectedProductName.isNotBlank() &&
                        s.quantity.isNotBlank() &&
                        s.selectedShippingBranchId != null
            ) {
                if (s.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                        Text(saveText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("ยกเลิก", color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
            }

            if (s.error != null) {
                Text(s.error!!, color = AppColors.Error, fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun FormField(label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AppColors.TextPrimary)
        content()
    }
}

@Preview(showBackground = true)
@Composable
fun AddProductScreenPreview() {
    SalesTrackingTheme {
        AddProductContent(
            s = AddProductUiState(
                products = listOf(
                    ProductSimpleDto("1", "Product A", "Brand X", "Category 1", "Subcategory 1", "ชิ้น",
                        "Red", "10mm", "100cm", "200cm", "cm")
                ),
                selectedBrand = "Brand X", selectedProductName = "Product A",
                selectedGroup = "Category 1", selectedSubgroup = "Subcategory 1",
                color = "Red", thickness = "10mm", width = "100cm", length = "200cm", dimensionUnit = "cm",
                quantity = "5", unit = "ชิ้น", filteredNames = listOf("Product A"),
                shippingBranchOptions    = listOf("B1" to "Bangkok Branch"),
                selectedShippingBranchId = "B1", selectedShippingBranchName = "Bangkok Branch",
                isEditMode = true
            ),
            onBack = {}, onBrandSelected = {}, onNameSelected = {},
            onQuantityChange = {}, onDateSelected = {}, onShippingBranchSelected = { _, _ -> }, onSave = {}
        )
    }
}