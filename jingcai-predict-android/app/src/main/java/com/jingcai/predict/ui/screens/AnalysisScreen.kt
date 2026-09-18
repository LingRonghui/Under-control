package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.predict.BacktestStats
import com.jingcai.predict.data.predict.PredictionEngine
import com.jingcai.predict.data.predict.PredictionResult
import com.jingcai.predict.data.predict.PredictionStore
import com.jingcai.predict.data.predict.ProfileRepository
import com.jingcai.predict.data.remote.JingCaiApi
import com.jingcai.predict.data.remote.MatchPreviewApi
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 预测分析：综合信心榜。
 * 基于竞彩官方真实赔率（+ 联赛参数模板）对今日/明日未开赛比赛生成预测，
 * 按置信度排序；顶部展示本地回测统计（真实命中率）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen() {
    val context = LocalContext.current
    var predictions by remember { mutableStateOf<List<PredictionResult>>(emptyList()) }
    var stats by remember { mutableStateOf(BacktestStats.of(emptyList())) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 回测统计流
    LaunchedEffect(Unit) {
        PredictionStore.recordsFlow(context).collect { stats = BacktestStats.of(it) }
    }

    fun load(showRefresh: Boolean) {
        if (showRefresh) refreshing = true else loading = true
        failed = false
        scope.launch {
            runCatching {
                val days = JingCaiApi.fetchMatchDays()
                // 竞彩官方列表即为在售场次，matchStatus 实际取值为 "Selling"（首字母大写），需忽略大小写判定
                val upcoming = days.flatMap { it.matches }
                    .filter {
                        val s = it.status.uppercase()
                        s.isEmpty() || s == "0" || s == "SELLING" || s == "PRE_SELL"
                    }
                val results = upcoming.map { m ->
                    val profile = ProfileRepository.getProfile(context, m.league)
                    PredictionEngine.predictLight(m, profile)
                }.sortedByDescending { it.conf }
                // 自动记录回测（每场保留一条未结算记录）
                val existing = PredictionStore.records(context)
                results.forEach { r ->
                    val hasPending = existing.any { it.matchId == r.matchId && !it.settled }
                    if (!hasPending) {
                        PredictionStore.upsert(
                            context,
                            com.jingcai.predict.data.predict.BacktestRecord(
                                matchId = r.matchId,
                                league = r.league,
                                home = r.home,
                                away = r.away,
                                kickoff = r.kickoff,
                                pick = r.wdlPick,
                                homeProb = r.homeProb,
                                conf = r.conf,
                                predictedAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                }
                predictions = results
            }.onFailure { failed = true }
            loading = false
            refreshing = false
        }
    }

    /** 结算：对已保存的未结算预测，拉取实时赛果写入回测 */
    fun settleFinished() {
        scope.launch {
            PredictionStore.records(context)
                .filter { !it.settled }
                .forEach { rec ->
                    runCatching {
                        val live = MatchPreviewApi.fetchLive(rec.matchId)
                        if (live != null && live.isFinished) {
                            resultOfScore(live.score)?.let {
                                PredictionStore.settle(context, rec.matchId, it)
                            }
                        }
                    }
                }
        }
    }

    LaunchedEffect(Unit) { load(false) }

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
                "按模型置信度排序",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when {
            loading && predictions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载竞彩赛事赔率…", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            failed && predictions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("竞彩官方数据加载失败", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("请检查网络后下拉重试", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { load(false) }, shape = RoundedCornerShape(10.dp)) {
                        Text("重新加载", fontSize = 13.sp)
                    }
                }
            }

            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { load(true) },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { BacktestCard(stats, onSettle = ::settleFinished) }
                    if (predictions.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "今日暂无未开赛的竞彩赛事",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(predictions.size, key = { predictions[it].matchId }) { idx ->
                        PredictionCard(predictions[idx])
                    }
                    item {
                        Text(
                            "预测基于竞彩官方真实赔率与联赛统计参数，仅供参考，理性购彩",
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
        }
    }
}

/* ================= 回测统计卡片 ================= */

@Composable
private fun BacktestCard(stats: BacktestStats, onSettle: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("回测统计（真实赛果）", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "已结算 ${stats.settled} / 累计 ${stats.total}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onSettle, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "更新赛果",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            StatCell("胜平负命中率", pct(stats.hitRate), Modifier.weight(1f))
            StatCell("高置信≥85", pct(stats.highRate) + "\n${stats.hitHigh}/${stats.settledHigh}", Modifier.weight(1f))
            StatCell("中置信70-84", pct(stats.midRate) + "\n${stats.hitMid}/${stats.settledMid}", Modifier.weight(1f))
            StatCell("低置信<70", pct(stats.lowRate) + "\n${stats.hitLow}/${stats.settledLow}", Modifier.weight(1f))
        }
        if (stats.settled == 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "开赛后点击右上角刷新图标，用真实赛果结算预测，命中率由此如实统计",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp
            )
        }
    }
}

private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
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

/* ================= 预测卡片 ================= */

@Composable
private fun confColor(conf: Int): Color =
    if (conf >= 85) MaterialTheme.colorScheme.tertiary
    else if (conf >= 70) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.secondary

@Composable
private fun PredictionCard(p: PredictionResult) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(13.dp)
    ) {
        // 顶部：联赛 + 对阵 + 置信度
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${p.league} · ${p.num} · ${p.kickoff}开赛",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${p.home} VS ${p.away}",
                    Modifier.padding(top = 2.dp),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(confColor(p.conf).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${p.conf}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = confColor(p.conf)
                    )
                    Text(
                        "置信度",
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 胜平负概率条（真实融合概率）
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp))) {
            Box(
                Modifier
                    .weight((p.homeProb * 1000).toFloat().coerceAtLeast(0.5f))
                    .fillMaxSize()
                    .background(Color(0xFFD93A2B))
            )
            Box(
                Modifier
                    .weight((p.drawProb * 1000).toFloat().coerceAtLeast(0.5f))
                    .fillMaxSize()
                    .background(Color(0xFF9AA0A6))
            )
            Box(
                Modifier
                    .weight((p.awayProb * 1000).toFloat().coerceAtLeast(0.5f))
                    .fillMaxSize()
                    .background(Color(0xFF2E6BE6))
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
            ProbLabel("主胜 ${pct(p.homeProb)}", Color(0xFFD93A2B), Modifier.weight(1f))
            ProbLabel("平 ${pct(p.drawProb)}", Color(0xFF9AA0A6), Modifier.weight(1f), Alignment.CenterHorizontally)
            ProbLabel("客胜 ${pct(p.awayProb)}", Color(0xFF2E6BE6), Modifier.weight(1f), Alignment.End)
        }

        // 预测项
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            PredItem("胜平负", p.wdlPick, Modifier.weight(1f))
            PredItem("让球", if (p.hdpPick == "--") "--" else p.hdpPick + (p.hdpLine.takeIf { it.isNotBlank() }?.let { "($it)" } ?: ""), Modifier.weight(1.1f))
            PredItem("比分", p.scorePick, Modifier.weight(1f))
            PredItem("半全场", p.hfPick, Modifier.weight(1.2f))
            PredItem("总进球", p.totalPick, Modifier.weight(1f))
        }

        // 信号与要点
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(
                "信号：${p.signalNote} · 完整度 ${pct(p.dataComplete)}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "要点：${p.key}",
            Modifier.padding(top = 5.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun ProbLabel(text: String, color: Color, modifier: Modifier = Modifier, align: Alignment.Horizontal = Alignment.Start) {
    Row(modifier, horizontalArrangement = when (align) {
        Alignment.CenterHorizontally -> Arrangement.Center
        Alignment.End -> Arrangement.End
        else -> Arrangement.Start
    }, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
        Text(
            " $text",
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PredItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            Modifier.padding(top = 1.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}

/** 比分字符串 → 胜平负结果 */
private fun resultOfScore(score: String?): String? {
    if (score == null) return null
    val parts = score.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val a = parts[1].toIntOrNull() ?: return null
    return when {
        h > a -> "主胜"
        h < a -> "客胜"
        else -> "平局"
    }
}
