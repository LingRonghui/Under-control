package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
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
import com.jingcai.predict.data.llm.CombinedPick
import com.jingcai.predict.data.llm.CombinedPrediction
import com.jingcai.predict.data.predict.LeagueProfile
import com.jingcai.predict.data.predict.LeagueProfiles
import com.jingcai.predict.data.predict.ProfileRepository
import com.jingcai.predict.ui.components.UiMessage
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 回测复盘：**统计回测 + 参数校准**（不是模型训练）。
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
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { SectionTitle("总览（真实赛果结算）") }
        item { OverviewSection(overview) }

        item { SectionTitle("校准分析（概率 vs 实际）") }
        item { CalibrationSection(bins) }

        item { SectionTitle("参数校准建议（可一键应用、可回退）") }
        if (suggestions.isEmpty()) {
            item {
                HintCard(
                    "暂无可给出建议的联赛：已结算快照需 ≥ ${BacktestStats.MIN_LEAGUE_MATCHES} 场（小样本噪声大），" +
                        "结算更多场次后此处会自动出现建议。"
                )
            }
        }
        items(suggestions.size, key = { suggestions[it].league }) { i ->
            SuggestionCard(suggestions[i])
        }

        item { SectionTitle("逐场复盘（按更新时间倒序，最多 ${BacktestStats.REVIEW_LIMIT} 场）") }
        if (reviews.isEmpty()) {
            item { HintCard("暂无已结算场次，无法逐场复盘（比赛完赛并回写真实赛果后自动生成）。") }
        }
        items(reviews.size, key = { reviews[it].matchId }) { i ->
            ReviewCard(reviews[i])
        }

        item {
            Text(
                "以上全部为真实赛果统计：仅做概率与实际的对比校准，不涉及任何模型训练。",
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 8.dp, start = 14.dp, end = 14.dp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )
        }
    }
}

/* ================= 通用小件 ================= */

@Composable
private fun SectionTitle(t: String) {
    Text(
        t,
        Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(13.dp),
        content = { content() }
    )
}

@Composable
private fun HintCard(text: String) {
    CardBox {
        Text(
            text,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
    }
}

/** 一行「左标签 + 右数值」 */
@Composable
private fun StatRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, Modifier.weight(1f), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.primary
        )
    }
}

/* ================= ① 总览 ================= */

@Composable
private fun OverviewSection(o: BacktestOverview) {
    if (!o.hasData) {
        HintCard("暂无已结算数据（比赛完赛并回写真实赛果后自动统计）")
        return
    }
    CardBox {
        Row(Modifier.fillMaxWidth()) {
            OverviewCell("已结算场次", "${o.settledMatches}", Modifier.weight(1f))
            OverviewCell("已结算玩法项", "${o.settledPicks}", Modifier.weight(1f))
            OverviewCell("命中项", "${o.hitPicks}", Modifier.weight(1f))
            OverviewCell("整体命中率", BacktestStats.rateText(o.overallRate), Modifier.weight(1f))
        }
        Spacer(Modifier.size(6.dp))
        o.playStats.forEach { s ->
            StatRow(
                "${s.label}命中率",
                "${BacktestStats.rateText(s.rate)}（${s.hit}/${s.settled}）"
            )
        }
        StatRow(
            "最具价值命中率",
            "${BacktestStats.rateText(o.bestValueRate)}（${o.bestValueHit}/${o.bestValueSettled}）"
        )
        StatRow(
            "最稳健命中率",
            "${BacktestStats.rateText(o.safestRate)}（${o.safestHit}/${o.safestSettled}）"
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "口径：整体命中率 = 命中玩法数 ÷ 已结算玩法数（hit != null 的项）；未结算场次不计入。",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun OverviewCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            Modifier.padding(top = 1.dp),
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

/* ================= ② 校准分析 ================= */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalibrationSection(bins: List<CalibrationBin>) {
    CardBox {
        Row(Modifier.fillMaxWidth()) {
            CalibCell("概率区间", 1.1f, header = true)
            CalibCell("项数", 0.7f, header = true)
            CalibCell("平均预测", 1f, header = true)
            CalibCell("实际命中", 1f, header = true)
            CalibCell("偏差", 1f, header = true)
        }
        bins.forEach { b ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dev = b.deviation
                CalibCell(b.label, 1.1f)
                CalibCell(if (b.count == 0) "--" else "${b.count}", 0.7f)
                CalibCell(BacktestStats.rateText(b.avgProbability), 1f)
                CalibCell(BacktestStats.rateText(b.actualRate), 1f)
                CalibCell(
                    BacktestStats.deviationText(dev),
                    1f,
                    color = if (dev != null && abs(dev) >= BacktestStats.SIGNIFICANT_PP) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "偏差 = 实际命中率 − 平均预测概率；空箱（无已结算项）显示 --。",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
        Text(
            "校准分析 = 把预测概率与实际命中频率对比，偏差用于校准参数，不做模型训练。",
            Modifier.padding(top = 2.dp),
            fontSize = 10.sp,
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
    color: androidx.compose.ui.graphics.Color? = null,
) {
    Text(
        text,
        Modifier.weight(weight),
        fontSize = if (header) 9.sp else 11.sp,
        fontWeight = if (header) FontWeight.Normal else FontWeight.Bold,
        color = color
            ?: if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        lineHeight = 13.sp
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

    CardBox {
        Text(
            "${c.league} · 样本 ${c.settledMatches} 场",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "校准依据（胜平负 + 让球）：已结算 ${c.settledPicks} 项 · 平均预测概率 " +
                "${BacktestStats.rateText(c.avgProbability)} · 实际命中 ${BacktestStats.rateText(actualRate)} · " +
                "偏差 ${BacktestStats.deviationText(c.deviation)}",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 16.sp
        )
        Spacer(Modifier.size(4.dp))
        Text(
            when (c.direction) {
                CalibDirection.OPTIMISTIC ->
                    "该联赛预测偏乐观：把 drawBias +0.01、市场权重 −0.05、统计权重 +0.05（更保守、更依赖统计）"
                CalibDirection.CONSERVATIVE ->
                    "该联赛预测偏保守：把 drawBias −0.01、市场权重 +0.05、统计权重 −0.05"
                null -> "偏差在 ±5pp 内，无需调整"
            },
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (target != null) {
            Spacer(Modifier.size(6.dp))
            val from = current ?: base
            Text(
                "当前参数：drawBias ${BacktestStats.paramText(from.drawBias)} · " +
                    "市场 ${BacktestStats.paramText(from.weights.market)} · " +
                    "泊松 ${BacktestStats.paramText(from.weights.poisson)} · " +
                    "统计 ${BacktestStats.paramText(from.weights.stat)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
            Text(
                "建议参数：drawBias ${BacktestStats.paramText(target.drawBias)} · " +
                    "市场 ${BacktestStats.paramText(target.weights.market)} · " +
                    "泊松 ${BacktestStats.paramText(target.weights.poisson)} · " +
                    "统计 ${BacktestStats.paramText(target.weights.stat)}" +
                    "（avgGoals / homeAdv / rho 保持不变）",
                Modifier.padding(top = 2.dp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                lineHeight = 15.sp
            )
        }

        Spacer(Modifier.size(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (applied) {
                Text(
                    "已应用（再次校准前请先恢复默认）",
                    Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = {
                    scope.launch {
                        ProfileRepository.reset(context, c.league)
                        version += 1
                        UiMessage.info("已恢复 ${c.league} 默认参数")
                    }
                }) {
                    Text("恢复默认", fontSize = 11.sp)
                }
            } else if (target != null) {
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
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
                ) {
                    Text("应用", fontSize = 12.sp)
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ================= ④ 逐场复盘 ================= */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewCard(p: CombinedPrediction) {
    val judged = p.picks.filter { it.hit != null }

    CardBox {
        // 头部：联赛 · 编号 · 主队 VS 客队 + 已完赛
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${p.league} · ${p.matchNum} · ${p.home} VS ${p.away}",
                Modifier.weight(1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "已完赛",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 赛前：综合置信度 + 各玩法选项与架构概率
        Spacer(Modifier.size(6.dp))
        Text(
            "赛前：综合置信度 ${p.rankConfidence}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            p.picks.forEach { k ->
                Text(
                    "${BacktestStats.playLabel(k.play)} ${k.option}（架构概率 ${BacktestStats.probText(k.probability)}）",
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 赛中/赛后：命中明细（命中红、未中绿）
        if (judged.isEmpty()) {
            Spacer(Modifier.size(6.dp))
            Text(
                "本场无赛果数据，无法判定",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        Spacer(Modifier.size(8.dp))
        Text("赛中/赛后结算", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            judged.forEach { k ->
                val ok = k.hit == true
                Text(
                    "${BacktestStats.playLabel(k.play)} ${k.option} → ${if (ok) "命中" else "未命中"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) HitRed else MissGreen
                )
            }
        }

        // 价值口径单列一行
        Spacer(Modifier.size(6.dp))
        ValueHitLine("最具价值", p.bestValue)
        ValueHitLine("最稳健", p.safest)
    }
}

@Composable
private fun ValueHitLine(tag: String, pick: CombinedPick?) {
    if (pick == null) return
    val color = when (pick.hit) {
        true -> HitRed
        false -> MissGreen
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        "$tag：${BacktestStats.playLabel(pick.play)} ${pick.option} → " +
            when (pick.hit) {
                true -> "命中"
                false -> "未命中"
                null -> "未回写赛果"
            },
        Modifier.padding(top = 2.dp),
        fontSize = 11.sp,
        color = color
    )
}
