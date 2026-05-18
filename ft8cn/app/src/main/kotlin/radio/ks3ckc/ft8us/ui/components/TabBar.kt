package radio.ks3ckc.ft8us.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import radio.ks3ckc.ft8us.theme.*
import radio.ks3ckc.ft8us.ui.motion.MotionTokens
import radio.ks3ckc.ft8us.ui.motion.rememberHaptics

enum class FT8USTab(val label: String) {
    DECODE("Decode"),
    MAP("Map"),
    WATERFALL("Waterfall"),
    LOG("Logbook"),
    SETTINGS("Settings"),
}

@Composable
fun TabBar(
    activeTab: FT8USTab,
    onTabSelected: (FT8USTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val tabBounds = remember { mutableStateMapOf<FT8USTab, Pair<Float, Float>>() } // center x, width (px)

    LaunchedEffect(activeTab) {
        haptics.tick()
    }

    val activeBounds = tabBounds[activeTab]
    val targetCenter = activeBounds?.first ?: 0f
    val targetWidth = activeBounds?.second ?: 0f

    val animatedCenter by animateFloatAsState(
        targetValue = targetCenter,
        animationSpec = MotionTokens.SpringSmooth,
        label = "tab-indicator-center"
    )
    val animatedWidth by animateFloatAsState(
        targetValue = targetWidth,
        animationSpec = MotionTokens.SpringSmooth,
        label = "tab-indicator-width"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, BgApp.copy(alpha = 0.95f)),
                    startY = 0f,
                    endY = 40f,
                )
            )
    ) {
        // Animated pill indicator behind the active tab (sized to match the Row, not the
        // available constraints — using fillMaxSize here would blow up the Box's height
        // when placed in a Column with unbounded vertical constraints).
        if (activeBounds != null && animatedWidth > 0f) {
            val pillWidthPx = animatedWidth * 0.56f
            val pillHeightPx = with(density) { 28.dp.toPx() }
            val pillTopPx = with(density) { 6.dp.toPx() }
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = 1f }
            ) {
                val cx = animatedCenter
                val left = cx - pillWidthPx / 2f
                drawRoundRect(
                    color = Accent.copy(alpha = 0.14f),
                    topLeft = Offset(left, pillTopPx),
                    size = Size(pillWidthPx, pillHeightPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(pillHeightPx / 2f, pillHeightPx / 2f),
                )
                // Top accent dot above the active tab
                val dotR = with(density) { 1.5.dp.toPx() }
                drawCircle(
                    color = Accent,
                    radius = dotR,
                    center = Offset(cx, pillTopPx - with(density) { 4.dp.toPx() })
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 8.dp, bottom = 30.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            for (tab in FT8USTab.entries) {
                val isActive = tab == activeTab

                val color by animateColorAsState(
                    targetValue = if (isActive) Accent else TextFaint,
                    animationSpec = tween(MotionTokens.DurMed, easing = MotionTokens.EasingStandard),
                    label = "tab-color"
                )
                val strokeWidth = if (isActive) 1.8f else 1.5f

                // Bounce animation when this tab becomes active
                val bounceScale = remember { Animatable(1f) }
                LaunchedEffect(isActive) {
                    if (isActive) {
                        bounceScale.snapTo(1f)
                        bounceScale.animateTo(1.18f, tween(140, easing = MotionTokens.EasingEmphasizedDecel))
                        bounceScale.animateTo(1.12f, MotionTokens.SpringSmooth)
                    } else {
                        bounceScale.animateTo(1f, MotionTokens.SpringSmooth)
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coords ->
                            val cx = coords.positionInParent().x + coords.size.width / 2f
                            tabBounds[tab] = cx to coords.size.width.toFloat()
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onTabSelected(tab) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier.graphicsLayer {
                            scaleX = bounceScale.value
                            scaleY = bounceScale.value
                        }
                    ) {
                        when (tab) {
                            FT8USTab.DECODE -> FT8USIcons.Decode(color = color, strokeWidth = strokeWidth)
                            FT8USTab.MAP -> FT8USIcons.Globe(color = color, strokeWidth = strokeWidth)
                            FT8USTab.WATERFALL -> FT8USIcons.Waterfall(color = color, strokeWidth = strokeWidth)
                            FT8USTab.LOG -> FT8USIcons.Book(color = color, strokeWidth = strokeWidth)
                            FT8USTab.SETTINGS -> FT8USIcons.Cog(color = color, strokeWidth = strokeWidth)
                        }
                    }
                    Text(
                        text = tab.label,
                        color = color,
                        fontSize = 10.5.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        letterSpacing = 0.02.sp,
                    )
                }
            }
        }
    }
}
