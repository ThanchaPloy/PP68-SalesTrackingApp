package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val RedActive = Color(0xFF8C0202)
private val TextGray  = Color(0xFF888888)
private val White     = Color.White

@Composable
fun BottomNavBar(currentTab: Int, onTabChange: (Int) -> Unit) {
    val tabs = listOf(
        Icons.Default.Home         to "Home",
        Icons.Default.Business     to "Company",
        Icons.Default.Contacts     to "Contact",
        Icons.Default.Assignment   to "Project",
        Icons.Default.BarChart     to "Stats"
    )
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp // ✅ ปรับ tonalElevation เป็น 0 เพื่อให้สีขาวสนิทเหมือน TopBar
    ) {
        tabs.forEachIndexed { index, (icon, label) ->
            NavigationBarItem(
                selected = currentTab == index,
                onClick  = { onTabChange(index) },
                icon  = { Icon(icon, label, modifier = Modifier.size(22.dp)) },
                label = { Text(label, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor   = RedActive,
                    selectedTextColor   = RedActive,
                    unselectedIconColor = TextGray,
                    unselectedTextColor = TextGray,
                    indicatorColor      = Color.Transparent
                )
            )
        }
    }
}
