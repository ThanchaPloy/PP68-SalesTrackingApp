package com.example.pp68_salestrackingapp.ui.screen.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.ui.components.AddFloatingActionButton
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

private fun String.stripThaiPrefix(): String {
    val prefixes = listOf(
        "นางสาว ", "นาง ", "นาย ", "ด.ช. ", "ด.ญ. ", "คุณ ",
        "บริษัท ", "ห้างหุ้นส่วนจำกัด ", "ห้างหุ้นส่วน ", "หจก. ", "บจก. ", "กิจการร่วมค้า "
    )
    val t = this.trim()
    return prefixes.firstOrNull { t.startsWith(it) }?.let { t.removePrefix(it) } ?: t
}

private val CUST_THAI_CONSONANTS = listOf(
    "ก","ข","ค","ง","จ","ฉ","ช","ซ","ญ","ด",
    "ต","ถ","ท","น","บ","ป","ผ","ฝ","พ","ฟ",
    "ภ","ม","ย","ร","ล","ว","ส","ห","อ","ฮ"
)
private val CUST_ALL_SIDEBAR = listOf("#") + CUST_THAI_CONSONANTS + ('A'..'Z').map { it.toString() }
private val CustAccent = Color(0xFF3F51B5)
private val CustTextGray = Color(0xFF888888)

@Composable
fun CustomerListScreen(
    onCustomerClick: (String) -> Unit,
    onAddClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    currentTab: Int = 1,
    onTabChange: (Int) -> Unit = {},
    viewModel: CustomerListViewModel = hiltViewModel()
) {
    val customers: List<Customer> by viewModel.customers.collectAsState(initial = emptyList())
    val isLoading        by viewModel.isLoading.collectAsState(initial = false)
    val searchQuery      by viewModel.searchQuery.collectAsState(initial = "")
    val error            by viewModel.error.collectAsState(initial = null)
    val authUser         by viewModel.authUser.collectAsState(initial = null)
    val selectedBizGroup by viewModel.selectedBizGroup.collectAsState(initial = null)
    val selectedCustType by viewModel.selectedCustType.collectAsState(initial = null)

    var showFilterModal by remember { mutableStateOf(false) }

    CustomerListContent(
        customers = customers,
        isLoading = isLoading,
        searchQuery = searchQuery,
        error = error,
        authUser = authUser,
        hasActiveFilter = selectedBizGroup != null || selectedCustType != null,
        onSearchChange = viewModel::onSearchChange,
        onFilterClick = { showFilterModal = true },
        onCustomerClick = onCustomerClick,
        onAddClick = onAddClick,
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onLogoutClick = {
            viewModel.logout()
            onLogoutClick()
        },
        currentTab = currentTab,
        onTabChange = onTabChange
    )

    if (showFilterModal) {
        CustomerFilterModal(
            selectedBizGroup = selectedBizGroup,
            selectedCustType = selectedCustType,
            onBizGroupToggle = viewModel::onBizGroupFilter,
            onCustTypeToggle = viewModel::onCustTypeFilter,
            onReset   = viewModel::resetFilters,
            onDismiss = { showFilterModal = false }
        )
    }
}

@Composable
fun CustomerListContent(
    customers: List<Customer>,
    isLoading: Boolean,
    searchQuery: String,
    error: String?,
    authUser: AuthUser?,
    hasActiveFilter: Boolean,
    onSearchChange: (String) -> Unit,
    onFilterClick: () -> Unit,
    onCustomerClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    currentTab: Int,
    onTabChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val sortedCustomers = remember(customers) {
        customers.sortedBy { it.companyName.stripThaiPrefix() }
    }
    val letterIndex = remember(sortedCustomers) {
        val map = linkedMapOf<String, Int>()
        sortedCustomers.forEachIndexed { i, c ->
            val ch = c.companyName.stripThaiPrefix().firstOrNull()?.toString() ?: "#"
            if (!map.containsKey(ch)) map[ch] = i
        }
        map
    }
    var activeLetter by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Customer/Company",
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick,
                user = authUser
            )
        },
        bottomBar = { BottomNavBar(currentTab = currentTab, onTabChange = onTabChange) },
        floatingActionButton = {
            AddFloatingActionButton(onClick = onAddClick, contentDescription = "เพิ่มลูกค้า")
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery, onValueChange = onSearchChange,
                placeholder = { Text("ค้นหาบริษัท...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, null, tint = Color.Gray)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor    = Color.Transparent,
                    focusedBorderColor      = Color(0xFF3F51B5),
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor   = Color.White,
                    unfocusedTextColor      = Color.DarkGray,
                    focusedTextColor        = Color.DarkGray,
                    cursorColor             = Color(0xFF3F51B5)
                ),
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Row(
                    modifier = Modifier.clickable { onFilterClick() }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Filter", color = if (hasActiveFilter) Color(0xFFAE2138) else Color.DarkGray, fontSize = 13.sp)
                    Icon(Icons.Default.Tune, null,
                        tint = if (hasActiveFilter) Color(0xFFAE2138) else Color.DarkGray,
                        modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(4.dp))

            error?.let {
                Text(it, color = Color.Red, fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF3F51B5))
                }
            } else if (customers.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Business, null, tint = Color.Gray,
                            modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("ไม่พบข้อมูลลูกค้า", color = Color.Gray, fontSize = 14.sp)
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 20.dp)
                    ) {
                        items(sortedCustomers, key = { it.custId }) { customer ->
                            CustomerListItem(customer = customer,
                                onClick = { onCustomerClick(customer.custId) })
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }

                    if (activeLetter != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-32).dp)
                                .size(44.dp)
                                .background(CustAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(activeLetter!!, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }

                    if (searchQuery.isBlank()) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(20.dp)
                                .padding(vertical = 8.dp)
                                .pointerInput(Unit) {
                                    awaitEachGesture {
                                        awaitFirstDown(requireUnconsumed = false)
                                        do {
                                            val event = awaitPointerEvent()
                                            val y = event.changes.firstOrNull()?.position?.y ?: 0f
                                            val idx = ((y / size.height) * CUST_ALL_SIDEBAR.size)
                                                .toInt().coerceIn(0, CUST_ALL_SIDEBAR.lastIndex)
                                            val letter = CUST_ALL_SIDEBAR.getOrNull(idx)
                                            if (letter != null && letter != activeLetter) {
                                                activeLetter = letter
                                                letterIndex[letter]?.let { itemIdx ->
                                                    scope.launch { listState.scrollToItem(itemIdx) }
                                                }
                                            }
                                        } while (event.changes.any { it.pressed })
                                        activeLetter = null
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CUST_ALL_SIDEBAR.forEach { letter ->
                                Text(
                                    text = letter,
                                    fontSize = 10.sp,
                                    fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        letter == activeLetter -> CustAccent
                                        letterIndex.containsKey(letter) -> CustTextGray
                                        else -> CustTextGray.copy(alpha = 0.3f)
                                    },
                                    textAlign = TextAlign.Center,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerListItem(customer: Customer, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(52.dp).clip(CircleShape)
                        .background(avatarColorFor(customer.companyName)), 
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = customer.companyName.stripThaiPrefix().take(1).uppercase(),
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        customer.companyName,
                        color = Color.DarkGray,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!customer.branch.isNullOrBlank()) {
                        Text(
                            "สาขา: ${customer.branch}",
                            color = Color.Gray,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypeTag(customer.custType)
                BizGroupBadge(customer.branchId)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CustomerListScreenPreview() {
    val sampleCustomers = listOf(
        Customer(
            custId = "C001",
            companyName = "บริษัท ตัวอย่าง จำกัด",
            branch = "สำนักงานใหญ่",
            custType = "Main Constructor",
            companyAddr = "123 BKK",
            companyLat = 0.0,
            companyLong = 0.0,
            companyStatus = 1,
            createdAt = "2024-01-01"
        ),
        Customer(
            custId = "C002",
            companyName = "Example Corp",
            branch = "Bangkok",
            custType = "Developer",
            companyAddr = null,
            companyLat = 0.0,
            companyLong = 0.0,
            companyStatus = 0,
            createdAt = null
        )
    )
    SalesTrackingTheme {
        CustomerListContent(
            customers = sampleCustomers, isLoading = false,
            searchQuery = "", error = null, authUser = null,
            hasActiveFilter = false,
            onSearchChange = {}, onFilterClick = {},
            onCustomerClick = {}, onAddClick = {}, onNotificationClick = {},
            onSettingsClick = {}, onLogoutClick = {},
            currentTab = 1, onTabChange = {}
        )
    }
}

// ─── Customer Filter Modal ────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerFilterModal(
    selectedBizGroup: String?,
    selectedCustType: String?,
    onBizGroupToggle: (String) -> Unit,
    onCustTypeToggle: (String) -> Unit,
    onReset:   () -> Unit,
    onDismiss: () -> Unit
) {
    val bizGroups = listOf("R" to "Retail", "W" to "Wholesale", "I" to "Industry", "P" to "Project")
    val custTypes = listOf("Owner", "Developer", "Main Constructor", "Sub Constructor", "Installer")

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Color.White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Filter", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.DarkGray)
                Text("Reset", color = Color(0xFFAE2138), fontSize = 14.sp,
                    modifier = Modifier.clickable { onReset(); onDismiss() })
            }
            Spacer(Modifier.height(20.dp))

            Text("กลุ่มธุรกิจ", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.DarkGray)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                bizGroups.forEach { (code, label) ->
                    CustomerFilterTag(label, isSelected = selectedBizGroup == code) { onBizGroupToggle(code) }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("ประเภทลูกค้า", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.DarkGray)
            Spacer(Modifier.height(12.dp))
            FlowRowCustomer(mainAxisSpacing = 8.dp, crossAxisSpacing = 8.dp) {
                custTypes.forEach { type ->
                    CustomerFilterTag(type, isSelected = selectedCustType == type) { onCustTypeToggle(type) }
                }
            }
        }
    }
}

@Composable
private fun CustomerFilterTag(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val red = Color(0xFFAE2138)
    Surface(
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) red else Color(0xFFE0E0E0)),
        color  = if (isSelected) red.copy(alpha = 0.1f) else Color.White,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = if (isSelected) red else Color.DarkGray,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
private fun FlowRowCustomer(mainAxisSpacing: androidx.compose.ui.unit.Dp, crossAxisSpacing: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var cur = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var curW = 0
        placeables.forEach { p ->
            if (curW + p.width + mainAxisSpacing.roundToPx() > constraints.maxWidth && cur.isNotEmpty()) {
                rows.add(cur); cur = mutableListOf(); curW = 0
            }
            cur.add(p); curW += p.width + mainAxisSpacing.roundToPx()
        }
        rows.add(cur)
        val h = rows.sumOf { r -> r.maxOf { it.height } } + (rows.size - 1) * crossAxisSpacing.roundToPx()
        layout(constraints.maxWidth, h) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                row.forEach { p -> p.placeRelative(x, y); x += p.width + mainAxisSpacing.roundToPx() }
                y += row.maxOf { it.height } + crossAxisSpacing.roundToPx()
            }
        }
    }
}
