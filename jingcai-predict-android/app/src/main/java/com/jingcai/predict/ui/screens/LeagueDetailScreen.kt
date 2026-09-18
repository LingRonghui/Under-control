package com.jingcai.predict.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jingcai.predict.data.remote.LeagueApi
import com.jingcai.predict.data.remote.LeagueDateGroup
import com.jingcai.predict.data.remote.LeagueEntry
import com.jingcai.predict.data.remote.LeagueMatch
import com.jingcai.predict.ui.components.EmptyState
import com.jingcai.predict.ui.components.Hairline
import com.jingcai.predict.ui.components.PillTag
import com.jingcai.predict.ui.components.SectionTitle
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular

/**
 * 联赛详情页：顶部联赛信息 + 赛季选择 + 按日期分组的赛程赛果（比赛列表）。
 * 数据来源：竞彩官网「联赛历史数据」getMatchResultV1。
 */
@Composable
fun LeagueDetailScreen(onBack: () -> Unit) {
    val league = DetailHolder.league ?: run {
        onBack()
        return
    }

    var seasonId by remember { mutableStateOf(defaultSeasonId(league)) }
    var groups by remember { mutableStateOf<List<LeagueDateGroup>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(league.id, seasonId) {
        loading = true
        failed = false
        try {
            groups = LeagueApi.fetchMatchResult(league.id, seasonId)
        } catch (e: Exception) {
            groups = null
            failed = true
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：以联赛名命名
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.xs, end = Space.lg, top = Space.sm, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                league.name.ifEmpty { "联赛详情" },
                style = MaterialTheme.typography.titleLarge,
                color = Tone.textStrong(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 联赛信息头：玻璃卡（联赛标识 + 名称 + 联赛标签）
        SurfaceCard(
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
            contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeagueLogo(league.logo, Modifier.size(56.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = Space.md)
                ) {
                    Text(
                        league.name.ifEmpty { "未知联赛" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Tone.textStrong(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Space.xs))
                    PillTag(text = "联赛", color = LeagueAccent)
                }
            }
        }

        // 赛季选择
        if (league.seasons.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Space.lg, vertical = Space.sm),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                league.seasons.forEach { s ->
                    val selected = s.seasonId == seasonId
                    val chipBg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else Tone.fill(),
                        animationSpec = tween(180),
                        label = "seasonBg"
                    )
                    val chipFg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary
                        else Tone.textLabel(),
                        animationSpec = tween(180),
                        label = "seasonFg"
                    )
                    Box(
                        Modifier
                            .clip(Corner.pill)
                            .background(chipBg)
                            .border(
                                1.dp,
                                if (selected) Tone.brandStroke() else Tone.hairline(),
                                Corner.pill
                            )
                            .clickable { seasonId = s.seasonId }
                            .padding(horizontal = Space.md, vertical = 6.dp)
                    ) {
                        Text(
                            s.seasonName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = chipFg
                        )
                    }
                }
            }
        }

        // 内容区：赛程赛果
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "正在加载赛程赛果…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tone.textBody()
                        )
                    }
                }

                failed -> EmptyState(
                    icon = Icons.Outlined.CloudOff,
                    title = "赛程赛果加载失败",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Space.lg),
                    action = {
                        TextButton(onClick = {
                            loading = true
                            failed = false
                            groups = null
                        }) {
                            Text("重试", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                )

                groups.isNullOrEmpty() -> EmptyState(
                    icon = Icons.Outlined.SportsSoccer,
                    title = "该联赛暂无赛程赛果",
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = Space.lg)
                )

                else -> {
                    val g = groups.orEmpty()
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(g.size, key = { "g_${g[it].matchDate}_$it" }) { gi ->
                            val group = g[gi]
                            // 日期分组头
                            SectionTitle(
                                title = formatDate(group.matchDate),
                                modifier = Modifier.padding(
                                    start = Space.lg,
                                    end = Space.lg,
                                    top = Space.section,
                                    bottom = Space.xs
                                ),
                                trailing = {
                                    if (group.isToday) {
                                        PillTag(
                                            text = "今天",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            )
                            group.matches.forEachIndexed { mi, m ->
                                LeagueMatchRow(m)
                                if (mi != group.matches.lastIndex) {
                                    Hairline(startPadding = Space.lg, endPadding = Space.lg)
                                }
                            }
                        }
                        item(key = "bottom") { Spacer(Modifier.height(Space.lg)) }
                    }
                }
            }
        }
    }
}

/** 联赛专属强调色（与搜索页保持一致） */
private val LeagueAccent = Color(0xFF7C3AED)

/** 默认选中赛季：优先当前赛季（名称含今年），否则取最后一个 */
private fun defaultSeasonId(league: LeagueEntry): String {
    if (league.seasons.isEmpty()) return ""
    league.seasons.firstOrNull { it.seasonName.contains("2026") }?.let { return it.seasonId }
    league.seasons.firstOrNull { it.seasonName.contains("2025") }?.let { return it.seasonId }
    return league.seasons.last().seasonId
}

private fun formatDate(date: String): String {
    val d = date.take(10)
    if (d.length < 10) return d
    // 2026-09-19 -> 09-19
    val mm = if (d.length >= 7) d.substring(5, 7) else ""
    val dd = if (d.length >= 10) d.substring(8, 10) else ""
    return if (mm.isNotEmpty() && dd.isNotEmpty()) "$mm-$dd" else d
}

@Composable
private fun LeagueMatchRow(m: LeagueMatch) {
    val homeScore = if (m.isScore) m.fullScore.substringBefore(":").trim() else ""
    val awayScore = if (m.isScore) m.fullScore.substringAfter(":", "").trim() else ""
    // 上侧信息：时间 · 阶段/轮次
    val info = buildList {
        if (m.matchTime.isNotEmpty()) add(m.matchTime)
        if (m.gameweek.isNotEmpty()) add("第${m.gameweek}轮")
        if (m.phaseName.isNotEmpty()) add(m.phaseName)
        if (m.groupName.isNotEmpty()) add(m.groupName)
    }.joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md)
    ) {
        if (info.isNotEmpty()) {
            Text(
                info,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = Tone.textLabel()
            )
            Spacer(Modifier.height(Space.sm))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.home,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = Tone.textStrong(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Box(
                Modifier
                    .padding(horizontal = Space.md)
                    .clip(Corner.sm)
                    .background(Tone.fill())
                    .padding(horizontal = Space.md, vertical = 5.dp)
            ) {
                Text(
                    if (m.isScore) "$homeScore : $awayScore" else "VS",
                    style = MaterialTheme.typography.titleMedium
                        .copy(fontSize = 18.sp, fontWeight = FontWeight.Bold).tabular(),
                    color = if (m.isScore) Tone.textStrong() else Tone.textHint(),
                    maxLines = 1
                )
            }
            Text(
                m.away,
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = Tone.textStrong(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
            // 半场比分
            if (m.isScore && m.halfScore.isNotBlank()) {
                Spacer(Modifier.width(Space.sm))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "半场",
                        style = MaterialTheme.typography.labelSmall,
                        color = Tone.textHint()
                    )
                    Text(
                        m.halfScore,
                        style = MaterialTheme.typography.labelSmall.tabular()
                            .copy(fontSize = 12.sp),
                        color = Tone.textBody(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** 网络联赛图标（加载失败/为空时显示文字占位） */
@Composable
private fun LeagueLogo(url: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(Corner.md)
            .background(Tone.fill()),
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
            Text("🏆", fontSize = 32.sp)
        }
    }
}