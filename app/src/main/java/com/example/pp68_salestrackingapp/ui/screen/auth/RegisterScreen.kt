package com.example.pp68_salestrackingapp.ui.screen.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.ui.components.DropdownField
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.auth.RegisterUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.auth.RegisterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()
    RegisterScreenContent(
        s = s,
        onBack = onBack,
        onRegisterSuccess = onRegisterSuccess,
        onFullNameChange = viewModel::onFullNameChange,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onBranchSelected = viewModel::onBranchSelected,
        register = viewModel::register
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterScreenContent(
    s: RegisterUiState,
    onBack: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onFullNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBranchSelected: (Int) -> Unit,
    register: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    LaunchedEffect(s.isSuccess) {
        if (s.isSuccess) onRegisterSuccess()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ลงทะเบียนพนักงานใหม่", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = BrandRed)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = BgWhite)
            )
        },
        containerColor = BgWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Logo Section ──────────────────────────────────
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(BrandRed, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("S", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Text("สร้างบัญชีผู้ใช้งานใหม่", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextBlack)
            Spacer(Modifier.height(8.dp))

            // ── Full Name ────────────────────────────────────
            RegisterTextField(
                value = s.fullName,
                onValueChange = onFullNameChange,
                label = "ชื่อ-นามสกุล",
                icon = Icons.Default.Person,
                imeAction = ImeAction.Next,
                onAction = { focusManager.moveFocus(FocusDirection.Down) }
            )

            // ── Email ────────────────────────────────────────
            RegisterTextField(
                value = s.email,
                onValueChange = onEmailChange,
                label = "อีเมลพนักงาน",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
                onAction = { focusManager.moveFocus(FocusDirection.Down) }
            )

            // ── Password ─────────────────────────────────────
            var showPassword by remember { mutableStateOf(false) }
            OutlinedTextField(
                value = s.password,
                onValueChange = onPasswordChange,
                label = { Text("รหัสผ่าน", color = TextGray) },
                leadingIcon = { Icon(Icons.Default.Lock, null, tint = BrandRed) },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null, tint = TextGray)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                colors = registerFieldColors(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
            )

            // ── Branch Selection ──────────────────────────────
            Text(
                "เลือกสาขาที่สังกัด",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TextGray
            )
            
            val branchOptions = s.branches.map { it.branchName }
            DropdownField(
                value = s.selectedBranchName,
                placeholder = "เลือกสาขา",
                options = branchOptions,
                onSelect = { onBranchSelected(it) }
            )

            // ── Error Message ────────────────────────────────
            AnimatedVisibility(visible = s.error != null) {
                Text(s.error ?: "", color = ErrorRed, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(16.dp))

            // ── Register Button ──────────────────────────────
            Button(
                onClick = register,
                enabled = !s.isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
            ) {
                if (s.isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("ลงทะเบียนพนักงาน", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun RegisterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default,
    onAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextGray) },
        leadingIcon = { Icon(icon, null, tint = BrandRed) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = registerFieldColors(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onAny = { onAction() })
    )
}

@Composable
private fun registerFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = BrandRed,
    unfocusedBorderColor = BorderGray,
    focusedTextColor = TextBlack,
    unfocusedTextColor = TextBlack,
    cursorColor = BrandRed,
    focusedLabelColor = BrandRed,
    unfocusedLabelColor = TextGray,
    focusedContainerColor = Color.White,
    unfocusedContainerColor = BgGray
)

@Preview(showBackground = true)
@Composable
fun RegisterScreenPreview() {
    val sampleState = RegisterUiState(
        branches = listOf(
            Branch("1", "สาขาสำนักงานใหญ่", "Bangkok"),
            Branch("2", "สาขาเชียงใหม่", "North"),
            Branch("3", "สาขาขอนแก่น", "Northeast")
        )
    )
    SalesTrackingTheme {
        RegisterScreenContent(
            s = sampleState,
            onBack = {},
            onRegisterSuccess = {},
            onFullNameChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onBranchSelected = {},
            register = {}
        )
    }
}
