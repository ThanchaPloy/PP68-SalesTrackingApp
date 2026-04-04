package com.example.pp68_salestrackingapp.ui.screen.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RedReport = Color(0xFFAE2138)

@Composable
fun MonthlyReportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val s               by viewModel.uiState.collectAsState()
    val context         = LocalContext.current
    val scope           = rememberCoroutineScope()
    val snackbarState   = remember { SnackbarHostState() }

    var selectedMonth   by remember { mutableStateOf(YearMonth.now()) }
    var csvLoading      by remember { mutableStateOf(false) }
    var pdfLoading      by remember { mutableStateOf(false) }
    val isAnyExporting  = csvLoading || pdfLoading

    LaunchedEffect(selectedMonth) {
        viewModel.loadMonthlyData(selectedMonth)
    }

    // ── Export helpers ────────────────────────────────────────
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
                    message      = "✅ ส่งออก $label เรียบร้อยแล้ว",
                    duration     = SnackbarDuration.Short
                )
            } catch (e: Exception) {
                snackbarState.showSnackbar(
                    message      = "❌ เกิดข้อผิดพลาด: ${e.message}",
                    duration     = SnackbarDuration.Long
                )
            } finally {
                setLoading(false)
            }
        }
    }

    MonthlyReportContent(
        uiState = s,
        selectedMonth = selectedMonth,
        csvLoading = csvLoading,
        pdfLoading = pdfLoading,
        onBack = onBack,
        onMonthChange = { selectedMonth = it },
        onExportCsv = {
            doExport(
                setLoading = { csvLoading = it },
                label      = "CSV"
            ) {
                exportProjectsToCsv(
                    context,
                    "Monthly_Projects_$selectedMonth",
                    s.projects
                )
            }
        },
        onExportPdf = {
            doExport(
                setLoading = { pdfLoading = it },
                label      = "PDF"
            ) {
                exportProjectsToPdf(
                    context,
                    "Monthly_Report_$selectedMonth",
                    s.projects
                )
            }
        },
        snackbarState = snackbarState
    )
}

@Composable
fun MonthlyReportContent(
    uiState: ExportUiState,
    selectedMonth: YearMonth,
    csvLoading: Boolean,
    pdfLoading: Boolean,
    onBack: () -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    onExportCsv: () -> Unit,
    onExportPdf: () -> Unit,
    snackbarState: SnackbarHostState = remember { SnackbarHostState() }
) {
    val isAnyExporting = csvLoading || pdfLoading

    Scaffold(
        snackbarHost   = {
            SnackbarHost(snackbarState) { data ->
                Snackbar(
                    snackbarData    = data,
                    shape           = RoundedCornerShape(12.dp),
                    containerColor  = Color(0xFF1A1A1A),
                    contentColor    = Color.White,
                    modifier        = Modifier.padding(16.dp)
                )
            }
        },
        containerColor = Color(0xFFF5F5F5)
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // ── Top bar (Keep English navigation) ─────────────
            Surface(
                modifier        = Modifier.fillMaxWidth(),
                color           = Color.White,
                shadowElevation = 2.dp
            ) {
                Column {
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                                tint = Color(0xFF1A1A1A))
                        }
                        Text("Back", fontSize = 14.sp, color = Color(0xFF1A1A1A))
                        Spacer(Modifier.weight(1f))

                        // Export buttons
                        if (uiState.projects.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                                // ── CSV button ────────────────
                                ExportButton(
                                    label      = "CSV",
                                    color      = Color(0xFF388E3C),
                                    isLoading  = csvLoading,
                                    enabled    = !isAnyExporting,
                                    onClick    = onExportCsv
                                )

                                // ── PDF button ────────────────
                                ExportButton(
                                    label      = "PDF",
                                    color      = RedReport,
                                    isLoading  = pdfLoading,
                                    enabled    = !isAnyExporting,
                                    modifier   = Modifier.padding(end = 8.dp),
                                    onClick    = onExportPdf
                                )
                            }
                        }
                    }

                    // ── Month selector ────────────────────────
                    Row(
                        modifier              = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        val monthLabel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            selectedMonth.format(
                                DateTimeFormatter.ofPattern("MMMM yyyy", Locale("th", "TH"))
                            )
                        } else selectedMonth.toString()

                        Text(monthLabel, fontWeight = FontWeight.Bold,
                            fontSize = 20.sp, color = RedReport)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onMonthChange(selectedMonth.minusMonths(1)) }) {
                                Icon(Icons.Default.ChevronLeft, null, tint = RedReport)
                            }
                            IconButton(onClick = { onMonthChange(selectedMonth.plusMonths(1)) }) {
                                Icon(Icons.Default.ChevronRight, null, tint = RedReport)
                            }
                        }
                    }
                }
            }

            // ── Content ───────────────────────────────────────
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = RedReport)
                }

                uiState.projects.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Assignment, null,
                            modifier = Modifier.size(64.dp), tint = Color.LightGray)
                        Spacer(Modifier.height(8.dp))
                        Text("ไม่มีข้อมูลโครงการในเดือนนี้", color = Color.Gray)
                    }
                }

                else -> LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "สรุปโครงการ (${uiState.projects.size} รายการ)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 14.sp,
                            color      = Color.Gray,
                            modifier   = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    items(uiState.projects) { project -> ProjectReportCard(project) }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── Export button with loading ────────────────────────────────
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
                imageVector = if (label == "PDF") Icons.Default.PictureAsPdf
                else Icons.Default.TableChart,
                contentDescription = null,
                modifier    = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── Project card ─────────────────────────────────────────────
@Composable
fun ProjectReportCard(project: ExportProjectItem) {
    Surface(
        shape           = RoundedCornerShape(12.dp),
        color           = Color.White,
        shadowElevation = 1.dp,
        modifier        = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(project.projectName, fontWeight = FontWeight.Bold,
                    fontSize = 16.sp, color = Color(0xFF1A1A1A),
                    modifier = Modifier.weight(1f))
                project.score?.let { score ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (score.uppercase()) {
                            "HOT"  -> Color(0xFFFFEBEE)
                            "WARM" -> Color(0xFFFFF3E0)
                            else   -> Color(0xFFE3F2FD)
                        }
                    ) {
                        Text(score,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = when (score.uppercase()) {
                                "HOT"  -> Color(0xFFD32F2F)
                                "WARM" -> Color(0xFFF57C00)
                                else   -> Color(0xFF1976D2)
                            })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("มูลค่า", fontSize = 12.sp, color = Color.Gray)
                    Text("฿${"%,.0f".format(project.value)}",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        color = Color(0xFFAE2138))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("สถานะ", fontSize = 12.sp, color = Color.Gray)
                    Text(project.status, fontWeight = FontWeight.Medium,
                        fontSize = 14.sp, color = Color(0xFF1A1A1A))
                }
            }

            if (!project.closeDate.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EventAvailable, null,
                        modifier = Modifier.size(14.dp), tint = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text("คาดว่าจะปิด: ${project.closeDate}",
                        fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ── Export functions (No logic change) ────────────────────────
fun exportProjectsToCsv(context: Context, fileName: String, projects: List<ExportProjectItem>) {
    val header  = "Project Name,Status,Value,Opportunity,Expected Close\n"
    val content = projects.joinToString("\n") {
        "\"${it.projectName.replace(",", ";")}\",\"${it.status}\"," +
                "\"${it.value}\",\"${it.score ?: ""}\",\"${it.closeDate ?: ""}\""
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

fun exportProjectsToPdf(context: Context, fileName: String, projects: List<ExportProjectItem>) {
    val doc         = PdfDocument()
    val paint       = Paint()
    val titlePaint  = Paint().apply { textSize = 20f; isFakeBoldText = true }
    val headerPaint = Paint().apply { textSize = 14f; isFakeBoldText = true }
    val bodyPaint   = Paint().apply { textSize = 14f }

    var pageNum = 1
    var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
    var page     = doc.startPage(pageInfo)
    var canvas: Canvas = page.canvas
    var y = 50f

    canvas.drawText("Monthly Project Report", 50f, y, titlePaint); y += 40f
    canvas.drawText("Project Name", 50f, y, headerPaint)
    canvas.drawText("Status",       300f, y, headerPaint)
    canvas.drawText("Value (THB)",  450f, y, headerPaint)
    y += 20f
    canvas.drawLine(50f, y, 550f, y, paint); y += 25f

    projects.forEach { project ->
        if (y > 800) {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            page     = doc.startPage(pageInfo)
            canvas   = page.canvas
            y        = 50f
        }
        val name = project.projectName.let { if (it.length > 30) it.take(27) + "..." else it }
        canvas.drawText(name,                                    50f,  y, bodyPaint)
        canvas.drawText(project.status,                         300f,  y, bodyPaint)
        canvas.drawText("฿${"%,.0f".format(project.value)}",   450f,  y, bodyPaint)
        y += 25f
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
fun MonthlyReportScreenPreview() {
    SalesTrackingTheme {
        MonthlyReportContent(
            uiState = ExportUiState(
                projects = listOf(
                    ExportProjectItem(
                        projectName = "Project Alpha",
                        companyName = "Company A",
                        value = 1500000.0,
                        status = "In Progress",
                        score = "HOT",
                        closeDate = "2023-12-31"
                    ),
                    ExportProjectItem(
                        projectName = "Project Beta",
                        companyName = "Company B",
                        value = 500000.0,
                        status = "Qualified",
                        score = "WARM",
                        closeDate = "2024-01-15"
                    ),
                    ExportProjectItem(
                        projectName = "Project Gamma",
                        companyName = "Company C",
                        value = 2500000.0,
                        status = "Closed Won",
                        score = "COLD",
                        closeDate = "2023-11-20"
                    )
                )
            ),
            selectedMonth = YearMonth.now(),
            csvLoading = false,
            pdfLoading = false,
            onBack = {},
            onMonthChange = {},
            onExportCsv = {},
            onExportPdf = {}
        )
    }
}
