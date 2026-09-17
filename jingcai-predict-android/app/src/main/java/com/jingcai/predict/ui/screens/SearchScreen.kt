package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jingcai.predict.data.remote.JingCaiApi
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.remote.SearchPlayer
import com.jingcai.predict.data.remote.SearchTeam
import com.jingcai.predict.data.remote.TeamDbApi
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }

    var matches by remember { mutableStateOf<List<RemoteMatch>>(emptyList()) }
    var teams by remember { mutableStateOf<List<SearchTeam>>(emptyList()) }
    var players by remember { mutableStateOf<List<SearchPlayer>>(emptyList()) }
    // 竞彩官网全量数据缓存，避免每次搜索重复拉取
    var allMatches by remember { mutableStateOf<List<RemoteMatch>>(emptyList()) }

    val scope = rememberCoroutineScope()

    fun doSearch(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        scope.launch {
            loading = true
            error = false
            try {
                val all = if (allMatches.isEmpty()) JingCaiApi.fetchMatches() else allMatches
                allMatches = all
                matches = all.filter {
                    it.home.contains(kw, true) || it.away.contains(kw, true) ||
                        it.league.contains(kw, true) || it.num.contains(kw, true)
                }
                teams = TeamDbApi.searchTeams(kw)
                players = TeamDbApi.searchPlayers(kw)
                hasSearched = true
            } catch (e: Exception) {
                error = true
            }
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶部：返回 + 输入框 + 搜索按钮
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索联赛 / 球队 / 球员 / 比赛", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "清空", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { doSearch(query) })
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { doSearch(query) },
                enabled = query.isNotBlank() && !loading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("搜索", fontSize = 14.sp)
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("正在从竞彩官网 & TheSportsDB 检索…", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("网络请求失败，请检查网络后重试", fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            !hasSearched -> HotSuggestions(
                onPick = { doSearch(it) }
            )

            matches.isEmpty() && teams.isEmpty() && players.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Text(
                    "未找到与“${query.trim()}”相关的结果\n可尝试搜索英文球队名，如 Manchester、Liverpool",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }

            else -> SearchResults(
                matches = matches,
                teams = teams,
                players = players,
                onMatchClick = { onShowToast("${it.home} vs ${it.away} · ${it.time}") },
                onTeamClick = { onShowToast(it.name) },
                onPlayerClick = { onShowToast(it.name) },
            )
        }
    }
}

/* ---------- 热门搜索 ---------- */

private val HotKeywords = listOf("曼城", "皇马", "曼联", "巴萨", "利物浦", "拜仁", "阿森纳", "尤文")

@Composable
private fun HotSuggestions(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text("热门搜索", fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HotKeywords.take(4).forEach { kw ->
                HotChip(kw, Modifier.weight(1f), onPick)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HotKeywords.drop(4).forEach { kw ->
                HotChip(kw, Modifier.weight(1f), onPick)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "数据来源：中国体育彩票竞彩官网（中文比赛数据）\nTheSportsDB（全球球队/球员数据库）",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun HotChip(text: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            .clickable(onClick = { onClick(text) })
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    }
}

/* ---------- 搜索结果 ---------- */

@Composable
private fun SearchResults(
    matches: List<RemoteMatch>,
    teams: List<SearchTeam>,
    players: List<SearchPlayer>,
    onMatchClick: (RemoteMatch) -> Unit,
    onTeamClick: (SearchTeam) -> Unit,
    onPlayerClick: (SearchPlayer) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (matches.isNotEmpty()) {
            item { SectionHeader("竞彩赛事", matches.size) }
            items(matches, key = { "m_${it.matchId}" }) { m ->
                MatchResultCard(m, onClick = { onMatchClick(m) })
            }
        }
        if (teams.isNotEmpty()) {
            item { SectionHeader("球队", teams.size) }
            items(teams, key = { "t_${it.id}" }) { t ->
                TeamResultCard(t, onClick = { onTeamClick(t) })
            }
        }
        if (players.isNotEmpty()) {
            item { SectionHeader("球员", players.size) }
            items(players, key = { "p_${it.id}" }) { p ->
                PlayerResultCard(p, onClick = { onPlayerClick(p) })
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
        )
        Text(" $title", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("$count 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MatchResultCard(match: RemoteMatch, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(74.dp)) {
            Text(
                match.num.ifEmpty { "场次" },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                match.time,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                match.league,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(match.home, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(match.away, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            match.had?.let {
                Text("胜 ${it.first}  平 ${it.second}  负 ${it.third}", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (match.hhad != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "让球${match.goalLine}：胜 ${match.hhad.first} 平 ${match.hhad.second} 负 ${match.hhad.third}",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun TeamResultCard(team: SearchTeam, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkImage(team.badge, Modifier.size(38.dp), fallback = "⚽")
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(team.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                listOf(team.league, team.country).filter { it.isNotEmpty() }.joinToString(" · ")
                    .ifEmpty { "未知联赛" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text("查看", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun PlayerResultCard(player: SearchPlayer, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NetworkImage(player.photo, Modifier.size(38.dp), fallback = "👤")
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(player.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                listOf(player.team, player.position, player.nationality)
                    .filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "未知球队" },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Text("查看", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }
}

/** 网络图片（加载失败/为空时显示文字占位） */
@Composable
private fun NetworkImage(url: String, modifier: Modifier = Modifier, fallback: String) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
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
            Text(fallback, fontSize = 18.sp)
        }
    }
}
