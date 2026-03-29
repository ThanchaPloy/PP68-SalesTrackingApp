package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ChangePasswordViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.EditProfileViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.NotificationSettingsViewModel

private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFAE2138)
private val BgLight    = Color(0xFFF5F5F5)

// ── Edit Profile Screen ───────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    // ✅ navigate กลับเมื่อ save สำเร็จ
    LaunchedEffect(s.isSaved) {
        if (s.isSaved) onSave()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        },
        containerColor = White
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar
            Box(Modifier.size(100.dp)) {
                Surface(
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    color    = BgLight
                ) {
                    Icon(
                        Icons.Default.Person, null,
                        tint     = RedPrimary,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ✅ Full Name — แก้ได้
            OutlinedTextField(
                value         = s.fullName,
                onValueChange = { viewModel.onFullNameChange(it) },
                label         = { Text("Full Name") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            // ✅ Phone — แก้ได้
            OutlinedTextField(
                value         = s.phoneNumber,
                onValueChange = { viewModel.onPhoneChange(it) },
                label         = { Text("Phone Number") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            // ✅ Branch — read only
            OutlinedTextField(
                value         = s.branchName,
                onValueChange = {},
                label         = { Text("Branch") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                enabled       = false,
                colors        = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor    = Color.LightGray,
                    disabledContainerColor = Color(0xFFF0F0F0)
                )
            )
            Spacer(Modifier.height(16.dp))

            // ✅ Email — read only
            OutlinedTextField(
                value         = s.email,
                onValueChange = {},
                label         = { Text("Email") },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(12.dp),
                enabled       = false,
                colors        = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor    = Color.LightGray,
                    disabledContainerColor = Color(0xFFF0F0F0)
                )
            )

            if (s.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(s.error!!, color = Color.Red, fontSize = 13.sp)
            }

            Spacer(Modifier.height(40.dp))

            Button(
                onClick  = { viewModel.save() },
                enabled  = !s.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (s.isLoading) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Save Changes", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
// ── Notification Settings Screen ──────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Notification Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        },
        containerColor = BgLight
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "GENERAL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextGray,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            Surface(color = White) {
                Column {
                    SettingToggleItem(
                        label     = "Push Notifications",
                        isEnabled = s.pushEnabled,
                        onToggle  = { viewModel.onPushEnabledChange(it) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Color(0xFFEEEEEE))
                }
            }

            Text(
                "ALERTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextGray,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            Surface(color = White) {
                Column {
                    SettingToggleItem(
                        label     = "Visit Reminders",
                        isEnabled = s.visitReminder,
                        onToggle  = { viewModel.onVisitReminderChange(it) }
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}

@Composable
private fun SettingToggleItem(label: String, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, color = TextDark)
        Switch(
            checked = isEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = RedPrimary)
        )
    }
}

// ── Change Password Screen ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangePasswordScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(s.isSuccess) {
        if (s.isSuccess) onSave()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Change Password", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "รหัสผ่านใหม่ต้องแตกต่างจากรหัสผ่านเดิม",
                color    = TextGray,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value         = s.oldPassword,
                onValueChange = { viewModel.onOldPasswordChange(it) },
                label         = { Text("รหัสผ่านเดิม") },
                modifier      = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                shape         = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value         = s.newPassword,
                onValueChange = { viewModel.onNewPasswordChange(it) },
                label         = { Text("รหัสผ่านใหม่") },
                modifier      = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                shape         = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value         = s.confirmPassword,
                onValueChange = { viewModel.onConfirmPasswordChange(it) },
                label         = { Text("ยืนยันรหัสผ่านใหม่") },
                modifier      = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                shape         = RoundedCornerShape(12.dp)
            )

            if (s.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(s.error!!, color = Color.Red, fontSize = 13.sp)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick  = { viewModel.save() },
                enabled  = !s.isLoading,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                if (s.isLoading) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        modifier    = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Update Password", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Generic Detail Screen (For Privacy, About, Help) ──────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingDetailScreen(title: String, content: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Text(
                text = content,
                lineHeight = 24.sp,
                fontSize = 15.sp,
                color = TextDark
            )
        }
    }
}

// ── Language Selection Dialog ─────────────────────────────────
@Composable
fun LanguageDialog(onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Language") },
        text = {
            Column {
                val languages = listOf("English", "ไทย (Thai)", "日本語 (Japanese)")
                languages.forEach { lang ->
                    TextButton(
                        onClick = { onSelect(lang) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(lang, color = TextDark)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun EditProfileScreenPreview() {
    SalesTrackingTheme {
        EditProfileScreen(onBack = {}, onSave = {})
    }
}

@Preview(showBackground = true)
@Composable
fun NotificationSettingsScreenPreview() {
    SalesTrackingTheme {
        NotificationSettingsScreen(onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
fun ChangePasswordScreenPreview() {
    SalesTrackingTheme {
        ChangePasswordScreen(onBack = {}, onSave = {})
    }
}
