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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.jingcai.predict.data.MatchInfo
import com.jingcai.predict.data.MatchStatus
import com.jingcai.predict.data.TodayMatches
import com.jingcai.predict.data.TomorrowMatches

@Composable
fun MatchesScreen(
    favIds: SnapshotStateList<String>,
    onToggleFav: (String) -> Unit,
    onSearchClick: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }
    val liveCount = TodayMatches.count { it.status == MatchStatus.LIVE }

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
                        .background(MaterialTheme.colorScheme.error)
                )
                Text(
                    " ${liveCount}场直播中",
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
        val counts = listOf(
            TodayMatches.size,
            TomorrowMatches.size,
            favIds.size
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

        // 比赛列表
        val list = when (tab) {
            1 -> TomorrowMatches
            2 -> TodayMatches.filter { it.id in favIds }
            else -> TodayMatches
        }
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
                            "暂无收藏比赛\n点击比赛卡片旁的星标即可收藏",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp
                        )
                    }
                }
            }
            list.groupBy { it.league }.forEach { (league, matches) ->
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
                        onFav = { onToggleFav(matches[idx].id) }
                    )
                }
            }
            item {
                Text(
                    "数据来源：中国体育彩票 · 竞彩足球（模拟数据）",
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MatchCard(
    match: MatchInfo,
    faved: Boolean,
    onFav: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
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
        // 中：球队
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            TeamLine(match.home, match.homeColor, match.score, isAway = false)
            Spacer(Modifier.height(5.dp))
            TeamLine(match.away, match.awayColor, null, isAway = true)
        }
        // 右：赔率
        Column(horizontalAlignment = Alignment.End) {
            OddsText("胜 ${match.oddsW}")
            OddsText("平 ${match.oddsD}")
            OddsText("负 ${match.oddsL}")
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
private fun TeamLine(name: String, color: Color, score: String?, isAway: Boolean) {
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
                color = if (isAway) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
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
