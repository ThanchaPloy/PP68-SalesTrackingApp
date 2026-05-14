package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.project.InventoryItem
import com.example.pp68_salestrackingapp.ui.viewmodels.project.ProjectInventoryUiState
import com.example.pp68_salestrackingapp.ui.viewmodels.project.ProjectInventoryViewModel

private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFCC1D1D)
private val RedDark    = Color(0xFF8B0000)
private val BgPink     = Color(0xFFFFF5F5)
private val BgLight    = Color(0xFFF5F5F5)
private val BorderGray = Color(0xFFE8E8E8)

@Composable
fun ProjectInventoryScreen(
    projectId: String,
    onBack: () -> Unit,
    onAddProduct: (String) -> Unit,
    onEditProduct: (String, String) -> Unit,
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    viewModel: ProjectInventoryViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    // reload ทุกครั้งที่ composable กลับมา (เช่น หลัง navigate back)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.load()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ProjectInventoryContent(
        s = s,
        onBack = onBack,
        onAddProduct = { onAddProduct(projectId) },
        onEditProduct = { prodId -> onEditProduct(projectId, prodId) },
        onDeleteProduct = { viewModel.deleteProduct(it) },
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onLogoutClick = onLogoutClick,
        currentTab = currentTab,
        onTabChange = onTabChange
    )
}

@Composable
fun ProjectInventoryContent(
    s: ProjectInventoryUiState,
    onBack: () -> Unit,
    onAddProduct: () -> Unit,
    onEditProduct: (String) -> Unit,
    onDeleteProduct: (String) -> Unit,
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    currentTab: Int,
    onTabChange: (Int) -> Unit
) {
    var itemToDelete by remember { mutableStateOf<InventoryItem?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Project Inventory",
                onBackClick = onBack,
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddProduct,
                containerColor = RedPrimary,
                contentColor = White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("เพิ่มสินค้า", fontWeight = FontWeight.Bold)
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            BottomNavBar(currentTab = currentTab, onTabChange = onTabChange)
        },
        containerColor = BgLight
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Project Summary Header
            Surface(color = BgPink, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "โครงการ ${s.project?.projectName ?: ""}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextDark
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Business, null, tint = TextGray, modifier = Modifier.size(14.dp))
                            Text(s.companyName, fontSize = 13.sp, color = TextGray)
                        }
                    }
                    ProjectStatusBadge(s.project?.projectStatus ?: "กำลังดำเนินการ")
                }
            }

            if (s.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column {
                            Text(
                                "รายการสินค้าในโครงการ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextGray
                            )
                            Text(
                                "${s.items.size} รายการ",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }
                    }

                    items(s.items) { item ->
                        ProductInventoryCard(
                            item = item,
                            onEdit = { onEditProduct(item.productId) },
                            onDelete = { itemToDelete = item }
                        )
                    }
                    
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("ยืนยันการลบ") },
            text = { Text("คุณต้องการลบรายการ ${item.productName} ออกจากโครงการใช่หรือไม่?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProduct(item.productId)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = RedPrimary)
                ) {
                    Text("ลบ")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("ยกเลิก")
                }
            }
        )
    }
}

@Composable
fun ProductInventoryCard(
    item: InventoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = White,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGray),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.productName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextDark
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Icon(Icons.Default.Layers, null, tint = TextGray, modifier = Modifier.size(14.dp))
                        Text(item.category, fontSize = 13.sp, color = TextGray)
                    }
                }
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "แก้ไข", tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "ลบ", tint = RedPrimary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = BgPink
            ) {
                Text(
                    text = "${if(item.quantity % 1.0 == 0.0) item.quantity.toInt() else item.quantity} ${item.unit}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = RedPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProjectStatusBadge(status: String) {
    Surface(
        color = White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Text(
            text = status,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectInventoryScreenPreview() {
    SalesTrackingTheme {
        ProjectInventoryContent(
            s = ProjectInventoryUiState(

                project = Project(
                    projectId = "PJ-001",
                    projectName = "คอนโด Happy",
                    custId = "C-001",
                    branchId = "B-001",
                    projectStatus = "Active Project",
                    expectedValue = 1500000.0,
                    opportunityScore = "HOT",
                    startDate = "2023-01-01",
                    closingDate = "2023-12-31",
                    desiredCompletionDate = "2024-01-01",
                    projectLat = 13.7563,
                    projectLong = 100.5018
                ),
                companyName = "Tech Installer A",
                items = listOf(
                    InventoryItem("P1", "กระจกเทมเปอร์ 12mm", "กระจก", 45.0, "ตารางฟุต"),
                    InventoryItem("P2", "อะลูมิเนียม Profile X-200", "อะลูมิเนียม", 120.0, "ชิ้น"),
                    InventoryItem("P3", "Silicone Sealant Pro", "ซิลิโคน", 8.0, "เซ็ต")
                )
            ),
            onBack = {},
            onAddProduct = {},
            onEditProduct = {},
            onDeleteProduct = {},
            currentTab = 3,
            onTabChange = {}
        )
    }
}
