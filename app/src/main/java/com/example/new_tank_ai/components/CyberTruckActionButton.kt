package com.example.new_tank_ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.new_tank_ai.ui.theme.*

@Composable
fun CyberTruckActionButton(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val containerColor = if (isActive) CyberSurfaceVariant else CyberSurface
    val contentColor = if (isActive) CyberElectricBlue else CyberWhite

    Box(
        modifier = modifier
            .height(72.dp)
            .shadow(if (isActive) 8.dp else 2.dp, RoundedCornerShape(16.dp), spotColor = CyberElectricBlue)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(
                width = if (isActive) 2.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isActive) listOf(CyberElectricBlue, CyberCyan) else listOf(CyberSteel.copy(alpha = 0.3f), CyberSurfaceVariant)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 11.sp
                ),
                color = contentColor
            )
        }
    }
}
