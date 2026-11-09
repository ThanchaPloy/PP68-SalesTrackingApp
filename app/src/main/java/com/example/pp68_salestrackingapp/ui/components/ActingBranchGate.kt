package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.di.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ผู้จัดการภาค (ZM) ต้องเลือกว่า "วันนี้ทำงานในนามสาขาไหน" — รหัสโครงการที่สร้างจะขึ้นต้นด้วยรหัส
 * สาขานั้น (ดู trg_set_project_code ฝั่ง backend) และแก้ทีหลังไม่ได้ ใช้ ViewModel เดียวกันนี้กับ
 * ทั้ง 3 ที่: dialog บังคับเลือกตอน login ([ActingBranchGate]), banner ที่หน้า Home
 * ([ActingBranchBanner]), และแถวในหน้าตั้งค่า — เพื่อไม่ให้ลอจิกโหลด/เลือกสาขาซ้ำกัน 3 ชุด
 *
 * สาขาที่เลือกเป็น session preference ใน TokenManager (reactive ผ่าน StateFlow) ไม่ sync ขึ้น
 * server และล้างพร้อม token ตอน logout จึงถามใหม่ทุกครั้งที่ login
 */
@HiltViewModel
class ActingBranchViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val branchRepo: BranchRepository
) : ViewModel() {

    val managedRegion: String? = tokenManager.getManagedRegion()

    private val _options = MutableStateFlow<List<Branch>>(emptyList())
    val options: StateFlow<List<Branch>> = _options.asStateFlow()

    val actingBranchId: StateFlow<String?> = tokenManager.actingBranch

    val actingBranchName: StateFlow<String?> = combine(options, actingBranchId) { opts, id ->
        opts.find { it.branchId == id }?.branchName
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ⚠️ ห้ามโชว์ dialog บังคับที่ปิดไม่ได้และไม่มีตัวเลือกให้กด — ออฟไลน์ตั้งแต่ login แรกจะกลาย
    // เป็นจอค้างที่ออกไม่ได้เลย ต้องมีสาขาให้เลือกจริงก่อนถึงจะบังคับ
    val mustChoose: StateFlow<Boolean> = combine(options, actingBranchId) { opts, id ->
        managedRegion != null && opts.isNotEmpty() && id == null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        val region = managedRegion
        if (region != null) {
            viewModelScope.launch {
                var branches = branchRepo.observeBranches().filter { it.region == region }
                // เครื่องที่เพิ่งล้างข้อมูล/เพิ่ง login ครั้งแรกยังไม่มีตารางสาขาใน Room
                if (branches.isEmpty()) {
                    runCatching { branchRepo.syncFromRemote() }
                    branches = branchRepo.observeBranches().filter { it.region == region }
                }
                _options.value = branches
            }
        }
    }

    fun select(branchId: String) {
        tokenManager.saveActingBranch(branchId)
    }
}

@Composable
private fun BranchPickerContent(options: List<Branch>, selectedId: String?, onSelect: (Branch) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        options.forEach { branch ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(branch) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = branch.branchId == selectedId, onClick = { onSelect(branch) })
                Spacer(Modifier.width(8.dp))
                Text(branch.branchName)
            }
        }
    }
}

/** เด้งบังคับเลือกที่หน้าแรกหลัง login — ปิดไม่ได้จนกว่าจะเลือก เฉพาะ ZM ที่ยังไม่เคยเลือก */
@Composable
fun ActingBranchGate(viewModel: ActingBranchViewModel = hiltViewModel()) {
    val mustChoose by viewModel.mustChoose.collectAsState()
    val options by viewModel.options.collectAsState()
    val actingBranchId by viewModel.actingBranchId.collectAsState()
    if (!mustChoose) return

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("วันนี้ทำงานในนามสาขาไหน") },
        text = {
            Column {
                Text(
                    "รหัสโครงการที่สร้างจะขึ้นต้นด้วยรหัสสาขานี้ เปลี่ยนภายหลังได้ที่หน้า Home หรือหน้าตั้งค่า",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BranchPickerContent(options, actingBranchId) { viewModel.select(it.branchId) }
            }
        },
        // ไม่มีปุ่มยืนยัน/ยกเลิกโดยตั้งใจ — แตะสาขาคือเลือกเสร็จ ไม่มีทางออกแบบไม่เลือก
        confirmButton = {}
    )
}

/**
 * Banner บนหน้า Home แสดงสาขาที่ทำงานอยู่ตอนนี้ + ปุ่มเปลี่ยน — ย้ายมาจากที่เดิมซึ่งซ่อนอยู่ในหน้า
 * ตั้งค่าอย่างเดียว เพราะกลัวคนไม่กดเข้าไปเปลี่ยนแล้วสร้างโครงการผิดสาขาซ้ำๆ ไม่แสดงอะไรเลยถ้าไม่ใช่
 * ZM (managedRegion เป็น null)
 */
@Composable
fun ActingBranchBanner(modifier: Modifier = Modifier, viewModel: ActingBranchViewModel = hiltViewModel()) {
    if (viewModel.managedRegion == null) return
    val options by viewModel.options.collectAsState()
    val actingBranchId by viewModel.actingBranchId.collectAsState()
    val actingBranchName by viewModel.actingBranchName.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(8.dp))
            .clickable(enabled = options.isNotEmpty()) { showDialog = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("สาขาที่ทำงานอยู่ตอนนี้", fontSize = 12.sp, color = Color(0xFF888888))
            Text(
                actingBranchName ?: "ยังไม่ได้เลือก",
                fontWeight = FontWeight.Bold
            )
        }
        if (options.isNotEmpty()) {
            Icon(Icons.Default.ChevronRight, contentDescription = "เปลี่ยน", tint = Color(0xFFCCCCCC))
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("เลือกสาขาที่ทำงานอยู่ตอนนี้") },
            text = {
                BranchPickerContent(options, actingBranchId) {
                    viewModel.select(it.branchId)
                    showDialog = false
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showDialog = false }) { Text("ปิด") }
            }
        )
    }
}
