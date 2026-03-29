package com.example.pp68_salestrackingapp.ui.screen.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.auth.LoginUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.auth.LoginViewModel

// ── Brand Colors ──────────────────────────────────────────────
val BrandRed        = Color(0xFFAD1F36)
val BrandRedDark    = Color(0xFF8B1528)
val BgWhite         = Color(0xFFFFFFFF)
val BgGray          = Color(0xFFF5F5F5)
val TextBlack       = Color(0xFF1A1A1A)
val TextGray        = Color(0xFF6B7280)
val BorderGray      = Color(0xFFE5E7EB)
val ErrorRed        = Color(0xFFDC2626)

@Composable
fun LoginScreen(
    onLoginSuccess: (AuthUser) -> Unit,
    onRegisterClick: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val uiState  by viewModel.uiState.collectAsState()
    val email    by viewModel.email.collectAsState()
    val password by viewModel.password.collectAsState()

    LoginScreenContent(
        uiState = uiState,
        email = email,
        password = password,
        onEmailChange = viewModel::onEmailChange,
        onPasswordChange = viewModel::onPasswordChange,
        onLoginClick = viewModel::login,
        onRegisterClick = onRegisterClick,
        onLoginSuccess = {
            onLoginSuccess(it)
            viewModel.resetState()
        }
    )
}

@Composable
fun LoginScreenContent(
    uiState: LoginUiState,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginSuccess: (AuthUser) -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.Success) {
            onLoginSuccess(uiState.user)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgWhite),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Logo / Title ───────────────────────────────────
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(BrandRed, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "S",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "Sales Tracking",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextBlack
            )
            Text(
                text = "ระบบบริหารการขาย",
                fontSize = 14.sp,
                color = TextGray,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp)
            )

            // ── Email Field ────────────────────────────────────
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text("Email", color = TextGray) },
                leadingIcon = {
                    Icon(Icons.Default.Email, contentDescription = null, tint = BrandRed)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = BrandRed,
                    unfocusedBorderColor    = BorderGray,
                    focusedTextColor        = TextBlack,
                    unfocusedTextColor      = TextBlack,
                    cursorColor             = BrandRed,
                    focusedLabelColor       = BrandRed,
                    unfocusedLabelColor     = TextGray,
                    focusedContainerColor   = BgWhite,
                    unfocusedContainerColor = BgGray
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction    = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ── Password Field ─────────────────────────────────
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password", color = TextGray) },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = BrandRed)
                },
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff
                            else Icons.Default.Visibility,
                            contentDescription = null,
                            tint = TextGray
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor      = BrandRed,
                    unfocusedBorderColor    = BorderGray,
                    focusedTextColor        = TextBlack,
                    unfocusedTextColor      = TextBlack,
                    cursorColor             = BrandRed,
                    focusedLabelColor       = BrandRed,
                    unfocusedLabelColor     = TextGray,
                    focusedContainerColor   = BgWhite,
                    unfocusedContainerColor = BgGray
                ),
                visualTransformation = if (showPassword) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction    = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onLoginClick()
                    }
                ),
                singleLine = true
            )

            // ── Error message ──────────────────────────────────
            AnimatedVisibility(visible = uiState is LoginUiState.Error) {
                Text(
                    text = (uiState as? LoginUiState.Error)?.message ?: "",
                    color = ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Login Button ───────────────────────────────────
            Button(
                onClick  = onLoginClick,
                enabled  = uiState !is LoginUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor         = BrandRed,
                    disabledContainerColor = BrandRedDark
                )
            ) {
                if (uiState is LoginUiState.Loading) {
                    CircularProgressIndicator(
                        color       = Color.White,
                        strokeWidth = 2.dp,
                        modifier    = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text       = "Login",
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ── Register Link ──────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("ยังไม่มีบัญชีผู้ใช้งาน? ", color = TextGray, fontSize = 14.sp)
                Text(
                    text = "ลงทะเบียนที่นี่",
                    color = BrandRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onRegisterClick() }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LoginScreenPreview() {
    SalesTrackingTheme {
        LoginScreenContent(
            uiState = LoginUiState.Idle,
            email = "",
            password = "",
            onEmailChange = {},
            onPasswordChange = {},
            onLoginClick = {},
            onRegisterClick = {},
            onLoginSuccess = {}
        )
    }
}
