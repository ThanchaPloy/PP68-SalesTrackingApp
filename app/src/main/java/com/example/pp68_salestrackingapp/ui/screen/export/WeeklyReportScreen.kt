package com.example.pp68_salestrackingapp.ui.screen.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import com.example.pp68_salestrackingapp.utils.formatPhotoUrl
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.pp68_salestrackingapp.ui.theme.SalesTrackingTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.apache.poi.ss.usermodel.*
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import android.graphics.Bitmap
import android.graphics.BitmapFactory

private val RedReport = Color(0xFFAE2138)

@Composable
fun WeeklyReportScreen(
    onBack: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val s by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadWeeklyData(LocalDate.now().minusDays(6), LocalDate.now())
    }

    WeeklyReportContent(
        state = s,
        onBack = onBack,
        onDateRangeSelected = { start, end -> viewModel.loadWeeklyData(start, end) }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyReportContent(
    state: ExportUiState,
    onBack: () -> Unit,
    onDateRangeSelected: (LocalDate, LocalDate) -> Unit
) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val snackbarState = remember { SnackbarHostState() }

    var excelLoading  by remember { mutableStateOf(false) }
    var pdfLoading    by remember { mutableStateOf(false) }
    val isAnyExporting = excelLoading || pdfLoading

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = state.startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        initialSelectedEndDateMillis = state.endDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    )

    fun doExport(
        setLoading: (Boolean) -> Unit,
        label: String,
        block: suspend () -> Unit
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

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val startMillis = datePickerState.selectedStartDateMillis
                    val endMillis = datePickerState.selectedEndDateMillis
                    if (startMillis != null && endMillis != null) {
                        val startDate = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        val endDate = Instant.ofEpochMilli(endMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                        onDateRangeSelected(startDate, endDate)
                    }
                    showDatePicker = false
                }) { Text("ตกลง", color = RedReport) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("ยกเลิก") }
            }
        ) {
            DateRangePicker(
                state = datePickerState,
                modifier = Modifier.weight(1f)
            )
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

            // â”€â”€ Top bar â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                        Icon(Icons.Default.ArrowBack, null,
                            tint = Color(0xFF1A1A1A))
                    }
                    Text("Back", fontSize = 14.sp, color = Color(0xFF1A1A1A))

                    Spacer(Modifier.weight(1f))

                    if (state.activities.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                            // â”€â”€ Excel button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
                            ExportButton(
                                label     = "Excel",
                                color     = Color(0xFF1E6B38),
                                isLoading = excelLoading,
                                enabled   = !isAnyExporting,
                                onClick   = {
                                    doExport(
                                        setLoading = { excelLoading = it },
                                        label      = "Excel"
                                    ) {
                                        exportToExcel(
                                            context,
                                            "Weekly_Report_${state.startDate}_to_${state.endDate}",
                                            state.activities
                                        )
                                    }
                                }
                            )

                            // â”€â”€ PDF button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                                            "Weekly_Report_${state.startDate}_to_${state.endDate}",
                                            state.activities
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // â”€â”€ Date Selector â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.CalendarToday, null, tint = RedReport, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "\u0e2a\u0e31\u0e1b\u0e14\u0e32\u0e2b\u0e4c\u0e17\u0e35\u0e48\u0e40\u0e25\u0e37\u0e2d\u0e01", // สัปดาห์ที่เลือก
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = state.weekRangeText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = RedReport
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Edit, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                }
            }

            // â”€â”€ Content â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                        Text("ไม่มีแผนงานหรือผลการทำงานในช่วงสัปดาห์นี้", color = Color.Gray)
                        TextButton(onClick = { showDatePicker = true }) {
                            Text("เลือกสัปดาห์อื่น", color = RedReport)
                        }
                    }
                }

                else -> LazyColumn(
                    modifier        = Modifier.fillMaxSize(),
                    contentPadding  = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "สรุปกิจกรรม (${state.activities.size} รายการ)",
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

// â”€â”€ Shared export button â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
            Text("...", fontSize = 12.sp)
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

// â”€â”€ Activity card â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
@Composable
fun ReportActivityCard(item: ExportActivityItem) {
    var previewPhotoUrl by remember { mutableStateOf<String?>(null) }

    previewPhotoUrl?.let { url ->
        ImagePreviewDialog(imageUrl = url, onDismiss = { previewPhotoUrl = null })
    }

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
                if (!item.activityType.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFE3F2FD)
                    ) {
                        Text(
                            item.activityType.uppercase(),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color    = Color(0xFF1565C0)
                        )
                    }
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
                        .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Text(item.note, fontSize = 12.sp, color = Color.DarkGray, maxLines = 5)
                }
            }

            // —— บันทึกผลหลังการขาย (Results & Photos) ————————
            if (item.resultDetails.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("บันทึกผลการทำงาน / หลังการขาย:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF424242))
                item.resultDetails.forEach { detail ->
                    Spacer(Modifier.height(6.dp))
                    PostSalesResultDetailCard(detail = detail, onPhotoClick = { previewPhotoUrl = detail.photoUrls.firstOrNull()?.let { formatPhotoUrl(it) } ?: "" })
                }
            } else if (item.results.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("บันทึกผลการทำงาน:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF424242))
                item.results.forEach { res ->
                    Row(modifier = Modifier.padding(top = 4.dp, start = 4.dp)) {
                        Text("• ", fontSize = 12.sp, color = Color.Gray)
                        Text(res, fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
        }
    }
}

@Composable
private fun PostSalesResultDetailCard(
    detail: ExportResultDetail,
    onPhotoClick: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8F9FA),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Badges row
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!detail.newStatus.isNullOrBlank()) {
                    item { DetailChip(label = "\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e43\u0e2b\u0e21\u0e48: ${detail.newStatus}", color = Color(0xFFE3F2FD), textColor = Color(0xFF1565C0)) } // สถานะใหม่
                }
                if (!detail.opportunityScore.isNullOrBlank()) {
                    item { DetailChip(label = "\u0e42\u0e2d\u0e01\u0e32\u0e2a: ${detail.opportunityScore}", color = Color(0xFFFFF8E1), textColor = Color(0xFFF57F17)) } // โอกาส
                }
                if (!detail.dealPosition.isNullOrBlank()) {
                    item { DetailChip(label = "\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e14\u0e35\u0e25: ${detail.dealPosition}", color = Color(0xFFEDE7F6), textColor = Color(0xFF512DA8)) } // สถานะดีล
                }
                if (detail.isProposalSent) {
                    item { DetailChip(label = "\u0e43\u0e1a\u0e40\u0e2a\u0e19\u0e2d\u0e23\u0e32\u0e04\u0e32: \u0e2a\u0e48\u0e07\u0e41\u0e25\u0e49\u0077 (${detail.proposalDate ?: ""})", color = Color(0xFFE8F5E9), textColor = Color(0xFF2E7D32)) } // ใบเสนอราคา: ส่งแล้ว
                }
                if (detail.dmInvolved) {
                    item { DetailChip(label = "DM \u0e23\u0e48\u0e27\u0e21\u0e1b\u0e23\u0e30\u0e0a\u0e38\u0e21", color = Color(0xFFE0F7FA), textColor = Color(0xFF00838F)) } // ร่วมประชุม
                }
                if (detail.competitorCount > 0) {
                    item { DetailChip(label = "\u0e04\u0e39\u0e48\u0e41\u0e02\u0e48\u0e07: ${detail.competitorCount} \u0e23\u0e32\u0e22", color = Color(0xFFFFF3E0), textColor = Color(0xFFE65100)) } // คู่แข่ง: ... ราย
                }
                if (!detail.responseSpeed.isNullOrBlank()) {
                    item { DetailChip(label = "ตอบสนอง: ${detail.responseSpeed}", color = Color(0xFFF3E5F5), textColor = Color(0xFF6A1B9A)) }
                }
                if (!detail.previousSolution.isNullOrBlank()) {
                    item { DetailChip(label = "โซลูชั่นเดิม: ${detail.previousSolution}", color = Color(0xFFECEFF1), textColor = Color(0xFF37474F)) }
                }
                if (!detail.lossReason.isNullOrBlank()) {
                    item { DetailChip(label = "เหตุผลแพ้: ${detail.lossReason}", color = Color(0xFFFFEBEE), textColor = Color(0xFFC62828)) }
                }
            }

            if (!detail.summary.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = detail.summary,
                    fontSize = 12.sp,
                    color = Color(0xFF333333)
                )
            }

            // Photos gallery
            if (detail.photoUrls.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("รูปถ่ายยืนยัน (${detail.photoUrls.size} รูป):", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(detail.photoUrls) { photoUrl ->
                        val formattedUrl = formatPhotoUrl(photoUrl)
                        AsyncImage(
                            model = formattedUrl,
                            contentDescription = "รูปถ่ายบันทึกหลังการขาย",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                                .clickable { onPhotoClick(formattedUrl) },
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailChip(label: String, color: Color, textColor: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}


@Composable
fun ImagePreviewDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.Black
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "ปิด", tint = Color.White)
                    }
                }
                AsyncImage(
                    model = formatPhotoUrl(imageUrl),
                    contentDescription = "ดูรูปใหญ่",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 450.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private fun wrapTextLines(text: String, paint: Paint, maxWidth: Float): List<String> {
    if (text.isBlank()) return emptyList()
    val lines = mutableListOf<String>()
    val rawLines = text.split("\n")
    for (raw in rawLines) {
        if (raw.isBlank()) continue
        val words = raw.split(" ")
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                if (currentLine.isNotEmpty()) currentLine.append(" ")
                currentLine.append(word)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder()
                }
                // If single word exceeds maxWidth (like long URLs), break character by character
                if (paint.measureText(word) > maxWidth) {
                    var charLine = StringBuilder()
                    for (ch in word) {
                        if (paint.measureText(charLine.toString() + ch) > maxWidth) {
                            lines.add(charLine.toString())
                            charLine = StringBuilder(ch.toString())
                        } else {
                            charLine.append(ch)
                        }
                    }
                    if (charLine.isNotEmpty()) currentLine = charLine
                } else {
                    currentLine.append(word)
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
    }
    return if (lines.isEmpty()) listOf(text) else lines
}

// â”€â”€ Export functions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

private suspend fun getPhotoBytes(context: Context, photoUrl: String): ByteArray? {
    val formattedUrl = formatPhotoUrl(photoUrl)
    return try {
        if (formattedUrl.startsWith("file://") || formattedUrl.startsWith("/storage/") || formattedUrl.startsWith("/data/")) {
            val filePath = formattedUrl.removePrefix("file://")
            val file = File(filePath)
            if (file.exists()) {
                withContext(Dispatchers.IO) { file.readBytes() }
            } else null
        } else if (formattedUrl.startsWith("content://")) {
            val uri = android.net.Uri.parse(formattedUrl)
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        } else {
            // For remote URLs, fetch bytes directly
            withContext(Dispatchers.IO) {
                val url = java.net.URL(formattedUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.doInput = true
                connection.connect()
                if (connection.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { it.readBytes() }
                } else {
                    null
                }
            }
        }
    } catch (e: Exception) {
        null
    }
}

suspend fun exportToExcel(context: Context, fileName: String, activities: List<ExportActivityItem>) {
    withContext(Dispatchers.IO) {
        val workbook: Workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Activities")

        val headerStyle = workbook.createCellStyle().apply {
            fillForegroundColor = IndexedColors.GREY_25_PERCENT.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            setFont(workbook.createFont().apply { bold = true })
            verticalAlignment = VerticalAlignment.CENTER
        }
        val baseCellStyle = workbook.createCellStyle().apply {
            verticalAlignment = VerticalAlignment.CENTER
            wrapText = true
        }
        val statusCompletedStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(baseCellStyle)
            fillForegroundColor = IndexedColors.LIGHT_GREEN.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }
        val statusPendingStyle = workbook.createCellStyle().apply {
            cloneStyleFrom(baseCellStyle)
            fillForegroundColor = IndexedColors.LIGHT_YELLOW.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
        }

        val headers = listOf(
            "\u0e27\u0e31\u0e19\u0e17\u0e35\u0e48 (Date)", "\u0e1a\u0e23\u0e34\u0e29\u0e31\u0e17 (Company)", "\u0e42\u0e04\u0e23\u0e07\u0e01\u0e32\u0e23 (Project)", "\u0e2b\u0e31\u0e27\u0e02\u0e49\u0e2d (Topic)",
            "\u0e2a\u0e16\u0e32\u0e19\u0e30 (Status)", "\u0e1b\u0e23\u0e30\u0e40\u0e20\u0e17 (Type)", "\u0e40\u0e0a\u0e47\u0e04\u0e2d\u0e34\u0e19 (Check-in)", "\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e43\u0e2b\u0e21\u0e48 (New Status)",
            "\u0e42\u0e2d\u0e01\u0e32\u0e2a (Opportunity)", "\u0e43\u0e1a\u0e40\u0e2a\u0e19\u0e2d\u0e23\u0e32\u0e04\u0e32 (Proposal)", "\u0e27\u0e31\u0e19\u0e17\u0e35\u0e48\u0e40\u0e2a\u0e19\u0e2d\u0e23\u0e32\u0e04\u0e32 (Proposal Date)", "DM \u0e23\u0e48\u0e27\u0e21\u0e1b\u0e23\u0e30\u0e0a\u0e38\u0e21 (DM Involved)", 
            "\u0e08\u0e33\u0e19\u0e27\u0e19\u0e04\u0e39\u0e48\u0e41\u0e02\u0e48\u0e07 (CompetitorCount)", "\u0e04\u0e27\u0e32\u0e21\u0e40\u0e23\u0e47\u0e27 (Speed)", "\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e14\u0e35\u0e25 (Deal)", "\u0e42\u0e0b\u0e25\u0e39\u0e0a\u0e31\u0e48\u0e19\u0e40\u0e14\u0e34\u0e21 (Solution)",
            "\u0e40\u0e2b\u0e15\u0e38\u0e1c\u0e25\u0e41\u0e1e\u0e49 (Loss)", "\u0e2a\u0e23\u0e38\u0e1b\u0e1c\u0e25\u0e01\u0e32\u0e23\u0e17\u0e33\u0e07\u0e32\u0e19 (Summary)", "\u0e1c\u0e39\u0e49\u0e15\u0e34\u0e14\u0e15\u0e48\u0e2d (Contact)", "\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e40\u0e0a\u0e47\u0e04\u0e2d\u0e34\u0e19 (Check-in Status)", "\u0e2a\u0e16\u0e32\u0e19\u0e17\u0e35\u0e48\u0e19\u0e31\u0e14\u0e2b\u0e21\u0e32\u0e22 (Location Name)", "\u0e23\u0e39\u0e1b\u0e20\u0e32\u0e1e (Photos)"
        )
        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, title ->
            val cell = headerRow.createCell(i)
            cell.setCellValue(title)
            cell.cellStyle = headerStyle
            sheet.setColumnWidth(i, 4000)
        }
        sheet.setColumnWidth(17, 8000) // summary column wider
        sheet.setColumnWidth(21, 6000) // photos column wider

        val drawing = sheet.createDrawingPatriarch()
        val creationHelper = workbook.creationHelper
        var rowNum = 1

        // Track max character counts for manual auto-fit
        val maxChars = IntArray(22) { 10 } // Start with min width of 10 chars
        headers.forEachIndexed { i, h -> maxChars[i] = maxOf(maxChars[i], h.length) }
        var maxPhotosInAnyRow = 0

        for (item in activities) {
            val isCompleted = item.status.equals("completed", ignoreCase = true)
            
            if (item.resultDetails.isNotEmpty()) {
                for (res in item.resultDetails) {
                    val row = sheet.createRow(rowNum)
                    if (res.photoUrls.isNotEmpty()) {
                        row.height = (120 * 15).toShort()
                        maxPhotosInAnyRow = maxOf(maxPhotosInAnyRow, res.photoUrls.size)
                    }
                    
                    val values = listOf(
                        item.date,
                        item.companyName ?: "",
                        item.projectName ?: "",
                        item.topic ?: "",
                        item.status,
                        item.activityType?.uppercase() ?: "N/A",
                        if (item.activityType == "onsite" && item.checkInTime != null) { try { java.time.Instant.parse(item.checkInTime).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm")) } catch(e: Exception) { item.checkInTime.take(16).replace("T", " ") } } else "",
                        res.newStatus ?: "",
                        res.opportunityScore ?: "",
                        if (res.isProposalSent) "ส่งแล้ว" else "ยังไม่ส่ง",
                        res.proposalDate ?: "",
                        if (res.dmInvolved) "มี" else "ไม่มี",
                        res.competitorCount.toString(),
                        res.responseSpeed ?: "",
                        res.dealPosition ?: "",
                        res.previousSolution ?: "",
                        res.lossReason ?: "",
                        res.summary ?: "",
                        item.contactName ?: "",
                        item.checkInStatus ?: "",
                        item.locationName ?: ""
                    )

                    values.forEachIndexed { i, v ->
                        row.createCell(i).apply {
                            setCellValue(v)
                            cellStyle = if (i == 4) (if (isCompleted) statusCompletedStyle else statusPendingStyle) else baseCellStyle
                        }
                        // Update max chars (handle newlines)
                        v.split("\n").forEach { line -> maxChars[i] = maxOf(maxChars[i], line.length) }
                    }
                    
                    row.createCell(21).apply { this.cellStyle = baseCellStyle }

                    if (res.photoUrls.isNotEmpty()) {
                        var colOffset = 0
                        for (url in res.photoUrls) {
                            val bytes = getPhotoBytes(context, url)
                            if (bytes != null) {
                                val pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_JPEG)
                                val anchor = creationHelper.createClientAnchor()
                                anchor.setCol1(21) // Fixed to col 21
                                anchor.setRow1(rowNum)
                                anchor.setCol2(21)
                                anchor.setRow2(rowNum)
                                
                                val emuPerPx = 9525
                                val imgSizePx = 100
                                val paddingPx = 10
                                anchor.dx1 = (colOffset * (imgSizePx + paddingPx) + paddingPx) * emuPerPx
                                anchor.dy1 = paddingPx * emuPerPx
                                anchor.dx2 = anchor.dx1 + (imgSizePx * emuPerPx)
                                anchor.dy2 = anchor.dy1 + (imgSizePx * emuPerPx)
                                drawing.createPicture(anchor, pictureIdx)
                                colOffset++
                            }
                        }
                    }
                    rowNum++
                }
            } else {
                val row = sheet.createRow(rowNum)
                val resultsStr = item.results.joinToString("\n")
                val values = listOf(
                    item.date,
                    item.companyName ?: "",
                    item.projectName ?: "",
                    item.topic ?: "",
                    item.status,
                    item.activityType?.uppercase() ?: "N/A",
                    if (item.activityType == "onsite" && item.checkInTime != null) { try { java.time.Instant.parse(item.checkInTime).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm")) } catch(e: Exception) { item.checkInTime.take(16).replace("T", " ") } } else "",
                    "", "", "ยังไม่ส่ง", "", "ไม่มี", "0", "", "", "", "",
                    resultsStr,
                    item.contactName ?: "",
                    item.checkInStatus ?: "",
                    item.locationName ?: ""
                )
                values.forEachIndexed { i, v ->
                    row.createCell(i).apply {
                        setCellValue(v)
                        cellStyle = if (i == 4) (if (isCompleted) statusCompletedStyle else statusPendingStyle) else baseCellStyle
                    }
                    v.split("\n").forEach { line -> maxChars[i] = maxOf(maxChars[i], line.length) }
                }
                row.createCell(21).apply { this.cellStyle = baseCellStyle }
                rowNum++
            }
        }

        // Apply Manual Auto-fit
        for (i in 0..20) {
            // Width in units of 1/256th of a character width
            // Multiplier 256 * 1.2 to give some breathing room, max width 255 chars
            val width = minOf(255, (maxChars[i] * 1.3).toInt()) * 256
            sheet.setColumnWidth(i, width)
        }
        // Special handling for photo column
        if (maxPhotosInAnyRow > 0) {
            // Each photo is ~100px + padding. 1px is approx 36.5 units in POI
            val photoColWidth = (maxPhotosInAnyRow * 110 + 20) * 37
            sheet.setColumnWidth(21, photoColWidth)
        } else {
            sheet.setColumnWidth(21, 4000)
        }

        val file = File(context.cacheDir, "$fileName.xlsx")
        FileOutputStream(file).use { out ->
            workbook.write(out)
        }
        workbook.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        
        // Launch intent on main thread
        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Excel Report"))
        }
    }
}

suspend fun exportToPdf(context: Context, fileName: String, activities: List<ExportActivityItem>) {
    val doc         = PdfDocument()
    val paint       = Paint()
    val titlePaint  = Paint().apply { textSize = 18f; isFakeBoldText = true }
    val headerPaint = Paint().apply { textSize = 11f; isFakeBoldText = true }
    val bodyPaint   = Paint().apply { textSize = 10f; isFakeBoldText = true }
    val subPaint    = Paint().apply { textSize = 9f; color = android.graphics.Color.DKGRAY }
    val resultPaint = Paint().apply { textSize = 9f; color = android.graphics.Color.BLACK }
    val linkPaint   = Paint().apply {
        textSize = 9f
        color = android.graphics.Color.parseColor("#1A73E8")
        isUnderlineText = true
    }

    var pageNum  = 1
    var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
    var page     = doc.startPage(pageInfo)
    var canvas: Canvas = page.canvas
    var y = 50f

    val checkPageBreak: (Float) -> Unit = { neededHeight ->
        if (y + neededHeight > 780f) {
            doc.finishPage(page)
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNum).create()
            page     = doc.startPage(pageInfo)
            canvas   = page.canvas
            y        = 50f
        }
    }

    canvas.drawText("Weekly Performance Report", 50f, y, titlePaint); y += 35f
    canvas.drawText("Date",    50f,  y, headerPaint)
    canvas.drawText("Activity / Project Details", 150f, y, headerPaint)
    canvas.drawText("Status",  500f, y, headerPaint)
    y += 15f
    canvas.drawLine(50f, y, 550f, y, paint); y += 20f

    activities.forEach { item ->
        checkPageBreak(50f)
        
        // Date
        canvas.drawText(item.date.take(10), 50f, y, bodyPaint)
        
        // Status
        canvas.drawText(item.status, 500f, y, bodyPaint)

        // Topic (Wrapped)
        val topicLines = wrapTextLines("${item.topic ?: "N/A"} (${item.activityType?.uppercase() ?: "N/A"})", bodyPaint, 330f)
        topicLines.forEach { line ->
            checkPageBreak(14f)
            canvas.drawText(line, 150f, y, bodyPaint)
            y += 14f
        }
        
        // Company & Project Name (Wrapped)
        val projectComp = "${item.companyName ?: ""} (${item.projectName ?: ""})"
        val compLines = wrapTextLines(projectComp, subPaint, 330f)
        compLines.forEach { line ->
            checkPageBreak(13f)
            canvas.drawText(line, 150f, y, subPaint)
            y += 13f
        }

        if (item.activityType == "onsite" && item.checkInTime != null) {
            val timeFormatted = try {
                val instant = java.time.Instant.parse(item.checkInTime)
                instant.atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm"))
            } catch (e: Exception) { item.checkInTime.take(16).replace("T", " ") }
            checkPageBreak(13f)
            canvas.drawText("Check-in: $timeFormatted", 150f, y, subPaint)
            y += 13f
        }

        y += 4f

        // Results as bullets
        if (item.resultDetails.isNotEmpty()) {
            item.resultDetails.forEach { res ->
                val summaryText = res.summary ?: "N/A"
                val summaryLines = wrapTextLines("\u2022 \u0e2a\u0e23\u0e38\u0e1b\u0e1c\u0e25: $summaryText", resultPaint, 370f) // สรุปผล
                summaryLines.forEach { line ->
                    checkPageBreak(14f)
                    canvas.drawText(line, 160f, y, resultPaint)
                    y += 14f
                }

                val detailParts = mutableListOf<String>()
                if (!res.newStatus.isNullOrBlank()) detailParts.add("\u0e2a\u0e16\u0e32\u0e19\u0e30\u0e43\u0e2b\u0e21\u0e48: ${res.newStatus}") // สถานะใหม่
                if (!res.opportunityScore.isNullOrBlank()) detailParts.add("\u0e42\u0e2d\u0e01\u0e32\u0e2a: ${res.opportunityScore}%") // โอกาส
                if (res.isProposalSent) {
                    detailParts.add("proposal: \u0e43\u0e0a\u0e48 (${res.proposalDate ?: ""})") // ใช่
                } else {
                    detailParts.add("proposal: \u0e44\u0e21\u0e48\u0e43\u0e0a\u0e48") // ไม่ใช่
                }
                if (res.dmInvolved) detailParts.add("DM \u0e23\u0e48\u0e27\u0e21\u0e1b\u0e23\u0e30\u0e0a\u0e38\u0e21: \u0e21\u0e35") // มี
                if (res.competitorCount > 0) detailParts.add("\u0e08\u0e33\u0e19\u0e27\u0e19\u0e04\u0e39\u0e48\u0e41\u0e02\u0e48\u0e07: ${res.competitorCount} \u0e23\u0e32\u0e22") // จำนวนคู่แข่ง ... ราย
                if (!res.previousSolution.isNullOrBlank()) detailParts.add("\u0e42\u0e0b\u0e25\u0e39\u0e0a\u0e31\u0e48\u0e19\u0e40\u0e14\u0e34\u0e21: ${res.previousSolution}") // โซลูชั่นเดิม
                if (!res.lossReason.isNullOrBlank()) detailParts.add("\u0e40\u0e2b\u0e15\u0e38\u0e1c\u0e25\u0e41\u0e1e\u0e49: ${res.lossReason}") // เหตุผลแพ้

                if (detailParts.isNotEmpty()) {
                    detailParts.forEach { detail ->
                        val dLines = wrapTextLines("  - $detail", subPaint, 370f)
                        dLines.forEach { line ->
                            checkPageBreak(13f)
                            canvas.drawText(line, 160f, y, subPaint)
                            y += 13f
                        }
                    }
                }

                if (res.photoUrls.isNotEmpty()) {
                    checkPageBreak(14f)
                    canvas.drawText("\u0e23\u0e39\u0e1b\u0e20\u0e32\u0e1e\u0e41\u0e1a\u0e1a (${res.photoUrls.size} \u0e23\u0e39\u0e1b):", 160f, y, subPaint) // รูปภาพแนบ ... รูป
                    y += 14f

                    res.photoUrls.chunked(2).forEach { rowUrls ->
                        val rowItems = rowUrls.map { pUrl ->
                            val formattedUrl = formatPhotoUrl(pUrl)
                            val bytes = getPhotoBytes(context, pUrl)
                            val originalBitmap = bytes?.let {
                                BitmapFactory.decodeByteArray(it, 0, it.size)
                            }
                            Pair(formattedUrl, originalBitmap)
                        }

                        var rowMaxH = 0f
                        val renderItems = rowItems.map { (url, bitmap) ->
                            if (bitmap != null) {
                                val maxW = 190f
                                val maxH = 190f
                                val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
                                val finalW = (bitmap.width * scale).toInt()
                                val finalH = (bitmap.height * scale).toInt()
                                val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(bitmap, finalW, finalH, true)
                                rowMaxH = maxOf(rowMaxH, finalH.toFloat())
                                Triple(url, scaledBitmap, finalH.toFloat())
                            } else {
                                val pLines = wrapTextLines(url, linkPaint, 190f)
                                val textH = pLines.size * 12f
                                rowMaxH = maxOf(rowMaxH, textH)
                                Triple(url, null, textH)
                            }
                        }

                        checkPageBreak(rowMaxH + 10f)

                        var currentX = 160f
                        renderItems.forEach { (url, scaledBitmap, _) ->
                            if (scaledBitmap != null) {
                                canvas.drawBitmap(scaledBitmap, currentX, y, null)
                            } else {
                                val pLines = wrapTextLines(url, linkPaint, 190f)
                                var tempY = y + 12f
                                pLines.forEach { line ->
                                    canvas.drawText(line, currentX, tempY, linkPaint)
                                    tempY += 12f
                                }
                            }
                            currentX += 200f
                        }
                        y += rowMaxH + 10f
                    }
                }
                y += 4f
            }
        } else {
            item.results.forEach { res ->
                val resLines = wrapTextLines("• $res", resultPaint, 370f)
                resLines.forEach { line ->
                    checkPageBreak(14f)
                    canvas.drawText(line, 160f, y, resultPaint)
                    y += 14f
                }
            }
        }
        
        y += 6f
        canvas.drawLine(50f, y, 550f, y, Paint().apply { strokeWidth=0.5f; color=android.graphics.Color.LTGRAY })
        y += 14f
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
                        note = "Discuss about the project requirements.",
                        status = "completed",
                        results = listOf("ลูกค้าสนใจเพิ่ม Module A", "นัดคุยราคาต่ออาทิตย์หน้า"),
                        resultDetails = listOf(
                            ExportResultDetail(
                                summary = "ลูกค้าสนใจเพิ่ม Module A",
                                newStatus = "Quotation",
                                opportunityScore = "80%",
                                isProposalSent = true,
                                proposalDate = "2023-10-24",
                                dmInvolved = true,
                                photoUrls = listOf("https://example.com/photo1.jpg")
                            )
                        )
                    )
                ),
                startDate = LocalDate.now().minusDays(6),
                endDate = LocalDate.now(),
                weekRangeText = "23 ต.ค. 2023 - 29 ต.ค. 2023"
            ),
            onBack = {},
            onDateRangeSelected = { _, _ -> }
        )
    }
}


