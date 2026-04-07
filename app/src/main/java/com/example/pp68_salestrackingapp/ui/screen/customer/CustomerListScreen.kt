package com.example.pp68_salestrackingapp.ui.screen.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Customer

import com.example.pp68_salestrackingapp.ui.components.AddFloatingActionButton
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

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
    val isLoading   by viewModel.isLoading.collectAsState(initial = false)
    val searchQuery by viewModel.searchQuery.collectAsState(initial = "")
    val error       by viewModel.error.collectAsState(initial = null)
    val authUser by viewModel.authUser.collectAsState(initial = null)

    CustomerListContent(
        customers = customers,
        isLoading = isLoading,
        searchQuery = searchQuery,
        error = error,
        authUser = authUser,
        onSearchChange = viewModel::onSearchChange,
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
}

@Composable
fun CustomerListContent(
    customers: List<Customer>,
    isLoading: Boolean,
    searchQuery: String,
    error: String?,
    authUser: AuthUser?,
    onSearchChange: (String) -> Unit,
    onCustomerClick: (String) -> Unit,
    onAddClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    currentTab: Int,
    onTabChange: (Int) -> Unit
) {
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
            Spacer(Modifier.height(16.dp))
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
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(customers, key = { it.custId }) { customer ->
                        CustomerListItem(customer = customer,
                            onClick = { onCustomerClick(customer.custId) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
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
                        text = customer.companyName.take(1).uppercase(),
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
                // ✅ ใช้ TypeTag และ StatusBadgeLight จาก CustomerColors.kt
                TypeTag(customer.custType)
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
            companyStatus = "customer",
            firstCustomerDate = "2024-01-01"
        ),
        Customer(
            custId = "C002",
            companyName = "Example Corp",
            branch = "Bangkok",
            custType = "Developer",
            companyAddr = null,
            companyLat = 0.0,
            companyLong = 0.0,
            companyStatus = "prospect",
            firstCustomerDate = null
        )
    )
    SalesTrackingTheme {
        CustomerListContent(
            customers = sampleCustomers, isLoading = false,
            searchQuery = "", error = null, authUser = null,
            onSearchChange = {}, onCustomerClick = {},
            onAddClick = {}, onNotificationClick = {},
            onSettingsClick = {}, onLogoutClick = {},
            currentTab = 1, onTabChange = {}
        )
    }
}
