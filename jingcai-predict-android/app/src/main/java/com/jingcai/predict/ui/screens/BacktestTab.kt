package com.jingcai.predict.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.backtest.BacktestOverview
import com.jingcai.predict.data.backtest.BacktestStats
import com.jingcai.predict.data.backtest.CalibDirection
import com.jingcai.predict.data.backtest.CalibrationBin
import com.jingcai.predict.data.backtest.LeagueCalibration
import com.jingcai.predict.data.llm.CombinedPrediction
import com.jingcai.predict.data.predict.LeagueProfile
import com.jingcai.predict.data.predict.LeagueProfiles
import com.jingcai.predict.data.predict.ProfileRepository
import com.jingcai.predict.ui.components.EmptyState
import com.jingcai.predict.ui.components.Hairline
import com.jingcai.predict.ui.components.KeyValueRow
import com.jingcai.predict.ui.components.PillTag
import com.jingcai.predict.ui.components.SectionTitle
import com.jingcai.predict.ui.components.StatTile
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.components.UiMessage
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 回测复盘：**统计回测 + 参数校准**（纯统计，不涉及任何训练或学习过程）。
 *
 * 数据源与排行页一致 —— 仅使用已回写真实赛果的历史快照（`picks` 中 `hit != null`）：
 * 1. 总览：已结算场次 / 玩法项 / 命中率，按玩法拆分；
 * 2. 校准分析：预测概率分箱 vs 实际命中频率，偏差 = 实际 − 预测；
 * 3. 参数校准建议：按联赛聚合（已结算 ≥3 场），给出参数调整建议并可一键应用 / 恢复默认；
 * 4. 逐场复盘：按更新时间倒序最多 20 场，逐项列出命中明细。
 *
 * 无数据时一律显示 `--` / 暂无数据并说明原因，不填充任何占位数字。
 */
@Composable
fun BacktestContent(all: List<CombinedPrediction>) {
    val overview = remember(all) { BacktestStats.overview(all) }
    val bins = remember(all) { BacktestStats.calibrationBins(all) }
    val suggestions = remember(all) { BacktestStats.leagueSuggestions(all) }
    val reviews = remember(all) { BacktestStats.reviewList(all) }

    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        contentPadding = PaddingValues(bottom = Space.xl)
    ) {
        item {
            SectionTitle(
                title = "总览",
                subtitle = "真实赛果结算",
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs)
            )
        }
        item { OverviewSection(overview) }

        item {
            SectionTitle(
                title = "校准分析",
                subtitle = "概率 vs 实际 · 偏差 = 实际 − 预测（≥ ${(BacktestStats.SIGNIFICANT_PP * 100).toInt()}pp 为显著）",
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs)
            )
        }
        item { CalibrationSection(bins) }

        item {
            SectionTitle(
                title = "参数校准建议",
                subtitle = "按联赛 · 已结算 ≥ ${BacktestStats.MIN_LEAGUE_MATCHES} 场才给建议 · 可一键应用、可回退",
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs)
            )
        }
        if (suggestions.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Info,
                    title = "暂无可给出建议的联赛",
                    description = "已结算快照需 ≥ ${BacktestStats.MIN_LEAGUE_MATCHES} 场（小样本噪声大），" +
                        "结算更多场次后此处会自动出现建议。",
                    modifier = Modifier.padding(horizontal = Space.lg)
                )
            }
        }
        items(suggestions.size, key = { suggestions[it].league }) { i ->
            SuggestionCard(suggestions[i])
        }

        item {
            SectionTitle(
                title = "逐场复盘",
                subtitle = "按更新时间倒序 · 最多 ${BacktestStats.REVIEW_LIMIT} 场",
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs)
            )
        }
        if (reviews.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Info,
                    title = "暂无已结算场次",
                    description = "比赛完赛并回写真实赛果后自动生成，可逐场查看命中明细。",
                    modifier = Modifier.padding(horizontal = Space.lg)
                )
            }
        }
        items(reviews.size, key = { reviews[it].matchId }) { i ->
            ReviewCard(reviews[i])
        }

        item {
            Text(
                "以上全部为真实赛果统计：只做概率与实际的对比校准，不涉及任何训练或学习过程。",
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm, bottom = Space.sm, start = Space.lg, end = Space.lg),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}

/* ================= 通用小件 ================= */

@Composable
private fun HintCard(text: String) {
    SurfaceCard(modifier = Modifier.padding(horizontal = Space.lg)) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
        )
    }
}

/** 轻量行：左标签（labelSmall）+ 右数值（等宽、右对齐） */
@Composable
private fun StatLine(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.tabular(),
            fontWeight = FontWeight.SemiBold,
            color = if (valueColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else valueColor,
            maxLines = 1
        )
    }
}

/** 命中状态行：左标签 + 右侧命中状态胶囊（命中红 / 未中绿 / 待结算中性） */
@Composable
private fun StatusLine(label: String, hit: Boolean?) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        HitPill(hit)
    }
}

/** 命中状态胶囊：命中（红）/ 未中（绿）/ 待结算（中性） */
@Composable
private fun HitPill(hit: Boolean?) {
    when (hit) {
        true -> PillTag("命中", color = HitRed)
        false -> PillTag("未中", color = MissGreen)
        null -> PillTag("待结算", color = Tone.pending())
    }
}

/** 复盘三栏：左侧栏名（固定宽度）+ 右侧内容，保证三栏纵向对齐 */
@Composable
private fun ReviewBlock(label: String, content: @Composable ColumnScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs)
    ) {
        Text(
            label,
            Modifier.width(56.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            content = content
        )
    }
}

/** 克制的主/次操作按钮：主色淡填充或中性淡填充，不使用阴影 */
@Composable
private fun ActionButton(text: String, primary: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = Corner.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                Tone.fill()
            },
            contentColor = if (primary) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/* ================= ① 总览 ================= */

@Composable
private fun OverviewSection(o: BacktestOverview) {
    if (!o.hasData) {
        HintCard("暂无已结算数据（比赛完赛并回写真实赛果后自动统计）")
        return
    }
    SurfaceCard(modifier = Modifier.padding(horizontal = Space.lg)) {
        Row(Modifier.fillMaxWidth()) {
            StatTile("已结算场次", "${o.settledMatches}", Modifier.weight(1f), unit = "场")
            StatTile("已结算玩法项", "${o.settledPicks}", Modifier.weight(1f), unit = "项")
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth()) {
            StatTile(
                label = "命中项",
                value = "${o.hitPicks}",
                modifier = Modifier.weight(1f),
                unit = "项",
                valueColor = HitRed
            )
            StatTile(
                label = "整体命中率",
                value = BacktestStats.rateText(o.overallRate),
                modifier = Modifier.weight(1f),
                valueColor = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(Space.md))
        Hairline()
        Spacer(Modifier.height(Space.xs))
        o.playStats.forEach { s ->
            KeyValueRow("${s.label}命中率", "${BacktestStats.rateText(s.rate)}（${s.hit}/${s.settled}）")
        }
        KeyValueRow(
            "最具价值命中率",
            "${BacktestStats.rateText(o.bestValueRate)}（${o.bestValueHit}/${o.bestValueSettled}）",
            valueColor = MaterialTheme.colorScheme.primary,
            bold = true
        )
        KeyValueRow("最稳健命中率", "${BacktestStats.rateText(o.safestRate)}（${o.safestHit}/${o.safestSettled}）")
        Spacer(Modifier.height(Space.sm))
        Text(
            "口径：整体命中率 = 命中玩法数 ÷ 已结算玩法数（hit != null 的项）；未结算场次不计入。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
    }
}

/* ================= ② 校准分析 ================= */

@Composable
private fun CalibrationSection(bins: List<CalibrationBin>) {
    SurfaceCard(modifier = Modifier.padding(horizontal = Space.lg)) {
        Row(Modifier.fillMaxWidth()) {
            CalibCell("概率区间", 1.1f, header = true)
            CalibCell("项数", 0.7f, header = true)
            CalibCell("平均预测", 1f, header = true)
            CalibCell("实际命中", 1f, header = true)
            CalibCell("偏差", 1f, header = true)
        }
        Spacer(Modifier.height(Space.xs))
        Hairline()
        bins.forEach { b ->
            val dev = b.deviation
            val significant = dev != null && abs(dev) >= BacktestStats.SIGNIFICANT_PP
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CalibCell(
                    b.label,
                    1.1f,
                    color = MaterialTheme.colorScheme.onSurface,
                    bold = true
                )
                CalibCell(if (b.count == 0) "--" else "${b.count}", 0.7f)
                CalibCell(BacktestStats.rateText(b.avgProbability), 1f)
                CalibCell(BacktestStats.rateText(b.actualRate), 1f)
                CalibCell(
                    BacktestStats.deviationText(dev),
                    1f,
                    color = if (significant) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    bold = significant
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "偏差 = 实际命中率 − 平均预测概率；空箱（无已结算项）显示 --。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
        Text(
            "校准分析 = 把预测概率与实际命中频率对比，偏差用于校准参数，不涉及任何训练或学习。",
            Modifier.padding(top = Space.xxs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun RowScope.CalibCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    color: Color = Color.Unspecified,
    bold: Boolean = false,
) {
    Text(
        text,
        Modifier.weight(weight),
        style = if (header) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall.tabular(),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = if (color == Color.Unspecified) {
            if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        } else {
            color
        },
        textAlign = TextAlign.Center,
        maxLines = 1
    )
}

/* ================= ③ 参数校准建议 ================= */

@Composable
private fun SuggestionCard(c: LeagueCalibration) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val base = remember(c.league) { LeagueProfiles.forLeague(c.league) }
    var profile by remember(c.league) { mutableStateOf<LeagueProfile?>(null) }
    var version by remember(c.league) { mutableIntStateOf(0) }

    // 读取该联赛当前生效参数（可能已被用户校准过），保证建议基于当前值计算
    LaunchedEffect(c.league, version) {
        profile = ProfileRepository.getProfile(context, c.league)
    }

    val current = profile
    val applied = current != null && BacktestStats.isUserOverride(current)
    val actualRate = if (c.settledPicks == 0) null else c.hitPicks.toDouble() / c.settledPicks
    val target = when (c.direction) {
        CalibDirection.OPTIMISTIC -> BacktestStats.adjustedForOptimistic(current ?: base)
        CalibDirection.CONSERVATIVE -> BacktestStats.adjustedForConservative(current ?: base)
        null -> null
    }

    SurfaceCard(modifier = Modifier.padding(horizontal = Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.league,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            PillTag("样本 ${c.settledMatches} 场", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.xs))
            DirectionPill(c.direction)
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            "校准依据（胜平负 + 让球）：已结算 ${c.settledPicks} 项 · 平均预测概率 " +
                "${BacktestStats.rateText(c.avgProbability)} · 实际命中 ${BacktestStats.rateText(actualRate)} · " +
                "偏差 ${BacktestStats.deviationText(c.deviation)}",
            style = MaterialTheme.typography.bodySmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            when (c.direction) {
                CalibDirection.OPTIMISTIC ->
                    "该联赛预测偏乐观：把 drawBias +0.01、市场权重 −0.05、统计权重 +0.05（更保守、更依赖统计）"
                CalibDirection.CONSERVATIVE ->
                    "该联赛预测偏保守：把 drawBias −0.01、市场权重 +0.05、统计权重 −0.05"
                null -> "偏差在 ±5pp 内，无需调整"
            },
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (target != null) {
            val from = current ?: base
            Spacer(Modifier.height(Space.sm))
            Hairline()
            Spacer(Modifier.height(Space.sm))
            Text(
                "当前参数",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "drawBias ${BacktestStats.paramText(from.drawBias)} · " +
                    "市场 ${BacktestStats.paramText(from.weights.market)} · " +
                    "泊松 ${BacktestStats.paramText(from.weights.poisson)} · " +
                    "统计 ${BacktestStats.paramText(from.weights.stat)}",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(Space.xs))
            Text(
                "建议参数",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "drawBias ${BacktestStats.paramText(target.drawBias)} · " +
                    "市场 ${BacktestStats.paramText(target.weights.market)} · " +
                    "泊松 ${BacktestStats.paramText(target.weights.poisson)} · " +
                    "统计 ${BacktestStats.paramText(target.weights.stat)}" +
                    "（avgGoals / homeAdv / rho 保持不变）",
                style = MaterialTheme.typography.bodySmall.tabular(),
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (applied) {
                Text(
                    "已应用（再次校准前请先恢复默认）",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                ActionButton(text = "恢复默认", primary = false) {
                    scope.launch {
                        ProfileRepository.reset(context, c.league)
                        version += 1
                        UiMessage.info("已恢复 ${c.league} 默认参数")
                    }
                }
            } else if (target != null) {
                Spacer(Modifier.weight(1f))
                ActionButton(text = "应用", primary = true) {
                    scope.launch {
                        val cur = ProfileRepository.getProfile(context, c.league)
                        val newProfile = when (c.direction) {
                            CalibDirection.OPTIMISTIC -> BacktestStats.adjustedForOptimistic(cur)
                            CalibDirection.CONSERVATIVE -> BacktestStats.adjustedForConservative(cur)
                            null -> cur
                        }
                        ProfileRepository.saveProfile(context, c.league, newProfile)
                        version += 1
                        UiMessage.success("已应用 ${c.league} 校准参数，后续预测按新参数计算")
                    }
                }
            }
        }
    }
}

/** 校准方向标签：偏乐观（预警色）/ 偏保守（信息色）/ 无需调整（中性） */
@Composable
private fun DirectionPill(direction: CalibDirection?) {
    when (direction) {
        CalibDirection.OPTIMISTIC -> PillTag("预测偏乐观", color = MaterialTheme.colorScheme.tertiary)
        CalibDirection.CONSERVATIVE -> PillTag("预测偏保守", color = MaterialTheme.colorScheme.secondary)
        null -> PillTag("无需调整", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ================= ④ 逐场复盘 ================= */

@Composable
private fun ReviewCard(p: CombinedPrediction) {
    val judged = p.picks.filter { it.hit != null }

    SurfaceCard(modifier = Modifier.padding(horizontal = Space.lg)) {
        // 头部：联赛 · 编号 + 已完赛
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${p.league} · ${p.matchNum}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            PillTag("已完赛", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Space.xs))
        Text(
            "${p.home} VS ${p.away}",
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1
        )

        // 赛前：综合置信度 + 各玩法选项与模型概率
        ReviewBlock("赛前") {
            StatLine("综合置信度", "${p.rankConfidence}", valueColor = MaterialTheme.colorScheme.primary)
            p.picks.forEach { k ->
                StatLine(
                    "${BacktestStats.playLabel(k.play)} ${k.option}",
                    "模型概率 ${BacktestStats.probText(k.probability)}"
                )
            }
        }

        // 赛中/赛后：命中明细（命中红、未中绿）
        if (judged.isEmpty()) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "本场无赛果数据，无法判定",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@SurfaceCard
        }

        ReviewBlock("赛中·赛后") {
            judged.forEach { k ->
                StatusLine("${BacktestStats.playLabel(k.play)} ${k.option}", k.hit)
            }
        }

        // 价值口径单列一栏
        if (p.bestValue != null || p.safest != null) {
            ReviewBlock("价值口径") {
                p.bestValue?.let {
                    StatusLine("最具价值 · ${BacktestStats.playLabel(it.play)} ${it.option}", it.hit)
                }
                p.safest?.let {
                    StatusLine("最稳健 · ${BacktestStats.playLabel(it.play)} ${it.option}", it.hit)
                }
            }
        }
    }
}
