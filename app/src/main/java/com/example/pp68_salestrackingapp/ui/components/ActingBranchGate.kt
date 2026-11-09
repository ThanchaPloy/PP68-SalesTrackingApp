package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pp68_salestrackingapp.data.model.Branch
import com.example.pp68_salestrackingapp.data.repository.BranchRepository
import com.example.pp68_salestrackingapp.di.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ผู้จัดการภาค (ZM) ต้องเลือกว่า "วันนี้ทำงานในนามสาขาไหน" ก่อนใช้งาน — รหัสโครงการที่สร้างจะขึ้นต้น
 * ด้วยรหัสสาขานั้น (ดู trg_set_project_code ฝั่ง backend) และแก้ทีหลังไม่ได้
 *
 * acting_branch_id ถูกล้างใน TokenManager.clearToken() อยู่แล้ว จึงถามใหม่ทุกครั้งที่ login
 * เปลี่ยนระหว่างวันได้จากหน้าตั้งค่า ("สาขาที่ทำงานอยู่ตอนนี้")
 */
@HiltViewModel
class ActingBranchViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val branchRepo: BranchRepository
) : ViewModel() {

    private val _options = MutableStateFlow<List<Branch>>(emptyList())
    val options: StateFlow<List<Branch>> = _options.asStateFlow()

    private val _mustChoose = MutableStateFlow(false)
    val mustChoose: StateFlow<Boolean> = _mustChoose.asStateFlow()

    init {
        val region = tokenManager.getManagedRegion()
        if (region != null && tokenManager.getActingBranch() == null) {
            viewModelScope.launch {
                var branches = branchRepo.observeBranches().filter { it.region == region }
                // เครื่องที่เพิ่งล้างข้อมูล/เพิ่ง login ครั้งแรกยังไม่มีตารางสาขาใน Room
                if (branches.isEmpty()) {
                    runCatching { branchRepo.syncFromRemote() }
                    branches = branchRepo.observeBranches().filter { it.region == region }
                }
                _options.value = branches
                // ⚠️ ห้ามโชว์ dialog ที่ปิดไม่ได้และไม่มีตัวเลือกให้กด — ออฟไลน์ตั้งแต่ login แรกจะ
                // กลายเป็นจอค้างที่ออกไม่ได้เลย ปล่อยผ่านไปก่อน แล้วไปเลือกเองตอนสร้างโครงการ/หน้าตั้งค่า
                _mustChoose.value = branches.isNotEmpty()
            }
        }
    }

    fun select(branchId: String) {
        tokenManager.saveActingBranch(branchId)
        _mustChoose.value = false
    }
}

@Composable
fun ActingBranchGate(viewModel: ActingBranchViewModel = hiltViewModel()) {
    val mustChoose by viewModel.mustChoose.collectAsState()
    val options by viewModel.options.collectAsState()
    if (!mustChoose) return

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("วันนี้ทำงานในนามสาขาไหน") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "รหัสโครงการที่สร้างจะขึ้นต้นด้วยรหัสสาขานี้ เปลี่ยนภายหลังได้ที่หน้าตั้งค่า",
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                options.forEach { branch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.select(branch.branchId) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = false, onClick = { viewModel.select(branch.branchId) })
                        Spacer(Modifier.width(8.dp))
                        Text(branch.branchName)
                    }
                }
            }
        },
        // ไม่มีปุ่มยืนยัน/ยกเลิกโดยตั้งใจ — แตะสาขาคือเลือกเสร็จ ไม่มีทางออกแบบไม่เลือก
        confirmButton = {}
    )
}
