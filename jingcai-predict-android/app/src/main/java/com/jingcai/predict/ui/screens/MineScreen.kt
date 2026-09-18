package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.slip.ParlayMath
import com.jingcai.predict.data.slip.SlipAutoSettle
import com.jingcai.predict.data.slip.SlipStats
import com.jingcai.predict.ui.components.Hairline
import com.jingcai.predict.ui.components.IconBadge
import com.jingcai.predict.ui.components.KeyValueRow
import com.jingcai.predict.ui.components.SectionTitle
import com.jingcai.predict.ui.components.StatTile
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.components.openUrl
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular

/** 项目开源仓库（「关于」项跳转目标） */
private const val REPO_URL = "https://github.com/LingRonghui/Under-control"

/** 版本号（与 app/build.gradle.kts 的 versionName 保持一致） */
private const val APP_VERSION = "0.1.0"

/**
 * 我的：盈亏仪表盘 + 设置入口。
 *
 * 【盈亏统计口径（与 SlipStats 一致，界面已标注）】
 * - 主指标只统计**已结算**方案：总投入 / 总回报 / 净盈亏 / 收益率 / 方案命中率；
 * - 未结算方案（本金占用、理论最高回报）**单独列出，不并入盈亏与收益率**；
 * - 配色遵循中文竞彩习惯：红 = 盈利/命中，绿 = 亏损/未中。
 */
@Composable
fun MineScreen(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onShowToast: (String) -> Unit,
    onOpenSlips: () -> Unit,
    onOpenLlmConfig: () -> Unit,
) {
    val context = LocalContext.current
    var pnl by remember { mutableStateOf(SlipStats.compute(emptyList())) }
    var reloadKey by remember { mutableStateOf(0) }

    // 进入页面 / 点击刷新：先按官方赛果自动结算方案，再按统一口径统计盈亏
    LaunchedEffect(reloadKey) {
        val slips = runCatching { SlipAutoSettle.settleAll(context) }.getOrDefault(emptyList())
        pnl = SlipStats.compute(slips)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // 顶部留白（原「彩民小助手」卡片已按要求移除）
        Spacer(Modifier.height(Space.sm))

        // ===== 盈亏仪表盘 =====
        SurfaceCard(
            modifier = Modifier.padding(horizontal = Space.lg),
            accent = true,
        ) {
            SectionTitle(
                title = "方案盈亏",
                trailing = {
                    IconButton(onClick = { reloadKey++ }, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "结算并刷新",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            Spacer(Modifier.height(Space.md))

            if (pnl.hasSettled) {
                // 主指标：净盈亏
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (pnl.net > 0) "+${ParlayMath.money(pnl.net)}"
                        else if (pnl.net < 0) "-${ParlayMath.money(-pnl.net)}"
                        else "0",
                        style = MaterialTheme.typography.displaySmall.tabular(),
                        color = netColor(pnl.net)
                    )
                    Text(
                        "元",
                        Modifier.padding(start = 3.dp, bottom = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "收益率",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            roiText(pnl.roi),
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            color = netColor(pnl.net)
                        )
                    }
                }

                Spacer(Modifier.height(Space.md))
                Hairline()
                Spacer(Modifier.height(Space.md))

                Row(Modifier.fillMaxWidth()) {
                    StatTile(
                        label = "总投入（已结算）",
                        value = ParlayMath.money(pnl.stake),
                        unit = "元",
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "总回报（已结算）",
                        value = ParlayMath.money(pnl.payout),
                        unit = "元",
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Space.md))
                Row(Modifier.fillMaxWidth()) {
                    StatTile(
                        label = "方案命中率",
                        value = rateText(pnl.hitRate),
                        hint = "${pnl.wonCount} / ${pnl.settledCount} 单",
                        valueColor = if (pnl.hitRate > 0.5) Tone.hit else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    StatTile(
                        label = "单笔最大盈利",
                        value = "+${ParlayMath.money(pnl.bestProfit)}",
                        unit = "元",
                        valueColor = if (pnl.bestProfit > 0) Tone.hit else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(Space.sm))
                Hairline()
                Spacer(Modifier.height(Space.xs))

                KeyValueRow("已结算单数", "${pnl.settledCount} 单")
                KeyValueRow("中奖 / 未中", "${pnl.wonCount} / ${pnl.lostCount} 单")
                KeyValueRow(
                    "单笔最大亏损",
                    if (pnl.worstLoss < 0) "-${ParlayMath.money(-pnl.worstLoss)} 元" else "0 元",
                    valueColor = Tone.miss
                )
                KeyValueRow(
                    "最长连红 / 连黑",
                    "${pnl.maxWinStreak} / ${pnl.maxLoseStreak} 单",
                )
            } else {
                Text(
                    "暂无已结算方案：比赛完赛后会按官方赛果自动结算，届时这里展示真实盈亏",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (pnl.bestProfit > 0) {
                    Spacer(Modifier.height(Space.sm))
                    KeyValueRow("单笔最大盈利", "+${ParlayMath.money(pnl.bestProfit)} 元", valueColor = Tone.hit)
                }
            }

            // 未结算：单独统计，不并入上面的盈亏与收益率
            Spacer(Modifier.height(Space.md))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(Corner.md)
                    .background(Tone.track())
                    .padding(Space.md)
            ) {
                Column {
                    Text(
                        if (pnl.pendingCount > 0) "未结算 ${pnl.pendingCount} 单（不计入上面的盈亏与收益率）"
                        else "当前没有未结算方案",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (pnl.pendingCount > 0) {
                        Spacer(Modifier.height(Space.sm))
                        Row(Modifier.fillMaxWidth()) {
                            StatTile(
                                label = "占用本金",
                                value = ParlayMath.money(pnl.pendingStake),
                                unit = "元",
                                valueSize = 16,
                                modifier = Modifier.weight(1f)
                            )
                            StatTile(
                                label = "理论最高回报",
                                value = ParlayMath.money(pnl.pendingMaxPrize),
                                unit = "元",
                                hint = "全部命中时",
                                valueSize = 16,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Space.section))

        // ===== 设置 =====
        SurfaceCard(modifier = Modifier.padding(horizontal = Space.lg)) {
            SettingRow(
                icon = if (darkTheme) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                title = "深色模式",
                sub = "夜间观赛更护眼",
                onClick = { onThemeChange(!darkTheme) }
            ) {
                Switch(
                    checked = darkTheme,
                    onCheckedChange = onThemeChange,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Hairline(startPadding = 52.dp)
            SettingRow(
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = "方案中心",
                sub = "已保存的方案与命中情况",
                onClick = onOpenSlips
            ) { Chevron() }
            Hairline(startPadding = 52.dp)
            SettingRow(
                icon = Icons.Outlined.Tune,
                title = "模型配置",
                sub = "配置服务地址与模型，用于生成解读",
                onClick = onOpenLlmConfig
            ) { Chevron() }
            Hairline(startPadding = 52.dp)
            SettingRow(
                icon = Icons.Outlined.OpenInNew,
                title = "关于",
                sub = "版本 $APP_VERSION · 开源仓库 $REPO_URL",
                onClick = { openUrl(context, REPO_URL) }
            ) { Chevron() }
        }

        Text(
            "数据取自竞彩官方接口；盈亏按官方玩法口径在本地计算，仅供参考 · 理性购彩",
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.xl),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 盈亏配色：正 = 红（盈利）、负 = 绿（亏损）、零 = 中性 */
@Composable
private fun netColor(net: Double): Color = when {
    net > 0 -> Tone.gain()
    net < 0 -> Tone.loss()
    else -> MaterialTheme.colorScheme.onSurface
}

/** 收益率文案：带符号、保留 1 位小数 */
private fun roiText(roi: Double): String {
    val pct = roi * 100
    val sign = if (pct > 0) "+" else if (pct < 0) "-" else ""
    return "$sign${"%.1f".format(kotlin.math.abs(pct))}%"
}

/** 比率文案：保留 1 位小数百分比 */
private fun rateText(rate: Double): String = "${"%.1f".format(rate * 100)}%"

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    sub: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        IconBadge(icon = icon, size = 32.dp)
        Column(Modifier.padding(start = Space.md).weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(Space.sm))
        trailing()
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Outlined.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp)
    )
}
