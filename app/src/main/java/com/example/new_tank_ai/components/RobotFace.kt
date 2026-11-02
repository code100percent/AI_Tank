package com.example.new_tank_ai.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.new_tank_ai.model.RobotExpression
import com.example.new_tank_ai.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun RobotFace(
    expression: RobotExpression,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    isAnimated: Boolean = true
) {
    // Periodic blinking state
    var isBlinking by remember { mutableStateOf(false) }
    
    if (isAnimated) {
        LaunchedEffect(Unit) {
            while (true) {
                delay((3000..6000).random().toLong().milliseconds)
                isBlinking = true
                delay(150.milliseconds)
                isBlinking = false
            }
        }
    }

    // Infinite transition for pulsing (listening/excited) and speaking mouth
    val infiniteTransition = rememberInfiniteTransition(label = "face_anim")
    
    val pulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val speakAnim by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speak"
    )

    // Smooth transition values for expression parameters
    val eyeOpennessTarget = when {
        isBlinking -> 0.1f
        expression == RobotExpression.SLEEPY -> 0.3f
        expression == RobotExpression.SURPRISED -> 1.3f
        expression == RobotExpression.ANGRY -> 0.7f
        else -> 1.0f
    }
    
    val eyeOpenness by animateFloatAsState(
        targetValue = eyeOpennessTarget,
        animationSpec = tween(200),
        label = "eyeOpenness"
    )

    Box(
        modifier = modifier
            .size(size)
            .shadow(16.dp, RoundedCornerShape(32.dp), spotColor = CyberElectricBlue)
            .border(
                width = 3.dp,
                brush = Brush.linearGradient(
                    colors = listOf(CyberSteelLight, CyberElectricBlue, CyberSteel)
                ),
                shape = RoundedCornerShape(32.dp)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(CyberDarkCharcoal, CyberBlack),
                    radius = 400f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Decorative corner screws / cyber grill lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.toPx()
            val h = size.toPx()
            
            // Subtle cyber grid lines
            val gridColor = CyberElectricBlue.copy(alpha = 0.05f)
            for (i in 1..4) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * i / 5f),
                    end = Offset(w, h * i / 5f),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw Face Elements based on expression
            drawFaceElements(
                expression = expression,
                eyeOpenness = eyeOpenness,
                pulse = pulseAnim,
                speak = speakAnim,
                width = w,
                height = h
            )
        }
    }
}

private fun DrawScope.drawFaceElements(
    expression: RobotExpression,
    eyeOpenness: Float,
    pulse: Float,
    speak: Float,
    width: Float,
    height: Float
) {
    val primaryColor = when (expression) {
        RobotExpression.ANGRY -> CyberRed
        RobotExpression.EXCITED, RobotExpression.HAPPY -> CyberCyan
        RobotExpression.SLEEPY -> CyberSteelLight
        else -> CyberElectricBlue
    }

    val eyeY = height * 0.38f
    val leftEyeX = width * 0.32f
    val rightEyeX = width * 0.68f
    val eyeWidth = width * 0.18f
    val eyeHeight = height * 0.15f * eyeOpenness.coerceIn(0.05f, 1.3f)

    val mouthY = height * 0.70f

    // --- EYES ---
    when (expression) {
        RobotExpression.HAPPY -> {
            // Curved upward arches for happy eyes (^ ^)
            val pathLeft = Path().apply {
                moveTo(leftEyeX - eyeWidth/2, eyeY + eyeHeight/2)
                quadraticTo(leftEyeX, eyeY - eyeHeight, leftEyeX + eyeWidth/2, eyeY + eyeHeight/2)
            }
            val pathRight = Path().apply {
                moveTo(rightEyeX - eyeWidth/2, eyeY + eyeHeight/2)
                quadraticTo(rightEyeX, eyeY - eyeHeight, rightEyeX + eyeWidth/2, eyeY + eyeHeight/2)
            }
            drawPath(pathLeft, primaryColor, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
            drawPath(pathRight, primaryColor, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
        }
        RobotExpression.WINKING -> {
            // Left eye open, right eye closed (wink line)
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawLine(
                color = primaryColor,
                start = Offset(rightEyeX - eyeWidth/2, eyeY),
                end = Offset(rightEyeX + eyeWidth/2, eyeY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        RobotExpression.ANGRY -> {
            // Slanted angry eyes
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(rightEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            // Angry brows
            drawLine(
                color = primaryColor,
                start = Offset(leftEyeX - eyeWidth/2 - 4.dp.toPx(), eyeY - eyeHeight/2 - 8.dp.toPx()),
                end = Offset(leftEyeX + eyeWidth/2 + 4.dp.toPx(), eyeY - eyeHeight/2 + 4.dp.toPx()),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = primaryColor,
                start = Offset(rightEyeX - eyeWidth/2 - 4.dp.toPx(), eyeY - eyeHeight/2 + 4.dp.toPx()),
                end = Offset(rightEyeX + eyeWidth/2 + 4.dp.toPx(), eyeY - eyeHeight/2 - 8.dp.toPx()),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        RobotExpression.SAD -> {
            // Drooping eyes
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(rightEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
        RobotExpression.CONFUSED -> {
            // Asymmetrical eyes
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight * 1.2f),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            // Smaller right eye
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(rightEyeX - eyeWidth/3, eyeY - eyeHeight/3),
                size = Size(eyeWidth * 0.7f, eyeHeight * 0.6f),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
        RobotExpression.THINKING -> {
            // Eyes looking sideways / upward
            val offsetShift = 8.dp.toPx()
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftEyeX - eyeWidth/2 + offsetShift, eyeY - eyeHeight/2 - offsetShift/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(rightEyeX - eyeWidth/2 + offsetShift, eyeY - eyeHeight/2 - offsetShift/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
        }
        RobotExpression.LISTENING -> {
            // Pulsing attention eyes
            val pulseSizeW = eyeWidth * pulse
            val pulseSizeH = eyeHeight * pulse
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftEyeX - pulseSizeW/2, eyeY - pulseSizeH/2),
                size = Size(pulseSizeW, pulseSizeH),
                cornerRadius = CornerRadius(12.dp.toPx())
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(rightEyeX - pulseSizeW/2, eyeY - pulseSizeH/2),
                size = Size(pulseSizeW, pulseSizeH),
                cornerRadius = CornerRadius(12.dp.toPx())
            )
        }
        else -> {
            // Default rectangular robotic eyes
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(leftEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(rightEyeX - eyeWidth/2, eyeY - eyeHeight/2),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = CornerRadius(8.dp.toPx())
            )
        }
    }

    // --- MOUTH ---
    val mouthWidth = width * 0.35f
    val mouthHeight = height * 0.08f

    when (expression) {
        RobotExpression.HAPPY, RobotExpression.EXCITED, RobotExpression.WINKING -> {
            // Big smiling arc
            val smilePath = Path().apply {
                moveTo(width * 0.32f, mouthY)
                quadraticTo(width * 0.5f, mouthY + mouthHeight * 2.5f, width * 0.68f, mouthY)
            }
            drawPath(smilePath, primaryColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
        }
        RobotExpression.SAD -> {
            // Frown arc
            val frownPath = Path().apply {
                moveTo(width * 0.32f, mouthY + mouthHeight)
                quadraticTo(width * 0.5f, mouthY - mouthHeight * 1.5f, width * 0.68f, mouthY + mouthHeight)
            }
            drawPath(frownPath, primaryColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
        }
        RobotExpression.SPEAKING -> {
            // Animated speaking mouth box
            val hAnim = mouthHeight * (0.5f + speak * 1.2f)
            drawRoundRect(
                color = primaryColor,
                topLeft = Offset(width * 0.5f - mouthWidth * 0.4f, mouthY - hAnim / 2),
                size = Size(mouthWidth * 0.8f, hAnim),
                cornerRadius = CornerRadius(10.dp.toPx())
            )
        }
        RobotExpression.SURPRISED -> {
            // O-shaped mouth
            drawCircle(
                color = primaryColor,
                radius = mouthHeight * 0.9f,
                center = Offset(width * 0.5f, mouthY)
            )
        }
        RobotExpression.CONFUSED -> {
            // Tilted squiggly line
            drawLine(
                color = primaryColor,
                start = Offset(width * 0.35f, mouthY + 4.dp.toPx()),
                end = Offset(width * 0.65f, mouthY - 4.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        RobotExpression.SLEEPY -> {
            // Small sleepy line
            drawLine(
                color = primaryColor,
                start = Offset(width * 0.42f, mouthY),
                end = Offset(width * 0.58f, mouthY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        RobotExpression.ANGRY -> {
            // Firm flat downward mouth
            drawLine(
                color = primaryColor,
                start = Offset(width * 0.35f, mouthY + 6.dp.toPx()),
                end = Offset(width * 0.65f, mouthY + 6.dp.toPx()),
                strokeWidth = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        else -> {
            // Neutral flat mouth line
            drawLine(
                color = primaryColor,
                start = Offset(width * 0.38f, mouthY),
                end = Offset(width * 0.62f, mouthY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
