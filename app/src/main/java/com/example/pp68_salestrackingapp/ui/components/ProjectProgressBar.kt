package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pp68_salestrackingapp.ui.utils.ProjectProgressUtils


@Composable
fun ProjectProgressBar(
    progressPct: Int?,     // ✅ รับจาก project.progressPct
    status:      String?,
    modifier:    Modifier = Modifier,
    showLabel:   Boolean  = true
) {
    val pct      = progressPct ?: 0
    val progress = pct / 100f
    val color    = ProjectProgressUtils.getProgressColor(status)
    val bgColor  = Color(0xFFEEEEEE)

    val animatedProgress by animateFloatAsState(
        targetValue   = progress,
        animationSpec = tween(durationMillis = 600),
        label         = "progress"
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text(
                    text       = status ?: "Unknown",
                    fontSize   = 12.sp,
                    color      = color,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text       = "$pct%",
                    fontSize   = 12.sp,
                    color      = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bgColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}