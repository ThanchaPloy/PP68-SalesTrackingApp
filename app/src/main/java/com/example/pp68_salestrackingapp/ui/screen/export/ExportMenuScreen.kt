package com.example.pp68_salestrackingapp.ui.screen.export

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

@Composable
fun ExportMenuScreen(
    onBack: () -> Unit,
    onWeeklyClick: () -> Unit,
    onMonthlyClick: () -> Unit,
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    currentTab: Int,
    onTabChange: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Export Report",
                onBackClick = onBack,
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick
            )
        },
        bottomBar = {
            BottomNavBar(currentTab = currentTab, onTabChange = onTabChange)
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "เลือกประเภทการส่งออกรายงาน",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            ExportMenuCard(
                title = "รายงานรายสัปดาห์ (Weekly)",
                subtitle = "สรุปการเข้าพบลูกค้าและแผนงานรายสัปดาห์",
                icon = Icons.Default.CalendarViewWeek,
                color = Color(0xFF1976D2),
                onClick = onWeeklyClick
            )

            ExportMenuCard(
                title = "รายงานรายเดือน (Monthly)",
                subtitle = "สรุปผลการดำเนินงานและโครงการรายเดือน",
                icon = Icons.Default.CalendarMonth,
                color = Color(0xFFAE2138),
                onClick = onMonthlyClick
            )
        }
    }
}

@Composable
private fun ExportMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A1A))
                Spacer(Modifier.height(4.dp))
                Text(subtitle, fontSize = 13.sp, color = Color(0xFF888888))
            }

            Icon(Icons.Default.ChevronRight, null, tint = Color(0xFFCCCCCC))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ExportMenuScreenPreview() {
    SalesTrackingTheme {
        ExportMenuScreen(
            onBack = {},
            onWeeklyClick = {},
            onMonthlyClick = {},
            currentTab = 3,
            onTabChange = {}
        )
    }
}
