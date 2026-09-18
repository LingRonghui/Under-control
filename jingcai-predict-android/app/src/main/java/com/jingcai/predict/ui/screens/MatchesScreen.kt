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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
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
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.MatchInfo
import com.jingcai.predict.data.MatchStatus
import com.jingcai.predict.data.remote.JingCaiApi
import com.jingcai.predict.data.remote.LiveMatchBrief
import com.jingcai.predict.data.remote.LiveScore
import com.jingcai.predict.data.remote.MatchPreviewApi
import com.jingcai.predict.data.remote.RemoteMatch
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    favIds: SnapshotStateList<String>,
    onToggleFav: (String) -> Unit,
    onSearchClick: () -> Unit,
    onOpenMatch: (RemoteMatch) -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    var todayList by remember { mutableStateOf<List<MatchInfo>>(emptyList()) }
    var tomorrowList by remember { mutableStateOf<List<MatchInfo>>(emptyList()) }
    var todayRemote by remember { mutableStateOf<List<RemoteMatch>>(emptyList()) }
    var tomorrowRemote by remember { mutableStateOf<List<RemoteMatch>>(emptyList()) }
    // 实时比分（进行中/已结束的补充比赛 + 比分预览）
    var liveScores by remember { mutableStateOf<Map<String, LiveScore>>(emptyMap()) }
    var liveMatchByLeague by remember { mutableStateOf<Map<String, List<MatchInfo>>>(emptyMap()) }
    var liveRemoteByLeague by remember { mutableStateOf<Map<String, List<RemoteMatch>>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun load(showRefresh: Boolean) {
        if (showRefresh) refreshing = true else loading = true
        failed = false
        scope.launch {
            // 并行：竞彩售彩列表（官方 今日/明日） + 比分直播（已结束/进行中补充）
            val live = async {
                runCatching { MatchPreviewApi.fetchLiveBriefs() }
                    .getOrDefault(Pair(emptyList<LiveMatchBrief>(), emptyMap()))
            }
            try {
                val days = JingCaiApi.fetchMatchDays()
                val todayStr = LocalDate.now().toString()
                val today = mutableListOf<MatchInfo>()
                val tomorrow = mutableListOf<MatchInfo>()
                val todayR = mutableListOf<RemoteMatch>()
                val tomorrowR = mutableListOf<RemoteMatch>()
                days.forEachIndexed { i, day ->
                    val isToday = day.date.isNotEmpty() && day.date == todayStr
                    if (isToday || (day.date.isEmpty() && i == 0 && today.isEmpty())) {
                        today += day.matches.map { it.toMatchInfo() }
                        todayR += day.matches
                    } else {
                        tomorrow += day.matches.map { it.toMatchInfo() }
                        tomorrowR += day.matches
                    }
                }
                todayList = today
                tomorrowList = tomorrow
                todayRemote = todayR
                tomorrowRemote = tomorrowR
            } catch (e: Exception) {
                failed = true
            } finally {
                loading = false
                refreshing = false
            }

            // 组装比分直播补充比赛：进行中/已结束补到"今日"tab，按联赛分组
            val (briefs, scores) = live.await()
            liveScores = scores
            val targetDate = LocalDate.now().toString()
            val byLeague = mutableMapOf<String, MutableList<MatchInfo>>()
            val byLeagueRemote = mutableMapOf<String, MutableList<RemoteMatch>>()
            briefs.forEach { b ->
                // 补充：不重复已存在的官方比赛；仅保留"今天"相关（进行中/已结束）
                val score = scores[b.matchId] ?: return@forEach
                if (!score.isLive && !score.isFinished) return@forEach
                val date = b.matchDate
                if (date.isNotEmpty() && date < targetDate) return@forEach
                val lived = todayList.any { it.id == b.matchId }
                if (lived) return@forEach
                val st = when {
                    score.isLive -> MatchStatus.LIVE
                    else -> MatchStatus.FINISHED
                }
                val mi = MatchInfo(
                    id = b.matchId,
                    num = b.num,
                    league = b.league,
                    round = "",
                    kickoff = b.matchTime,
                    home = b.home,
                    away = b.away,
                    homeColor = teamColor(b.home),
                    awayColor = teamColor(b.away),
                    oddsW = 0.0, oddsD = 0.0, oddsL = 0.0,
                    status = st,
                    liveMinute = score.minute.toIntOrNull(),
                    score = score.score,
                    htScore = score.halfScore,
                )
                val rm = RemoteMatch(
                    matchId = b.matchId,
                    num = b.num,
                    league = b.league,
                    time = if (b.matchDate.isNotEmpty()) b.matchDate + " " + b.matchTime else b.matchTime,
                    home = b.home,
                    away = b.away,
                    had = null, hhad = null, goalLine = "", status = st.name,
                )
                byLeague.getOrPut(b.league) { mutableListOf() }.add(mi)
                byLeagueRemote.getOrPut(b.league) { mutableListOf() }.add(rm)
            }
            liveMatchByLeague = byLeague
            liveRemoteByLeague = byLeagueRemote
        }
    }

    LaunchedEffect(Unit) { load(false) }

    // 今日列表 = 官方售彩（未开赛） + 比分直播补充（进行中/已结束）
    val todayAll = todayList + liveMatchByLeague.values.flatten()

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
                Text("⚡", fontSize = 14.sp)
            }
            Text(
                "竞彩足球预测",
                Modifier.padding(start = 8.dp),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    " 今日 ${todayAll.size} 场",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 搜索框
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    RoundedCornerShape(14.dp)
                )
                .clickable { onSearchClick() }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Text(
                "搜索联赛 / 球队 / 球员 / 比赛",
                Modifier.padding(start = 8.dp),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 日期 Tab
        val tabs = listOf("今日", "明日", "收藏")
        val favCount = (todayAll + tomorrowList).count { it.id in favIds }
        val counts = listOf(
            todayAll.size,
            tomorrowList.size,
            favCount
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            tabs.forEachIndexed { i, name ->
                val selected = tab == i
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { tab = i }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        " ${counts[i]}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 比赛列表：官方数据 + 下拉刷新
        val noData = todayList.isEmpty() && tomorrowList.isEmpty()
        when {
            loading && noData -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载竞彩赛事…", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            failed && noData -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                val list = when (tab) {
                    1 -> tomorrowList
                    2 -> (todayList + tomorrowList + liveMatchByLeague.values.flatten())
                        .filter { it.id in favIds }
                    else -> (todayList + liveMatchByLeague.values.flatten())
                        .sortedBy { statusRank(it) }
                }
                val remote = when (tab) {
                    1 -> tomorrowRemote
                    2 -> (todayRemote + tomorrowRemote + liveRemoteByLeague.values.flatten())
                        .filter { it.matchId in favIds }
                    else -> todayRemote + liveRemoteByLeague.values.flatten()
                }
                val grouped = list.groupBy { it.league }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (list.isEmpty()) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 60.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (tab == 2) "暂无收藏比赛\n点击比赛卡片旁的星标即可收藏"
                                    else "今日暂无竞彩赛事",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 22.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    grouped.forEach { (league, matches) ->
                        item(key = "head_$league") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 14.dp, end = 14.dp, top = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    " $league",
                                    Modifier.padding(start = 4.dp),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${matches.size}场",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(matches.size, key = { matches[it].id }) { idx ->
                            MatchCard(
                                match = matches[idx],
                                faved = matches[idx].id in favIds,
                                onFav = { onToggleFav(matches[idx].id) },
                                onClick = { remote.find { it.matchId == matches[idx].id }?.let(onOpenMatch) }
                            )
                        }
                    }
                    item {
                        Text(
                            "数据来源：中国体育彩票 · 竞彩足球官方数据\n下拉可刷新",
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp),
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

/** 竞彩官方比赛数据 → 界面比赛模型 */
private val teamPalette = listOf(
    Color(0xFF2E6BE6), Color(0xFF0E9F6E), Color(0xFF7C3AED),
    Color(0xFFEA580C), Color(0xFFD93A2B), Color(0xFF0891B2),
    Color(0xFF0F766E), Color(0xFFA21CAF),
)

private fun teamColor(name: String): Color =
    teamPalette[(name.hashCode() and Int.MAX_VALUE) % teamPalette.size]

/** 排序权重：进行中 > 未开始 > 已结束 */
private fun statusRank(m: MatchInfo): Int = when (m.status) {
    MatchStatus.LIVE -> 0
    MatchStatus.UPCOMING -> 1
    MatchStatus.FINISHED -> 2
}

private fun RemoteMatch.toMatchInfo(): MatchInfo {
    val w = had?.first?.toDoubleOrNull()
    val d = had?.second?.toDoubleOrNull()
    val l = had?.third?.toDoubleOrNull()
    return MatchInfo(
        id = matchId,
        num = num,
        league = league,
        round = "",
        kickoff = time.substringAfter(' ', time).take(5),   // "2026-09-18 02:30:00" → "02:30"
        home = home,
        away = away,
        homeColor = teamColor(home),
        awayColor = teamColor(away),
        oddsW = w ?: 0.0,
        oddsD = d ?: 0.0,
        oddsL = l ?: 0.0,
        status = MatchStatus.UPCOMING,
    )
}

@Composable
private fun MatchCard(
    match: MatchInfo,
    faved: Boolean,
    onFav: () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .alpha(if (match.status == MatchStatus.FINISHED) 0.5f else 1f)
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 11.dp, bottom = 11.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：编号 / 时间 / 状态
        Column(Modifier.width(62.dp)) {
            Text(match.num, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                match.kickoff,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            StatusChip(match)
        }
        // 中：球队（进行中/已完赛时，各队比分放在球队名右边，一上一下）
        val scoreParts = if (match.status != MatchStatus.UPCOMING && match.score != null)
            match.score!!.split(":", limit = 2) else null
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            TeamLine(
                match.home, match.homeColor,
                scoreParts?.getOrNull(0), isAway = false,
                scoreColor = if (match.status == MatchStatus.LIVE) Color(0xFFD93A2B)
                else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(5.dp))
            TeamLine(
                match.away, match.awayColor,
                scoreParts?.getOrNull(1), isAway = true,
                scoreColor = if (match.status == MatchStatus.LIVE) Color(0xFFD93A2B)
                else MaterialTheme.colorScheme.primary
            )
        }
        // 右：赔率（仅未开始显示胜平负预览；进行中/已完赛只显示比分，隐藏赔率）
        if (match.status == MatchStatus.UPCOMING) {
            Column(horizontalAlignment = Alignment.End) {
                OddsText(if (match.oddsW > 0) "胜 ${match.oddsW}" else "--")
                OddsText(if (match.oddsD > 0) "平 ${match.oddsD}" else "--")
                OddsText(if (match.oddsL > 0) "负 ${match.oddsL}" else "--")
            }
        }
        // 收藏
        IconButton(onClick = onFav, modifier = Modifier.size(40.dp)) {
            Icon(
                if (faved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "收藏",
                tint = if (faved) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun TeamLine(name: String, color: Color, score: String?, isAway: Boolean, scoreColor: Color? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            " $name",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isAway && score != null) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface
        )
        if (score != null) {
            Spacer(Modifier.weight(1f))
            Text(
                score,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = scoreColor ?: (if (isAway) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

@Composable
private fun OddsText(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 16.sp
    )
}

@Composable
private fun StatusChip(match: MatchInfo) {
    when (match.status) {
        MatchStatus.LIVE -> Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
            )
            Text(
                " ${match.liveMinute}'",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
        MatchStatus.FINISHED -> Text(
            "完场",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MatchStatus.UPCOMING -> Text(
            "未开赛",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
