package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.jingcai.predict.data.remote.LeagueSeason

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
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                league.name.ifEmpty { "联赛详情" },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 联赛信息头
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LeagueLogo(league.logo, Modifier.size(72.dp))
            Spacer(Modifier.height(10.dp))
            Text(league.name.ifEmpty { "未知联赛" }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF7C3AED).copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text("联赛", fontSize = 11.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Medium)
            }
        }

        // 赛季选择
        if (league.seasons.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                league.seasons.forEach { s ->
                    val selected = s.seasonId == seasonId
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .clickable { seasonId = s.seasonId }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            s.seasonName,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
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
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载赛程赛果…", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("赛程赛果加载失败", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            loading = true
                            failed = false
                            groups = null
                        }) { Text("重试") }
                    }
                }

                groups.isNullOrEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("该联赛暂无赛程赛果", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                else -> {
                    val g = groups.orEmpty()
                    LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(g.size, key = { "g_${g[it].matchDate}" }) { gi ->
                        val group = g[gi]
                        // 日期分组头
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatDate(group.matchDate),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (group.isToday) {
                                Spacer(Modifier.width(8.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("今天", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        group.matches.forEachIndexed { mi, m ->
                            LeagueMatchRow(m)
                            if (mi != group.matches.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = 16.dp)
                                        .height(0.5.dp)
                                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                                )
                            }
                        }
                    }
                    item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
                }
                }
            }
        }
    }
}

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
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        if (info.isNotEmpty()) {
            Text(info, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                m.home,
                Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Box(
                Modifier
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (m.isScore) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    if (m.isScore) "$homeScore : $awayScore" else "VS",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (m.isScore) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                m.away,
                Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
            // 半场比分
            if (m.isScore && m.halfScore.isNotBlank()) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("半场", fontSize = 8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        m.halfScore,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
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
            .clip(RoundedCornerShape(16.dp))
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
            Text("🏆", fontSize = 32.sp)
        }
    }
}