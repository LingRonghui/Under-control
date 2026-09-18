package com.jingcai.predict.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsSoccer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.MatchInfo
import com.jingcai.predict.data.MatchStatus
import com.jingcai.predict.data.remote.JingCaiApi
import com.jingcai.predict.data.remote.LiveMatchBrief
import com.jingcai.predict.data.remote.MatchPreviewApi
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.remote.ResultBrief
import com.jingcai.predict.ui.components.EmptyState
import com.jingcai.predict.ui.components.PillTag
import com.jingcai.predict.ui.components.SectionTitle
import com.jingcai.predict.ui.components.SegmentedTabs
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    // 三大分类：未开始 / 进行中 / 已结束（收藏 = 三类中命中 favIds 的并集）
    var upcoming by remember { mutableStateOf<List<MatchInfo>>(emptyList()) }
    var liveList by remember { mutableStateOf<List<MatchInfo>>(emptyList()) }
    var finishedList by remember { mutableStateOf<List<MatchInfo>>(emptyList()) }
    // 点击进入详情所需的原始数据；timeById 存完整开赛时间（"yyyy-MM-dd HH:mm:ss"）用于排序
    var remoteById by remember { mutableStateOf<Map<String, RemoteMatch>>(emptyMap()) }
    var timeById by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    /** @param silent 静默刷新（自动刷新用：不显示加载态与下拉刷新圈） */
    fun load(showRefresh: Boolean, silent: Boolean = false) {
        if (!silent) {
            if (showRefresh) refreshing = true else loading = true
            failed = false
        }
        scope.launch {
            // 并行：竞彩在售列表（= 未开始） + 比分直播（= 进行中 / 已结束）
            val daysDeferred = async { runCatching { JingCaiApi.fetchMatchDays() }.getOrNull() }
            val liveDeferred = async {
                runCatching { MatchPreviewApi.fetchLiveBriefs() }
                    .getOrDefault(Pair(emptyList<LiveMatchBrief>(), emptyMap()))
            }
            // 已结束：赛果列表（每页 10 场，翻页取够最近 30 场）
            val resultDeferred = async {
                runCatching { MatchPreviewApi.fetchResultList() }.getOrDefault(emptyList())
            }
            val days = daysDeferred.await()
            val (briefs, scores) = liveDeferred.await()
            val resultList = resultDeferred.await()
            if (days == null) failed = true

            val todayStr = LocalDate.now().toString()
            val remoteMap = mutableMapOf<String, RemoteMatch>()
            val timeMap = mutableMapOf<String, String>()
            val live = mutableListOf<MatchInfo>()

            // 1) 比分直播：进行中的比赛（只展示当天的）
            briefs.forEach { b ->
                val score = scores[b.matchId] ?: return@forEach
                if (!score.isLive) return@forEach
                if (b.matchDate.isNotEmpty() && b.matchDate < todayStr) return@forEach
                val fullTime = if (b.matchDate.isNotEmpty()) "${b.matchDate} ${b.matchTime}" else b.matchTime
                live += MatchInfo(
                    id = b.matchId,
                    num = b.num,
                    league = b.league,
                    kickoff = b.matchTime,
                    home = b.home,
                    away = b.away,
                    homeColor = teamColor(b.home),
                    awayColor = teamColor(b.away),
                    oddsW = 0.0, oddsD = 0.0, oddsL = 0.0,
                    status = MatchStatus.LIVE,
                    liveMinute = score.minute.toIntOrNull(),
                    score = score.score,
                    htScore = score.halfScore,
                )
                remoteMap[b.matchId] = RemoteMatch(
                    matchId = b.matchId,
                    num = b.num,
                    league = b.league,
                    time = fullTime,
                    home = b.home,
                    away = b.away,
                    had = null, hhad = null, goalLine = "",
                    status = "LIVE",
                )
                timeMap[b.matchId] = fullTime
            }

            // 2) 竞彩官方在售列表 = 未开始（排除已在比分直播出现的场次，避免同一场重复）
            val up = mutableListOf<MatchInfo>()
            days?.forEach { day ->
                day.matches.forEach { m ->
                    if (remoteMap.containsKey(m.matchId)) return@forEach
                    up += m.toMatchInfo()
                    remoteMap[m.matchId] = m
                    timeMap[m.matchId] = if (m.time.contains(' ')) m.time else "${day.date} ${m.time}"
                }
            }

            // 3) 已结束：赛果按时间倒序取最近 30 场（新赛果出现后，最底部最早的一场自动被挤出）
            val liveIds = live.map { it.id }.toSet()
            val finished = resultList
                .filter { it.matchId !in liveIds }   // 正在直播的场次不属于已结束
                .distinctBy { it.matchId }
                .sortedWith(compareByDescending<ResultBrief> { it.matchDate }.thenByDescending { it.matchTime })
                .take(30)
            finished.forEach { f ->
                val full = "${f.matchDate} ${f.matchTime}".trim()
                remoteMap[f.matchId] = RemoteMatch(
                    matchId = f.matchId,
                    num = f.num,
                    league = f.league,
                    time = full,
                    home = f.home,
                    away = f.away,
                    had = null, hhad = null, goalLine = "",
                    status = "FINISHED",
                )
                timeMap[f.matchId] = full
            }

            upcoming = up
            liveList = live
            finishedList = finished.map { it.toMatchInfo() }
            remoteById = remoteMap
            timeById = timeMap
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { load(false) }

    // 停留在「进行中」时，每 1 分钟静默刷新一次比分
    LaunchedEffect(tab) {
        if (tab != 1) return@LaunchedEffect
        while (true) {
            delay(60_000)
            load(showRefresh = false, silent = true)
        }
    }

    val todayStr = LocalDate.now().toString()
    // 顶部角标：今日全部场次（未开始 + 进行中 + 今日已结束）
    val todayCount = upcoming.count { (timeById[it.id] ?: "").startsWith(todayStr) } +
        liveList.size + finishedList.count { (timeById[it.id] ?: "").startsWith(todayStr) }

    Column(Modifier.fillMaxSize()) {
        // 品牌栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.lg, top = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(Corner.sm)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary)
            }
            Text(
                "Under Control",
                Modifier.padding(start = Space.sm),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(Corner.pill)
                    .background(Tone.fill())
                    .border(1.dp, Tone.hairline(), Corner.pill)
                    .padding(horizontal = Space.sm, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    "今日 $todayCount 场",
                    Modifier.padding(start = 5.dp),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 搜索框：胶囊入口（点击进入搜索页）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.lg, vertical = Space.md)
                .clip(Corner.pill)
                .background(Tone.fill())
                .border(1.dp, Tone.hairline(), Corner.pill)
                .clickable { onSearchClick() }
                .padding(horizontal = Space.md, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp)
            )
            Text(
                "搜索联赛 / 比赛",
                Modifier.padding(start = Space.sm),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 分类 Tab：未开始 / 进行中 / 已结束 / 收藏
        val favAll = (upcoming + liveList + finishedList).filter { it.id in favIds }
        val tabs = listOf("未开始", "进行中", "已结束", "收藏")
        val counts = listOf(upcoming.size, liveList.size, finishedList.size, favAll.size)
        SegmentedTabs(
            items = tabs.mapIndexed { i, name -> "$name ${counts[i]}" },
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs)
        )

        // 比赛列表：官方数据 + 下拉刷新
        val noData = upcoming.isEmpty() && liveList.isEmpty() && finishedList.isEmpty()
        when {
            loading && noData -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "正在加载竞彩赛事…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            failed && noData -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "数据加载失败",
                    description = "请检查网络后下拉重试",
                    modifier = Modifier.padding(horizontal = Space.lg),
                    action = {
                        Button(onClick = { load(false) }, shape = Corner.sm) {
                            Text("重新加载", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )
            }

            else -> PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = { load(true) },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(bottom = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    if (counts.getOrElse(tab) { 0 } == 0) {
                        item {
                            val emptyIcon =
                                if (tab == 3) Icons.Outlined.StarBorder else Icons.Outlined.SportsSoccer
                            val emptyTitle = when (tab) {
                                0 -> "暂无未开始的竞彩赛事"
                                1 -> "当前没有进行中的比赛"
                                2 -> "今日暂无已结束的比赛"
                                else -> "暂无收藏比赛"
                            }
                            EmptyState(
                                icon = emptyIcon,
                                title = emptyTitle,
                                description = if (tab == 3) "点击比赛卡片旁的星标即可收藏" else null
                            )
                        }
                    }

                    if (tab == 0) {
                        // 未开始：按开赛时间从早到晚排序（跨天按真实时间比较），并按日期分组
                        val sorted = upcoming.sortedBy { timeById[it.id] ?: it.kickoff }
                        val tomorrowStr = LocalDate.now().plusDays(1).toString()
                        var lastDate: String? = null
                        sorted.forEach { m ->
                            val date = (timeById[m.id] ?: "").take(10)
                            if (date != lastDate) {
                                lastDate = date
                                val n = sorted.count { (timeById[it.id] ?: "").take(10) == date }
                                val title = when (date) {
                                    todayStr -> "今天 · ${date.takeLast(5)}"
                                    tomorrowStr -> "明天 · ${date.takeLast(5)}"
                                    "" -> "近期"
                                    else -> date
                                }
                                item(key = "d_$date") { GroupHeader(title, n) }
                            }
                            item(key = m.id) {
                                MatchCard(
                                    match = m,
                                    faved = m.id in favIds,
                                    showLeague = true,
                                    onFav = { onToggleFav(m.id) },
                                    onClick = { remoteById[m.id]?.let(onOpenMatch) }
                                )
                            }
                        }
                    } else {
                        val list = when (tab) {
                            1 -> liveList.sortedBy { timeById[it.id] ?: it.kickoff }
                            2 -> finishedList.sortedByDescending { timeById[it.id] ?: it.kickoff }
                            else -> favAll.sortedWith(
                                compareBy({ statusRank(it) }, { timeById[it.id] ?: it.kickoff })
                            )
                        }
                        list.groupBy { it.league }.forEach { (league, matches) ->
                            item(key = "h_$league") { GroupHeader(league, matches.size) }
                            items(matches.size, key = { matches[it].id }) { idx ->
                                MatchCard(
                                    match = matches[idx],
                                    faved = matches[idx].id in favIds,
                                    onFav = { onToggleFav(matches[idx].id) },
                                    onClick = { remoteById[matches[idx].id]?.let(onOpenMatch) }
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            "下拉可刷新",
                            Modifier
                                .fillMaxWidth()
                                .padding(top = Space.xl),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

/** 分组标题：未开始按日期分组，其余按联赛分组 */
@Composable
private fun GroupHeader(title: String, count: Int) {
    SectionTitle(
        title = title,
        modifier = Modifier.padding(
            start = Space.lg,
            end = Space.lg,
            top = Space.section,
            bottom = Space.xs
        ),
        trailing = {
            Text(
                "$count 场",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
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

/** 赛果列表条目 → 界面比赛模型 */
private fun ResultBrief.toMatchInfo(): MatchInfo = MatchInfo(
    id = matchId,
    num = num,
    league = league,
    kickoff = matchTime,
    home = home,
    away = away,
    homeColor = teamColor(home),
    awayColor = teamColor(away),
    oddsW = 0.0, oddsD = 0.0, oddsL = 0.0,
    status = MatchStatus.FINISHED,
    liveMinute = null,
    score = score,
    htScore = htScore,
)

@Composable
private fun MatchCard(
    match: MatchInfo,
    faved: Boolean,
    onFav: () -> Unit,
    onClick: () -> Unit,
    showLeague: Boolean = false,
) {
    val live = match.status == MatchStatus.LIVE
    val favTint by animateColorAsState(
        targetValue = if (faved) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(180),
        label = "favTint"
    )
    SurfaceCard(
        modifier = Modifier
            .padding(horizontal = Space.lg)
            .alpha(if (match.status == MatchStatus.FINISHED) 0.86f else 1f),
        onClick = onClick,
        contentPadding = PaddingValues(
            start = Space.md,
            end = Space.xs,
            top = Space.md,
            bottom = Space.md
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 左：编号 / 开赛时间 / 状态
            Column(Modifier.width(58.dp)) {
                Text(
                    match.num,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    match.kickoff,
                    style = MaterialTheme.typography.titleSmall
                        .copy(fontSize = 14.sp, fontWeight = FontWeight.Bold).tabular(),
                    color = if (live) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.height(Space.xs))
                StatusChip(match)
            }
            // 中：球队（进行中/已完赛时，各队比分放在球队名右侧，一上一下）
            val scoreParts = if (match.status != MatchStatus.UPCOMING && match.score != null)
                match.score!!.split(":", limit = 2) else null
            Column(Modifier.weight(1f).padding(horizontal = Space.sm + 2.dp)) {
                // 按日期分组的列表没有联赛表头，联赛名放在卡片内
                if (showLeague) {
                    Text(
                        match.league,
                        Modifier.padding(bottom = Space.xs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TeamLine(match.home, match.homeColor, scoreParts?.getOrNull(0), live)
                Spacer(Modifier.height(Space.sm))
                TeamLine(match.away, match.awayColor, scoreParts?.getOrNull(1), live)
            }
            // 右：赔率（仅未开始显示胜平负预览；进行中/已完赛只显示比分，隐藏赔率）
            if (match.status == MatchStatus.UPCOMING) {
                Column(horizontalAlignment = Alignment.End) {
                    OddsText("胜", match.oddsW)
                    OddsText("平", match.oddsD)
                    OddsText("负", match.oddsL)
                }
            }
            // 收藏
            IconButton(onClick = onFav, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (faved) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "收藏",
                    tint = favTint,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun TeamLine(name: String, color: Color, score: String?, live: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.9f))
        )
        Text(
            name,
            Modifier
                .padding(start = Space.sm)
                .weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (score != null) {
            Text(
                score,
                Modifier.padding(start = Space.sm),
                style = MaterialTheme.typography.bodyLarge
                    .copy(fontWeight = FontWeight.Bold).tabular(),
                color = if (live) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OddsText(label: String, value: Double) {
    Row(
        Modifier.padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (value > 0) value.toString() else "--",
            Modifier.padding(start = Space.xs),
            style = MaterialTheme.typography.labelLarge.tabular(),
            color = if (value > 0) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 1
        )
    }
}

@Composable
private fun StatusChip(match: MatchInfo) {
    when (match.status) {
        MatchStatus.LIVE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Spacer(Modifier.width(Space.xs))
            PillTag(
                text = match.liveMinute?.let { "$it'" } ?: "进行中",
                color = MaterialTheme.colorScheme.primary
            )
        }
        MatchStatus.FINISHED -> Text(
            "已完赛",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
        MatchStatus.UPCOMING -> Text(
            "未开赛",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
        )
    }
}
