package com.example.pp68_salestrackingapp.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.pp68_salestrackingapp.ui.theme.AppColors

@Composable
fun AddFloatingActionButton(
    onClick: () -> Unit,
    contentDescription: String = "Add",
    containerColor: Color = AppColors.Primary,
    contentColor: Color = Color.White
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = contentDescription)
    }
}
