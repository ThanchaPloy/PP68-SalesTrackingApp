package com.example.pp68_salestrackingapp.ui.screen.customer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.pp68_salestrackingapp.ui.components.*
import com.example.pp68_salestrackingapp.ui.theme.AppColors
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.components.GoogleMapPickerField
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.AddCustomerEvent
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.AddCustomerUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.AddCustomerViewModel

// ─── Constants ────────────────────────────────────────────────
private val CUSTOMER_TYPES = listOf(
    "Owner", "Developer", "Main Constructor", "Sub Constructor", "Installer"
)
private val CUSTOMER_STATUSES = listOf(
    "new lead" to "New Lead",
    "customer" to "Customer",
    "inactive" to "Inactive"
)
private const val DEFAULT_LAT = 13.7563
private const val DEFAULT_LNG = 100.5018  // Bangkok

// ═══════════════════════════════════════════════════════════════
// SCREEN ENTRY POINT
// ═══════════════════════════════════════════════════════════════
@Composable
fun AddCustomerScreen(
    custId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddCustomerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(custId) {
        if (custId != null) {
            viewModel.onEvent(AddCustomerEvent.LoadCustomer(custId))
        }
    }

    AddCustomerContent(
        uiState = uiState,
        onBack  = onBack,
        onSaved = onSaved,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun AddCustomerContent(
    uiState: AddCustomerUiState,
    onBack:  () -> Unit,
    onSaved: () -> Unit,
    onEvent: (AddCustomerEvent) -> Unit
) {
    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onSaved() }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp, color = AppColors.BgWhite) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = AppColors.TextPrimary)
                    }
                    Text(
                        if (uiState.custId != null) "Edit Customer" else "Add New Customer",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = AppColors.Primary
                    )
                }
            }
        },
        containerColor = AppColors.BgWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Company Name * ────────────────────────────────
            FormField("Company Name", required = true) {
                FormTextField(
                    value         = uiState.companyName,
                    onValueChange = { onEvent(AddCustomerEvent.CompanyNameChanged(it)) },
                    placeholder   = "Enter company's full name",
                    isError       = uiState.companyNameError != null,
                    errorMsg      = uiState.companyNameError,
                    leadingIcon   = Icons.Default.Business
                )
            }

            // ── Branch ────────────────────────────────────────
            FormField("Branch") {
                FormTextField(
                    value         = uiState.branch,
                    onValueChange = { onEvent(AddCustomerEvent.BranchChanged(it)) },
                    placeholder   = "e.g., Head Office, North Branch",
                    leadingIcon   = Icons.Default.AccountTree
                )
            }

            // ── Address ───────────────────────────────────────
            FormField("Address") {
                FormTextField(
                    value         = uiState.address,
                    onValueChange = { onEvent(AddCustomerEvent.AddressChanged(it)) },
                    placeholder   = "Enter address",
                    trailingIcon  = {
                        IconButton(onClick = { onEvent(AddCustomerEvent.UseCurrentLocation) }) {
                            Icon(Icons.Default.LocationOn, null, tint = AppColors.Primary)
                        }
                    }
                )
            }

            // ── Google Maps Picker ───────────────────────────
            GoogleMapPickerField(
                lat = uiState.selectedLat ?: DEFAULT_LAT,
                lng = uiState.selectedLng ?: DEFAULT_LNG,
                onLocationPicked = { pickedLat, pickedLng -> // ระบุชื่อตัวแปรที่รับมา
                    onEvent(AddCustomerEvent.LocationPicked(pickedLat, pickedLng))
                }            )

            // ── Select Project (Optional) ─────────────────────
            FormField("Select Project  (Optional)") {
                DropdownField(
                    value       = uiState.selectedProjectName ?: "",
                    placeholder = "Choose project",
                    options     = uiState.projectOptions.map { it.second },
                    onSelect    = { idx ->
                        onEvent(AddCustomerEvent.ProjectSelected(
                            uiState.projectOptions[idx].first,
                            uiState.projectOptions[idx].second
                        ))
                    }
                )
            }

            // ── Customer Type * ───────────────────────────────
            FormField("Customer Type", required = true) {
                DropdownField(
                    value       = uiState.custType ?: "",
                    placeholder = "Select customer type",
                    options     = CUSTOMER_TYPES,
                    isError     = uiState.custTypeError != null,
                    errorMsg    = uiState.custTypeError,
                    onSelect    = { idx ->
                        onEvent(AddCustomerEvent.CustTypeChanged(CUSTOMER_TYPES[idx]))
                    }
                )
            }

            // ── Customer Status ───────────────────────────────
            FormField("Customer Status") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CUSTOMER_STATUSES.forEach { (value, label) ->
                        val isSelected = uiState.companyStatus == value
                        FilterChip(
                            selected = isSelected,
                            onClick = { onEvent(AddCustomerEvent.StatusChanged(value)) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when(value) {
                                    "new lead" -> Color(0xFFE3F2FD)
                                    "customer" -> Color(0xFFE8F5E9)
                                    else -> Color(0xFFF5F5F5)
                                },
                                selectedLabelColor = when(value) {
                                    "new lead" -> Color(0xFF1976D2)
                                    "customer" -> Color(0xFF388E3C)
                                    else -> Color(0xFF616161)
                                }
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = AppColors.Border,
                                selectedBorderColor = when(value) {
                                    "new lead" -> Color(0xFF1976D2)
                                    "customer" -> Color(0xFF388E3C)
                                    else -> Color(0xFF616161)
                                }
                            )
                        )
                    }
                }
            }

            // ── First Customer Date (Optional) ───────────────────
            FormField("วันที่เริ่มเป็นลูกค้า  (Optional)") {
                DatePickerField(
                    selectedDate  = uiState.firstCustomerDate,
                    placeholder   = "Pick a date",
                    onDateSelected = { onEvent(AddCustomerEvent.FirstCustomerDateChanged(it)) }
                )
            }

            Spacer(Modifier.height(4.dp))

            // ── Error ─────────────────────────────────────────
            uiState.saveError?.let {
                Text(it, color = AppColors.Error, fontSize = 13.sp)
            }

            // ── Save Button ───────────────────────────────────
            Button(
                onClick  = { onEvent(AddCustomerEvent.Save) },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                } else {
                    Text(if (uiState.custId != null) "Update Customer" else "Save Customer", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}


@Preview(showBackground = true)
@Composable
fun AddCustomerScreenPreview() {
    val mockState = AddCustomerUiState(
        companyName = "Example Company",
        branch = "Main Branch",
        address = "123 Example St, Bangkok",
        custType = "Owner",
        projectOptions = listOf("1" to "Project A", "2" to "Project B")
    )
    SalesTrackingTheme {
        AddCustomerContent(
            uiState = mockState,
            onBack = {},
            onSaved = {},
            onEvent = {}
        )
    }
}