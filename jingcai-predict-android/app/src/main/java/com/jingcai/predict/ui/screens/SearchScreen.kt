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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.remote.LeagueApi
import com.jingcai.predict.data.remote.LeagueEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 搜索结果状态持有者：离开/返回搜索页时保留结果。
 * Compose Navigation 默认在离开 composable 后丢弃 remember 状态，用全局对象恢复。
 * 搜索数据源为竞彩官网联赛列表（getLeagueListV1），无网络依赖，仅本地过滤。
 */
object SearchStateHolder {
    var query: String = ""
    var hasSearched: Boolean = false
    var allLeagues: List<LeagueEntry> = emptyList()
    var results: List<LeagueEntry> = emptyList()
    var jcFailed: Boolean = false
    var cacheLoaded: Boolean = false
}

/** 实时联想下拉中的一条建议 */
data class Suggestion(
    val title: String,   // 主显示文本
    val sub: String,     // 副标题
    val type: String,    // 联赛 / 搜索
    val payload: String, // 点击后用于搜索的关键词
)

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenLeague: (LeagueEntry) -> Unit,
) {
    var query by remember { mutableStateOf(SearchStateHolder.query) }
    var allLeagues by remember { mutableStateOf(SearchStateHolder.allLeagues) }
    var results by remember { mutableStateOf(SearchStateHolder.results) }
    var hasSearched by remember { mutableStateOf(SearchStateHolder.hasSearched) }
    var loading by remember { mutableStateOf(false) }
    var jcFailed by remember { mutableStateOf(SearchStateHolder.jcFailed) }

    // 实时联想下拉
    var suggestions by remember { mutableStateOf<List<Suggestion>>(emptyList()) }
    var showSuggestions by remember { mutableStateOf(false) }
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    val scope = rememberCoroutineScope()

    // 首次进入时加载联赛列表（本地缓存，用于实时联想与搜索）
    LaunchedEffect(Unit) {
        if (SearchStateHolder.cacheLoaded && allLeagues.isNotEmpty()) return@LaunchedEffect
        loading = true
        try {
            allLeagues = LeagueApi.fetchLeagueList()
            SearchStateHolder.cacheLoaded = true
            jcFailed = false
        } catch (e: Exception) {
            jcFailed = true
        }
        SearchStateHolder.allLeagues = allLeagues
        SearchStateHolder.jcFailed = jcFailed
        loading = false
    }

    /** 输入防抖 300ms 后生成本地联赛联想建议（纯本地，无网络） */
    fun updateSuggestions(text: String) {
        debounceJob?.cancel()
        if (text.isBlank()) {
            suggestions = emptyList()
            showSuggestions = false
            return
        }
        debounceJob = scope.launch {
            delay(300)
            if (!isActive) return@launch
            val kw = text.trim()
            if (kw.isEmpty()) {
                suggestions = emptyList()
                showSuggestions = false
                return@launch
            }
            val list = mutableListOf<Suggestion>()
            allLeagues
                .filter { it.name.contains(kw, ignoreCase = true) }
                .take(6)
                .forEach { l ->
                    list += Suggestion(
                        title = l.name,
                        sub = "联赛 · 查看赛程赛果",
                        type = "联赛",
                        payload = l.name,
                    )
                }
            list += Suggestion(
                title = "搜索“$kw”",
                sub = "按联赛名称搜索全部赛程赛果",
                type = "搜索",
                payload = kw,
            )
            suggestions = list
            showSuggestions = true
        }
    }

    fun doSearch(keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        debounceJob?.cancel()
        showSuggestions = false
        query = kw
        loading = true
        scope.launch {
            // 缓存为空时先拉一次联赛列表
            if (allLeagues.isEmpty()) {
                try {
                    allLeagues = LeagueApi.fetchLeagueList()
                    jcFailed = false
                } catch (e: Exception) {
                    jcFailed = true
                }
            }
            results = allLeagues.filter { it.name.contains(kw, ignoreCase = true) }
            hasSearched = true
            loading = false
            // 保存状态，返回本页时恢复
            SearchStateHolder.query = kw
            SearchStateHolder.hasSearched = true
            SearchStateHolder.allLeagues = allLeagues
            SearchStateHolder.results = results
            SearchStateHolder.jcFailed = jcFailed
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
                onValueChange = {
                    query = it
                    updateSuggestions(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("搜索联赛 / 比赛", fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            suggestions = emptyList()
                            showSuggestions = false
                        }) {
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

        if (showSuggestions && suggestions.isNotEmpty()) {
            SuggestionPanel(
                suggestions = suggestions,
                onPick = { s ->
                    query = s.payload
                    doSearch(s.payload)
                }
            )
        } else {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Text("正在加载联赛数据…", fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                !hasSearched -> HotSuggestions(
                    jcFailed = jcFailed,
                    onPick = { kw ->
                        query = kw
                        doSearch(kw)
                    }
                )

                results.isEmpty() -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "未找到与“${query.trim()}”相关的联赛",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "提示：按联赛名称搜索，如 德甲、英超、欧冠",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                        if (jcFailed) {
                            Spacer(Modifier.height(14.dp))
                            Text(
                                "竞彩官网数据暂不可达，请稍后重试",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                else -> LeagueResults(
                    leagues = results,
                    onLeagueClick = onOpenLeague,
                )
            }
        }
    }
}

/** 实时联想下拉 */
@Composable
private fun SuggestionPanel(
    suggestions: List<Suggestion>,
    onPick: (Suggestion) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .shadow(6.dp, RoundedCornerShape(14.dp))
    ) {
        suggestions.forEachIndexed { i, s ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(s) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(30.dp).clip(CircleShape).background(
                        (if (s.type == "联赛") Color(0xFF7C3AED) else MaterialTheme.colorScheme.primary)
                            .copy(alpha = 0.12f)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (s.type == "联赛") Icons.Outlined.EmojiEvents else Icons.Outlined.Search,
                        contentDescription = s.type,
                        modifier = Modifier.size(16.dp),
                        tint = if (s.type == "联赛") Color(0xFF7C3AED) else MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.title, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    if (s.sub.isNotEmpty()) {
                        Spacer(Modifier.height(1.dp))
                        Text(s.sub, fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                if (s.type != "搜索") {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            if (i != suggestions.lastIndex) {
                HorizontalDivider(
                    Modifier.padding(start = 14.dp, end = 14.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                )
            }
        }
    }
}

/* ---------- 热门搜索 ---------- */

private val HotKeywords = listOf("英超", "西甲", "德甲", "意甲", "法甲", "中超", "欧冠", "日职联")

@Composable
private fun HotSuggestions(jcFailed: Boolean, onPick: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
        Text("热门联赛", fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
            buildString {
                append("数据来源：中国体育彩票竞彩官网（联赛赛程赛果）\n输入时实时联想，支持中文搜索联赛")
                if (jcFailed) append("\n当前无法连接官网数据，请下拉重试")
            },
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

/* ---------- 联赛搜索结果 ---------- */

@Composable
private fun LeagueResults(
    leagues: List<LeagueEntry>,
    onLeagueClick: (LeagueEntry) -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "sec_l") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                )
                Text(" 联赛", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("${leagues.size} 条", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(leagues, key = { i, l -> "l_${l.id}_$i" }) { _, l ->
            LeagueResultCard(l, onClick = { onLeagueClick(l) })
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun LeagueResultCard(league: LeagueEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 联赛图标（去掉头像，用图标替代）
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF7C3AED).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = Color(0xFF7C3AED)
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(league.name.ifEmpty { "未知联赛" }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            Text(
                league.seasons.lastOrNull()?.let { "${it.seasonName} 赛季 · ${league.seasons.size} 个赛季" }
                    ?: "点击查看赛程赛果",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}