package com.example.pp68_salestrackingapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Brand Colors (ใช้ทั่วทั้งแอป) ────────────────────────────
object AppColors {
    val Primary         = Color(0xFFAD1F36)   // แดงหลัก
    val PrimaryDark     = Color(0xFF8B1528)   // แดงเข้ม (pressed)
    val PrimaryLight    = Color(0xFFF9E4E7)   // แดงอ่อน (background chip)

    val BgWhite         = Color(0xFFFFFFFF)   // พื้นหลังหลัก
    val BgGray          = Color(0xFFF5F5F5)   // พื้นหลังรอง
    val BgCard          = Color(0xFFFFFFFF)   // การ์ด

    val TextPrimary     = Color(0xFF1A1A1A)   // ตัวหนังสือหลัก
    val TextSecondary   = Color(0xFF6B7280)   // ตัวหนังสือรอง
    val TextHint        = Color(0xFF9CA3AF)   // placeholder

    val Border          = Color(0xFFE5E7EB)   // เส้นขอบ
    val Divider         = Color(0xFFF3F4F6)   // เส้นคั่น

    val Success         = Color(0xFF10B981)   // สีเขียว (สำเร็จ)
    val Warning         = Color(0xFFF59E0B)   // สีเหลือง (เตือน)
    val Error           = Color(0xFFDC2626)   // สีแดง (ผิดพลาด)

    // Opportunity Score
    val Hot             = Color(0xFFAD1F36)   // Hot = แดง (ใช้สีหลักเลย)
    val Warm            = Color(0xFFF59E0B)   // Warm = เหลือง
    val Cold            = Color(0xFF6B7280)   // Cold = เทา
}

private val LightColorScheme = lightColorScheme(
    primary          = AppColors.Primary,
    onPrimary        = Color.White,
    primaryContainer = AppColors.PrimaryLight,
    background       = AppColors.BgWhite,
    surface          = AppColors.BgWhite,
    onBackground     = AppColors.TextPrimary,
    onSurface        = AppColors.TextPrimary,
    error            = AppColors.Error,
    outline          = AppColors.Border
)

@Composable
fun SalesTrackingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content     = content
    )
}