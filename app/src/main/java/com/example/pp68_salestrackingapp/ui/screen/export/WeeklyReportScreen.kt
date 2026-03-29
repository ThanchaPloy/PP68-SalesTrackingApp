package com.example.pp68_salestrackingapp.ui.screen.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

private val RedReport = Color(0xFFAE2138)

@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadWeeklyData(LocalDate.now())
    }

    WeeklyReportContent(
        state = s,
        onBack = onBack
    )
}

@Composable
fun WeeklyReportContent(
    state: ExportUiState,
    onBack: () -> Unit
) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    var csvLoading    by remember { mutableStateOf(false) }
    var pdfLoading    by remember { mutableStateOf(false) }
    val isAnyExporting = csvLoading || pdfLoading

    fun doExport(
        setLoading: (Boolean) -> Unit,
        label: String,
        block: () -> Unit
    ) {
        if (isAnyExporting) return
        scope.launch {
            setLoading(true)
            try {
                withContext(Dispatchers.IO) { block() }
                snackbarState.showSnackbar(
                    message  = "✅ ส่งออก $label เรียบร้อยแล้ว",
                    duration = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                snackbarState.showSnackbar(
                    message  = "❌ เกิดข้อผิดพลาด: ${e.message}",
                    duration = SnackbarDuration.Long
                )
            } finally {
                setLoading(false)
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarState) { data ->
                Snackbar(
                    snackbarData   = data,
                    shape          = RoundedCornerShape(12.dp),
                    containerColor = Color(0xFF1A1A1A),
                    contentColor   = Color.White,
                    modifier       = Modifier.padding(16.dp)
                )
            }
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Top bar ───────────────────────────────────────
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                color           = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        // เปลี่ยนจาก Icons.AutoMirrored.Filled.ArrowBack เป็น Icons.Default.ArrowBack
                        // เพื่อแก้ปัญหา NoClassDefFoundError: StrokeJoin$Companion ในการแสดง Preview
                        Icon(Icons.Default.ArrowBack, null,
                            tint = Color(0xFF1A1A1A))
                    }
                    Text("Back", fontSize = 14.sp, color = Color(0xFF1A1A1A))
                    Spacer(Modifier.weight(1f))

                    if (state.activities.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                            // ── CSV button ────────────────────
                            ExportButton(
                                label     = "CSV",
                                color     = Color(0xFF388E3C),
                                isLoading = csvLoading,
                                enabled   = !isAnyExporting,
                                onClick   = {
                                    doExport(
                                        setLoading = { csvLoading = it },
                                        label      = "CSV"
                                    ) {
                                        exportToCsv(
                                            context,
                                            "Weekly_Plan_${LocalDate.now()}",
                                            state.activities
                                        )
                                    }
                                }
                            )

                            // ── PDF button ────────────────────
                            ExportButton(
                                label     = "PDF",
                                color     = RedReport,
                                isLoading = pdfLoading,
                                enabled   = !isAnyExporting,
                                modifier  = Modifier.padding(end = 8.dp),
                                onClick   = {
                                    doExport(
                                        setLoading = { pdfLoading = it },
                                        label      = "PDF"
                                    ) {
                                        exportToPdf(
                                            context,
                                            "Weekly_Plan_${LocalDate.now()}",
                                            state.activities
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // ── Content ───────────────────────────────────────
            when {
                state.isLoading -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(color = RedReport) }

                state.activities.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.EventNote, null,
                            modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(8.dp))
                        Text("ไม่มีแผนงานในสัปดาห์นี้", color = Color.Gray)
                    }
                }

                else -> LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Weekly Plan Summary (${state.activities.size} items)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            color      = Color.Gray,
                            modifier   = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(state.activities) { item -> ReportActivityCard(item) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── Shared export button ──────────────────────────────────────
@Composable
private fun ExportButton(
    label:     String,
    color:     Color,
    isLoading: Boolean,
    enabled:   Boolean,
    modifier:  Modifier = Modifier,
    onClick:   () -> Unit
) {
    Button(
        onClick          = onClick,
        enabled          = enabled,
        colors           = ButtonDefaults.buttonColors(
            containerColor         = if (isLoading) color.copy(alpha = 0.7f) else color,
            disabledContainerColor = color.copy(alpha = 0.4f)
        ),
        contentPadding   = PaddingValues(horizontal = 14.dp),
        modifier         = modifier.height(36.dp),
        shape            = RoundedCornerShape(8.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color       = Color.White,
                modifier    = Modifier.size(14.dp),
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(6.dp))
            Text("กำลังส่งออก...", fontSize = 12.sp)
        } else {
            Icon(
                imageVector        = if (label == "PDF") Icons.Default.PictureAsPdf
                else Icons.Default.TableChart,
                contentDescription = null,
                modifier           = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Activity card (ไม่เปลี่ยน) ────────────────────────────────
@Composable
fun ReportActivityCard(item: ExportActivityItem) {
    Surface(
        shape           = RoundedCornerShape(12.dp),
        color           = Color.White,
        shadowElevation = 1.dp,
        modifier        = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(item.date, fontSize = 12.sp, color = Color.Gray,
                    fontWeight = FontWeight.Medium)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (item.status == "completed") Color(0xFFE8F5E9)
                    else Color(0xFFFFF3E0)
                ) {
                    Text(
                        item.status.uppercase(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        color    = if (item.status == "completed") Color(0xFF2E7D32)
                        else Color(0xFFE65100)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(item.topic ?: "ไม่มีหัวข้อ",
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1A1A1A))

            if (!item.companyName.isNullOrBlank())
                Text(item.companyName, fontSize = 13.sp, color = Color(0xFF1976D2))

            if (!item.projectName.isNullOrBlank())
                Text("Project: ${item.projectName}", fontSize = 13.sp, color = Color.Gray)

            if (!item.note.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF9F9F9), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(item.note, fontSize = 12.sp, color = Color.DarkGray, maxLines = 3)
                }
            }
        }
    }
}

// ── Export functions (ไม่เปลี่ยน logic) ──────────────────────
fun exportToCsv(context: Context, fileName: String, activities: List<ExportActivityItem>) {
    val header  = "Date,Company,Project,Topic,Status,Note\n"
    val content = activities.joinToString("\n") {
        val note = it.note?.replace(",", ";")?.replace("\n", " ") ?: ""
        "\"${it.date}\"," +
                "\"${it.companyName?.replace(",", ";") ?: ""}\"," +
                "\"${it.projectName?.replace(",", ";") ?: ""}\"," +
                "\"${it.topic?.replace(",", ";") ?: ""}\"," +
                "\"${it.status}\"," +
                "\"$note\""
    }
    val file = File(context.cacheDir, "$fileName.csv")
    file.writeText(header + content, Charsets.UTF_8)

    val uri    = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share CSV Report"))
}

fun exportToPdf(context: Context, fileName: String, activities: List<ExportActivityItem>) {
    val doc         = PdfDocument()
    val paint       = Paint()
    val titlePaint  = Paint().apply { textSize = 20f; isFakeBoldText = true }
    val headerPaint = Paint().apply { textSize = 12f; isFakeBoldText = true }
    val bodyPaint   = Paint().apply { textSize = 12f }
    val notePaint   = Paint().apply { textSize = 10f; color = android.graphics.Color.GRAY }

    var pageNum  = 1
    var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
    var page     = doc.startPage(pageInfo)
    var canvas: Canvas = page.canvas
    var y = 50f

    canvas.drawText("Weekly Plan Report", 50f, y, titlePaint); y += 40f
    canvas.drawText("Date",    50f,  y, headerPaint)
    canvas.drawText("Company", 150f, y, headerPaint)
    canvas.drawText("Topic",   350f, y, headerPaint)
    canvas.drawText("Status",  500f, y, headerPaint)
    y += 20f
    canvas.drawLine(50f, y, 550f, y, paint); y += 25f

    activities.forEach { item ->
        if (y > 790) {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            page     = doc.startPage(pageInfo)
            canvas   = page.canvas
            y        = 50f
        }
        val company = item.companyName?.let { if (it.length > 20) it.take(17) + "..." else it } ?: "-"
        val topic   = item.topic?.let { if (it.length > 20) it.take(17) + "..." else it } ?: "-"
        canvas.drawText(item.date, 50f,  y, bodyPaint)
        canvas.drawText(company,   150f, y, bodyPaint)
        canvas.drawText(topic,     350f, y, bodyPaint)
        canvas.drawText(item.status, 500f, y, bodyPaint)
        y += 25f

        item.note?.takeIf { it.isNotBlank() }?.let { note ->
            val short = if (note.length > 80) note.take(77) + "..." else note
            canvas.drawText("Note: $short", 70f, y, notePaint)
            y += 20f
        }
    }
    doc.finishPage(page)

    val file = File(context.cacheDir, "$fileName.pdf")
    doc.writeTo(FileOutputStream(file))
    doc.close()

    val uri    = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share PDF Report"))
}

@Preview(showBackground = true)
@Composable
fun WeeklyReportPreview() {
    SalesTrackingTheme {
        WeeklyReportContent(
            state = ExportUiState(
                activities = listOf(
                    ExportActivityItem(
                        date = "2023-10-23",
                        projectName = "Project Alpha",
                        companyName = "Company A",
                        topic = "Meeting with client",
                        note = "Discuss about the project requirements and timeline.",
                        status = "completed"
                    ),
                    ExportActivityItem(
                        date = "2023-10-24",
                        projectName = "Project Beta",
                        companyName = "Company B",
                        topic = "Design Review",
                        note = "Review the initial design concepts.",
                        status = "pending"
                    )
                )
            ),
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeeklyReportEmptyPreview() {
    SalesTrackingTheme {
        WeeklyReportContent(
            state = ExportUiState(activities = emptyList()),
            onBack = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
fun WeeklyReportLoadingPreview() {
    SalesTrackingTheme {
        WeeklyReportContent(
            state = ExportUiState(isLoading = true),
            onBack = {}
        )
    }
}
