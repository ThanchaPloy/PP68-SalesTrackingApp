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
import com.example.pp68_salestrackingapp.ui.components.SearchableDropdownField
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.project.AddProductUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.project.AddProductViewModel

@Composable
fun AddProductScreen(
    projectId: String,
    productId: String? = null,
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
        onGroupSelected = { viewModel.onGroupSelected(it) },
        onSubgroupSelected = { viewModel.onSubgroupSelected(it) },
        onNameSelected = { viewModel.onNameSelected(it) },
        onQuantityChange = { viewModel.onQuantityChange(it) },
        onDateSelected = { viewModel.onDateSelected(it) },
        onShippingBranchSelected = { id, name -> viewModel.onShippingBranchSelected(id, name) },
        onUnitSelected = { viewModel.onUnitSelected(it) },
        onSave = { viewModel.save() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductContent(
    s: AddProductUiState,
    onBack: () -> Unit,
    onBrandSelected: (String) -> Unit,
    onGroupSelected: (String) -> Unit,
    onSubgroupSelected: (String) -> Unit,
    onNameSelected: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onDateSelected: (String) -> Unit,
    onShippingBranchSelected: (String, String) -> Unit,
    onUnitSelected: (String) -> Unit,
    onSave: () -> Unit
) {
    val title = if (s.isEditMode) "Edit Product" else "Add Product"
    val buttonText = if (s.isEditMode) "แก้ไขข้อมูลสินค้า" else "บันทึกสินค้า"

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
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
            // Section Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Inventory, null, tint = AppColors.Primary, modifier = Modifier.size(18.dp))
                Text("ข้อมูลสินค้า", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AppColors.TextSecondary)
            }

            // Form Fields
            FormField(label = "แบรนด์สินค้า") {
                SearchableDropdownField(
                    value = s.selectedBrand,
                    placeholder = if (s.isLoading) "กำลังโหลด..." else "พิมพ์เพื่อค้นหาแบรนด์",
                    options = s.filteredBrands,
                    onSelect = { onBrandSelected(it) },
                    onClear  = { onBrandSelected("") },
                    enabled  = !s.isEditMode
                )
            }

            FormField(label = "กลุ่มสินค้า") {
                SearchableDropdownField(
                    value = s.selectedGroup,
                    placeholder = "พิมพ์เพื่อค้นหากลุ่มสินค้า",
                    options = s.filteredGroups,
                    onSelect = { onGroupSelected(it) },
                    onClear  = { onGroupSelected("") },
                    enabled  = !s.isEditMode
                )
            }

            FormField(label = "กลุ่มย่อยสินค้า") {
                SearchableDropdownField(
                    value = s.selectedSubgroup,
                    placeholder = "พิมพ์เพื่อค้นหากลุ่มย่อย",
                    options = s.filteredSubgroups,
                    onSelect = { onSubgroupSelected(it) },
                    onClear  = { onSubgroupSelected("") },
                    enabled  = !s.isEditMode
                )
            }

            FormField(label = "ชื่อสินค้า") {
                SearchableDropdownField(
                    value = s.selectedProductName,
                    placeholder = if (s.isLoading) "กำลังโหลด..." else "พิมพ์เพื่อค้นหาชื่อสินค้า",
                    options = s.filteredNames,
                    onSelect = { onNameSelected(it) },
                    onClear  = { onNameSelected("") },
                    enabled  = !s.isEditMode
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormField(label = "สี", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value = s.color ?: "-",
                        onValueChange = {},
                        placeholder = "สี",
                        readOnly = true
                    )
                }
                FormField(label = "ความหนา", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value = s.thickness ?: "-",
                        onValueChange = {},
                        placeholder = "หนา",
                        readOnly = true
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormField(label = "กว้าง", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value = s.width ?: "-",
                        onValueChange = {},
                        placeholder = "กว้าง",
                        readOnly = true
                    )
                }
                FormField(label = "ยาว", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value = s.length ?: "-",
                        onValueChange = {},
                        placeholder = "ยาว",
                        readOnly = true
                    )
                }
                FormField(label = "หน่วยวัด", modifier = Modifier.weight(1f)) {
                    FormTextField(
                        value = s.dimensionUnit ?: "-",
                        onValueChange = {},
                        placeholder = "หน่วย",
                        readOnly = true
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FormField(label = "จำนวน", modifier = Modifier.weight(0.6f)) {
                    FormTextField(
                        value = s.quantity,
                        onValueChange = onQuantityChange,
                        placeholder = "จำนวน",
                        keyboardType = KeyboardType.Number
                    )
                }
                FormField(label = "หน่วยนับ", modifier = Modifier.weight(0.4f)) {
                    SearchableDropdownField(
                        value = s.unit,
                        placeholder = "หน่วย",
                        options = s.allUnits,
                        onSelect = { onUnitSelected(it) }
                    )
                }
            }

            FormField(label = "วันที่ลูกค้าต้องการ") {
                DatePickerField(
                    selectedDate = s.wantedDate,
                    placeholder = "เลือกวันที่ส่งมอบ",
                    onDateSelected = onDateSelected
                )
            }

            FormField(label = "สาขาที่ออกของ") {
                DropdownField(
                    value = s.selectedShippingBranchName ?: "",
                    placeholder = if (s.isLoadingBranches) "กำลังโหลด..." else "เลือกสาขาที่ออกของ",
                    options = s.shippingBranchOptions.map { it.second },
                    onSelect = { idx ->
                        val item = s.shippingBranchOptions[idx]
                        onShippingBranchSelected(item.first, item.second)
                    }
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
                        Icon(if (s.isEditMode) Icons.Default.EditNote else Icons.Default.CheckCircle, null, modifier = Modifier.size(20.dp))
                        Text(buttonText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            if (!s.isEditMode) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ยกเลิก", color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }

            if (s.error != null) {
                Text(
                    s.error!!,
                    color = AppColors.Error,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label, 
            fontSize = 14.sp, 
            fontWeight = FontWeight.Bold, 
            color = if (enabled) AppColors.TextPrimary else AppColors.TextSecondary
        )
        content()
    }
}
