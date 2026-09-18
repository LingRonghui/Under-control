package com.jingcai.predict.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.jingcai.predict.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular

/**
 * 通用质感组件库 —— 全应用统一视觉语言的最小集合。
 *
 * 设计口径：深色以「细描边 + 极淡渐变」代替阴影；浅色用极浅底色分层；
 * 数据一律等宽对齐；强调色只用于关键结论，避免花哨。
 */

/**
 * 屏幕氛围底：深空底 + 双色柔光（右上品牌绿、左下青），全应用共用同一层氛围。
 * 作为 Modifier 扩展使用（如 `Modifier.appBackground()`），按实际绘制区域尺寸生成径向渐变，
 * 避免固定尺寸导致的光斑偏移。
 */
@Composable
fun Modifier.appBackground(): Modifier {
    val base = MaterialTheme.colorScheme.background
    val brand = MaterialTheme.colorScheme.primary
    val teal = Tone.teal
    val dark = Tone.isDark()
    // drawBehind 的 lambda 不是 @Composable，所有令牌必须在组合作用域内先取出
    val brandGlow = brand.copy(alpha = if (dark) 0.14f else 0.10f)
    val brandClear = brand.copy(alpha = 0f)
    val tealGlow = teal.copy(alpha = if (dark) 0.09f else 0.07f)
    val tealClear = teal.copy(alpha = 0f)
    return this.drawBehind {
        drawRect(base)
        val radius = size.maxDimension * 0.85f
        drawRect(
            Brush.radialGradient(
                listOf(brandGlow, brandClear),
                center = Offset(size.width * 0.88f, size.height * 0.03f),
                radius = radius
            )
        )
        drawRect(
            Brush.radialGradient(
                listOf(tealGlow, tealClear),
                center = Offset(size.width * 0.08f, size.height * 0.9f),
                radius = radius
            )
        )
    }
}

/**
 * 卡片：主容器。玻璃质感 = 半透明渐变底 + 渐变描边（上亮下隐）+ 顶部高光细线；
 * [glow] = true 时在右上角叠一层品牌色柔光，用于强调区块。
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = Corner.lg
    val brand = MaterialTheme.colorScheme.primary
    // 令牌在组合作用域内取值（drawBehind 的 lambda 不是 @Composable）
    val glass = Tone.glassBrush()
    val glassStroke = Tone.glassStroke()
    // 两个分支必须同为 Brush，否则公共父类型退化为 Any、border 无法解析
    val stroke: Brush = if (accent) {
        Brush.verticalGradient(listOf(brand.copy(alpha = 0.42f), brand.copy(alpha = 0.10f)))
    } else {
        glassStroke
    }
    val spec = Tone.specular()
    val glowTop = brand.copy(alpha = 0.22f)
    val glowClear = brand.copy(alpha = 0f)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind {
                if (accent) {
                    drawRect(
                        Brush.radialGradient(
                            listOf(glowTop, glowClear),
                            center = Offset(size.width * 0.86f, -size.height * 0.18f),
                            radius = size.width * 0.9f
                        )
                    )
                }
                drawRect(glass)
                val inset = 1.5.dp.toPx()
                drawRect(
                    brush = spec,
                    topLeft = Offset(inset, 0f),
                    size = Size(size.width - inset * 2, 1.dp.toPx())
                )
            }
            .border(1.dp, stroke, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

/** 主数据：大号高对比数字 + 可选单位/说明（关键结论一律用它） */
@Composable
fun HeroNumber(
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    label: String? = null,
    color: Color = Tone.textStrong(),
    size: Int = 34,
) {
    Column(modifier) {
        label?.let {
            Text(it, style = MaterialTheme.typography.labelMedium, color = Tone.textLabel(), maxLines = 1)
            Spacer(Modifier.height(Space.xxs))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.displaySmall.copy(fontSize = size.sp).tabular(),
                color = color,
                maxLines = 1
            )
            if (unit != null) {
                Text(
                    unit,
                    Modifier.padding(start = 3.dp, bottom = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Tone.textLabel()
                )
            }
        }
    }
}

/** 区块标题：左侧竖条 + 标题 + 副标题 + 右侧插槽 */
@Composable
fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(Corner.pill)
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    )
                )
        )
        Column(Modifier.padding(start = Space.sm).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Tone.textStrong(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Tone.textLabel(),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        trailing?.invoke()
    }
}

/** 指标块：标签 + 大号数值（等宽）+ 单位 + 可选说明 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String = "",
    hint: String? = null,
    valueColor: Color = Color.Unspecified,
    valueSize: Int = 19,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = Tone.textLabel(),
            maxLines = 1
        )
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = valueSize.sp).tabular(),
                color = if (valueColor == Color.Unspecified) Tone.textStrong() else valueColor,
                maxLines = 1
            )
            if (unit.isNotEmpty()) {
                Text(
                    unit,
                    Modifier.padding(start = 2.dp, bottom = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Tone.textLabel()
                )
            }
        }
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = Tone.textHint(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 标签胶囊 */
@Composable
fun PillTag(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .clip(Corner.pill)
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = if (filled) 0f else 0.28f), Corner.pill)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(11.dp), tint = color)
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1
        )
    }
}

/** 细分隔线 */
@Composable
fun Hairline(modifier: Modifier = Modifier, startPadding: Dp = 0.dp, endPadding: Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = startPadding, end = endPadding)
            .height(1.dp)
            .background(Tone.divider())
    )
}

/** 键值行（左标签、右数值，数值等宽右对齐） */
@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.Unspecified,
    bold: Boolean = false,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = Tone.textLabel()
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.tabular(),
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            color = if (valueColor == Color.Unspecified) Tone.textStrong() else valueColor,
            maxLines = 1
        )
    }
}

/** 分段切换：胶囊轨道 + 动画指示（用于分类切换） */
@Composable
fun SegmentedTabs(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Corner.pill)
            .background(Tone.track())
            .padding(3.dp)
    ) {
        items.forEachIndexed { index, label ->
            val active = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                animationSpec = tween(180),
                label = "segBg"
            )
            val fg by animateColorAsState(
                targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(180),
                label = "segFg"
            )
            Box(
                Modifier
                    .weight(1f)
                    .clip(Corner.pill)
                    .background(bg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSelect(index) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                    maxLines = 1
                )
            }
        }
    }
}

/** 空状态：图标 + 标题 + 说明 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Tone.fill()),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.md))
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        if (description != null) {
            Spacer(Modifier.height(Space.xs))
            Text(
                description,
                Modifier.alpha(0.9f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (action != null) {
            Spacer(Modifier.height(Space.lg))
            action()
        }
    }
}

/** 细进度条（0..1，带入场动画） */
@Composable
fun ThinProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = Tone.track(),
    height: Dp = 5.dp,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "thinProgress"
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(Corner.pill)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(Corner.pill)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.75f), color)))
        )
    }
}

/** 小图标底座（设置项 / 列表项左侧） */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    size: Dp = 34.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

/**
 * 应用品牌标识（与启动图标同源）：掌心拢球。
 * 用 Image 而非 Icon——保留自身配色，不被主题 tint 覆盖。
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
) {
    Image(
        painter = painterResource(R.drawable.ic_brand_mark),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

/** 品牌标识 + 玻璃底座（顶栏 / 头像用） */
@Composable
fun BrandMarkBadge(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    cornerPercent: Int = 34,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(percent = cornerPercent))
            .background(Tone.fill())
            .border(1.dp, Tone.glassStroke(), RoundedCornerShape(percent = cornerPercent)),
        contentAlignment = Alignment.Center
    ) {
        BrandMark(size = size * 0.7f)
    }
}
