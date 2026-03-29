package com.example.pp68_salestrackingapp.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.components.AppTopBar
import com.example.pp68_salestrackingapp.ui.components.BottomNavBar
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme

// ── Colors ────────────────────────────────────────────────────
private val White        = Color.White
private val TextDark     = Color(0xFF1A1A1A)
private val TextGray     = Color(0xFF888888)
private val RedPrimary   = Color(0xFFCC1D1D)
private val RedDark      = Color(0xFF8B0000)
private val BgLight      = Color(0xFFF5F5F5)
private val CardPink     = Color(0xFFFFF0F0)

// pipeline bar gradient — เข้มสุดที่ Lead/Quotation ไล่ลงจาง
private val pipelineColors = listOf(
    "Lead"             to Color(0xFFB71C1C),
    "New Project"      to Color(0xFFC62828),
    "Quotation"        to Color(0xFFD32F2F),
    "Bidding"          to Color(0xFFE53935),
    "Make a Decision"  to Color(0xFFEF5350),
    "Assured"          to Color(0xFFE57373),
    "PO"               to Color(0xFFEF9A9A),
    "Completed"        to Color(0xFFFFCDD2),
    "Lost"             to Color(0xFFBDBDBD),
    "Failed"           to Color(0xFFE0E0E0)
)

private val oppColors = mapOf(
    "HOT"  to Triple(Color(0xFFE53935), Color(0xFFFFEBEE), "🔴"),
    "WARM" to Triple(Color(0xFFF57C00), Color(0xFFFFF3E0), "🟡"),
    "COLD" to Triple(Color(0xFF1976D2), Color(0xFFE3F2FD), "🔵")
)

// ═══════════════════════════════════════════════════════════════
@Composable
fun StatScreen(
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    currentTab:  Int,
    onTabChange: (Int) -> Unit,
    viewModel:   StatsViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    StatScreenContent(
        s = s,
        onNotificationClick = onNotificationClick,
        onSettingsClick = onSettingsClick,
        onExportClick = onExportClick,
        currentTab = currentTab,
        onTabChange = onTabChange
    )
}

@Composable
fun StatScreenContent(
    s: StatsUiState,
    onNotificationClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    currentTab: Int,
    onTabChange: (Int) -> Unit
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Personal Dashboard",
                onNotificationClick = onNotificationClick,
                onSettingsClick = onSettingsClick
            )
        },
        bottomBar = { BottomNavBar(currentTab = currentTab, onTabChange = onTabChange) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onExportClick,
                containerColor = RedPrimary,
                contentColor = White,
                icon = { Icon(Icons.Default.FileDownload, null) },
                text = { Text("Export Report") }
            )
        },
        containerColor = BgLight
    ) { padding ->
        if (s.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RedPrimary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── TOTAL OVERVIEW (New) ──────────────────────────
            SectionBlock(label = "Total Overview") {
                StatCard(
                    value    = formatValue(s.totalProjectValue),
                    label    = "Grand Total Project Value",
                    icon     = Icons.Default.Public,
                    modifier = Modifier.fillMaxWidth(),
                    valueFontSize = 32
                )
            }

            // ── WEEKLY ────────────────────────────────────────
            SectionBlock(label = "Weekly") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("${s.weeklyNewLeads}",   "New Leads",          Icons.Default.PersonAdd, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(4.dp))
                // Visit count full width
                VisitCountCard(count = s.weeklyVisitCount)
            }

            // ── MONTHLY ───────────────────────────────────────
            SectionBlock(label = "Monthly") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        value    = formatValue(s.monthlyClosedSales),
                        label    = "Total Closed Sales",
                        icon     = Icons.Default.AttachMoney,
                        modifier = Modifier.weight(1f),
                        valueFontSize = 20
                    )
                    StatCard("${s.monthlyNewLeads}", "New Leads", Icons.Default.PersonAdd, Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard("${s.activeProjects}",     "Active Projects",          Icons.Default.Pending,    Modifier.weight(1f))
                    StatCard("${s.closingThisMonth}", "Closing this Month", Icons.Default.Event, Modifier.weight(1f))
                }
            }

            // ── PROJECT PIPELINE STATUS ───────────────────────
            SectionCard(title = "Project Pipeline Status") {
                val maxCount = s.pipelineStages.maxOfOrNull { it.count }?.takeIf { it > 0 } ?: 1
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.pipelineStages.forEach { stage ->
                        PipelineBar(
                            stage    = stage,
                            maxCount = maxCount
                        )
                    }
                }
            }

            // ── OPPORTUNITY OVERVIEW ──────────────────────────
            SectionCard(title = "Opportunity Overview") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    s.opportunityGroups.forEach { group ->
                        OpportunityRow(group = group)
                    }
                    if (s.opportunityGroups.all { it.count == 0 }) {
                        Text("ยังไม่มีโครงการที่ระบุ Opportunity Score",
                            color = TextGray, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(80.dp))
        }
    }
}

// ── Section with red pill header ─────────────────────────────
@Composable
private fun SectionBlock(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape  = RoundedCornerShape(20.dp),
                color  = RedDark
            ) {
                Text(label, color = White, fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp))
            }
        }
        content()
    }
}

// ── Section card with title ───────────────────────────────────
@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextDark)
            content()
        }
    }
}

// ── Stat card ────────────────────────────────────────────────
@Composable
private fun StatCard(
    value:         String,
    label:         String,
    icon:          ImageVector,
    modifier:      Modifier = Modifier,
    valueFontSize: Int = 28
) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = CardPink,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = RedPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(value,
                    fontWeight = FontWeight.Bold,
                    fontSize   = valueFontSize.sp,
                    color      = TextDark)
            }
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 12.sp, color = TextGray, lineHeight = 16.sp)
        }
    }
}

// ── Visit count card — full width ────────────────────────────
@Composable
private fun VisitCountCard(count: Int) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = CardPink,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("$count", fontWeight = FontWeight.Bold, fontSize = 28.sp, color = TextDark)
                Text("Visit this week", fontSize = 12.sp, color = TextGray)
            }
            Icon(Icons.Default.DirectionsWalk, null,
                tint = RedPrimary, modifier = Modifier.size(36.dp))
        }
    }
}

// ── Pipeline bar ─────────────────────────────────────────────
@Composable
private fun PipelineBar(stage: PipelineStageCount, maxCount: Int) {
    val barColor = pipelineColors.find { it.first == stage.stage }?.second
        ?: Color(0xFFEF9A9A)
    val fraction = stage.count.toFloat() / maxCount.toFloat()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5))
        ) {
            // fill bar
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.05f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(barColor)
            )
            // label
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stage.stage, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (fraction > 0.3f) White else TextDark)
                Text("${stage.count}", fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (fraction > 0.3f) White else TextDark)
            }
        }
    }
}

// ── Opportunity row ───────────────────────────────────────────
@Composable
private fun OpportunityRow(group: OpportunityGroup) {
    val (textColor, bgColor, emoji) = oppColors[group.score]
        ?: Triple(TextGray, BgLight, "⚪")

    Surface(
        shape    = RoundedCornerShape(10.dp),
        color    = bgColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // color dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(textColor)
                )
                Text(group.score, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, color = textColor)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("${group.count} โครงการ",
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextDark)
                    Text(formatValue(group.totalValue),
                        fontSize = 12.sp, color = textColor, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ── Formatter ─────────────────────────────────────────────────
private fun formatValue(value: Double): String = when {
    value >= 1_000_000 -> "฿${"%.1f".format(value / 1_000_000)}M"
    value >= 1_000     -> "฿${"%.0f".format(value / 1_000)}k"
    value > 0          -> "฿${"%.0f".format(value)}"
    else               -> "฿0"
}

@Preview(showBackground = true)
@Composable
fun StatScreenPreview() {
    SalesTrackingTheme {
        StatScreenContent(
            s = StatsUiState(
                weeklyNewLeads = 5,
                weeklyNewProjects = 2,
                weeklyVisitCount = 8,
                monthlyClosedSales = 2500000.0,
                totalProjectValue = 15000000.0,
                monthlyNewLeads = 20,
                activeProjects = 15,
                closingThisMonth = 3,
                pipelineStages = listOf(
                    PipelineStageCount("Lead", 10, 1000000.0),
                    PipelineStageCount("Quotation", 5, 2000000.0),
                    PipelineStageCount("PO", 2, 5000000.0)
                ),
                opportunityGroups = listOf(
                    OpportunityGroup("HOT", 3, 3000000.0),
                    OpportunityGroup("WARM", 5, 2000000.0),
                    OpportunityGroup("COLD", 7, 1000000.0)
                )
            ),
            currentTab = 2,
            onTabChange = {}
        )
    }
}