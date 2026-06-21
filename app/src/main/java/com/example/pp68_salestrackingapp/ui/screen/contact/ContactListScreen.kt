package com.example.pp68_salestrackingapp.ui.screen.contact

import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.ContactPerson
import com.example.pp68_salestrackingapp.ui.components.AddFloatingActionButton
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import com.example.pp68_salestrackingapp.ui.viewmodels.contact.ContactListViewModel
import kotlinx.coroutines.launch

private val BgLight      = Color(0xFFF5F5F5)
private val TextDark     = Color(0xFF1A1A1A)
private val TextGray     = Color(0xFF888888)
private val AccentIndigo = Color(0xFFF5406A)

private fun String.stripThaiPrefix(): String {
    val prefixes = listOf(
        "นางสาว ", "นาง ", "นาย ", "ด.ช. ", "ด.ญ. ", "คุณ ",
        "บริษัท ", "ห้างหุ้นส่วนจำกัด ", "ห้างหุ้นส่วน ", "หจก. ", "บจก. ", "กิจการร่วมค้า "
    )
    val t = this.trim()
    return prefixes.firstOrNull { t.startsWith(it) }?.let { t.removePrefix(it) } ?: t
}

private val THAI_CONSONANTS = listOf(
    "ก","ข","ค","ง","จ","ฉ","ช","ซ","ญ","ด",
    "ต","ถ","ท","น","บ","ป","ผ","ฝ","พ","ฟ",
    "ภ","ม","ย","ร","ล","ว","ส","ห","อ","ฮ"
)
private val ALL_SIDEBAR_LETTERS =
    listOf("#") + THAI_CONSONANTS + ('A'..'Z').map { it.toString() }

@Composable
fun ContactListScreen(
    onAddClick: () -> Unit = {},
    onEditClick: (String) -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    currentTab: Int,
    onTabChange: (Int) -> Unit,
    viewModel: ContactListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedContact by remember { mutableStateOf<ContactPerson?>(null) }

    ContactListScreenContent(
        contacts = uiState.contacts,
        isLoading = uiState.isLoading,
        searchQuery = uiState.searchQuery,
        error = uiState.error,
        authUser = uiState.authUser,
        onSearchChange = viewModel::onSearchChange,
        onContactClick = { contact -> selectedContact = contact },
        onAddClick = onAddClick,
        onEditClick = onEditClick,
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onLogoutClick = {
            viewModel.logout()
            onLogoutClick()
        },
        currentTab = currentTab,
        onTabChange = onTabChange
    )

    if (selectedContact != null) {
        ContactDetailOverlay(contact = selectedContact!!, onDismiss = { selectedContact = null })
    }
}

@Composable
fun ContactListScreenContent(
    contacts: List<ContactPerson>,
    isLoading: Boolean,
    searchQuery: String,
    error: String?,
    authUser: AuthUser?,
    onSearchChange: (String) -> Unit,
    onContactClick: (ContactPerson) -> Unit,
    onAddClick: () -> Unit,
    onEditClick: (String) -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLogoutClick: () -> Unit,
    currentTab: Int,
    onTabChange: (Int) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sortedContacts = remember(contacts) {
        contacts.sortedBy { (it.fullName ?: "").stripThaiPrefix() }
    }
    val letterIndex = remember(sortedContacts) {
        val map = linkedMapOf<String, Int>()
        sortedContacts.forEachIndexed { i, c ->
            val ch = (c.fullName ?: "").stripThaiPrefix().firstOrNull()?.toString() ?: "#"
            if (!map.containsKey(ch)) map[ch] = i
        }
        map
    }

    var activeLetter by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Contact Person",
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick,
                user = authUser
            )
        },
        bottomBar = { BottomNavBar(currentTab = currentTab, onTabChange = onTabChange) },
        floatingActionButton = { AddFloatingActionButton(onClick = onAddClick, contentDescription = "เพิ่มผู้ติดต่อ") },
        containerColor = BgLight
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery, onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("ค้นหาผู้ติดต่อ...") },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize().padding(end = 20.dp)
                    ) {
                        items(sortedContacts, key = { it.contactId }) { contact ->
                            ContactCard(
                                contact = contact,
                                onClick = { onContactClick(contact) },
                                onEdit = { onEditClick(contact.contactId) }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }

                    // Letter bubble popup while dragging
                    if (activeLetter != null) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-32).dp)
                                .size(44.dp)
                                .background(AccentIndigo, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                activeLetter!!,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
                        }
                    }

                    // Alphabetical index sidebar
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
                                            val idx = ((y / size.height) * ALL_SIDEBAR_LETTERS.size)
                                                .toInt().coerceIn(0, ALL_SIDEBAR_LETTERS.lastIndex)
                                            val letter = ALL_SIDEBAR_LETTERS.getOrNull(idx)
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
                            ALL_SIDEBAR_LETTERS.forEach { letter ->
                                Text(
                                    text = letter,
                                    fontSize = 10.sp,
                                    fontWeight = if (letter == activeLetter) FontWeight.Bold else FontWeight.Normal,
                                    color = when {
                                        letter == activeLetter -> AccentIndigo
                                        letterIndex.containsKey(letter) -> TextGray
                                        else -> TextGray.copy(alpha = 0.3f)
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
private fun ContactCard(contact: ContactPerson, onClick: () -> Unit, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(AccentIndigo.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (contact.fullName ?: "?").stripThaiPrefix().take(1).uppercase(),
                        color = AccentIndigo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.fullName ?: "ไม่ระบุชื่อ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(contact.position ?: "ไม่มีตำแหน่ง", color = TextGray, fontSize = 13.sp)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp), tint = TextGray)
                }
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
                Box(
                    modifier = Modifier.size(64.dp).clip(CircleShape)
                        .background(AccentIndigo.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (contact.fullName ?: "?").stripThaiPrefix().take(1).uppercase(),
                        color = AccentIndigo, fontWeight = FontWeight.Bold, fontSize = 24.sp
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(contact.fullName ?: "ไม่ระบุชื่อ", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(contact.position ?: "-", color = TextGray, fontSize = 14.sp)

                Spacer(Modifier.height(24.dp))

                DetailRow(Icons.Default.Phone, "เบอร์โทรศัพท์", contact.phoneNumber ?: "-") {
                    contact.phoneNumber?.let {
                        clipboardManager?.setText(AnnotatedString(it))
                        Toast.makeText(context, "คัดลอกเบอร์โทรศัพท์แล้ว", Toast.LENGTH_SHORT).show()
                    }
                }
                DetailRow(Icons.Default.Email, "อีเมล", contact.email ?: "-") {
                    contact.email?.let {
                        clipboardManager?.setText(AnnotatedString(it))
                        Toast.makeText(context, "คัดลอกอีเมลแล้ว", Toast.LENGTH_SHORT).show()
                    }
                }
                DetailRow(Icons.Default.Chat, "Line ID", contact.line ?: "-") {
                    contact.line?.let {
                        clipboardManager?.setText(AnnotatedString(it))
                        Toast.makeText(context, "คัดลอก Line ID แล้ว", Toast.LENGTH_SHORT).show()
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) { Text("ปิด") }
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

@Preview(showBackground = true)
@Composable
fun ContactListScreenPreview() {
    SalesTrackingTheme {
        ContactListScreenContent(
            contacts = listOf(
                ContactPerson(contactId = "1", custId = "C001", fullName = "กิจการร่วมค้า ABC", phoneNumber = "081-234-5678"),
                ContactPerson(contactId = "2", custId = "C002", fullName = "คุณสมชาย ใจดี", email = "somchai@example.com"),
                ContactPerson(contactId = "3", custId = "C003", fullName = "นายบพิตร ธนสาร", phoneNumber = "089-123-4567")
            ),
            isLoading = false, searchQuery = "", error = null, authUser = null,
            onSearchChange = {}, onContactClick = {}, onAddClick = {}, onEditClick = {},
            onNotificationClick = {}, onSettingsClick = {}, onLogoutClick = {},
            currentTab = 2, onTabChange = {}
        )
    }
}
