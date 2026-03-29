package com.example.pp68_salestrackingapp.ui.screen.customer

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.data.model.Customer
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.customer.CustomerDetailViewModel

@Composable
fun CustomerDetailScreen(
    custId: String,
    onBack: () -> Unit,
    onEditCustomer: (String) -> Unit = {},
    onEditContact: (String) -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onTabChange: (Int) -> Unit = {},
    viewModel: CustomerDetailViewModel = hiltViewModel()
) {
    val customer by viewModel.customer.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val activeProjects by viewModel.activeProjects.collectAsState()
    val closedProjects by viewModel.closedProjects.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(custId) { viewModel.load(custId) }

    CustomerDetailScreenContent(
        customer = customer,
        contacts = contacts,
        activeProjects = activeProjects,
        closedProjects = closedProjects,
        isLoading = isLoading,
        onBack = onBack,
        onEditCustomer = onEditCustomer,
        onEditContact = onEditContact,
        onProjectClick = onProjectClick,
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onLogoutClick = {
            viewModel.logout()
            onLogoutClick()
        },
        onTabChange = onTabChange
    )
}

@Composable
fun CustomerDetailScreenContent(
    customer: Customer?,
    contacts: List<ContactPerson>,
    activeProjects: List<Project>,
    closedProjects: List<Project>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onEditCustomer: (String) -> Unit = {},
    onEditContact: (String) -> Unit = {},
    onProjectClick: (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onTabChange: (Int) -> Unit = {},
) {
    var selectedContact by remember { mutableStateOf<ContactPerson?>(null) }

    CustomerDetailContent(
        customer = customer,
        contacts = contacts,
        activeProjects = activeProjects,
        closedProjects = closedProjects,
        isLoading = isLoading,
        onBack = onBack,
        onEditCustomer = onEditCustomer,
        onEditContact = onEditContact,
        onProjectClick = onProjectClick,
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onLogoutClick = onLogoutClick,
        onTabChange = onTabChange,
        onContactClick = { contact -> selectedContact = contact }
    )

    if (selectedContact != null) {
        ContactDetailOverlay(
            contact = selectedContact!!,
            onDismiss = { selectedContact = null }
        )
    }
}

@Composable
fun CustomerDetailContent(
    customer: Customer?,
    contacts: List<ContactPerson>,
    activeProjects: List<Project>,
    closedProjects: List<Project>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onEditCustomer: (String) -> Unit,
    onEditContact: (String) -> Unit,
    onProjectClick: (String) -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    onTabChange: (Int) -> Unit,
    onContactClick: (ContactPerson) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Info", "Contact", "History")

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Customer Details",
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick
            )
        },
        bottomBar = { BottomNavBar(currentTab = 1, onTabChange = onTabChange) },
        containerColor = BgLight
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)) {
            // Header Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RedPrimary)
                    .padding(16.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = White)
                }
                Row(
                    modifier = Modifier.padding(top = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(White.copy(0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(customer?.companyName ?: "Loading...", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Branch: ${customer?.branch ?: "-"}", color = White.copy(0.8f), fontSize = 12.sp)
                    }
                    IconButton(onClick = { customer?.let { onEditCustomer(it.custId) } }) {
                        Icon(Icons.Default.Edit, "Edit", tint = White)
                    }
                }
            }

            // Tabs
            TabRow(selectedTabIndex = selectedTab, containerColor = White, contentColor = RedPrimary) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            } else {
                when (selectedTab) {
                    0 -> InfoTab(customer, activeProjects, onProjectClick)
                    1 -> ContactTab(contacts, onContactClick, onEditContact)
                    2 -> HistoryTab(closedProjects, onProjectClick)
                }
            }
        }
    }
}

@Composable
private fun InfoTab(customer: Customer?, projects: List<Project>, onProjectClick: (String) -> Unit) {
    LazyColumn(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = White)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Business Type: ${customer?.custType ?: "-"}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(customer?.companyAddr ?: "No address info")
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)); Text("Active Projects", fontWeight = FontWeight.Bold) }
        items(projects) { project ->
            ProjectMiniCard(project) { onProjectClick(project.projectId) }
        }
    }
}

@Composable
private fun ProjectMiniCard(project: Project, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Work, null, tint = RedPrimary)
            Spacer(Modifier.width(12.dp))
            Text(project.projectName, modifier = Modifier.weight(1f))
            ProjectStatusBadge(project.projectStatus ?: "N/A")
        }
    }
}

@Composable
private fun ContactTab(contacts: List<ContactPerson>, onContactClick: (ContactPerson) -> Unit, onEdit: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(contacts, key = { it.contactId }) { contact ->
            ContactCard(
                contact = contact,
                onClick = { onContactClick(contact) },
                onEdit = { onEdit(contact.contactId) }
            )
        }
    }
}

@Composable
private fun ContactCard(contact: ContactPerson, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(AccentIndigo.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text(
                        text = (contact.fullName ?: "?").take(1).uppercase(),
                        color = AccentIndigo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.fullName ?: "Unknown Name", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(contact.position ?: "No position", color = TextGray, fontSize = 13.sp)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = TextGray) }
            }
            
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color.LightGray.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ContactSmallItem(Icons.Default.Phone, contact.phoneNumber)
                ContactSmallItem(Icons.Default.Email, contact.email)
            }
        }
    }
}

@Composable
private fun ContactSmallItem(icon: ImageVector, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(14.dp), tint = TextGray)
        Spacer(Modifier.width(4.dp))
        Text(
            text = value ?: "-",
            fontSize = 12.sp,
            color = TextGray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ContactDetailOverlay(contact: ContactPerson, onDismiss: () -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(AccentIndigo.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                    Text((contact.fullName ?: "?").take(1).uppercase(), color = AccentIndigo, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                }
                Spacer(Modifier.height(16.dp))
                Text(contact.fullName ?: "Unknown Name", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(contact.position ?: "-", color = TextGray, fontSize = 14.sp)
                
                Spacer(Modifier.height(24.dp))
                
                DetailRow(Icons.Default.Phone, "Phone", contact.phoneNumber ?: "-") {
                    contact.phoneNumber?.let {
                        clipboardManager?.setText(AnnotatedString(it))
                        Toast.makeText(context, "Copied Phone Number", Toast.LENGTH_SHORT).show()
                    }
                }
                DetailRow(Icons.Default.Email, "Email", contact.email ?: "-") {
                    contact.email?.let {
                        clipboardManager?.setText(AnnotatedString(it))
                        Toast.makeText(context, "Copied Email", Toast.LENGTH_SHORT).show()
                    }
                }
                DetailRow(Icons.Default.Chat, "Line ID", contact.line ?: "-") {
                    contact.line?.let {
                        clipboardManager?.setText(AnnotatedString(it))
                        Toast.makeText(context, "Copied Line ID", Toast.LENGTH_SHORT).show()
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = AccentIndigo)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 12.sp, color = TextGray)
            Text(value, fontSize = 15.sp, color = TextDark, fontWeight = FontWeight.Medium)
        }
        if (value != "-") {
            IconButton(onClick = onCopy) {
                Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp), tint = AccentIndigo)
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

@Composable
private fun HistoryTab(projects: List<Project>, onProjectClick: (String) -> Unit) {
    if (projects.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No history found") }
    } else {
        LazyColumn(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)) {
            items(projects) { project -> ProjectMiniCard(project) { onProjectClick(project.projectId) } }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun CustomerDetailScreenPreview() {
    val sampleCustomer = Customer(
        custId = "C001",
        companyName = "Example Corp",
        branch = "Bangkok",
        custType = "Developer",
        companyAddr = "123 Sukhumvit Road",
        companyLat = 13.7563,
        companyLong = 100.5018,
        companyStatus = "customer",
        firstCustomerDate = "2024-01-01"
    )

    val sampleContacts = listOf(
        ContactPerson(
            contactId = "CP001",
            custId = "C001",
            fullName = "John Doe",
            nickname = "John",
            position = "Purchasing Manager",
            phoneNumber = "0812345678",
            email = "john@example.com",
            line = "johndoe",
            isActive = true,
            isDmConfirmed = true
        )
    )

    val sampleProjects = listOf(
        Project(
            projectId = "P001",
            custId = "C001",
            branchId = "B001",
            projectNumber = "PJ-001",
            projectName = "Project Alpha",
            expectedValue = 1000000.0,
            projectStatus = "Active",
            startDate = "2024-01-01",
            closingDate = null,
            desiredCompletionDate = "2024-12-31",
            projectLat = 13.7563,
            projectLong = 100.5018,
            opportunityScore = "Hot"
        )
    )

    SalesTrackingTheme {
        CustomerDetailScreenContent(
            customer = sampleCustomer,
            contacts = sampleContacts,
            activeProjects = sampleProjects,
            closedProjects = emptyList(),
            isLoading = false,
            onBack = {},
            onEditCustomer = {},
            onEditContact = {},
            onProjectClick = {},
            onNotificationClick = {},
            onSettingsClick = {},
            onLogoutClick = {},
            onTabChange = {}
        )
    }
}
