package com.example.pp68_salestrackingapp.ui.screen.contact

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.*
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.contact.AddContactViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.contact.AddContactUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.contact.AddContactEvent

// ─── Colors ───────────────────────────────────────────────────
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFCC1D1D)
private val BgLight    = Color(0xFFF5F5F5)
private val ErrorRed   = Color(0xFFE53935)
private val BorderGray = Color(0xFFE0E0E0)


// ═══════════════════════════════════════════════════════════════
// SCREEN ENTRY POINT
// ═══════════════════════════════════════════════════════════════
@Composable
fun AddContactScreen(
    contactId: String? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddContactViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(contactId) {
        if (contactId != null) {
            viewModel.onEvent(AddContactEvent.LoadContact(contactId))
        }
    }

    LaunchedEffect(uiState.isSaved) { if (uiState.isSaved) onSaved() }

    AddContactContent(
        uiState = uiState,
        onBack  = onBack,
        onSaved = onSaved,
        onEvent = viewModel::onEvent
    )
}

@Composable
fun AddContactContent(
    uiState: AddContactUiState,
    onBack:  () -> Unit,
    onSaved: () -> Unit,
    onEvent: (AddContactEvent) -> Unit
) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp, color = White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextDark)
                    }
                    Text(
                        if (uiState.contactId != null) "Edit Contact Person" else "Add Contact Person",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 18.sp,
                        color      = RedPrimary
                    )
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ── Select Company * ──────────────────────────────
            FormField("Select Company", required = true) {
                if (uiState.isLoadingCompanies) {
                    LoadingField()
                } else {
                    DropdownField(
                        value       = uiState.selectedCompanyName ?: "",
                        placeholder = "Choose customer company",
                        options     = uiState.companyOptions.map { it.second },
                        isError     = uiState.companyError != null,
                        errorMsg    = uiState.companyError,
                        onSelect    = { idx ->
                            onEvent(AddContactEvent.CompanySelected(
                                uiState.companyOptions[idx].first,
                                uiState.companyOptions[idx].second
                            ))
                        }
                    )
                }
            }

            // ── Select Project (กรองตาม company) ─────────────
            FormField("Select Project  (Optional)") {
                if (uiState.isLoadingProjects) {
                    LoadingField()
                } else {
                    DropdownField(
                        value       = uiState.selectedProjectName ?: "",
                        placeholder = if (uiState.selectedCompanyId == null)
                            "เลือก Company ก่อน" else "Choose project",
                        options     = uiState.projectOptions.map { it.second },
                        onSelect    = { idx ->
                            onEvent(AddContactEvent.ProjectSelected(
                                uiState.projectOptions[idx].first,
                                uiState.projectOptions[idx].second
                            ))
                        }
                    )
                }
            }

            // ── Full Name * ───────────────────────────────────
            FormField("Full Name", required = true) {
                FormTextField(
                    value         = uiState.fullName,
                    onValueChange = { onEvent(AddContactEvent.FullNameChanged(it)) },
                    placeholder   = "Enter full name",
                    leadingIcon   = Icons.Default.Person,
                    isError       = uiState.fullNameError != null,
                    errorMsg      = uiState.fullNameError
                )
            }

            // ── Nickname (Optional) ───────────────────────────
            FormField("Nickname  (Optional)") {
                FormTextField(
                    value         = uiState.nickname,
                    onValueChange = { onEvent(AddContactEvent.NicknameChanged(it)) },
                    placeholder   = "Enter nickname"
                )
            }

            // ── Position ──────────────────────────────────────
            FormField("Position") {
                FormTextField(
                    value         = uiState.position,
                    onValueChange = { onEvent(AddContactEvent.PositionChanged(it)) },
                    placeholder   = "e.g., Sales Manager, CEO",
                    leadingIcon   = Icons.Default.Work
                )
            }

            // ── Mobile Number ─────────────────────────────────
            FormField("Mobile Number") {
                FormTextField(
                    value         = uiState.phoneNum,
                    onValueChange = { onEvent(AddContactEvent.PhoneChanged(it)) },
                    placeholder   = "e.g., 06x-xxx-xxxx",
                    leadingIcon   = Icons.Default.Phone,
                    keyboardType  = KeyboardType.Phone
                )
            }

            // ── Email ─────────────────────────────────────────
            FormField("Email") {
                FormTextField(
                    value         = uiState.email,
                    onValueChange = { onEvent(AddContactEvent.EmailChanged(it)) },
                    placeholder   = "Enter Email",
                    leadingIcon   = Icons.Default.Email,
                    keyboardType  = KeyboardType.Email,
                    isError       = uiState.emailError != null,
                    errorMsg      = uiState.emailError
                )
            }

            // ── Line ID (Optional) ────────────────────────────
            FormField("Line ID  (Optional)") {
                FormTextField(
                    value         = uiState.lineId,
                    onValueChange = { onEvent(AddContactEvent.LineIdChanged(it)) },
                    placeholder   = "Enter Line ID",
                    leadingIcon   = Icons.Default.Chat
                )
            }

            // ── Is Decision Maker Checkbox ────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                    .clickable { onEvent(AddContactEvent.IsDecisionMakerToggled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked         = uiState.isDecisionMaker,
                    onCheckedChange = { onEvent(AddContactEvent.IsDecisionMakerToggled) },
                    colors = CheckboxDefaults.colors(
                        checkedColor   = RedPrimary,
                        uncheckedColor = TextGray
                    )
                )
                Column {
                    Text("Is Decision Maker?", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(
                        if (uiState.isDecisionMaker) "ผู้ติดต่อนี้เป็นผู้ตัดสินใจหลัก"
                        else "ผู้ติดต่อนี้ไม่ใช่ผู้ตัดสินใจหลัก",
                        fontSize = 12.sp, color = TextGray
                    )
                }
            }

            // ── Is Active Checkbox ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
                    .clickable { onEvent(AddContactEvent.IsActiveToggled) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Checkbox(
                    checked         = uiState.isActive,
                    onCheckedChange = { onEvent(AddContactEvent.IsActiveToggled) },
                    colors = CheckboxDefaults.colors(
                        checkedColor   = RedPrimary,
                        uncheckedColor = TextGray
                    )
                )
                Column {
                    Text("Is Active?", fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(
                        if (uiState.isActive) "ผู้ติดต่อนี้ยังใช้งานอยู่"
                        else "ผู้ติดต่อนี้ไม่ได้ใช้งานแล้ว",
                        fontSize = 12.sp, color = TextGray
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // ── Error ─────────────────────────────────────────
            uiState.saveError?.let {
                Text(it, color = ErrorRed, fontSize = 13.sp)
            }

            // ── Save Button ───────────────────────────────────
            Button(
                onClick  = { onEvent(AddContactEvent.Save) },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp
                    )
                } else {
                    Text(if (uiState.contactId != null) "Update Contact" else "Save Contact", fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold, color = White)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─── Loading placeholder ──────────────────────────────────────
@Composable
private fun LoadingField() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderGray, RoundedCornerShape(10.dp))
            .background(Color(0xFFF9F9F9)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                color = RedPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp
            )
            Text("กำลังโหลด...", color = TextGray, fontSize = 13.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AddContactScreenPreview() {
    SalesTrackingTheme {
        AddContactContent(
            uiState = AddContactUiState(
                fullName = "John Doe",
                companyOptions = listOf("1" to "Example Corp", "2" to "Mock Ltd"),
                selectedCompanyName = "Example Corp"
            ),
            onBack = {},
            onSaved = {},
            onEvent = {}
        )
    }
}
