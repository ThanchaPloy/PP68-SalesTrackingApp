package com.example.pp68_salestrackingapp.ui.screen.project

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.data.model.AuthUser
import com.example.pp68_salestrackingapp.data.model.Project
import com.example.pp68_salestrackingapp.ui.components.AddFloatingActionButton
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.components.ProjectProgressBar
import com.example.pp68_salestrackingapp.ui.viewmodels.project.ProjectListViewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

private val BgLight    = Color(0xFFF5F5F5)
private val White      = Color.White
private val TextDark   = Color(0xFF1A1A1A)
private val TextGray   = Color(0xFF888888)
private val RedPrimary = Color(0xFFCC1D1D)
private val RedFab     = Color(0xFFE53935)

// ─── Project Status Badge ─────────────────────────────────────
@Composable
fun ProjectStatusBadge(status: String?) {
    val (bg, fg) = when (status) {
        "Lead"            -> Color(0xFFE8EAF6) to Color(0xFF283593)
        "New Project"     -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
        "Quotation"       -> Color(0xFFFEF9C3) to Color(0xFF92400E)
        "Bidding"         -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        "Make a Decision" -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        "Assured"         -> Color(0xFFD1FAE5) to Color(0xFF065F46)
        "Product Processing" -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        "Working"         -> Color(0xFFF0F9FF) to Color(0xFF0E7490)
        "Quality Issue"   -> Color(0xFFFFF7ED) to Color(0xFFC2410C)
        "PO"              -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        "Completed"       -> Color(0xFFF3F4F6) to Color(0xFF374151)
        "Lost"            -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        "Failed"          -> Color(0xFF1F2937) to Color(0xFF9CA3AF)
        else              -> Color(0xFFF3F4F6) to Color(0xFF6B7280)
    }
    val label = when (status) {
        "Lead"            -> "Lead"
        "New Project"     -> "New Project"
        "Quotation"       -> "Quotation"
        "Bidding"         -> "Bidding"
        "Make a Decision" -> "Decision"
        "Assured"         -> "Assured"
        "PO"              -> "PO"
        "Completed"       -> "Completed"
        "Lost"            -> "Lost"
        "Failed"          -> "Failed"
        else              -> status ?: "-"
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(label, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

// ─── HOT / COLD Badge ─────────────────────────────────────────
@Composable
fun HotColdBadge(score: String?) {
    val isHot  = score?.uppercase() == "HOT"
    val isWarm = score?.uppercase() == "WARM"
    val isCold = score?.uppercase() == "COLD"
    if (!isHot && !isWarm && !isCold) return
    val (bg, label) = when {
        isHot  -> Color(0xFFE53935) to "HOT"
        isWarm -> Color(0xFFF57C00) to "WARM"
        else   -> Color(0xFF60A5FA) to "COLD"
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(label, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

// ─── Opportunity % bar ────────────────────────────────────────
@Composable
fun OpportunityBar(score: Int, modifier: Modifier = Modifier) {
    val pct      = score.coerceIn(0, 100) / 100f
    val barColor = when {
        pct >= 0.7f -> Color(0xFF22C55E)
        pct >= 0.4f -> Color(0xFFF59E0B)
        else        -> Color(0xFFEF4444)
    }
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text("$score%", color = TextGray, fontSize = 12.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(4.dp)
            .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE5E7EB))
        ) {
            Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight()
                .clip(RoundedCornerShape(2.dp)).background(barColor))
        }
    }
}

private fun formatValue(value: Double?): String {
    if (value == null) return "-"
    return when {
        value >= 1_000_000 -> "฿${"%.1f".format(value / 1_000_000)}M"
        value >= 1_000     -> "฿${"%.0f".format(value / 1_000)}k"
        else               -> "฿${"%.0f".format(value)}"
    }
}

// ═══════════════════════════════════════════════════════════════
@Composable
fun ProjectListScreen(
    onProjectClick: (String) -> Unit = {},
    onAddClick: () -> Unit = {},
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    currentTab: Int = 3,
    onTabChange: (Int) -> Unit = {},
    viewModel: ProjectListViewModel = hiltViewModel()
) {
    val projects         by viewModel.projects.collectAsState()
    val isLoading        by viewModel.isLoading.collectAsState()
    val searchQuery      by viewModel.searchQuery.collectAsState()
    val selectedTabIndex by viewModel.selectedTabIndex.collectAsState()
    val error            by viewModel.error.collectAsState()
    val authUser         by viewModel.authUser.collectAsState()

    val selectedStatuses by viewModel.selectedStatuses.collectAsState()
    val selectedScores   by viewModel.selectedScores.collectAsState()

    var showFilterModal by remember { mutableStateOf(false) }

    ProjectListContent(
        projects = projects, isLoading = isLoading,
        searchQuery = searchQuery, selectedTabIndex = selectedTabIndex, error = error,
        authUser = authUser,
        selectedStatuses = selectedStatuses, selectedScores = selectedScores,
        onSearchChange = viewModel::onSearchChange, onSelectTab = viewModel::onSelectTab,
        onFilterClick = { showFilterModal = true },
        onProjectClick = onProjectClick, onAddClick = onAddClick,
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onLogoutClick = {
            viewModel.logout()
            onLogoutClick()
        },
        currentTab = currentTab, onTabChange = onTabChange
    )

    if (showFilterModal) {
        FilterModal(
            selectedStatuses = selectedStatuses,
            selectedScores   = selectedScores,
            onStatusToggle   = viewModel::toggleStatusFilter,
            onScoreToggle    = viewModel::toggleScoreFilter,
            onDismiss        = { showFilterModal = false },
            onReset          = viewModel::resetFilters
        )
    }
}

@Composable
fun ProjectListContent(
    projects: List<Project>, isLoading: Boolean,
    searchQuery: String, selectedTabIndex: Int, error: String?,
    authUser: AuthUser?,
    selectedStatuses: Set<String>, selectedScores: Set<String>,
    onSearchChange: (String) -> Unit, onSelectTab: (Int) -> Unit,
    onFilterClick: () -> Unit,
    onProjectClick: (String) -> Unit, onAddClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick:     () -> Unit,
    onLogoutClick:       () -> Unit,
    currentTab: Int, onTabChange: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Project",
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick,
                onLogoutClick = onLogoutClick,
                user = authUser
            )
        },
        bottomBar = { BottomNavBar(currentTab = currentTab, onTabChange = onTabChange) },
        floatingActionButton = {
            AddFloatingActionButton(onClick = onAddClick, contentDescription = "เพิ่มโครงการ")
        },
        containerColor = BgLight
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery, onValueChange = onSearchChange,
                placeholder = { Text("ค้นหาโครงการ...", color = TextGray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextGray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty())
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, null, tint = TextGray)
                        }
                },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent, focusedBorderColor = RedPrimary,
                    unfocusedContainerColor = White, focusedContainerColor = White,
                    unfocusedTextColor = TextDark, focusedTextColor = TextDark, cursorColor = RedPrimary
                ), singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(), 
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = listOf("Active", "Closed", "Inactive")
                tabs.forEachIndexed { index, label ->
                    val isSelected = selectedTabIndex == index
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) RedPrimary else Color(0xFFE5E7EB),
                        modifier = Modifier.clickable { onSelectTab(index) }
                    ) {
                        Text(
                            label,
                            color = if (isSelected) White else TextGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    if (index < tabs.size - 1) Spacer(Modifier.width(8.dp))
                }
                
                Spacer(Modifier.weight(1f))
                
                val hasFilter = selectedStatuses.isNotEmpty() || selectedScores.isNotEmpty()
                Row(modifier = Modifier.clickable { onFilterClick() }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Filter", color = if (hasFilter) RedPrimary else TextDark, fontSize = 13.sp)
                    Icon(Icons.Default.Tune, null, tint = if (hasFilter) RedPrimary else TextDark, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            error?.let { Text(it, color = RedFab, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RedPrimary)
                }
            } else if (projects.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.WorkOff, null, tint = TextGray, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(8.dp))
                        val emptyMsg = when (selectedTabIndex) {
                            1 -> "ไม่มีโครงการที่ปิดแล้ว"
                            2 -> "ไม่มีโครงการที่ไม่เคลื่อนไหว"
                            else -> "ไม่มีโครงการที่กำลังดำเนินการ"
                        }
                        Text(emptyMsg, color = TextGray, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(projects, key = { it.projectId }) { project ->
                        ProjectListItem(project = project, onClick = { onProjectClick(project.projectId) })
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ─── Filter Modal ─────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterModal(
    selectedStatuses: Set<String>,
    selectedScores:   Set<String>,
    onStatusToggle:   (String) -> Unit,
    onScoreToggle:    (String) -> Unit,
    onDismiss:        () -> Unit,
    onReset:          () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = White) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 40.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Filter", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = TextDark)
                Text("Reset", color = RedPrimary, fontSize = 14.sp, modifier = Modifier.clickable { onReset() })
            }
            Spacer(Modifier.height(20.dp))
            
            Text("Project Status", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextDark)
            Spacer(Modifier.height(12.dp))
            val statuses = listOf(
                "Lead", "New Project", "Quotation", "Bidding", 
                "Make a Decision", "Assured", "PO","Lost", "Failed"
            )
            FlowRow(mainAxisSpacing = 8.dp, crossAxisSpacing = 8.dp) {
                statuses.forEach { s ->
                    FilterTag(label = s, isSelected = s in selectedStatuses, onClick = { onStatusToggle(s) })
                }
            }
            
            Spacer(Modifier.height(24.dp))
            Text("Hot/Warm/Cold", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextDark)
            Spacer(Modifier.height(12.dp))
            val scores = listOf("Hot", "Warm", "Cold")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                scores.forEach { s ->
                    FilterTag(label = s, isSelected = s.uppercase() in selectedScores, onClick = { onScoreToggle(s) })
                }
            }
        }
    }
}

@Composable
fun FilterTag(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape   = RoundedCornerShape(20.dp),
        border  = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) RedPrimary else Color(0xFFE0E0E0)),
        color   = if (isSelected) RedPrimary.copy(alpha = 0.1f) else White,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = if (isSelected) RedPrimary else TextDark, 
            fontSize = 13.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

@Composable
fun FlowRow(
    mainAxisSpacing: Dp = 0.dp,
    crossAxisSpacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(content) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val rows = mutableListOf<List<androidx.compose.ui.layout.Placeable>>()
        var currentRow = mutableListOf<androidx.compose.ui.layout.Placeable>()
        var currentRowWidth = 0

        placeables.forEach { p ->
            if (currentRowWidth + p.width + mainAxisSpacing.roundToPx() > constraints.maxWidth && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0
            }
            currentRow.add(p)
            currentRowWidth += p.width + mainAxisSpacing.roundToPx()
        }
        rows.add(currentRow)

        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1) * crossAxisSpacing.roundToPx()
        layout(constraints.maxWidth, height) {
            var y = 0
            rows.forEach { row ->
                var x = 0
                row.forEach { p ->
                    p.place(x, y)
                    x += p.width + mainAxisSpacing.roundToPx()
                }
                y += row.maxOf { it.height } + crossAxisSpacing.roundToPx()
            }
        }
    }
}

// ─── Project List Item ────────────────────────────────────────
@Composable
fun ProjectListItem(project: Project, onClick: () -> Unit) {
    Surface(
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(16.dp),
        color           = White,
        shadowElevation = 2.dp,
        onClick         = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── Row 1: ชื่อ + มูลค่า ──────────────────────────
            Row(
                modifier            = Modifier.fillMaxWidth(),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.projectName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "เลขที่: ${project.projectNumber ?: "-"}",
                        fontSize = 12.sp,
                        color = TextGray
                    )
                }
                Text(
                    text = formatValue(project.expectedValue),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = RedPrimary
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── Row 2: Status + Hot/Cold ──────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ProjectStatusBadge(status = project.projectStatus)
                HotColdBadge(score = project.opportunityScore)
            }

            // ── แสดง Loss Reason ถ้าสถานะเป็น Lost หรือ Failed ──
            if ((project.projectStatus == "Lost" || project.projectStatus == "Failed") && !project.lossReason.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Color(0xFFFEE2E2).copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Info, null, tint = Color(0xFF991B1B), modifier = Modifier.size(14.dp))
                        Text(
                            text = "สาเหตุ: ${project.lossReason}",
                            fontSize = 11.sp,
                            color = Color(0xFF991B1B),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Row 3: Progress ──────────────────────────────
            ProjectProgressBar(
                progressPct = project.progressPct,
                status      = project.projectStatus
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProjectListScreenPreview() {
    val sampleProjects = listOf(
        Project(
            projectId = "1",
            custId = "C001",
            projectNumber = "PJ-001",
            projectName = "Office Building Renovation",
            expectedValue = 1500000.0,
            projectStatus = "New Project",
            opportunityScore = "HOT",
            progressPct = 10
        ),
        Project(
            projectId = "2",
            custId = "C002",
            projectNumber = "PJ-002",
            projectName = "New Condominium Project",
            expectedValue = 5000000.0,
            projectStatus = "Quotation",
            opportunityScore = "WARM",
            progressPct = 35
        ),
        Project(
            projectId = "3",
            custId = "C003",
            projectNumber = "PJ-003",
            projectName = "Small Shop Interior",
            expectedValue = 250000.0,
            projectStatus = "Completed",
            opportunityScore = "COLD",
            progressPct = 100
        )
    )
    val sampleUser = AuthUser(
        userId = "U001",
        email = "test@example.com",
        role = "Sales",
        fullName = "John Doe",
        branchName = "Main Branch"
    )

    SalesTrackingTheme {
        ProjectListContent(
            projects = sampleProjects,
            isLoading = false,
            searchQuery = "",
            selectedTabIndex = 0,
            error = null,
            authUser = sampleUser,
            selectedStatuses = emptySet(),
            selectedScores = emptySet(),
            onSearchChange = {},
            onSelectTab = {},
            onFilterClick = {},
            onProjectClick = {},
            onAddClick = {},
            onNotificationClick = {},
            onSettingsClick = {},
            onLogoutClick = {},
            currentTab = 3,
            onTabChange = {}
        )
    }
}
