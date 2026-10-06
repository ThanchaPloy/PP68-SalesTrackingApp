package com.example.pp68_salestrackingapp.ui.screen.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.AppointmentDraft
import com.example.pp68_salestrackingapp.ui.viewmodels.activity.AppointmentDraftListViewModel
import java.time.Duration
import java.time.Instant

private val TextDark = Color(0xFF1A1A1A)
private val TextGray = Color(0xFF888888)
private val RedPrimary = Color(0xFFAE2138)

/**
 * รายการฉบับร่างนัดหมาย เรียงแก้ล่าสุดก่อน (แผนงาน C.3)
 *
 * เต็มโควตาแล้วปุ่มสร้างใหม่จะถูกปิดพร้อมบอกเหตุผล ผู้ใช้เลือกลบเองว่าจะทิ้งอันไหน
 * ระบบไม่ลบให้ เพราะร่างที่เก่าที่สุดไม่ได้แปลว่าสำคัญน้อยที่สุด
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppointmentDraftListScreen(
    onBack: () -> Unit,
    onOpenDraft: (String) -> Unit,
    onCreateNew: () -> Unit,
    viewModel: AppointmentDraftListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<AppointmentDraft?>(null) }

    pendingDelete?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("ลบฉบับร่าง") },
            text = { Text("ลบ \"${draft.title ?: "ฉบับร่างไม่มีหัวข้อ"}\" ทิ้ง กู้คืนไม่ได้") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(draft.draftId)
                    pendingDelete = null
                }) { Text("ลบ", color = RedPrimary) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("ยกเลิก") }
            }
        )
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "ย้อนกลับ") }
                Text("ฉบับร่างนัดหมาย", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp))
                state.drafts.isEmpty() -> Text(
                    "ยังไม่มีฉบับร่างที่บันทึกไว้",
                    color = TextGray,
                    fontSize = 14.sp
                )
                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.drafts, key = { it.draftId }) { draft ->
                        DraftRow(
                            draft = draft,
                            onContinue = { onOpenDraft(draft.draftId) },
                            onDelete = { pendingDelete = draft }
                        )
                    }
                }
            }

            if (state.isFull) {
                Text(
                    "เก็บฉบับร่างได้สูงสุด ${state.max} รายการ กรุณาลบรายการที่ไม่ใช้ก่อนสร้างใหม่",
                    color = RedPrimary,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = onCreateNew,
                enabled = !state.isFull,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RedPrimary)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("สร้างนัดหมายใหม่")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DraftRow(draft: AppointmentDraft, onContinue: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onContinue),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    draft.title ?: "ฉบับร่างไม่มีหัวข้อ",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextDark
                )
                // ชื่อที่เก็บไว้ตอนบันทึกร่าง — โครงการอาจถูกลบไปแล้ว แต่ผู้ใช้ยังต้องรู้ว่าร่างนี้คือเรื่องอะไร
                listOfNotNull(draft.customerNameSnapshot, draft.projectNameSnapshot)
                    .takeIf { it.isNotEmpty() }
                    ?.let { Text(it.joinToString(" · "), fontSize = 13.sp, color = TextGray) }
                listOfNotNull(draft.plannedDate, draft.plannedTime)
                    .takeIf { it.isNotEmpty() }
                    ?.let { Text("นัด ${it.joinToString(" ")}", fontSize = 12.sp, color = TextGray) }
                Text("แก้ล่าสุด ${relativeTime(draft.updatedAt)}", fontSize = 11.sp, color = TextGray)
            }
            TextButton(onClick = onContinue) { Text("ทำต่อ", color = RedPrimary) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "ลบ", tint = TextGray, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** เวลาแบบอ่านง่าย ค่าที่อ่านไม่ออกคืนค่าดิบไป ดีกว่าโชว์ว่างเปล่า */
private fun relativeTime(iso: String): String {
    val then = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    val minutes = Duration.between(then, Instant.now()).toMinutes()
    return when {
        minutes < 1 -> "เมื่อสักครู่"
        minutes < 60 -> "$minutes นาทีที่แล้ว"
        minutes < 60 * 24 -> "${minutes / 60} ชั่วโมงที่แล้ว"
        else -> "${minutes / (60 * 24)} วันที่แล้ว"
    }
}
