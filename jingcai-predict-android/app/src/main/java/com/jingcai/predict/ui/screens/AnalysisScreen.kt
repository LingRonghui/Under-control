package com.jingcai.predict.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.backtest.BacktestOverview
import com.jingcai.predict.data.backtest.BacktestStats
import com.jingcai.predict.data.backtest.SettlementRunner
import com.jingcai.predict.data.llm.AiPredictionStore
import com.jingcai.predict.data.llm.BatchState
import com.jingcai.predict.data.llm.CombinedPick
import com.jingcai.predict.data.llm.CombinedPrediction
import com.jingcai.predict.data.llm.PredictionBatchRunner
import com.jingcai.predict.ui.components.EmptyState
import com.jingcai.predict.ui.components.Hairline
import com.jingcai.predict.ui.components.HeroNumber
import com.jingcai.predict.ui.components.IconBadge
import com.jingcai.predict.ui.components.PillTag
import com.jingcai.predict.ui.components.SectionTitle
import com.jingcai.predict.ui.components.SegmentedTabs
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.components.ThinProgress
import com.jingcai.predict.ui.components.UiMessage
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 命中红：仅用于「已完赛命中」相关内容 */
internal val HitRed: Color = Tone.hit

/** 未中绿：仅用于「已完赛未命中」相关内容 */
internal val MissGreen: Color = Tone.miss

/**
 * 预测分析：分为「置信度排行」与「回测复盘」两个分类。
 *
 * 数据来源：本地综合预测快照缓存（只读）。App 启动后由后台对今明两日赛程批量生成结果并长期保留，
 * 本页只读缓存、不做二次预测；后台进度与失败重跑入口位于两个分类之上，两页都可见。
 *
 * @param onOpenMatch 点击排行卡片进入该场比赛详情页（由 AppRoot 负责赋值与跳转）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(onOpenMatch: (CombinedPrediction) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshots by AiPredictionStore.items.collectAsState()
    val batch by PredictionBatchRunner.state.collectAsState()
    var refreshing by remember { mutableStateOf(false) }
    var tabIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { AiPredictionStore.loadOnce(context) }
    // 定时刷新本地缓存：后台批量预测进行中时列表能自动长出来；页面销毁即停止
    // 顺带做结算回填（内部节流 60 秒，不会频繁请求接口），让已完赛场次的命中自动出现
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            SettlementRunner.settleAll(context)
            AiPredictionStore.reload(context)
        }
    }

    // 已结算口径的真实统计（无数据时各项为 null，界面显示 --）
    val overview = remember(snapshots) { BacktestStats.overview(snapshots) }
    // 置信度排行：只展示今明两日，按综合置信度降序取前 10
    val shown = remember(snapshots) {
        snapshots.filter { isTodayOrTomorrow(it.kickoff) }
            .sortedByDescending { it.rankConfidence }
            .take(10)
    }

    fun refreshFromDisk() {
        refreshing = true
        scope.launch {
            AiPredictionStore.reload(context)
            refreshing = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 品牌栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(Icons.Outlined.Analytics, size = 30.dp)
            Text(
                "预测分析",
                Modifier.padding(start = Space.sm),
                style = MaterialTheme.typography.titleMedium,
                color = Tone.textStrong()
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (tabIndex == 0) "共 ${shown.size} 场" else "已结算 ${overview.settledMatches} 场",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = Tone.textLabel()
            )
        }

        // 后台预测进度 + 失败重跑入口：位于两个分类之上，两页都可见
        if (batch.running || batch.error != null || batch.total > 0 || batch.failed > 0) {
            BatchBanner(batch)
        }

        // 两个分类
        SegmentedTabs(
            items = listOf("置信度排行", "回测复盘"),
            selectedIndex = tabIndex,
            onSelect = { tabIndex = it },
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm)
        )

        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshFromDisk() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (tabIndex == 0) {
                RankingTab(
                    shown = shown,
                    overview = overview,
                    running = batch.running,
                    onOpenMatch = onOpenMatch,
                    onRefresh = {
                        scope.launch {
                            // 手动刷新：强制结算一次（跳过 60 秒节流），再重读本地结果
                            SettlementRunner.settleAll(context, force = true)
                            AiPredictionStore.reload(context)
                            UiMessage.success("已刷新本地结果")
                        }
                    }
                )
            } else {
                BacktestContent(all = snapshots)
            }
        }
    }
}

/* ================= 置信度排行 ================= */

@Composable
private fun RankingTab(
    shown: List<CombinedPrediction>,
    overview: BacktestOverview,
    running: Boolean,
    onOpenMatch: (CombinedPrediction) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Space.md),
        contentPadding = PaddingValues(bottom = Space.xl)
    ) {
        item { DashboardCard(overview, onRefresh) }
        item {
            SectionTitle(
                title = "今明两日综合置信度排行",
                subtitle = "按综合置信度降序 · 前 ${shown.size} 场",
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs)
            )
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.Analytics,
                    title = if (running) "正在生成今明两日预测结果" else "今明两日暂无预测结果",
                    description = if (running) {
                        "后台批量预测进行中，完成后会自动显示在这里"
                    } else {
                        "可下拉刷新，或稍后重新进入本页"
                    },
                    modifier = Modifier.padding(horizontal = Space.lg)
                )
            }
        }
        items(shown.size, key = { shown[it].matchId }) { idx ->
            CombinedCard(shown[idx], onOpenMatch)
        }
        item {
            Text(
                "预测结果仅供参考，理性购彩",
                Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm, bottom = Space.sm),
                style = MaterialTheme.typography.labelSmall,
                color = Tone.textHint(),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

/* ================= 仪表盘（本页视觉主角） ================= */

/**
 * 顶部仪表盘：半圆 Canvas 仪表盘显示整体命中率，下方用克制的网格排布
 * 5 个玩法命中率 + 最具价值 / 最稳健命中率 + 平均综合置信度（高度约屏幕 1/3）。
 */
@Composable
private fun DashboardCard(o: BacktestOverview, onRefresh: () -> Unit) {
    // 高度约屏幕 1/3；下限保证中心大号命中率与下方指标网格都不被挤压
    val cardHeight = (LocalConfiguration.current.screenHeightDp.dp / 3).coerceAtLeast(256.dp)
    // 整体命中率（0..1），无数据时为 0；仪表盘入场动画的起点固定为 0
    val rate = (o.overallRate ?: 0.0).toFloat().coerceIn(0f, 1f)
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val animated by animateFloatAsState(
        targetValue = if (appeared) rate else 0f,
        animationSpec = tween(820),
        label = "gauge"
    )

    SurfaceCard(
        modifier = Modifier
            .padding(horizontal = Space.lg)
            .height(cardHeight),
        accent = true,
        contentPadding = PaddingValues(Space.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("命中率仪表盘", style = MaterialTheme.typography.titleMedium, color = Tone.textStrong())
                Text(
                    if (o.hasData) {
                        "真实赛果结算 · 已结算 ${o.settledMatches} 场 / ${o.settledPicks} 项"
                    } else {
                        "真实赛果结算"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = Tone.textLabel()
                )
            }
            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "更新赛果",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (!o.hasData) {
            // 无已结算数据：如实显示占位，不编造任何数字
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HeroNumber("--", size = 40, color = Tone.textHint())
                    Text(
                        "整体命中率",
                        Modifier.padding(top = Space.xxs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Tone.textLabel()
                    )
                    Text(
                        "暂无已结算数据（比赛完赛并回写真实赛果后自动统计）",
                        Modifier.padding(top = Space.sm),
                        style = MaterialTheme.typography.labelSmall,
                        color = Tone.textHint(),
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
            return@SurfaceCard
        }

        // 半圆仪表盘：极淡轨道 + 品牌色渐变进度弧（端点圆头），中心大号整体命中率
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            HalfGauge(
                progress = animated,
                trackColor = Tone.track(),
                modifier = Modifier.fillMaxSize()
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Space.xxs),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HeroNumber(BacktestStats.rateText(o.overallRate), size = 40)
                Text(
                    "整体命中率",
                    style = MaterialTheme.typography.labelMedium,
                    color = Tone.textLabel()
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        Hairline()
        Spacer(Modifier.height(Space.sm))

        // 指标网格：2 列 × 4 行（玩法命中率 → 平均综合置信度 → 价值口径）
        val metrics: List<Pair<String, String>> = buildList {
            o.playStats.forEach { add("${it.label}命中率" to BacktestStats.rateText(it.rate)) }
            add("平均综合置信度" to (o.avgRankConfidence?.let { v -> "${v.roundToInt()}" } ?: "--"))
            add("最具价值命中率" to BacktestStats.rateText(o.bestValueRate))
            add("最稳健命中率" to BacktestStats.rateText(o.safestRate))
        }
        metrics.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) -> MetricItem(label, value, Modifier.weight(1f)) }
                repeat(2 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** 半圆仪表盘：极淡底轨 + 品牌色到青色渐变进度弧（端点圆头） */
@Composable
private fun HalfGauge(
    progress: Float,
    trackColor: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 12.dp,
) {
    val brand = Tone.brand
    val teal = Tone.teal
    Canvas(modifier) {
        val sw = stroke.toPx()
        val radius = minOf((size.width - sw) / 2f, size.height - sw)
        if (radius <= 0f) return@Canvas
        val topLeft = Offset(size.width / 2f - radius, size.height - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        val style = Stroke(width = sw, cap = StrokeCap.Round)

        drawArc(
            color = trackColor,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = style
        )

        val v = progress.coerceIn(0f, 1f)
        if (v > 0f) {
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(brand, teal),
                    start = Offset(topLeft.x, topLeft.y),
                    end = Offset(topLeft.x + arcSize.width, topLeft.y)
                ),
                startAngle = 180f,
                sweepAngle = (180f * v).coerceAtLeast(0.6f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
        }
    }
}

/** 指标网格单元：标签（次要）+ 数值（等宽加粗、近白高对比） */
@Composable
private fun MetricItem(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(end = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = Tone.textLabel(),
            maxLines = 1
        )
        Text(
            value,
            Modifier.padding(start = Space.xs),
            style = MaterialTheme.typography.titleSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = Tone.textStrong(),
            maxLines = 1
        )
    }
}

/* ================= 后台预测进度 ================= */

@Composable
private fun BatchBanner(s: BatchState) {
    val context = LocalContext.current
    SurfaceCard(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs)) {
        if (s.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "正在批量预测今明两日赛程",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = Tone.textBody()
                )
                Text(
                    if (s.total > 0) "${s.done}/${s.total}" else "…",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = Tone.textStrong()
                )
            }
            if (s.total > 0) {
                Spacer(Modifier.height(Space.sm))
                ThinProgress(s.done.toFloat() / s.total)
            }
            if (s.current.isNotBlank()) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    s.current,
                    style = MaterialTheme.typography.labelSmall,
                    color = Tone.textLabel(),
                    maxLines = 1
                )
            }
        } else if (s.failed == 0 && s.error != null) {
            Text(
                s.error,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                lineHeight = 16.sp
            )
        } else if (s.failed == 0 && s.total > 0) {
            Text(
                "后台预测已完成（共 ${s.total} 场）· 结果已保留，重复进入不会重复预测",
                style = MaterialTheme.typography.labelSmall,
                color = Tone.textBody(),
                lineHeight = 15.sp
            )
        }

        // 失败提示 + 「只重跑出错场次」入口
        if (s.failed > 0) {
            if (s.running) Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "有 ${s.failed} 场未能完成预测（多为网络或服务波动），可只重跑这些场次",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(
                    onClick = {
                        PredictionBatchRunner.retryFailed(context)
                        UiMessage.info("已开始重跑出错的场次")
                    },
                    // 预测进行中（含重跑中）不能再次触发
                    enabled = !s.running
                ) {
                    Text("重新预测出错的 ${s.failed} 场", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/* ================= 综合预测卡片 ================= */

/** 置信度配色：≥70 主色、50-69 近白高对比、<50 提示灰（数值一律走对比度阶梯） */
@Composable
private fun confColor(v: Int): Color = when {
    v >= 70 -> MaterialTheme.colorScheme.primary
    v >= 50 -> Tone.textStrong()
    else -> Tone.textHint()
}

/** 命中状态配色：命中红、未中绿、未结算中性（近白高对比） */
@Composable
private fun hitColor(hit: Boolean?): Color = when (hit) {
    true -> HitRed
    false -> MissGreen
    null -> Tone.textStrong()
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

private fun adviceText(p: CombinedPick): String = "${BacktestStats.playLabel(p.play)} ${p.option}"

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun fmtTime(ts: Long): String =
    if (ts <= 0L) "--"
    else Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFmt)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombinedCard(p: CombinedPrediction, onOpenMatch: (CombinedPrediction) -> Unit) {
    SurfaceCard(
        modifier = Modifier.padding(horizontal = Space.lg),
        onClick = { onOpenMatch(p) },
        contentPadding = PaddingValues(Space.lg)
    ) {
        // 联赛 · 编号 · 开赛时间
        Text(
            "${p.league} · ${p.matchNum} · ${p.kickoff}开赛",
            style = MaterialTheme.typography.labelSmall,
            color = Tone.textLabel(),
            maxLines = 1
        )

        // 对阵 + 综合置信度
        Spacer(Modifier.height(Space.xs))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${p.home} VS ${p.away}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Tone.textStrong(),
                maxLines = 2
            )
            Column(
                Modifier.padding(start = Space.sm),
                horizontalAlignment = Alignment.End
            ) {
                HeroNumber("${p.rankConfidence}", size = 26, color = confColor(p.rankConfidence))
                Text(
                    "综合置信度",
                    style = MaterialTheme.typography.labelSmall,
                    color = Tone.textLabel()
                )
            }
        }

        Spacer(Modifier.height(Space.sm))
        ThinProgress(
            progress = p.rankConfidence / 100f,
            color = confColor(p.rankConfidence),
            height = 4.dp
        )

        // 各玩法综合选项（命中状态用胶囊：命中红 / 未中绿 / 待结算中性）
        if (p.picks.isNotEmpty()) {
            Spacer(Modifier.height(Space.md))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                p.picks.forEach { PickChip(it) }
            }
        }

        // 最具价值 / 最稳健（命中情况同色规则）
        if (p.bestValue != null || p.safest != null) {
            Spacer(Modifier.height(Space.md))
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                p.bestValue?.let { ValueLine("最具价值", it) }
                p.safest?.let { ValueLine("最稳健", it) }
            }
        }

        // 结论来源 + 更新时间（不展示任何具体模型名称）
        Spacer(Modifier.height(Space.md))
        Hairline()
        Spacer(Modifier.height(Space.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (p.model.isBlank()) "模型结果（未配置模型）" else "模型综合结论",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = Tone.textLabel(),
                maxLines = 1
            )
            Text(
                "更新 ${fmtTime(p.updatedAt)}",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = Tone.textLabel(),
                maxLines = 1
            )
        }
    }
}

/** 单玩法选项胶囊：命中状态 + 玩法 + 综合选项（选项为高对比数值） */
@Composable
private fun PickChip(pick: CombinedPick) {
    Row(
        Modifier
            .clip(Corner.sm)
            .background(Tone.fill())
            .padding(horizontal = Space.sm, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HitPill(pick.hit)
        Text(
            BacktestStats.playLabel(pick.play),
            Modifier.padding(start = Space.xs),
            style = MaterialTheme.typography.labelSmall,
            color = Tone.textLabel(),
            maxLines = 1
        )
        Text(
            pick.option,
            Modifier.padding(start = 3.dp),
            style = MaterialTheme.typography.titleSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = hitColor(pick.hit),
            maxLines = 1
        )
    }
}

/** 价值口径行：左侧口径名 + 右侧「命中前缀 + 玩法 选项」 */
@Composable
private fun ValueLine(tag: String, pick: CombinedPick) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            tag,
            Modifier.width(56.dp),
            style = MaterialTheme.typography.labelSmall,
            color = Tone.textLabel(),
            maxLines = 1
        )
        Text(
            hitPrefix(pick.hit) + adviceText(pick),
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.SemiBold,
            color = hitColor(pick.hit),
            maxLines = 1
        )
    }
}

/** 命中前缀：命中 → 「命中 · 」；未中 → 「未中 · 」；未结算 → 空 */
internal fun hitPrefix(hit: Boolean?): String = when (hit) {
    true -> "命中 · "
    false -> "未中 · "
    null -> ""
}

/* ================= 今明两日判定 ================= */

/**
 * kickoff 形如 "MM-dd HH:mm"：用当前年份补全为完整日期后与今天/明天比较。
 * 解析失败（含跨年等情况）保留展示，避免误过滤真实比赛。
 */
private fun isTodayOrTomorrow(kickoff: String): Boolean {
    val datePart = kickoff.substringBefore(' ').trim()
    if (datePart.isEmpty()) return true
    val today = LocalDate.now()
    fun parseOf(year: Int): LocalDate? = runCatching { LocalDate.parse("$year-$datePart") }.getOrNull()
    val date = parseOf(today.year) ?: return true
    // 跨年补正：以当年拼出的日期若明显早于今天（超过 30 天，例如今天 12-31、比赛 01-01），
    // 说明实际属于下一年，用 year+1 再解析一次，避免把「明天」误判为过去而过滤掉。
    val resolved = if (date.isBefore(today.minusDays(30))) parseOf(today.year + 1) ?: date else date
    return resolved == today || resolved == today.plusDays(1)
}
