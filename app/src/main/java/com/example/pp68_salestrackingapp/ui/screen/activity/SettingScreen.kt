package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SettingsViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.SettingsUiState

// ── Colors ────────────────────────────────────────────────────
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFAE2138)
private val BgLight    = Color(0xFFF5F5F5)
private val DividerCol = Color(0xFFEEEEEE)

@Composable
fun SettingScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    SettingScreenContent(
        uiState = uiState,
        onBack = onBack,
        onLogout = {
            viewModel.logout()
            onLogout()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreenContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    // State สำหรับควบคุมว่ากำลังแสดงหน้าย่อยไหน
    var activeSubScreen by remember { mutableStateOf<String?>(null) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    // เลือกแสดงผลตาม State
    when (activeSubScreen) {
        "edit_profile" -> EditProfileScreen(
            onBack = { activeSubScreen = null },
            onSave = { activeSubScreen = null }
        )
        "notification_settings" -> NotificationSettingsScreen(
            onBack = { activeSubScreen = null }
        )
        "change_password" -> ChangePasswordScreen(
            onBack = { activeSubScreen = null },
            onSave = { activeSubScreen = null }
        )
        "help" -> SettingDetailScreen(
            title   = "Help Center",
            content = """
            ยินดีให้ความช่วยเหลือ
            
            📱 การใช้งานแอป
            - หน้า Home — ดูแผนการเข้าพบและนัดหมายทั้งหมด
            - หน้า Company — จัดการข้อมูลลูกค้าและบริษัท
            - หน้า Contact — จัดการผู้ติดต่อของแต่ละบริษัท
            - หน้า Project — ติดตามโครงการและ pipeline การขาย
            - หน้า Dashboard — ดูสถิติและผลงานการขาย
            
            📋 การสร้างแผนการเข้าพบ
            1. กดปุ่ม + ที่หน้า Home
            2. เลือก Project และ Contact
            3. กำหนดวันเวลาและประเภทการพบ
            4. เลือก Objective ที่ต้องการทำ
            5. กด Save
            
            ✅ การ Check-in
            - Onsite — กด Check-in แล้วยืนยันตำแหน่ง GPS
            - Online/Call — กด Finish ได้เลย
            
            📊 การบันทึกผล
            - หลัง Finish แล้วกด "บันทึกผล" ที่หน้า Home
            
            📞 ติดต่อทีม Support
            - Email: support@company.com
            - โทร: 02-xxx-xxxx
            - เวลาทำการ: จันทร์-ศุกร์ 9:00-18:00
                """.trimIndent(),
            onBack = { activeSubScreen = null }
        )

        "about" -> SettingDetailScreen(
            title   = "About App",
            content = """
            Sales Tracking App
            Version 1.0.0
            
            พัฒนาโดย: ทีมพัฒนา PP68
            สถาบัน: สถาบันเทคโนโลยีพระจอมเกล้าเจ้าคุณทหารลาดกระบัง (KMITL)
            ปีการศึกษา: 2568
            
            📱 เกี่ยวกับแอปพลิเคชัน
            แอปพลิเคชันสำหรับพนักงานขายในการติดตามและบริหารจัดการกิจกรรมการขาย ครอบคลุมตั้งแต่การจัดการลูกค้า โครงการ การนัดหมาย และการวิเคราะห์ผลงาน
            
            🛠 เทคโนโลยีที่ใช้
            - Android: Kotlin + Jetpack Compose
            - Backend: PostgREST + Google Cloud Functions
            - Database: PostgreSQL บน Google Cloud SQL
            - Authentication: JWT
            - Push Notification: Firebase Cloud Messaging

            © 2026 PP68 Sales Tracking. All rights reserved.
            """.trimIndent(),
            onBack = { activeSubScreen = null }
        )

        "privacy" -> SettingDetailScreen(
            title   = "Privacy Policy",
            content = """
            นโยบายความเป็นส่วนตัว
            อัปเดตล่าสุด: มีนาคม 2026
            
            1. ข้อมูลที่เก็บรวบรวม
            เราเก็บข้อมูลที่จำเป็นสำหรับการให้บริการ ได้แก่:
            - ชื่อ-นามสกุล และอีเมล
            - ตำแหน่งที่ตั้ง GPS (เฉพาะตอน Check-in)
            - ข้อมูลกิจกรรมการขายและนัดหมาย
            - FCM Token สำหรับการแจ้งเตือน
            
            2. การใช้ข้อมูล
            ข้อมูลของคุณถูกใช้เพื่อ:
            - แสดงผลในแอปพลิเคชัน
            - ส่งการแจ้งเตือนนัดหมาย
            - วิเคราะห์ผลงานการขาย
            
            3. การแบ่งปันข้อมูล
            เราไม่แบ่งปันข้อมูลส่วนตัวกับบุคคลภายนอก ยกเว้นผู้บังคับบัญชาในองค์กรที่มีสิทธิ์เข้าถึง
            
            4. ความปลอดภัย
            ข้อมูลทั้งหมดถูกเข้ารหัสและจัดเก็บบน Google Cloud Platform ที่ได้มาตรฐานความปลอดภัยสากล
            
            5. สิทธิ์ของผู้ใช้
            คุณมีสิทธิ์ขอดู แก้ไข หรือลบข้อมูลส่วนตัวได้โดยติดต่อ support@company.com
            
            6. การเปลี่ยนแปลงนโยบาย
            เราอาจอัปเดตนโยบายนี้เป็นครั้งคราว โดยจะแจ้งให้ทราบผ่านแอปพลิเคชัน
            """.trimIndent(),
            onBack = { activeSubScreen = null }
        )
        else -> {
            // หน้าเมนูหลัก
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = White,
                            titleContentColor = TextDark
                        )
                    )
                },
                containerColor = BgLight
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                ) {
                    ProfileSection(user = uiState.user)
                    Spacer(Modifier.height(24.dp))

                    SettingGroup(title = "Account") {
                        SettingItem(icon = Icons.Default.Person, label = "Edit Profile", onClick = { activeSubScreen = "edit_profile" })
                        SettingItem(icon = Icons.Default.Lock, label = "Change Password", onClick = { activeSubScreen = "change_password" })
                        SettingItem(icon = Icons.Default.Notifications, label = "Notification Settings", onClick = { activeSubScreen = "notification_settings" })
                    }

//                    SettingGroup(title = "Application") {
//                        SettingItem(icon = Icons.Default.Language, label = "Language", value = "English", onClick = { showLanguageDialog = true })
//                        SettingItem(icon = Icons.Default.DarkMode, label = "Dark Mode", hasSwitch = true)
//                        SettingItem(icon = Icons.Default.CloudUpload, label = "Sync Data")
//                    }

                    SettingGroup(title = "Support") {
                        SettingItem(icon = Icons.Default.Help, label = "Help Center", onClick = { activeSubScreen = "help" })
                        SettingItem(icon = Icons.Default.Info, label = "About App", value = "v1.0.5", onClick = { activeSubScreen = "about" })
                        SettingItem(icon = Icons.Default.Policy, label = "Privacy Policy", onClick = { activeSubScreen = "privacy" })
                    }

                    Spacer(Modifier.height(32.dp))
                    LogoutButton(onClick = onLogout)
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            onDismiss = { showLanguageDialog = false },
            onSelect = { showLanguageDialog = false }
        )
    }
}

@Composable
private fun ProfileSection(user: AuthUser?) {
    Surface(color = White, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFDECEA)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = RedPrimary,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(Modifier.height(16.dp))

            // ✅ แสดง fullName จริง
            Text(
                text = user?.fullName
                    ?: user?.email?.substringBefore("@")
                        ?.replaceFirstChar { it.uppercase() }
                    ?: "User Name",
                fontWeight = FontWeight.Bold,
                fontSize   = 18.sp,
                color      = TextDark
            )

            // ✅ แสดง role badge
            Surface(
                color = Color(0xFFFDECEA),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = user?.role?.uppercase() ?: "SALE",
                    fontSize   = 11.sp,
                    color      = RedPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // ✅ แสดง branchName
            if (!user?.branchName.isNullOrBlank()) {
                Text(
                    text     = user?.branchName ?: "",
                    fontSize = 13.sp,
                    color    = Color(0xFF0369A1),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Text(
                text     = user?.email ?: "user@example.com",
                fontSize = 13.sp,
                color    = TextGray,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = TextGray,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Surface(color = White) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    label: String,
    value: String? = null,
    hasSwitch: Boolean = false,
    onClick: () -> Unit = {}
) {
    var checked by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (!hasSwitch) onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BgLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(label, modifier = Modifier.weight(1f), fontSize = 15.sp, color = TextDark)
        
        if (value != null) {
            Text(value, fontSize = 14.sp, color = TextGray)
            Spacer(Modifier.width(8.dp))
        }

        if (hasSwitch) {
            Switch(
                checked = checked,
                onCheckedChange = { checked = it },
                colors = SwitchDefaults.colors(checkedThumbColor = White, checkedTrackColor = RedPrimary)
            )
        } else {
            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(20.dp))
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = DividerCol)
}

@Composable
private fun LogoutButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFDECEA),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFCDD2))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = RedPrimary)
            Spacer(Modifier.width(12.dp))
            Text("Logout", color = RedPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingScreenPreview() {
    SalesTrackingTheme {
        // Use the stateless version for Preview to avoid ViewModel instantiation issues
        SettingScreenContent(
            uiState = SettingsUiState(
                user = AuthUser(
                    userId = "1",
                    email = "preview@example.com",
                    role = "Sales Manager"
                )
            ),
            onBack = {},
            onLogout = {}
        )
    }
}
