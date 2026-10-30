package app.nova.vpn.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import kotlin.math.hypot

private fun shieldPath(left: Float, top: Float, s: Float) = Path().apply {
    moveTo(left + 20 * s, top)
    lineTo(left + 40 * s, top + 7 * s)
    lineTo(left + 40 * s, top + 25 * s)
    cubicTo(left + 40 * s, top + 37 * s, left + 31 * s, top + 46 * s, left + 20 * s, top + 50 * s)
    cubicTo(left + 9 * s, top + 46 * s, left, top + 37 * s, left, top + 25 * s)
    lineTo(left, top + 7 * s)
    close()
}

private fun DrawScope.drawLogo(cx: Float, cy: Float, s: Float, shield: Color, v: Color) {
    val left = cx - 20 * s
    val top = cy - 25 * s
    drawPath(shieldPath(left, top, s), shield)
    val check = Path().apply {
        moveTo(left + 11 * s, top + 21 * s)
        lineTo(left + 20 * s, top + 31 * s)
        lineTo(left + 29 * s, top + 21 * s)
    }
    drawPath(check, v, style = Stroke(width = 5 * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * dark screen + lime logo
 *  -> a lime circle grows from the top-left corner (logo turns dark)
 *  -> a dark circle grows from the bottom-right corner (logo turns lime again) + soft glow
 *  -> fade into the app (which is dark).
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val toLime = remember { Animatable(0f) }
    val toDark = remember { Animatable(0f) }
    val glow = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(400)
        toLime.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        delay(180)
        toDark.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        glow.animateTo(1f, tween(650, easing = LinearEasing))
        delay(200)
        exit.animateTo(0f, tween(450))
        onFinished()
    }

    Canvas(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = exit.value }
            .pointerInput(Unit) {}   // swallow touches while the splash is visible
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val s = (w * 0.24f / 40f) * (0.94f + 0.06f * toLime.value)
        val maxR = hypot(w, h)

        // 1) dark with a lime logo
        drawRect(NovaColors.Bg)
        drawLogo(cx, cy, s, NovaColors.Lime, NovaColors.Bg)

        // 2) lime floods in from the top-left
        val lime = Path().apply { addOval(Rect(Offset(0f, 0f), toLime.value * maxR)) }
        clipPath(lime) {
            drawRect(NovaColors.Lime)
            drawLogo(cx, cy, s, NovaColors.Bg, NovaColors.Lime)
        }

        // 3) dark returns from the bottom-right
        val dark = Path().apply { addOval(Rect(Offset(w, h), toDark.value * maxR)) }
        clipPath(dark) {
            drawRect(NovaColors.Bg)
            val glowR = 40 * s * 2.2f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(NovaColors.Lime.copy(alpha = 0.30f * glow.value), Color.Transparent),
                    center = Offset(cx, cy), radius = glowR
                ),
                radius = glowR, center = Offset(cx, cy)
            )
            drawLogo(cx, cy, s, NovaColors.Lime, NovaColors.Bg)
        }
    }
}
