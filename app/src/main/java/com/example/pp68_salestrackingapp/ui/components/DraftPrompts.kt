package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pp68_salestrackingapp.ui.theme.AppColors

// เรียกตอนกดออกจากฟอร์มทั้งที่มีข้อมูลค้าง (dirty) — ให้เลือกบันทึกฉบับร่าง/ทิ้ง/กรอกต่อ
// ใช้ร่วมกันได้ทุกฟอร์ม ไม่มี logic เฉพาะหน้าใดหน้าหนึ่ง
@Composable
fun DiscardChangesDialog(
    onSaveDraft: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("มีข้อมูลที่ยังไม่ได้บันทึก") },
        text = { Text("ต้องการบันทึกฉบับร่างไว้ก่อนออกไหม ถ้าไม่บันทึกข้อมูลที่กรอกไว้จะหายทั้งหมด") },
        confirmButton = {
            TextButton(onClick = onSaveDraft) {
                Text("บันทึกฉบับร่าง", color = AppColors.Primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDiscard) { Text("ทิ้งข้อมูล", color = AppColors.Error) }
                TextButton(onClick = onDismiss) { Text("กรอกต่อ") }
            }
        }
    )
}

// แบนเนอร์เตือนว่ามีฉบับร่างค้างอยู่ — ใช้ได้ทั้งในหน้าลิสต์ (เช่น "มีโครงการที่กรอกค้างไว้")
// และในตัวฟอร์มเองตอนเปิดมาแล้วเจอฉบับร่างเก่า (เช่น "กู้คืนฉบับร่างที่บันทึกไว้ก่อนหน้านี้?")
@Composable
fun DraftBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.PrimaryLight)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Description, null, tint = AppColors.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            fontSize = 13.sp,
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onAction) {
            Text(actionLabel, color = AppColors.Primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.Close, contentDescription = "ปิด", tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}
