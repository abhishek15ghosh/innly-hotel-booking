package com.innly.hotelbooking.presentation.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.innly.hotelbooking.core.ui.ChampagneGold
import com.innly.hotelbooking.core.ui.ChampagneGoldLight
import com.innly.hotelbooking.core.ui.EmeraldPrimary
import com.innly.hotelbooking.core.ui.WarmIvoryBackground
import kotlinx.coroutines.delay

private const val SplashDurationMillis = 2400L

private val SplashTop = Color(0xFF071B17)
private val SplashMiddle = Color(0xFF0F3E36)
private val SplashBottom = Color(0xFF0A2621)
private val AccentGold = ChampagneGold
private val AccentGoldLight = ChampagneGoldLight
private val SplashBody = WarmIvoryBackground
private val SplashTextMuted = Color(0xFFC7D3D0)

@Composable
fun OnboardingScreen(
    onAnimationFinished: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "luxury-splash")
    val orbShift by infiniteTransition.animateFloat(
        initialValue = -24f,
        targetValue = 28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb-shift",
    )
    val haloScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-scale",
    )
    val haloAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.52f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "halo-alpha",
    )
    val titleScale by infiniteTransition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "title-scale",
    )
    val titleAlpha by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "title-alpha",
    )
    val taglineAlpha by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tagline-alpha",
    )

    LaunchedEffect(Unit) {
        delay(SplashDurationMillis)
        onAnimationFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SplashTop,
                        SplashMiddle,
                        SplashBottom,
                    ),
                ),
            ),
    ) {
        SplashOrb(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 30.dp, end = 10.dp),
            size = 200.dp,
            color = AccentGold.copy(alpha = 0.16f),
            offsetX = -orbShift * 0.4f,
            offsetY = orbShift * 0.2f,
        )
        SplashOrb(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-40).dp),
            size = 220.dp,
            color = EmeraldPrimary.copy(alpha = 0.26f),
            offsetX = orbShift * 0.35f,
            offsetY = -orbShift * 0.3f,
        )
        SplashOrb(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 90.dp),
            size = 210.dp,
            color = AccentGoldLight.copy(alpha = 0.18f),
            offsetX = -orbShift * 0.25f,
            offsetY = orbShift * 0.25f,
        )

        // Center ambient halo without expensive runtime blur
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(300.dp)
                .graphicsLayer {
                    scaleX = haloScale
                    scaleY = haloScale
                    alpha = haloAlpha
                }
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AccentGold.copy(alpha = 0.25f),
                            EmeraldPrimary.copy(alpha = 0.15f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Brand Monogram Emblem
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(EmeraldPrimary)
                    .border(2.dp, AccentGold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "I",
                    style = TextStyle(
                        color = AccentGold,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                    ),
                )
            }

            Text(
                text = "Innly",
                modifier = Modifier.graphicsLayer {
                    scaleX = titleScale
                    scaleY = titleScale
                    alpha = titleAlpha
                },
                style = TextStyle(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            AccentGoldLight,
                            AccentGold,
                        ),
                    ),
                    fontSize = 60.sp,
                    lineHeight = 64.sp,
                    letterSpacing = (-2.5).sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    shadow = Shadow(
                        color = Color(0x66000000),
                        blurRadius = 32f,
                    ),
                ),
            )

            Box(
                modifier = Modifier
                    .size(height = 3.dp, width = 100.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AccentGold.copy(alpha = 0.9f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            Text(
                text = "Reliable stays. Clear pricing. Faster booking decisions.",
                modifier = Modifier.graphicsLayer { alpha = taglineAlpha },
                style = TextStyle(
                    color = SplashTextMuted,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    letterSpacing = 0.4.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 28.dp, end = 28.dp, bottom = 54.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Opening your stay dashboard",
                style = TextStyle(
                    color = SplashBody,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            SplashDots()
        }
    }
}

@Composable
private fun SplashOrb(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp,
    color: Color,
    offsetX: Float,
    offsetY: Float,
) {
    Box(
        modifier = modifier
            .graphicsLayer {
                translationX = offsetX
                translationY = offsetY
            }
            .size(size)
            .drawWithCache {
                val brush = Brush.radialGradient(
                    colors = listOf(
                        color,
                        Color.Transparent,
                    ),
                )
                onDrawBehind {
                    drawCircle(brush = brush)
                }
            },
    )
}

@Composable
private fun SplashDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "dots")
    val dotOneScale by infiniteTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-one",
    )
    val dotTwoScale by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 700,
                delayMillis = 180,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-two",
    )
    val dotThreeScale by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 700,
                delayMillis = 360,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot-three",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        SplashDot(scale = dotOneScale)
        SplashDot(scale = dotTwoScale)
        SplashDot(scale = dotThreeScale)
    }
}

@Composable
private fun SplashDot(scale: Float) {
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = 0.45f + (scale * 0.55f)
            }
            .size(10.dp)
            .clip(CircleShape)
            .background(ChampagneGold),
    )
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    OnboardingScreen(onAnimationFinished = {})
}
