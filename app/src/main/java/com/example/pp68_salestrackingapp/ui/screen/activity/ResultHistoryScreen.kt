package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ResultHistoryViewModel
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.ResultVersionItem

private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFAE2138)
private val BgLight    = Color(0xFFF5F5F5)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultHistoryScreen(
    onBack: () -> Unit,
    onSelectVersion: (String) -> Unit,
    viewModel: ResultHistoryViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("ประวัติการแก้ไข", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = White)
            )
        },
        containerColor = BgLight
    ) { padding ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
        } else if (s.versions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("ไม่พบประวัติการแก้ไข", color = TextGray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(s.versions) { item ->
                    VersionCard(item = item, onClick = { onSelectVersion(item.resultId) })
                }
            }
        }
    }
}

@Composable
private fun VersionCard(item: ResultVersionItem, onClick: () -> Unit) {
    Surface(
        color = White,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (item.isLatest) RedPrimary.copy(alpha = 0.1f) else Color(0xFFEEEEEE),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "เวอร์ชัน ${item.version}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (item.isLatest) RedPrimary else TextGray
                    )
                }
                if (item.isLatest) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ล่าสุด", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.weight(1f))
                Text(item.reportDate ?: "-", fontSize = 12.sp, color = TextGray)
            }
            if (!item.newStatus.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text("สถานะใหม่: ${item.newStatus}", fontSize = 13.sp, color = TextDark, fontWeight = FontWeight.Medium)
            }
            if (!item.summary.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    item.summary,
                    fontSize = 13.sp,
                    color = TextGray,
                    maxLines = 2
                )
            }
        }
    }
}
