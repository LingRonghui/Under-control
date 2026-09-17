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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jingcai.predict.data.MatchInfo
import com.jingcai.predict.data.TodayMatches
import com.jingcai.predict.data.TomorrowMatches
import com.jingcai.predict.data.remote.JingCaiApi
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.remote.SearchPlayer
import com.jingcai.predict.data.remote.SearchTeam
import com.jingcai.predict.data.remote.TeamDbApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onShowToast: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var jcFailed by remember { mutableStateOf(false) }   // 竞彩官网接口失败（已降级本地数据）
    var tdFailed by remember { mutableStateOf(false) }   // TheSportsDB 失败

    var matches by remember { mutableStateOf<List<RemoteMatch>>(emptyList()) }
    var teams by remember { mutableStateOf<List<SearchTeam>>(emptyList()) }
    var players by remember { mutableStateOf<List<SearchPlayer>>(emptyList()) }

    val scope = rememberCoroutineScope()

    fun doSearch(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        scope.launch {
            loading = true
            jcFailed = false
            tdFailed = false
            // 三个数据源并行请求，总耗时 = 最慢单个
            val jc = async {
                try {
                    JingCaiApi.fetchMatches()
                } catch (e: Exception) {
                    jcFailed = true
                    emptyList()
                }
            }
            val td = async {
                try {
                    val t = TeamDbApi.searchTeams(kw)
                    val p = TeamDbApi.searchPlayers(kw)
                    t to p
                } catch (e: Exception) {
                    tdFailed = true
                    emptyList<SearchTeam>() to emptyList<SearchPlayer>()
                }
            }
            val jcMatches = jc.await()
            val (teamList, playerList) = td.await()

            // 竞彩接口失败时降级为本地模拟数据，保证中文球队/联赛搜索仍可用
            val source = if (jcMatches.isNotEmpty()) jcMatches
            else (TodayMatches + TomorrowMatches).map { it.toRemote() }

            matches = source.filter {
                it.home.contains(kw, true) || it.away.contains(kw, true) ||
                    it.league.contains(kw, true) || it.num.contains(kw, true)
            }
            teams = teamList
            players = playerList
            hasSearched = true
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
                    Text("正在检索比赛、球队、球员…", fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            !hasSearched -> HotSuggestions(
                onPick = { doSearch(it) }
            )

            matches.isEmpty() && teams.isEmpty() && players.isEmpty() -> Box(
                Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "未找到与“${query.trim()}”相关的结果",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "提示：中文球队名搜索竞彩赛事（如 曼城）\n英文名搜索全球球队/球员（如 Manchester）",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        lineHeight = 18.sp
                    )
                    if (tdFailed) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "海外数据源（TheSportsDB）暂不可达，已为你展示竞彩赛事结果",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            else -> SearchResults(
                matches = matches,
                teams = teams,
                players = players,
                jcFailed = jcFailed,
                tdFailed = tdFailed,
                onMatchClick = { onShowToast("${it.home} vs ${it.away} · ${it.time}") },
                onTeamClick = { onShowToast(it.name) },
                onPlayerClick = { onShowToast(it.name) },
            )
        }
    }
}

/** 本地模拟数据 → 远程比赛模型（竞彩接口失败时的降级数据源） */
private fun MatchInfo.toRemote(): RemoteMatch = RemoteMatch(
    matchId = id,
    num = num,
    league = league,
    time = kickoff,
    home = home,
    away = away,
    had = Triple(oddsW.toString(), oddsD.toString(), oddsL.toString()),
    hhad = null,
    goalLine = "",
    status = status.name,
)

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
    jcFailed: Boolean,
    tdFailed: Boolean,
    onMatchClick: (RemoteMatch) -> Unit,
    onTeamClick: (SearchTeam) -> Unit,
    onPlayerClick: (SearchPlayer) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (jcFailed || tdFailed) {
            item(key = "notice") {
                val text = buildList {
                    if (jcFailed) add("竞彩官方接口暂不可达，已展示本地模拟数据")
                    if (tdFailed) add("海外数据源（TheSportsDB）暂不可达")
                }.joinToString("；")
                Text(
                    text,
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                        .padding(10.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
        if (matches.isNotEmpty()) {
            item(key = "sec_m") { SectionHeader("竞彩赛事", matches.size) }
            itemsIndexed(matches, key = { i, m -> "m_${m.matchId}_$i" }) { _, m ->
                MatchResultCard(m, onClick = { onMatchClick(m) })
            }
        }
        if (teams.isNotEmpty()) {
            item(key = "sec_t") { SectionHeader("球队", teams.size) }
            itemsIndexed(teams, key = { i, t -> "t_${t.id}_$i" }) { _, t ->
                TeamResultCard(t, onClick = { onTeamClick(t) })
            }
        }
        if (players.isNotEmpty()) {
            item(key = "sec_p") { SectionHeader("球员", players.size) }
            itemsIndexed(players, key = { i, p -> "p_${p.id}_$i" }) { _, p ->
                PlayerResultCard(p, onClick = { onPlayerClick(p) })
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
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
