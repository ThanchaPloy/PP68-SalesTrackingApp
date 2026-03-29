package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.AuthUser

@Composable
fun AppTopBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    user: AuthUser? = null,
    viewModel: AppTopBarViewModel? = null
) {
    // 💡 Handle ViewModel initialization carefully for Previews.
    // Previews cannot instantiate Hilt ViewModels directly.
    val actualViewModel: AppTopBarViewModel? = if (LocalInspectionMode.current) {
        null
    } else {
        viewModel ?: hiltViewModel()
    }
    
    var showProfilePopup by remember { mutableStateOf(false) }
    
    // ดึงข้อมูล User จาก ViewModel มาใช้ (ถ้าไม่มีการส่ง user มาจากภายนอก)
    val authUserFlowState = actualViewModel?.user?.collectAsState()
    val displayUser = user ?: authUserFlowState?.value

    Surface(
        shadowElevation = 2.dp,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = Color(0xFF1A1A1A))
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }

            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF1A1A1A),
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "notification",
                        tint = Color(0xFF1A1A1A)
                    )
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "settings",
                        tint = Color(0xFF1A1A1A)
                    )
                }
                Box {
                    Surface(
                        onClick = { 
                            actualViewModel?.refreshUser() // รีเฟรชข้อมูลทุกครั้งที่เปิดดู
                            showProfilePopup = !showProfilePopup 
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape),
                        color = Color(0xFFE0E0E0)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "profile",
                                tint = Color(0xFF888888),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (showProfilePopup) {
                        Popup(
                            alignment = Alignment.TopEnd,
                            onDismissRequest = { showProfilePopup = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .padding(top = 44.dp, end = 0.dp)
                                    .width(260.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                shadowElevation = 8.dp,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEEEEEE))
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(60.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFDECEA)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            modifier = Modifier.size(36.dp),
                                            tint = Color(0xFFAE2138)
                                        )
                                    }
                                    Spacer(Modifier.height(12.dp))

                                    if (displayUser != null) {
                                        // ✅ แสดงชื่อจริง ถ้าไม่มีค่อย fallback เป็น email
                                        Text(
                                            text = displayUser.fullName
                                                ?: displayUser.email.substringBefore("@")
                                                    .replaceFirstChar { it.uppercase() },
                                            fontWeight = FontWeight.Bold,
                                            fontSize   = 16.sp,
                                            color      = Color(0xFF1A1A1A)
                                        )
                                        Text(
                                            text     = displayUser.email,
                                            fontSize = 13.sp,
                                            color    = Color(0xFF888888)
                                        )

                                        // ✅ แสดง branch name
                                        if (!displayUser.branchName.isNullOrBlank()) {
                                            Text(
                                                text     = displayUser.branchName,
                                                fontSize = 12.sp,
                                                color    = Color(0xFF0369A1),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }

                                        Spacer(Modifier.height(12.dp))

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            // Role badge
                                            Surface(
                                                color = Color(0xFFFDECEA),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = displayUser.role,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                    fontSize = 11.sp,
                                                    color    = Color(0xFFAE2138),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }

                                            // ✅ Branch badge
                                            if (!displayUser.branchName.isNullOrBlank()) {
                                                Surface(
                                                    color = Color(0xFFF0F9FF),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = displayUser.branchName,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                                        fontSize = 11.sp,
                                                        color    = Color(0xFF0369A1),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        // กรณีไม่มีข้อมูล user จริงๆ (เช่น ยังไม่ได้ Login หรือ session หลุด)
                                        Text("Not Logged In", fontWeight = FontWeight.Bold)
                                        Text("Please login again", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    
                                    Spacer(Modifier.height(16.dp))
                                    HorizontalDivider(color = Color(0xFFF5F5F5))
                                    Spacer(Modifier.height(8.dp))
                                    
                                    TextButton(
                                        onClick = {
                                            showProfilePopup = false
                                            onLogoutClick()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFAE2138))
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Log Out", fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
