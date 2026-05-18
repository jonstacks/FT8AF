package radio.ks3ckc.ft8us.ui.splash

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import radio.ks3ckc.ft8us.theme.Accent
import radio.ks3ckc.ft8us.theme.AccentGlow
import radio.ks3ckc.ft8us.theme.GeistMonoFamily
import radio.ks3ckc.ft8us.theme.Signal
import radio.ks3ckc.ft8us.theme.TextPrimary
import radio.ks3ckc.ft8us.ui.components.Wordmark
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SPLASH_DURATION_MS = 1800L

@Composable
fun FT8USplashScreen(
    onSplashComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        delay(SPLASH_DURATION_MS)
        onSplashComplete()
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val auraPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aura-phase",
    )
    val blinkPhase by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "blink",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0f to Color(0xFF1A2238),
                    0.55f to Color(0xFF0A1020),
                    1f to Color(0xFF04070E),
                )
            )
    ) {
        // Range rings + compass marks + amber halo backdrop.
        SplashBackdrop(modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Icon + animated pulse aura.
            Box(contentAlignment = Alignment.Center) {
                PulseAura(phase = auraPhase, modifier = Modifier.size(220.dp))
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(RoundedCornerShape(33.dp)),
                ) {
                    SplashIcon(modifier = Modifier.fillMaxSize())
                }
            }

            Spacer(modifier = Modifier.height(36.dp))

            Wordmark(fontSize = 40.sp)

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "FT8 · MOBILE COMPANION",
                color = TextPrimary.copy(alpha = 0.55f),
                fontSize = 12.sp,
                letterSpacing = 0.18.em,
                fontWeight = FontWeight.Medium,
            )

            Spacer(modifier = Modifier.weight(1f))

            // Loading state.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Signal.copy(alpha = blinkPhase)),
                )
                LoadingDots(label = "INITIALIZING RADIO", color = Accent.copy(alpha = 0.85f))
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "v1.0 · Forked from FT8CN",
                color = Color(0xFF8A96B1).copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = GeistMonoFamily,
                letterSpacing = 0.08.em,
            )

            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}

@Composable
private fun SplashBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        val centerY = size.height * 0.38f

        // Amber center halo.
        val haloRadius = size.minDimension * 0.4f
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFAF5E).copy(alpha = 0.18f),
                0.6f to Color(0xFFFFAF5E).copy(alpha = 0.04f),
                1f to Color(0xFFFFAF5E).copy(alpha = 0f),
                center = Offset(centerX, centerY),
                radius = haloRadius,
            ),
            center = Offset(centerX, centerY),
            radius = haloRadius,
        )

        // Range rings (dashed, blue-grey).
        val ringStroke = Stroke(
            width = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 8f)),
        )
        val ringColor = Color(0xFF94AFDC).copy(alpha = 0.1f)
        floatArrayOf(80f, 160f, 240f, 320f).forEach { r ->
            drawCircle(
                color = ringColor,
                radius = r,
                center = Offset(centerX, centerY),
                style = ringStroke,
            )
        }

        // Compass NSEW marks at radius 175 from a slightly lower center (matches splash.jsx).
        val compassCenterY = centerY + size.height * 0.08f
        val compassRadius = 175f
        val compassColor = Color(0xFFFFAF5E).copy(alpha = 0.45f)
        listOf("N", "E", "S", "W").forEachIndexed { i, _ ->
            val theta = (i * 90 - 90) * PI.toFloat() / 180f
            val x = centerX + cos(theta) * compassRadius
            val y = compassCenterY + sin(theta) * compassRadius
            drawCircle(
                color = compassColor,
                radius = 2f,
                center = Offset(x, y),
            )
        }
    }
}

@Composable
private fun PulseAura(phase: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val baseR = size.minDimension * 0.34f
        for (i in 0..2) {
            val p = ((phase + i * 0.33f) % 1f)
            val r = baseR * (1f + p * 0.8f)
            val alpha = (1f - p) * 0.55f
            drawCircle(
                color = Accent.copy(alpha = alpha),
                radius = r,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/**
 * Compose-rendered version of the launcher icon for the splash screen.
 * Slightly higher fidelity than the launcher vector drawable: includes the
 * vertical scan band, denser gradients, and a more diffuse center glow.
 */
@Composable
private fun SplashIcon(modifier: Modifier = Modifier) {
    Canvas(
        modifier = modifier.background(
            Brush.radialGradient(
                0f to Color(0xFF1A2238),
                0.55f to Color(0xFF0A1020),
                1f to Color(0xFF04070E),
            )
        )
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.527f // matches icon.jsx center at (512, 540) of 1024

        // Subtle horizontal scan band.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF5CD6E8).copy(alpha = 0f),
                0.5f to Color(0xFF5CD6E8).copy(alpha = 0.10f),
                1f to Color(0xFF5CD6E8).copy(alpha = 0f),
                startY = h * 0.37f,
                endY = h * 0.62f,
            ),
            topLeft = Offset(0f, h * 0.37f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.25f),
        )

        // Center glow halo.
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFC878).copy(alpha = 0.55f),
                0.35f to Color(0xFFFFAF5E).copy(alpha = 0.30f),
                0.7f to Color(0xFFFFAF5E).copy(alpha = 0.08f),
                1f to Color(0xFFFFAF5E).copy(alpha = 0f),
                center = Offset(cx, cy),
                radius = w * 0.41f,
            ),
            center = Offset(cx, cy),
            radius = w * 0.41f,
        )

        // Range rings (dashed).
        val ringStroke = Stroke(
            width = 1f.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 8f)),
        )
        val ringColor = Color(0xFF94AFDC).copy(alpha = 0.18f)
        floatArrayOf(0.166f, 0.274f, 0.381f).forEach { fr ->
            drawCircle(
                color = ringColor,
                radius = w * fr,
                center = Offset(cx, cy),
                style = ringStroke,
            )
        }

        // Signal arcs — top half, 4 progressively fainter.
        // arc spans ±100° from straight up.
        val arcs = listOf(
            Arc(0.127f, 0.95f, 4.0f),
            Arc(0.225f, 0.75f, 3.5f),
            Arc(0.332f, 0.50f, 2.9f),
            Arc(0.440f, 0.25f, 2.3f),
        )
        val ringBrush = Brush.linearGradient(
            0f to Color(0xFFFFD7A0).copy(alpha = 0.95f),
            0.5f to Color(0xFFFFAF5E).copy(alpha = 0.85f),
            1f to Color(0xFFFF8A2C).copy(alpha = 0.65f),
            start = Offset(0f, 0f),
            end = Offset(w, h),
        )
        for (a in arcs) {
            val r = w * a.radiusFraction
            // Drawn as a circle's top half via arc.
            drawArc(
                brush = ringBrush,
                startAngle = -190f,
                sweepAngle = 200f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                alpha = a.opacity,
                style = Stroke(width = a.strokeWidth.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }

        // Tiny sparks on outer arc edges.
        listOf(
            Offset(cx - w * 0.434f, cy),
            Offset(cx + w * 0.434f, cy),
            Offset(cx - w * 0.325f, cy - w * 0.039f),
            Offset(cx + w * 0.325f, cy - w * 0.039f),
        ).forEach {
            drawCircle(color = AccentGlow.copy(alpha = 0.5f), radius = w * 0.006f, center = it)
        }

        // Center disc — operator QTH.
        val discR = w * 0.076f
        drawCircle(
            brush = Brush.verticalGradient(
                0f to Color(0xFFFFD7A0),
                0.5f to Color(0xFFFFAF5E),
                1f to Color(0xFFFF8A2C),
                startY = cy - discR,
                endY = cy + discR,
            ),
            radius = discR,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = Color(0xFFFFE6BE).copy(alpha = 0.6f),
            radius = discR,
            center = Offset(cx, cy),
            style = Stroke(width = 0.6f.dp.toPx()),
        )
        // Specular highlight on the disc.
        drawOval(
            color = Color(0xFFFFF5DC).copy(alpha = 0.55f),
            topLeft = Offset(cx - discR * 0.45f, cy - discR * 0.55f),
            size = androidx.compose.ui.geometry.Size(discR * 0.7f, discR * 0.42f),
        )

        // Top-edge shine.
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.12f),
                0.4f to Color.White.copy(alpha = 0f),
                startY = 0f,
                endY = h * 0.4f,
            ),
            topLeft = Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.23f),
        )
    }
}

private data class Arc(val radiusFraction: Float, val opacity: Float, val strokeWidth: Float)

@Composable
private fun LoadingDots(label: String, color: Color) {
    val transition = rememberInfiniteTransition(label = "dots")
    val tick by transition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dots-tick",
    )
    val dotCount = tick.toInt().coerceIn(0, 3)
    val dots = ".".repeat(dotCount)
    Text(
        text = "$label$dots",
        color = color,
        fontSize = 11.sp,
        fontFamily = GeistMonoFamily,
        letterSpacing = 0.12.em,
    )
}
