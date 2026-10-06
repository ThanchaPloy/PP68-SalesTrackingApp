package com.example.pp68_salestrackingapp.ui.screen.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.auth.InitialAccountSetupViewModel

@Composable
fun InitialAccountSetupScreen(
    onCompleted: () -> Unit,
    onLogout: () -> Unit,
    viewModel: InitialAccountSetupViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    BackHandler(enabled = true) { }

    LaunchedEffect(state.isCompleted) {
        if (state.isCompleted) onCompleted()
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            shape = RoundedCornerShape(20.dp),
            // พื้นขาวล้วน ไม่ใช้ tonalElevation เพราะ Material จะผสมสีหลักของแอป (แดง)
            // ลงบนพื้นผิวตามระดับความสูง ทำให้การ์ดออกชมพู
            color = Color.White,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 680.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("ตั้งค่าบัญชีก่อนใช้งาน", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("กรุณาเปลี่ยนรหัสผ่านเริ่มต้นของบัญชี", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(20.dp))

                SetupPasswordField("รหัสผ่านปัจจุบัน", state.currentPassword, viewModel::onCurrentPasswordChange)
                Spacer(Modifier.height(12.dp))
                SetupPasswordField("รหัสผ่านใหม่ (อย่างน้อย 8 ตัวอักษร)", state.newPassword, viewModel::onNewPasswordChange)
                Spacer(Modifier.height(12.dp))
                SetupPasswordField("ยืนยันรหัสผ่านใหม่", state.confirmPassword, viewModel::onConfirmPasswordChange)

                if (state.phoneRequired) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "กรุณากรอกเบอร์มือถือสำหรับใช้ระบบนี้",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "บริษัทจะเก็บและใช้เบอร์มือถือของท่านเพื่อการดำเนินงานภายใน Sales Tracking App",
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.phoneNumber,
                        onValueChange = viewModel::onPhoneNumberChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("เบอร์มือถือ") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        enabled = !state.isLoading
                    )
                }

                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else Text("บันทึกและเข้าใช้งาน")
                }
                TextButton(
                    onClick = { viewModel.logout(); onLogout() },
                    enabled = !state.isLoading
                ) { Text("ออกจากระบบ") }
            }
        }
    }
}

@Composable
private fun SetupPasswordField(label: String, value: String, onValueChange: (String) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (visible) "ซ่อนรหัสผ่าน" else "แสดงรหัสผ่าน"
                )
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        singleLine = true
    )
}
