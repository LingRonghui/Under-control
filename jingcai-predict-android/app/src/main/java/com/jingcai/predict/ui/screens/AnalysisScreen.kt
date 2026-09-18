package com.jingcai.predict.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.jingcai.predict.ui.components.UiMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** 命中红：仅用于「已完赛命中」相关内容 */
internal val HitRed = Color(0xFFD93A2B)

/** 未中绿：仅用于「已完赛未命中」相关内容 */
internal val MissGreen = Color(0xFF1B8A4B)

/** 低置信度灰（<50） */
private val ConfGray = Color(0xFF9AA0A6)

/**
 * 预测分析：分为「置信度排行」与「回测复盘」两个分类。
 *
 * 数据全部来自 [AiPredictionStore]（综合预测快照缓存）：
 * App 启动即由 [PredictionBatchRunner] 在后台对今明两日赛程批量预测并长期保留，
 * 本页只读缓存、不做二次预测；后台进度与失败重跑入口在两个分类之上，两页都可见。
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
                .padding(start = 14.dp, end = 14.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("🔮", fontSize = 14.sp)
            }
            Text(
                "预测分析",
                Modifier.padding(start = 8.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (tabIndex == 0) "共 ${shown.size} 场" else "已结算 ${overview.settledMatches} 场",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 后台预测进度 + 失败重跑入口：位于两个分类之上，两页都可见
        if (batch.running || batch.error != null || batch.total > 0 || batch.failed > 0) {
            BatchBanner(batch)
        }

        // 两个分类
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = tabIndex == 0,
                onClick = { tabIndex = 0 },
                text = { Text("置信度排行", fontSize = 13.sp) }
            )
            Tab(
                selected = tabIndex == 1,
                onClick = { tabIndex = 1 },
                text = { Text("回测复盘", fontSize = 13.sp) }
            )
        }

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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { DashboardCard(overview, onRefresh) }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("今明两日综合置信度排行", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text(
                    "前 ${shown.size} 场",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (shown.isEmpty()) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (running) "后台正在生成今明两日预测结果…" else "今明两日暂无综合预测结果",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                    .padding(top = 18.dp, bottom = 8.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
        }
    }
}

/** 顶部仪表盘：半圆环显示整体命中率 + 各口径真实命中率小卡（高度约屏幕 1/3） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DashboardCard(o: BacktestOverview, onRefresh: () -> Unit) {
    val cardHeight = (LocalConfiguration.current.screenHeightDp.dp / 3)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .height(cardHeight)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("命中率仪表盘（真实赛果结算）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRefresh, modifier = Modifier.size(26.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "更新赛果",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp)
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
                    Text(
                        "--",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "整体命中率",
                        Modifier.padding(top = 2.dp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "暂无已结算数据（比赛完赛并回写真实赛果后自动统计）",
                        Modifier.padding(top = 6.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 15.sp
                    )
                }
            }
            return@Column
        }

        // 半圆环仪表盘
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.BottomCenter
        ) {
            HalfGauge(
                rate = o.overallRate ?: 0.0,
                primary = MaterialTheme.colorScheme.primary,
                track = MaterialTheme.colorScheme.surfaceVariant
            )
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    BacktestStats.rateText(o.overallRate),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "整体命中率",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "已结算 ${o.settledMatches} 场 / ${o.settledPicks} 项",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 各玩法命中率（横向排列，窄屏自动换行）
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            o.playStats.forEach { s ->
                MiniStat("${s.label}命中率", BacktestStats.rateText(s.rate))
            }
        }

        // 价值口径 + 平均综合置信度
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            MiniStat("最具价值命中率", BacktestStats.rateText(o.bestValueRate))
            MiniStat("最稳健命中率", BacktestStats.rateText(o.safestRate))
            MiniStat(
                "平均综合置信度",
                o.avgRankConfidence?.let { "${it.roundToInt()}" } ?: "--"
            )
        }
    }
}

/** 半圆仪表盘：底轨 + 按命中率比例填色的主色弧 */
@Composable
private fun HalfGauge(rate: Double, primary: Color, track: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 14.dp.toPx()
        val radius = minOf((size.width - stroke) / 2f, size.height - stroke)
        if (radius <= 0f) return@Canvas
        val topLeft = Offset(size.width / 2f - radius, size.height - radius)
        val arcSize = Size(radius * 2f, radius * 2f)
        drawArc(
            color = track,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        val v = rate.coerceIn(0.0, 1.0)
        if (v > 0.0) {
            drawArc(
                color = primary,
                startAngle = 180f,
                sweepAngle = (180f * v.toFloat()).coerceAtLeast(1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            fontSize = 13.sp,
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

/* ================= 后台预测进度 ================= */

/** 界面文案不允许出现「AI」字样：统一改写为「大模型」 */
private fun noAiText(t: String): String = t.replace("AI", "大模型")

@Composable
private fun BatchBanner(s: BatchState) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        when {
            s.running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "后台预测中 ${s.done}/${s.total} · ${noAiText(s.current)}",
                    Modifier.padding(start = 8.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 有失败场次时，失败原因与重跑入口在下方统一展示（避免重复）
            s.failed > 0 -> {}

            s.error != null -> Text(
                noAiText(s.error),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error
            )

            s.total > 0 -> Text(
                "后台预测已完成（共 ${s.total} 场）· 结果已保留，重复进入不会重复预测",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 失败提示 + 「只重跑出错场次」入口
        if (s.failed > 0) {
            if (s.running) Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "有 ${s.failed} 场预测失败（原因：${noAiText(s.error ?: "未知错误")}）",
                    Modifier.weight(1f),
                    fontSize = 11.sp,
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
                    Text("重新预测出错的 ${s.failed} 场", fontSize = 11.sp)
                }
            }
        }
    }
}

/* ================= 综合预测卡片 ================= */

/** 置信度配色：≥70 主色、50-69 中性、<50 灰 */
@Composable
private fun confColor(v: Int): Color = when {
    v >= 70 -> MaterialTheme.colorScheme.primary
    v >= 50 -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> ConfGray
}

/** 玩法选项文本（含命中配色：命中红、未中绿、未结算中性） */
@Composable
private fun hitColor(hit: Boolean?): Color = when (hit) {
    true -> HitRed
    false -> MissGreen
    null -> MaterialTheme.colorScheme.primary
}

private fun adviceText(p: CombinedPick): String = "${BacktestStats.playLabel(p.play)} ${p.option}"

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun fmtTime(ts: Long): String =
    if (ts <= 0L) "--"
    else Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).format(timeFmt)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CombinedCard(p: CombinedPrediction, onOpenMatch: (CombinedPrediction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onOpenMatch(p) }
            .padding(13.dp)
    ) {
        // 第一行：联赛 · 编号 · 开赛时间 + 综合置信度
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${p.league} · ${p.matchNum} · ${p.kickoff}开赛",
                Modifier.weight(1f),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(confColor(p.rankConfidence).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${p.rankConfidence}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = confColor(p.rankConfidence)
                    )
                    Text(
                        "综合置信度",
                        fontSize = 7.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 第二行：对阵
        Text(
            "${p.home} VS ${p.away}",
            Modifier.padding(top = 4.dp),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        // 第三行：各玩法综合选项（命中红 / 未中绿，未结算中性）
        if (p.picks.isNotEmpty()) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                p.picks.forEach { pick ->
                    val fg = hitColor(pick.hit)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (pick.hit) {
                                    true -> HitRed.copy(alpha = 0.12f)
                                    false -> MissGreen.copy(alpha = 0.12f)
                                    null -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pick.hit != null) {
                            Text(
                                if (pick.hit) "命中 " else "未中 ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                        }
                        Text(
                            "${BacktestStats.playLabel(pick.play)} ",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            pick.option,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                    }
                }
            }
        }

        // 第四行：最具价值 / 最稳健（命中情况同色规则）
        if (p.bestValue != null || p.safest != null) {
            FlowRow(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                p.bestValue?.let {
                    Text(
                        hitPrefix(it.hit) + "最具价值：${adviceText(it)}",
                        fontSize = 11.sp,
                        color = if (it.hit == null) MaterialTheme.colorScheme.primary else hitColor(it.hit)
                    )
                }
                p.safest?.let {
                    Text(
                        hitPrefix(it.hit) + "最稳健：${adviceText(it)}",
                        fontSize = 11.sp,
                        color = if (it.hit == null) MaterialTheme.colorScheme.onSurfaceVariant else hitColor(it.hit)
                    )
                }
            }
        }

        // 第五行：模型 + 更新时间
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                if (p.model.isBlank()) "架构结果（未配置模型）" else "模型 ${p.model}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "更新 ${fmtTime(p.updatedAt)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    val date = runCatching {
        LocalDate.parse("${today.year}-$datePart")
    }.getOrNull() ?: return true
    return date == today || date == today.plusDays(1)
}
