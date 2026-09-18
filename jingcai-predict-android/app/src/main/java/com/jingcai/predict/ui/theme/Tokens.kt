package com.jingcai.predict.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * 设计令牌（Design Tokens）—— 全应用统一的间距/圆角/质感口径。
 *
 * 【约定】页面实现统一使用这里的令牌，避免零散 dp/sp 魔法数字，
 * 以保证「简约、高级感、质感」的视觉一致性。
 */

/** 间距阶梯（4 的倍数） */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp          // 页面标准水平边距
    val xl = 20.dp
    val xxl = 28.dp
    val section = 14.dp     // 区块之间的垂直间距
}

/** 圆角阶梯 */
object Corner {
    val xs = RoundedCornerShape(6.dp)
    val sm = RoundedCornerShape(10.dp)
    val md = RoundedCornerShape(14.dp)
    val lg = RoundedCornerShape(18.dp)
    val xl = RoundedCornerShape(22.dp)
    val pill = RoundedCornerShape(percent = 50)
}

/**
 * 语义色与质感资源（随明暗主题自动切换）。
 *
 * 【业务口径，不可擅改】
 * - 命中 = 红 [hit]；未中 = 绿 [miss]；待结算 = 中性 [pending]。
 */
object Tone {

    @Composable
    fun isDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

    /** 命中（竞彩红） */
    val hit: Color = Color(0xFFD93A2B)

    /** 未中（竞彩绿） */
    val miss: Color = Color(0xFF1B8A4B)

    /** 待结算：中性灰 */
    @Composable
    fun pending(): Color = MaterialTheme.colorScheme.onSurfaceVariant

    /** 盈利（正盈亏）：遵循中文竞彩习惯，红 = 盈利/命中 */
    @Composable
    fun gain(): Color = hit

    /** 亏损（负盈亏）：绿 = 亏损/未中 */
    @Composable
    fun loss(): Color = miss

    /** 细描边：用描边代替阴影，是深色「高级感」的关键 */
    @Composable
    fun hairline(): Color = if (isDark()) Color(0x14FFFFFF) else Color(0x140F172A)

    /** 更明显的分隔线 */
    @Composable
    fun divider(): Color = if (isDark()) Color(0x1FFFFFFF) else Color(0x1A0F172A)

    /** 中性填充块（图标底、标签底） */
    @Composable
    fun fill(): Color = if (isDark()) Color(0x14FFFFFF) else Color(0x0A0F172A)

    /** 分段控件轨道 */
    @Composable
    fun track(): Color = if (isDark()) Color(0x0FFFFFFF) else Color(0x0D0F172A)

    /** 卡片底色：极淡的纵向渐变，营造层次感 */
    @Composable
    fun cardBrush(): Brush = if (isDark()) {
        Brush.verticalGradient(listOf(Color(0xFF151D28), Color(0xFF0E141B)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFD)))
    }

    /** 强调色卡片底色（仪表盘 / 主指标区） */
    @Composable
    fun accentBrush(): Brush = if (isDark()) {
        Brush.verticalGradient(listOf(Color(0xFF12261C), Color(0xFF0D1620)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFEAF9F0), Color(0xFFF7FBF9)))
    }

    /** 品牌主色的柔和描边（用于强调区块） */
    @Composable
    fun brandStroke(): Color = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark()) 0.34f else 0.28f)
}

/** 数字等宽：金额/赔率/概率等数字列对齐更整齐 */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
