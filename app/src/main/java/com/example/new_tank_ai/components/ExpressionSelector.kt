package com.example.new_tank_ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.new_tank_ai.model.RobotExpression
import com.example.new_tank_ai.ui.theme.*

@Composable
fun ExpressionSelector(
    selectedExpression: RobotExpression,
    onExpressionSelected: (RobotExpression) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "EXPRESSIONS",
            style = MaterialTheme.typography.labelLarge.copy(
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold
            ),
            color = CyberSteelLight,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(RobotExpression.entries) { expression ->
                val isSelected = expression == selectedExpression
                
                val borderColor = if (isSelected) CyberElectricBlue else CyberSurfaceVariant
                val backgroundColor = if (isSelected) CyberSurfaceVariant else CyberSurface
                val textColor = if (isSelected) CyberElectricBlue else CyberWhite

                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .shadow(if (isSelected) 8.dp else 2.dp, RoundedCornerShape(16.dp), spotColor = CyberElectricBlue)
                        .clip(RoundedCornerShape(16.dp))
                        .background(backgroundColor)
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            brush = if (isSelected) {
                                Brush.linearGradient(listOf(CyberElectricBlue, CyberCyan))
                            } else {
                                Brush.linearGradient(listOf(CyberSurfaceVariant, CyberSurface))
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onExpressionSelected(expression) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        // Mini preview face
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CyberBlack)
                                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            RobotFace(
                                expression = expression,
                                size = 36.dp,
                                isAnimated = false
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = expression.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 12.sp
                            ),
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}
