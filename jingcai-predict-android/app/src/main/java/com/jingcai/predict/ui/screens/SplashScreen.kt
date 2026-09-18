package com.jingcai.predict.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.R
import com.jingcai.predict.ui.components.BrandMark
import com.jingcai.predict.ui.components.appBackground
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone

/**
 * 启动动画：与图标同源的「掌心拢球」入场。
 *
 * 编排（总长约 1.7s，与 MainActivity 的切换时长对应）：
 * 1. 0~500ms  光晕先亮，品牌标识以轻微回弹（spring）从 0.72 放大到 1.0 并浮入；
 * 2. 常驻     能量环缓慢旋转（2.6s/圈）＋ 标识极轻的呼吸浮动，营造"球在掌中"的生命感；
 * 3. 400ms~   应用名淡入，700ms 起标语淡入，形成错落的收尾。
 */
@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow))
    }
    val progress = appear.value

    val loop = rememberInfiniteTransition(label = "splashLoop")
    val ringAngle by loop.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "ringAngle"
    )
    val floatY by loop.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "floatY"
    )

    val markScale = 0.72f + 0.28f * progress
    val titleAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
    val taglineAlpha = ((progress - 0.6f) / 0.4f).coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxSize()
            .appBackground(),
        contentAlignment = Alignment.Center
    ) {
        // 品牌色光晕（随标识一起亮起）
        Box(
            Modifier
                .size(280.dp)
                .alpha(progress * 0.95f)
                .background(Tone.glow(0.22f), CircleShape)
        )

        // 旋转能量环：与图标里的光环同源
        Canvas(Modifier.size(184.dp).alpha(progress)) {
            val stroke = 2.4.dp.toPx()
            val inset = stroke / 2f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Tone.brand.copy(alpha = 0f),
                        Tone.brand.copy(alpha = 0.9f),
                        Tone.teal.copy(alpha = 0.55f),
                        Tone.brand.copy(alpha = 0f)
                    )
                ),
                startAngle = ringAngle,
                sweepAngle = 268f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BrandMark(
                modifier = Modifier
                    .scale(markScale)
                    .offset(y = floatY.dp)
                    .alpha(progress),
                size = 104.dp
            )
            Spacer(Modifier.height(Space.xl))
            Text(
                stringResource(R.string.app_name),
                Modifier.alpha(titleAlpha),
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = Tone.textStrong()
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                "竞彩足球 · 数据取自官方接口",
                Modifier.alpha(taglineAlpha),
                style = MaterialTheme.typography.labelMedium,
                color = Tone.textLabel(),
                textAlign = TextAlign.Center
            )
        }
    }
}
