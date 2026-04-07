package com.example.pp68_salestrackingapp.ui.screen.customer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Colors ───────────────────────────────────────────────────
internal val BgLight      = Color(0xFFF5F5F5)
internal val White        = Color.White
internal val TextDark     = Color(0xFF1A1A1A)
internal val TextGray     = Color(0xFF888888)
internal val DividerColor = Color(0xFFE8E8E8)
internal val RedPrimary   = Color(0xFFAE2138)
internal val RedFab       = Color(0xFFE53935)
internal val AccentIndigo = Color(0xFF6366F1)
internal val OrangeAvatar = Color(0xFFEA580C)

private val avatarColors = listOf(
    Color(0xFF7C3AED), Color(0xFF2563EB), Color(0xFFEA580C),
    Color(0xFF059669), Color(0xFFDB2777), Color(0xFF0891B2),
)

internal fun avatarColorFor(name: String): Color =
    avatarColors[(name.firstOrNull()?.code ?: 0) % avatarColors.size]

// ─── Customer Status Badge ────────────────────────────────────
@Composable
fun StatusBadgeLight(status: String?) {
    val (label, bg) = when (status) {
        "prospect" -> "Prospect"  to Color(0xFF22C55E)
        "new lead" -> "New Lead"  to Color(0xFF22C55E)
        "customer" -> "Customer"  to Color(0xFF374151)
        "inactive" -> "Inactive"  to Color(0xFF9CA3AF)
        else       -> (status ?: "-") to Color(0xFF9CA3AF)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(label, color = White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}

// ─── Customer Type Tag ────────────────────────────────────────
@Composable
fun TypeTag(type: String?) {
    if (type.isNullOrBlank()) return
    val color = when (type) {
        "Owner"            -> Color(0xFF7C3AED)
        "Developer"        -> Color(0xFF2563EB)
        "Main Constructor" -> Color(0xFFE53935)
        "Sub Constructor"  -> Color(0xFFEA580C)
        "Installer"        -> Color(0xFF059669)
        else               -> Color(0xFF6B7280)
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color) {
        Text(type, color = White, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

// ─── Project Status Badge ─────────────────────────────────────
@Composable
fun ProjectStatusBadge(status: String?) {
    val (bg, textColor) = when (status) {
        "New Project"        -> Color(0xFFE0E7FF) to Color(0xFF4338CA)
        "Quotation"          -> Color(0xFFFEF9C3) to Color(0xFF92400E)
        "Bidding"            -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
        "Make a Decision"    -> Color(0xFFFEF3C7) to Color(0xFFB45309)
        "Assured"            -> Color(0xFFD1FAE5) to Color(0xFF065F46)
        "Product Processing" -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
        "Working"            -> Color(0xFFDCFCE7) to Color(0xFF166534)
        "Quality Issue"      -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
        "Completed"          -> Color(0xFFF3F4F6) to Color(0xFF374151)
        "Lost"               -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        "Failed"             -> Color(0xFF1F2937) to Color(0xFF9CA3AF)
        else                 -> Color(0xFFF3F4F6) to Color(0xFF6B7280)
    }
    Surface(shape = RoundedCornerShape(20.dp), color = bg) {
        Text(status ?: "-", color = textColor, fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
    }
}