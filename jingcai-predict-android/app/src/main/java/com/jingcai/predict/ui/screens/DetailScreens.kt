package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jingcai.predict.data.remote.FeatureDim
import com.jingcai.predict.data.remote.FutureMatch
import com.jingcai.predict.data.remote.H2hMatch
import com.jingcai.predict.data.remote.H2hSummary
import com.jingcai.predict.data.remote.InjuryPlayer
import com.jingcai.predict.data.remote.LiveEvent
import com.jingcai.predict.data.remote.LiveScore
import com.jingcai.predict.data.remote.LeagueEntry
import com.jingcai.predict.data.remote.MatchFeature
import com.jingcai.predict.data.remote.MatchHead
import com.jingcai.predict.data.remote.MatchOdds
import com.jingcai.predict.data.remote.MatchPreviewApi
import com.jingcai.predict.data.remote.OddsCell
import com.jingcai.predict.data.remote.PlayerStat
import com.jingcai.predict.data.remote.RecentMatch
import com.jingcai.predict.data.remote.RecentTeam
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.predict.LeagueProfile
import com.jingcai.predict.data.predict.LeagueProfiles
import com.jingcai.predict.data.predict.OddsMath
import com.jingcai.predict.data.predict.PredictionEngine
import com.jingcai.predict.data.predict.PredictionResult
import com.jingcai.predict.data.predict.ProfileRepository
import com.jingcai.predict.data.predict.SignalWeights
import com.jingcai.predict.data.remote.SearchPlayer
import com.jingcai.predict.data.remote.SearchTeam
import com.jingcai.predict.data.remote.TableRow
import com.jingcai.predict.data.remote.TeamTables
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 跨路由传递数据的轻量持有者。
 * 搜索结果 → 详情页之间用全局对象传数据，避免 Composable Navigation 传复杂对象的繁琐序列化。
 */
object DetailHolder {
    var team: SearchTeam? = null
    var player: SearchPlayer? = null
    var match: RemoteMatch? = null
    var league: LeagueEntry? = null
}

/* ================= 球队详情 ================= */

@Composable
fun TeamDetailScreen(onBack: () -> Unit) {
    val team = DetailHolder.team ?: run {
        onBack()
        return
    }
    DetailScaffold(title = "球队详情", onBack = onBack) {
        // 顶部：队徽 + 名称
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NetworkImage(team.badge, Modifier.size(120.dp), fallback = "⚽", round = true)
            Spacer(Modifier.height(14.dp))
            Text(team.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            TypeTag("球队", Color(0xFF2E6BE6))
        }
        Spacer(Modifier.height(24.dp))
        InfoCard(
            listOf(
                "联赛" to team.league.ifEmpty { "未知" },
                "国家/地区" to team.country.ifEmpty { "未知" },
                "球队 ID" to team.id.ifEmpty { "未知" },
            )
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "数据来源：TheSportsDB",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

/* ================= 球员详情 ================= */

@Composable
fun PlayerDetailScreen(onBack: () -> Unit) {
    val player = DetailHolder.player ?: run {
        onBack()
        return
    }
    DetailScaffold(title = "球员详情", onBack = onBack) {
        // 顶部：头像 + 名称
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            NetworkImage(player.photo, Modifier.size(120.dp), fallback = "👤", round = true)
            Spacer(Modifier.height(14.dp))
            Text(player.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            TypeTag("球员", Color(0xFF0E9F6E))
        }
        Spacer(Modifier.height(24.dp))
        InfoCard(
            listOf(
                "所属球队" to player.team.ifEmpty { "未知" },
                "场上位置" to player.position.ifEmpty { "未知" },
                "国籍" to player.nationality.ifEmpty { "未知" },
                "球员 ID" to player.id.ifEmpty { "未知" },
            )
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "数据来源：TheSportsDB",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

/* ================= 比赛详情 ================= */

private val matchTabs = listOf("前瞻", "赔率", "预测分析")

@Composable
fun MatchDetailScreen(onBack: () -> Unit) {
    val match = DetailHolder.match ?: run {
        onBack()
        return
    }
    var tabIndex by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：以"主队 VS 客队"命名
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                "${match.home} VS ${match.away}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 约 1/3：实时状态卡片
        MatchStatusCard(match)

        // 分类栏
        TabRow(
            selectedTabIndex = tabIndex,
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            matchTabs.forEachIndexed { i, t ->
                Tab(
                    selected = tabIndex == i,
                    onClick = { tabIndex = i },
                    text = { Text(t, fontSize = 13.sp) }
                )
            }
        }

        // 内容区
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (tabIndex) {
                0 -> ForwardTab(match)
                1 -> OddsTab(match)
                else -> PredictionTab(match)
            }
        }
    }
}

/* ---------- 实时状态卡片 ---------- */

@Composable
private fun MatchStatusCard(match: RemoteMatch) {
    val status = statusText(match.status)
    val fallbackLive = status == "进行中"
    val fallbackFinished = status == "已完赛"
    // 实时比分（比分直播接口），优先于静态状态
    var live by remember { mutableStateOf<LiveScore?>(null) }
    var liveFailed by remember { mutableStateOf(false) }

    // 进入后立即拉取一次实时数据；进行中每 30s 轮询刷新
    LaunchedEffect(match.matchId, fallbackLive) {
        while (true) {
            runCatching { MatchPreviewApi.fetchLive(match.matchId) }
                .onSuccess { live = it; liveFailed = false }
                .onFailure { liveFailed = true; live = null }
            if (fallbackLive) delay(30_000) else break
        }
    }

    val isLive = live?.isLive ?: fallbackLive
    val isFinished = live?.isFinished ?: fallbackFinished
    val score = live?.score?.takeIf { it.isNotBlank() }        // "2:1"
    val half = live?.halfScore?.takeIf { it.isNotBlank() }     // 半场 "1:1"
    val penalty = live?.penalty?.takeIf { it.isNotBlank() }    // 点球
    val phase = live?.phaseName?.takeIf { it.isNotBlank() }    // 上半场/中场/比赛结束
    val minute = live?.minute?.takeIf { it.isNotBlank() }      // 分钟

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                RoundedCornerShape(18.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上：联赛 + 编号 + 状态
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                match.league.ifEmpty { "竞彩足球" },
                Modifier.weight(1f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                match.num.ifEmpty { "" },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        when {
                            isLive -> Color(0xFFD93A2B).copy(alpha = 0.14f)
                            isFinished -> Color(0xFF0F766E).copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    live?.statusName?.takeIf { it.isNotBlank() } ?: status,
                    fontSize = 11.sp,
                    color = when {
                        isLive -> Color(0xFFD93A2B)
                        isFinished -> Color(0xFF0F766E)
                        else -> MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 中：主队 | 比分/VS | 客队
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                match.home,
                Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isLive || isFinished) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        score ?: " -- : -- ",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isLive) Color(0xFFD93A2B) else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    if (half != null) {
                        Text("半场 $half", fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (penalty != null) {
                        Text("点球 $penalty", fontSize = 10.sp,
                            color = Color(0xFFD93A2B))
                    }
                }
            } else {
                Text(
                    " VS ",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
            }
            Text(
                match.away,
                Modifier.weight(1f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(10.dp))

        // 下：阶段/分钟 / 赛果 / 开赛时间
        Text(
            when {
                isLive && live != null -> {
                    val m = minute?.let { runCatching { it.toInt() }.getOrNull() }?.coerceAtMost(999)
                    val minText = when {
                        m == null -> ""
                        m > 45 && m <= 90 -> "${m}'"
                        m > 90 -> "90+${m - 90}'"
                        else -> "$m'"
                    }
                    listOf(phase, if (minText.isNotBlank()) minText else null).filterNotNull()
                        .joinToString(" · ").ifEmpty { "比赛进行中，实时赛况持续更新…" }
                }
                isFinished && live != null ->
                    phase?.takeIf { it.isNotBlank() } ?: "比赛已结束，赛果已锁定"
                isFinished -> "比赛已结束，赛果已锁定"
                else -> "开赛时间：${match.time}"
            },
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // 实时事件列表（进球/红牌等）
        if (live != null && live!!.events.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text("关键事件", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                live!!.events.forEach { e ->
                    LiveEventRow(e, match)
                }
            }
        }
    }
}

@Composable
private fun LiveEventRow(e: LiveEvent, match: RemoteMatch) {
    val isHome = e.teamType == "home"
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${e.minute}'",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Text(
            (if (isHome) match.home else match.away) + " ",
            Modifier.weight(1f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isHome) TextAlign.End else TextAlign.Start
        )
        Text(
            e.name.ifEmpty { "事件" },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isHome) Color(0xFFD93A2B) else Color(0xFF2E6BE6)
        )
    }
}

/* ---------- 前瞻 Tab ---------- */

/** 前瞻页聚合数据（官方「赛事前瞻」接口） */
private data class PreviewData(
    val head: MatchHead? = null,
    val feature: MatchFeature? = null,
    val history: List<H2hMatch> = emptyList(),
    val h2hSum: H2hSummary? = null,
    val homeTables: TeamTables? = null,
    val awayTables: TeamTables? = null,
    val homeRecent: RecentTeam? = null,
    val awayRecent: RecentTeam? = null,
    val homeFuture: List<FutureMatch> = emptyList(),
    val awayFuture: List<FutureMatch> = emptyList(),
    val homePlayers: List<PlayerStat> = emptyList(),
    val awayPlayers: List<PlayerStat> = emptyList(),
    val homeInjuries: List<InjuryPlayer> = emptyList(),
    val awayInjuries: List<InjuryPlayer> = emptyList(),
)

@Composable
private fun ForwardTab(match: RemoteMatch) {
    var preview by remember { mutableStateOf<PreviewData?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(match.matchId) {
        preview = runCatching { buildPreview(match) }.getOrNull()
        failed = preview == null
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (failed) {
            EmptyHint("前瞻数据暂不可用\n（官方接口未返回，稍后重试）")
            return@Column
        }
        val p = preview ?: run {
            Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                Text("正在加载前瞻数据…", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        val homeName = p.head?.homeName ?: match.home
        val awayName = p.head?.awayName ?: match.away

        // 特征分析
        SectionTitle("特征分析")
        FeatureCard(p.feature, homeName, awayName)
        Spacer(Modifier.height(16.dp))

        // 历史交锋
        SectionTitle("历史交锋")
        HistoryCard(p.h2hSum, p.history)
        Spacer(Modifier.height(16.dp))

        // 积分榜
        SectionTitle("积分榜")
        TablesCard(p.homeTables, p.awayTables)
        Spacer(Modifier.height(16.dp))

        // 比赛近况
        SectionTitle("比赛近况")
        RecentCard(p.homeRecent, homeName)
        Spacer(Modifier.height(10.dp))
        RecentCard(p.awayRecent, awayName)
        Spacer(Modifier.height(16.dp))

        // 未来赛事
        SectionTitle("未来赛事")
        FutureCard(p.homeFuture, homeName)
        Spacer(Modifier.height(10.dp))
        FutureCard(p.awayFuture, awayName)
        Spacer(Modifier.height(16.dp))

        // 射手信息
        SectionTitle("射手信息")
        PlayerCard(p.homePlayers, homeName)
        Spacer(Modifier.height(10.dp))
        PlayerCard(p.awayPlayers, awayName)
        Spacer(Modifier.height(16.dp))

        // 伤停一览
        SectionTitle("伤停一览")
        InjuryCard(p.homeInjuries, homeName)
        Spacer(Modifier.height(10.dp))
        InjuryCard(p.awayInjuries, awayName)
        Spacer(Modifier.height(16.dp))

        // 赛前情报
        SectionTitle("赛前情报")
        Text(
            preMatchBrief(match),
            fontSize = 13.sp,
            lineHeight = 21.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "数据来源：中国体育彩票竞彩官网 · 赛事前瞻",
            Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** 拉取官方前瞻全部数据，单项失败独立降级 */
private suspend fun buildPreview(match: RemoteMatch): PreviewData {
    val mid = match.matchId
    if (mid.isBlank()) throw IllegalStateException("缺少比赛 ID")
    val head = runCatching { MatchPreviewApi.fetchHead(mid) }.getOrNull()
    val feature = runCatching { MatchPreviewApi.fetchFeature(mid) }.getOrNull()
    val (h2h, h2hSum) = runCatching { MatchPreviewApi.fetchHistory(mid) }
        .getOrDefault(Pair(emptyList<H2hMatch>(), null))
    val (homeTables, awayTables) = runCatching { MatchPreviewApi.fetchTables(mid) }
        .getOrDefault(Pair(null, null))
    val (homeRecent, awayRecent) = runCatching { MatchPreviewApi.fetchResults(mid) }
        .getOrDefault(Pair(null, null))
    val (homeFuture, awayFuture) = runCatching { MatchPreviewApi.fetchFuture(mid) }
        .getOrDefault(Pair(emptyList(), emptyList()))
    val (homePlayers, awayPlayers) = runCatching { MatchPreviewApi.fetchPlayers(mid) }
        .getOrDefault(Pair(emptyList(), emptyList()))
    val (homeInjuries, awayInjuries) = runCatching { MatchPreviewApi.fetchInjuries(mid) }
        .getOrDefault(Pair(emptyList(), emptyList()))
    if (head == null && feature == null && h2h.isEmpty() && homeTables == null && homeRecent == null) {
        throw IllegalStateException("官方前瞻接口全部失败")
    }
    return PreviewData(
        head, feature, h2h, h2hSum, homeTables, awayTables, homeRecent, awayRecent,
        homeFuture, awayFuture, homePlayers, awayPlayers, homeInjuries, awayInjuries,
    )
}

/** 特征分析：近10场交锋/同主客交锋/近10场战况/同主客战况/场均进球失球 */
@Composable
private fun FeatureCard(f: MatchFeature?, homeName: String, awayName: String) {
    if (f == null) {
        EmptyHint("特征分析数据缺失")
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(homeName, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFD93A2B))
            Text(awayName, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFF2E6BE6), textAlign = TextAlign.End)
        }
        @Composable
        fun dimRow(label: String, d: FeatureDim?, homeV: String = "", awayV: String = "") {
            if (d == null && homeV.isEmpty()) return
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(label, Modifier.weight(1f), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (d != null) "${d.homeWin}胜${d.homeDraw}平${d.homeLoss}负" else homeV,
                    Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text(
                    if (d != null) "${d.awayWin}胜${d.awayDraw}平${d.awayLoss}负" else awayV,
                    Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End
                )
            }
        }
        dimRow("近10场交锋", f.last10)
        dimRow("同主客交锋", f.sameHomeAway)
        dimRow("近10场战况", f.last10Form)
        dimRow("同主客战况", f.sameHomeAwayForm)
        dimRow("场均进球", null, "${f.homeGoalAvg}个", "${f.awayGoalAvg}个")
        dimRow("场均失球", null, "${f.homeLossAvg}个", "${f.awayLossAvg}个")
        Spacer(Modifier.height(4.dp))
    }
}

/** 历史交锋：汇总 + 交锋列表 */
@Composable
private fun HistoryCard(sum: H2hSummary?, list: List<H2hMatch>) {
    if (sum == null && list.isEmpty()) {
        EmptyHint("暂无两队交锋记录")
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        sum?.let { s ->
            Text(
                "近${s.win + s.draw + s.loss}场 ${s.teamName} ${s.win}胜 (${s.winProb}) | ${s.draw}平 (${s.drawProb}) | ${s.loss}负 (${s.lossProb})",
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
        }
        if (list.isEmpty()) {
            Text("暂无数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            list.forEachIndexed { i, m ->
                Column {
                    Text(
                        "${m.date} · ${m.tournament}",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${m.home} ${m.score} ${m.away}", Modifier.weight(1f),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "半场${m.halfScore.ifEmpty { "-" }} · 总${m.totalGoal}球",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (i != list.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)))
                }
            }
        }
    }
}

/** 积分榜：主客两队各 总/主/客 三行 */
@Composable
private fun TablesCard(home: TeamTables?, away: TeamTables?) {
    if (home == null && away == null) {
        EmptyHint("暂无积分榜数据")
        return
    }
    Column(Modifier.fillMaxWidth()) {
        home?.let { TablePanel(it) }
        if (home != null && away != null) Spacer(Modifier.height(10.dp))
        away?.let { TablePanel(it) }
    }
}

@Composable
private fun TablePanel(t: TeamTables) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            "${t.name} 第${t.total.ranking}名",
            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            val head = listOf("", "场次", "胜/平/负", "进/失", "净", "积分", "排名")
            head.forEach { h ->
                Text(h, Modifier.weight(1f), fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        listOf(t.total, t.home, t.away).forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text(r.scope, Modifier.weight(1f), fontSize = 11.sp,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("${r.played}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("${r.win}/${r.draw}/${r.loss}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("${r.goal}/${r.lossGoal}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("${r.netGoal}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text(r.points, Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text(r.ranking, Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

/** 比赛近况：单队近 10 场 */
@Composable
private fun RecentCard(t: RecentTeam?, fallbackName: String) {
    val name = t?.name ?: fallbackName
    if (t == null) {
        EmptyHint("$name 近况数据缺失")
        return
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text("${t.name} ${t.stat}", fontSize = 12.sp, fontWeight = FontWeight.Bold,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        if (t.matches.isEmpty()) {
            Text("暂无比赛记录", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            t.matches.forEachIndexed { i, m ->
                Column {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${m.home} ${m.score} ${m.away}", fontSize = 12.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${m.date} · ${m.tournament}", fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (m.halfScore.isNotEmpty()) "半${m.halfScore}" else "",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            m.result,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = when (m.result) {
                                "胜" -> Color(0xFFD93A2B)
                                "负" -> Color(0xFF0E9F6E)
                                "平" -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
                if (i != t.matches.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)))
                }
            }
        }
    }
}

/** 未来赛事：单队列表 */
@Composable
private fun FutureCard(list: List<FutureMatch>, name: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            Text("暂无未来赛事", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            list.forEachIndexed { i, m ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${m.home} vs ${m.away}", Modifier.weight(1f), fontSize = 12.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${m.date}${if (m.round.isNotEmpty()) " · ${m.round}" else ""}",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (i != list.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp).height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)))
                }
            }
        }
    }
}

/** 射手信息：单队射手榜 */
@Composable
private fun PlayerCard(list: List<PlayerStat>, name: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            Text("暂无射手数据", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(Modifier.fillMaxWidth()) {
            val head = listOf("球员", "出场(首/替)", "进球", "助攻", "场均进/助")
            head.forEach { h ->
                Text(h, Modifier.weight(1f), fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        list.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (p.no.isNotEmpty()) "${p.no}-" else ""}${p.name}(${p.position})",
                    Modifier.weight(1f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("${p.played}(${p.started}/${p.sub})", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("${p.goal} (${p.goalProb})", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("${p.assist} (${p.assistProb})", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text(
                    if (p.goalAvg.isNotEmpty() || p.assistAvg.isNotEmpty()) "${p.goalAvg}/${p.assistAvg}" else "-",
                    Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** 伤停一览：单队伤停名单 */
@Composable
private fun InjuryCard(list: List<InjuryPlayer>, name: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (list.isEmpty()) {
            Text("暂无伤停信息", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        Row(Modifier.fillMaxWidth()) {
            val head = listOf("球员", "总出场", "首发", "替补", "状态")
            head.forEach { h ->
                Text(h, Modifier.weight(1f), fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        list.forEach { p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${if (p.no.isNotEmpty()) "${p.no}-" else ""}${p.name}(${p.position})",
                    Modifier.weight(1f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text("${p.played}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("${p.started}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text("${p.sub}", Modifier.weight(1f), fontSize = 11.sp, textAlign = TextAlign.Center)
                Text(
                    when {
                        p.injury && p.suspension -> "伤停"
                        p.injury -> "伤"
                        p.suspension -> "停"
                        else -> "-"
                    },
                    Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (p.injury || p.suspension) Color(0xFFD93A2B)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun preMatchBrief(match: RemoteMatch): String {
    val sb = StringBuilder()
    val had = match.had
    if (had != null) {
        val h = had.first.toDoubleOrNull() ?: 0.0
        val d = had.second.toDoubleOrNull() ?: 0.0
        val a = had.third.toDoubleOrNull() ?: 0.0
        val min = minOf(h, d, a)
        when (min) {
            h -> sb.append("机构开出主胜低赔 ${had.first}，市场倾向看好主队 ${match.home}。")
            a -> sb.append("机构开出客胜低赔 ${had.third}，市场倾向看好客队 ${match.away}。")
            else -> sb.append("胜平负赔率接近（${had.first}/${had.second}/${had.third}），机构对本场态度谨慎。")
        }
    } else {
        sb.append("本场暂无可用的官方胜平负赔率。")
    }
    match.hhad?.let { (hh, dh, ah) ->
        sb.append("\n让球盘口 ${match.goalLine}，让球方胜赔 ${hh}，平赔 ${dh}，负赔 ${ah}。")
    }
    sb.append("\n以上为基于官方赔率的概率倾向分析，不构成投注建议，请理性购彩。")
    return sb.toString()
}

/* ---------- 赔率 Tab ---------- */

@Composable
private fun OddsTab(match: RemoteMatch) {
    // 最新赔率（zqdz 详情页同源，取最后一条时间线），失败时降级到静态售彩赔率
    var odds by remember { mutableStateOf<MatchOdds?>(null) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(match.matchId) {
        runCatching { MatchPreviewApi.fetchOdds(match.matchId) }
            .onSuccess { odds = it }
        loading = false
    }
    val o = odds
    val had = o?.had ?: match.had?.let {
        Triple(OddsCell(it.first, 0), OddsCell(it.second, 0), OddsCell(it.third, 0))
    }
    val hhad = o?.hhad ?: match.hhad?.let {
        Triple(OddsCell(it.first, 0), OddsCell(it.second, 0), OddsCell(it.third, 0))
    }
    val goalLine = when {
        !o?.goalLine.isNullOrEmpty() -> o!!.goalLine
        else -> match.goalLine
    }
    val crs: Map<String, OddsCell> =
        o?.crs?.takeIf { it.isNotEmpty() } ?: match.crs?.mapValues { OddsCell(it.value, 0) } ?: emptyMap()
    val hafu: Map<String, OddsCell> =
        o?.hafu?.takeIf { it.isNotEmpty() } ?: match.hafu?.mapValues { OddsCell(it.value, 0) } ?: emptyMap()
    val ttg: Map<String, OddsCell> =
        o?.ttg?.takeIf { it.isNotEmpty() } ?: match.ttg?.mapValues { OddsCell(it.value, 0) } ?: emptyMap()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (loading) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        // 1. 胜平负
        SectionTitle("胜平负")
        if (had != null) TripleOddsCard(listOf("胜" to had.first, "平" to had.second, "负" to had.third))
        else EmptyHint("本场未开售")
        Spacer(Modifier.height(16.dp))

        // 2. 让球胜平负
        SectionTitle("让球胜平负${goalLine}")
        if (hhad != null) TripleOddsCard(listOf("让球胜" to hhad.first, "让球平" to hhad.second, "让球负" to hhad.third))
        else EmptyHint("本场未开售")
        Spacer(Modifier.height(16.dp))

        // 3. 全场比分
        SectionTitle("全场比分")
        if (crs.isEmpty()) EmptyHint("本场比分玩法未开售")
        else OddsGrid(crsList(crs), columns = 4)
        Spacer(Modifier.height(16.dp))

        // 4. 半全场胜平负
        SectionTitle("半全场胜平负")
        if (hafu.isEmpty()) EmptyHint("本场半全场玩法未开售")
        else OddsGrid(hafuList(hafu), columns = 3)
        Spacer(Modifier.height(16.dp))

        // 5. 总进球数
        SectionTitle("总进球数")
        if (ttg.isEmpty()) EmptyHint("本场总进球玩法未开售")
        else OddsGrid(ttgList(ttg), columns = 4)
        Spacer(Modifier.height(8.dp))
        Text(
            "数据来源：中国体育彩票 · 竞彩足球官方数据（最新更新赔率）",
            Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
    }
}

/** 全场比分选项解析：遍历官方返回的全部赔率（s{H}s{A} → H:A），胜/平/负其他放末尾 */
private fun crsList(crs: Map<String, OddsCell>): List<Pair<String, OddsCell>> {
    val out = mutableListOf<Pair<String, OddsCell>>()
    val rx = Regex("s(\\d+)s(\\d+)")
    // 普通比分按 (主队 H, 客队 A) 数值升序
    crs.filterKeys { rx.containsMatchIn(it) }
        .map { e ->
            val m = rx.find(e.key)!!
            Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), e.value)
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .forEach { (h, a, v) -> out += "$h:$a" to v }
    // 官方"其他"键实际为 s-1sh / s-1sd / s-1sa（兼容 s1sh 等写法）
    val otherRx = Regex("^s-?\\d+s([hda])$")
    crs.forEach { (k, v) ->
        val m = otherRx.find(k) ?: return@forEach
        out += when (m.groupValues[1]) {
            "h" -> "胜其他"
            "d" -> "平其他"
            else -> "负其他"
        } to v
    }
    return out
}

/** 半全场选项解析（标签为竞彩官方写法） */
private fun hafuList(hafu: Map<String, OddsCell>): List<Pair<String, OddsCell>> {
    val labels = mapOf(
        "hh" to "胜胜", "hd" to "胜平", "ha" to "胜负",
        "dh" to "平胜", "dd" to "平平", "da" to "平负",
        "ah" to "负胜", "ad" to "负平", "aa" to "负负",
    )
    return labels.mapNotNull { (k, label) ->
        val v = hafu[k] ?: return@mapNotNull null
        label to v
    }
}

/** 总进球解析：s0~s7 → 0球…7球+ */
private fun ttgList(ttg: Map<String, OddsCell>): List<Pair<String, OddsCell>> {
    return (0..7).mapNotNull { i ->
        val v = ttg["s$i"] ?: return@mapNotNull null
        (if (i == 7) "7球+" else "${i}球") to v
    }
}

/* ---------- 预测分析 Tab ---------- */

/**
 * 预测分析：基于竞彩官方真实数据的三路信号融合深度预测 + 联赛参数配置面板。
 * 并行拉取 前瞻特征/积分榜/近况/交锋/赔率 5 个官方接口 → predictDeep 融合计算；
 * 联赛参数可展开调整（保存在本机 DataStore，立即重算），默认值来自 2025/26 真实统计。
 */
@Composable
private fun PredictionTab(match: RemoteMatch) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prediction by remember { mutableStateOf<PredictionResult?>(null) }
    var oddsData by remember { mutableStateOf<MatchOdds?>(null) }
    var profile by remember { mutableStateOf<LeagueProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var configOpen by remember { mutableStateOf(false) }

    // 参数编辑态（进入页面时从当前生效模板初始化）
    var editAvg by remember { mutableStateOf(2.75) }
    var editAdv by remember { mutableStateOf(1.30) }
    var editRho by remember { mutableStateOf(-0.04) }
    var editDraw by remember { mutableStateOf(0.10) }
    var editWm by remember { mutableStateOf(0.50) }
    var editWp by remember { mutableStateOf(0.30) }
    var editWs by remember { mutableStateOf(0.20) }

    fun syncEdits(p: LeagueProfile) {
        editAvg = p.avgGoals; editAdv = p.homeAdv; editRho = p.rho; editDraw = p.drawBias
        editWm = p.weights.market; editWp = p.weights.poisson; editWs = p.weights.stat
    }

    fun recompute(p: LeagueProfile) {
        scope.launch {
            loading = true
            val r = runCatching {
                // 5 个官方接口并行拉取，任一失败仅降级该信号，不阻塞整体
                val feature = async { runCatching { MatchPreviewApi.fetchFeature(match.matchId) }.getOrNull() }
                val tables = async { runCatching { MatchPreviewApi.fetchTables(match.matchId) }.getOrNull() }
                val results = async { runCatching { MatchPreviewApi.fetchResults(match.matchId) }.getOrNull() }
                val history = async { runCatching { MatchPreviewApi.fetchHistory(match.matchId) }.getOrNull() }
                val odds = async { runCatching { MatchPreviewApi.fetchOdds(match.matchId) }.getOrNull() }
                val o = odds.await()
                val res = PredictionEngine.predictDeep(
                    match, p,
                    feature.await(), tables.await(), results.await(), history.await(), o,
                )
                o to res
            }
            r.onSuccess { (o, res) ->
                oddsData = o
                prediction = res
            }.onFailure {
                // 深度接口整体失败时保底：用列表自带赔率做轻量预测
                prediction = prediction ?: runCatching { PredictionEngine.predictLight(match, p) }.getOrNull()
            }
            loading = false
        }
    }

    LaunchedEffect(match.matchId) {
        val p = ProfileRepository.getProfile(context, match.league)
        profile = p
        syncEdits(p)
        recompute(p)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        when {
            loading && prediction == null -> Box(
                Modifier.fillMaxWidth().padding(vertical = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "正在并行拉取官方前瞻数据…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            prediction == null -> Box(
                Modifier.fillMaxWidth().padding(vertical = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("预测数据加载失败", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text("请检查网络后重试", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { profile?.let(::recompute) }, shape = RoundedCornerShape(10.dp)) {
                        Text("重试", fontSize = 13.sp)
                    }
                }
            }

            else -> {
                val p = prediction!!
                val market = marketWdl(match, oddsData)

                SectionTitle("预测目标")
                PredictionRow("胜平负", labelOdds(p.wdlPick, wdlOdds(match, oddsData)))
                Spacer(Modifier.height(8.dp))
                PredictionRow("让球胜平负${p.hdpLine.ifEmpty { match.goalLine }}", labelOdds(p.hdpPick, hhadOdds(match, oddsData)))
                Spacer(Modifier.height(8.dp))
                PredictionRow("总比分", pickOdds(p.scorePick, crsByLabel(crsOdds(match, oddsData))))
                Spacer(Modifier.height(8.dp))
                PredictionRow("半全场胜平负", pickOdds(p.hfPick, hafuByLabel(hafuOdds(match, oddsData))))
                Spacer(Modifier.height(8.dp))
                PredictionRow("总进球数", pickOdds(p.totalPick, ttgByLabel(ttgOdds(match, oddsData))))
                Spacer(Modifier.height(20.dp))

                SectionTitle("思路分析")
                if (market != null) {
                    val marketPick = listOf("主胜", "平局", "客胜")[argmaxOf(market)]
                    if (marketPick != p.wdlPick) {
                        DivergenceHint(p, marketPick, market)
                        Spacer(Modifier.height(8.dp))
                    }
                }
                Text(
                    analysisText(match, p, market),
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))
            }
        }

        // 联赛参数配置面板（入口：预测分析栏）
        profile?.let { pf ->
            LeagueConfigPanel(
                base = pf,
                open = configOpen,
                editAvg = editAvg, editAdv = editAdv, editRho = editRho, editDraw = editDraw,
                editWm = editWm, editWp = editWp, editWs = editWs,
                onToggle = { configOpen = !configOpen },
                onAvg = { editAvg = it }, onAdv = { editAdv = it }, onRho = { editRho = it },
                onDraw = { editDraw = it }, onWm = { editWm = it }, onWp = { editWp = it },
                onWs = { editWs = it },
                onApply = {
                    val edited = pf.copy(
                        avgGoals = editAvg, homeAdv = editAdv, rho = editRho, drawBias = editDraw,
                        weights = SignalWeights(editWm, editWp, editWs),
                        sourceNote = "用户自定义参数（已保存）",
                    )
                    scope.launch {
                        ProfileRepository.saveProfile(context, match.league, edited)
                        profile = edited
                        recompute(edited)
                    }
                },
                onReset = {
                    scope.launch {
                        ProfileRepository.reset(context, match.league)
                        val base = LeagueProfiles.forLeague(match.league)
                        profile = base
                        syncEdits(base)
                        recompute(base)
                    }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "预测由竞彩官方真实数据（赔率 / 统计 / 交锋）融合计算，仅供参考，不构成投注建议。",
            Modifier.fillMaxWidth(),
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
    }
}

/* ---------- 预测项：官方赔率映射 ---------- */

/** 半全场标签（竞彩官方写法）→ 官方赔率键 */
private val hafuKeys = mapOf(
    "胜胜" to "hh", "胜平" to "hd", "胜负" to "ha",
    "平胜" to "dh", "平平" to "dd", "平负" to "da",
    "负胜" to "ah", "负平" to "ad", "负负" to "aa",
)

private fun wdlOdds(match: RemoteMatch, odds: MatchOdds?): Triple<String, String, String>? =
    odds?.had?.let { Triple(it.first.value, it.second.value, it.third.value) } ?: match.had

private fun hhadOdds(match: RemoteMatch, odds: MatchOdds?): Triple<String, String, String>? =
    odds?.hhad?.let { Triple(it.first.value, it.second.value, it.third.value) } ?: match.hhad

private fun crsOdds(match: RemoteMatch, odds: MatchOdds?): Map<String, String>? =
    odds?.crs?.mapValues { it.value.value }?.takeIf { it.isNotEmpty() } ?: match.crs

private fun hafuOdds(match: RemoteMatch, odds: MatchOdds?): Map<String, String>? =
    odds?.hafu?.mapValues { it.value.value }?.takeIf { it.isNotEmpty() } ?: match.hafu

private fun ttgOdds(match: RemoteMatch, odds: MatchOdds?): Map<String, String>? =
    odds?.ttg?.mapValues { it.value.value }?.takeIf { it.isNotEmpty() } ?: match.ttg

/** 胜平负 / 让球：预测项 → (标签, 该项官方赔率)；赔率缺失时只显示预测 */
private fun labelOdds(label: String, wdl: Triple<String, String, String>?): Pair<String, String>? {
    if (label == "--") return null
    val v = when (label) {
        "主胜", "让胜" -> wdl?.first
        "平局", "让平" -> wdl?.second
        "客胜", "让负" -> wdl?.third
        else -> null
    }
    return label to v.orEmpty()
}

/** 官方比分赔率键 → 展示标签（"0:1" / "胜其他" 等），兼容官方两种键写法 */
private fun crsByLabel(map: Map<String, String>?): Map<String, String> {
    if (map == null) return emptyMap()
    // 官方比分键为两位零填充（s00s01 = 0:1）；"其他"键实际为 s-1sh / s-1sd / s-1sa（兼容 s1sh 等写法）
    val pairRx = Regex("^s(\\d+)s(\\d+)$")
    val otherRx = Regex("^s-?\\d+s([hda])$")
    val out = mutableMapOf<String, String>()
    map.forEach { (k, v) ->
        pairRx.find(k)?.let { m ->
            val h = m.groupValues[1].toIntOrNull()
            val a = m.groupValues[2].toIntOrNull()
            if (h != null && a != null) out["$h:$a"] = v
        }
        otherRx.find(k)?.let { m ->
            out[when (m.groupValues[1]) {
                "h" -> "胜其他"
                "d" -> "平其他"
                else -> "负其他"
            }] = v
        }
    }
    return out
}

private fun hafuByLabel(map: Map<String, String>?): Map<String, String> {
    if (map == null) return emptyMap()
    return hafuKeys.entries.mapNotNull { (label, code) -> map[code]?.let { label to it } }.toMap()
}

private fun ttgByLabel(map: Map<String, String>?): Map<String, String> {
    if (map == null) return emptyMap()
    return (0..7).mapNotNull { k ->
        map["s$k"]?.let { (if (k == 7) "7球+" else "${k}球") to it }
    }.toMap()
}

/** 预测项 + 对应官方赔率（按标签查表）；预测缺失返回 null，赔率缺失只显示预测项 */
private fun pickOdds(label: String, map: Map<String, String>): Pair<String, String>? =
    if (label == "--") null else label to (map[label] ?: "")

/** 官方赔率隐含的胜平负概率（比例去水）；不可用返回 null */
private fun marketWdl(match: RemoteMatch, odds: MatchOdds?): Triple<Double, Double, Double>? {
    val t = wdlOdds(match, odds) ?: return null
    val p = OddsMath.impliedWdl(
        t.first.toDoubleOrNull() ?: 0.0,
        t.second.toDoubleOrNull() ?: 0.0,
        t.third.toDoubleOrNull() ?: 0.0,
    )
    return if (p.first + p.second + p.third > 0.0) p else null
}

private fun argmaxOf(t: Triple<Double, Double, Double>): Int {
    val l = listOf(t.first, t.second, t.third)
    return l.indices.maxByOrNull { l[it] } ?: 0
}

/* ---------- 预测文案 ---------- */

/** 模型结论与市场赔率方向相反时的显式提示（如实告知，不隐瞒风险） */
@Composable
private fun DivergenceHint(p: PredictionResult, marketPick: String, market: Triple<Double, Double, Double>) {
    val idx = listOf("主胜", "平局", "客胜").indexOf(marketPick).coerceAtLeast(0)
    val marketP = listOf(market.first, market.second, market.third)[idx]
    val modelP = when (p.wdlPick) {
        "主胜" -> p.homeProb
        "平局" -> p.drawProb
        else -> p.awayProb
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            "⚠ 与市场分歧：官方赔率更看好$marketPick（隐含 ${pctOf(marketP)}），模型倾向 ${p.wdlPick}（${pctOf(modelP)}），属逆向判断，请谨慎参考。",
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

private fun analysisText(
    match: RemoteMatch,
    p: PredictionResult,
    market: Triple<Double, Double, Double>?,
): String {
    val sb = StringBuilder()
    sb.append("模型融合「${p.signalNote}」信号后倾向 ${p.wdlPick}（主胜 ${pctOf(p.homeProb)} / 平 ${pctOf(p.drawProb)} / 客胜 ${pctOf(p.awayProb)}），置信度 ${p.conf}，数据完整度 ${pctOf(p.dataComplete)}。")
    if (p.hdpPick != "--") {
        sb.append("让球盘口 ${p.hdpLine.ifEmpty { match.goalLine }} 下倾向 ${p.hdpPick}；")
    }
    if (p.scorePick != "--") {
        sb.append("泊松比分矩阵显示最可能比分 ${p.scorePick}、总进球 ${p.totalPick}，半全场倾向 ${p.hfPick}；")
    }
    market?.let {
        sb.append("官方赔率隐含概率为 主胜 ${pctOf(it.first)} / 平 ${pctOf(it.second)} / 客胜 ${pctOf(it.third)}。")
    }
    if (p.key.isNotBlank()) sb.append("\n\n依据：${p.key}")
    return sb.toString()
}

/* ---------- 联赛参数配置面板 ---------- */

@Composable
private fun LeagueConfigPanel(
    base: LeagueProfile,
    open: Boolean,
    editAvg: Double, editAdv: Double, editRho: Double, editDraw: Double,
    editWm: Double, editWp: Double, editWs: Double,
    onToggle: () -> Unit,
    onAvg: (Double) -> Unit, onAdv: (Double) -> Unit, onRho: (Double) -> Unit, onDraw: (Double) -> Unit,
    onWm: (Double) -> Unit, onWp: (Double) -> Unit, onWs: (Double) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
    ) {
        // 头部（点击展开/收起）
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${base.league} 联赛参数配置", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (base.sourceNote.startsWith("用户自定义"))
                        "当前为自定义参数，已本地保存"
                    else "联赛默认模板，调整后立即重算",
                    Modifier.padding(top = 1.dp),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(if (open) "▾" else "▸", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)))
            Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                StepRow("场均进球 μ", fmt2(editAvg),
                    { onAvg((editAvg - 0.05).coerceIn(1.5, 4.5)) },
                    { onAvg((editAvg + 0.05).coerceIn(1.5, 4.5)) })
                StepRow("主场优势", fmt3(editAdv),
                    { onAdv((editAdv - 0.01).coerceIn(1.0, 1.6)) },
                    { onAdv((editAdv + 0.01).coerceIn(1.0, 1.6)) })
                StepRow("DC修正 ρ", fmt3(editRho),
                    { onRho((editRho - 0.01).coerceIn(-0.12, 0.0)) },
                    { onRho((editRho + 0.01).coerceIn(-0.12, 0.0)) })
                StepRow("平局保底", fmt3(editDraw),
                    { onDraw((editDraw - 0.01).coerceIn(0.04, 0.30)) },
                    { onDraw((editDraw + 0.01).coerceIn(0.04, 0.30)) })
                StepRow("权重·市场", fmt2(editWm),
                    { onWm((editWm - 0.05).coerceIn(0.0, 1.0)) },
                    { onWm((editWm + 0.05).coerceIn(0.0, 1.0)) })
                StepRow("权重·统计", fmt2(editWp),
                    { onWp((editWp - 0.05).coerceIn(0.0, 1.0)) },
                    { onWp((editWp + 0.05).coerceIn(0.0, 1.0)) })
                StepRow("权重·官方", fmt2(editWs),
                    { onWs((editWs - 0.05).coerceIn(0.0, 1.0)) },
                    { onWs((editWs + 0.05).coerceIn(0.0, 1.0)) })
                Spacer(Modifier.height(4.dp))
                Text(
                    "参数来源：${base.sourceNote}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onReset, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("恢复联赛默认", fontSize = 12.sp)
                    }
                    Button(onClick = onApply, Modifier.weight(1f), shape = RoundedCornerShape(10.dp)) {
                        Text("应用并重新计算", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = onMinus,
            Modifier.size(30.dp),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
        ) { Text("−", fontSize = 14.sp) }
        Text(
            " $value ",
            Modifier.width(64.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onPlus,
            Modifier.size(30.dp),
            contentPadding = PaddingValues(0.dp),
            shape = CircleShape
        ) { Text("＋", fontSize = 13.sp) }
    }
}

private fun pctOf(v: Double): String = "${(v * 100).roundToInt()}%"
private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun fmt3(v: Double): String = String.format(Locale.US, "%.3f", v)

/* ---------- 通用小组件 ---------- */

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(bottom = 8.dp),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 18.dp),
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun TripleOddsCard(items: List<Pair<String, OddsCell>>) {
    if (items.isEmpty()) {
        EmptyHint("本场未开售")
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, c) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.value, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(3.dp))
                    TrendArrow(c)
                }
            }
        }
    }
}

@Composable
private fun OddsGrid(items: List<Pair<String, OddsCell>>, columns: Int) {
    items.chunked(columns).forEach { rowItems ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rowItems.forEach { (label, c) ->
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                        .padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(c.value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(2.dp))
                        TrendArrow(c)
                    }
                }
            }
            repeat(columns - rowItems.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 赔率变化箭头：上升红 ↑，下降绿 ↓ */
@Composable
private fun TrendArrow(c: OddsCell) {
    if (c.trend == 0) return
    Text(
        if (c.trend == 1) "↑" else "↓",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (c.trend == 1) Color(0xFFDB2B2B) else Color(0xFF0BA64A)
    )
}

@Composable
private fun PredictionRow(title: String, pick: Pair<String, String>?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.07f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        if (pick != null) {
            Text(pick.first, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            if (pick.second.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Text("赔率 ${pick.second}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("暂无数据", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TwoColumnBars(items: List<Pair<String, String>>) {
    Row(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, (name, desc) ->
            if (i > 0) Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(desc, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun statusText(status: String): String = when (status.uppercase()) {
    "UPCOMING", "0" -> "未开赛"
    "LIVE", "1" -> "进行中"
    "FINISHED", "2", "CLOSED" -> "已完赛"
    "SELLING", "PRE_SELL" -> "未开赛"
    "OPEN" -> "进行中"
    else -> status.ifEmpty { "未开赛" }
}

/* ================= 通用组件 ================= */

@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            content()
        }
    }
}

@Composable
private fun TypeTag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>, title: String? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        if (title != null) {
            Text(
                title,
                Modifier.padding(top = 8.dp, bottom = 4.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        rows.forEachIndexed { i, (k, v) ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(k, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(v, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface)
            }
            if (i != rows.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                )
            }
        }
    }
}

/** 网络图片（加载失败/为空时显示文字占位） */
@Composable
private fun NetworkImage(url: String, modifier: Modifier = Modifier, fallback: String, round: Boolean = false) {
    Box(
        modifier
            .clip(if (round) CircleShape else RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(fallback, fontSize = 48.sp)
        }
    }
}
