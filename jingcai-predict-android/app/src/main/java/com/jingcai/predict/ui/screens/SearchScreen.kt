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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.remote.LeagueApi
import com.jingcai.predict.data.remote.LeagueEntry
import com.jingcai.predict.ui.components.EmptyState
import com.jingcai.predict.ui.components.Hairline
import com.jingcai.predict.ui.components.IconBadge
import com.jingcai.predict.ui.components.SectionTitle
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular
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
    // 聚焦态：品牌色描边 + 极淡内发光
    var searchFocused by remember { mutableStateOf(false) }

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
                .padding(start = Space.xs, end = Space.lg, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = Tone.textBody()
                )
            }
            // 搜索框：胶囊玻璃底 + 上亮下隐渐变描边；聚焦时转品牌色描边并叠一层极淡内发光
            // 描边两支必须同为 Brush（Color 与 Brush 混用会让公共父类型退化为 Any，border 无法解析）
            val fieldStroke: Brush = if (searchFocused) {
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                )
            } else {
                Tone.glassStroke()
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(Corner.pill)
                    .background(Tone.glassBrush())
                    .then(if (searchFocused) Modifier.background(Tone.glow(0.10f)) else Modifier)
                    .border(1.dp, fieldStroke, Corner.pill)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        updateSuggestions(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { searchFocused = it.isFocused },
                    placeholder = {
                        Text(
                            "搜索联赛 / 比赛",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tone.textHint()
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            tint = if (searchFocused) MaterialTheme.colorScheme.primary
                            else Tone.textLabel(),
                            modifier = Modifier.size(19.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                suggestions = emptyList()
                                showSuggestions = false
                            }) {
                                Icon(
                                    Icons.Outlined.Clear,
                                    contentDescription = "清空",
                                    tint = Tone.textLabel(),
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = Corner.pill,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = Tone.textStrong(),
                        fontWeight = FontWeight.Medium
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Tone.textStrong(),
                        unfocusedTextColor = Tone.textStrong()
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch(query) })
                )
            }
            Spacer(Modifier.width(Space.sm))
            Button(
                onClick = { doSearch(query) },
                enabled = query.isNotBlank() && !loading,
                shape = Corner.pill,
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("搜索", style = MaterialTheme.typography.labelLarge)
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
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "正在加载联赛数据…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Tone.textBody()
                        )
                    }
                }

                !hasSearched -> HotSuggestions(
                    jcFailed = jcFailed,
                    onPick = { kw ->
                        query = kw
                        doSearch(kw)
                    }
                )

                results.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "未找到与“${query.trim()}”相关的联赛",
                    description = buildString {
                        append("提示：按联赛名称搜索，如 德甲、英超、欧冠")
                        if (jcFailed) append("\n竞彩官网数据暂不可达，请稍后重试")
                    },
                    modifier = Modifier.padding(horizontal = Space.lg)
                )

                else -> LeagueResults(
                    leagues = results,
                    onLeagueClick = onOpenLeague,
                )
            }
        }
    }
}

/** 联赛专属强调色（与联赛详情页保持一致） */
private val LeagueAccent = Color(0xFF7C3AED)

/** 实时联想下拉：玻璃浮层（同 SurfaceCard 容器语言，行内用极细分隔线分层） */
@Composable
private fun SuggestionPanel(
    suggestions: List<Suggestion>,
    onPick: (Suggestion) -> Unit,
) {
    SurfaceCard(
        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
        contentPadding = PaddingValues(0.dp)
    ) {
        suggestions.forEachIndexed { i, s ->
            val accent = if (s.type == "联赛") LeagueAccent else MaterialTheme.colorScheme.primary
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(s) }
                    .padding(horizontal = Space.lg, vertical = Space.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconBadge(
                    icon = if (s.type == "联赛") Icons.Outlined.EmojiEvents else Icons.Outlined.Search,
                    tint = accent,
                    size = 30.dp
                )
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Tone.textStrong(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (s.sub.isNotEmpty()) {
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            s.sub,
                            style = MaterialTheme.typography.labelSmall,
                            color = Tone.textLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (s.type != "搜索") {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Tone.textHint()
                    )
                }
            }
            if (i != suggestions.lastIndex) {
                Hairline(startPadding = 54.dp, endPadding = Space.lg)
            }
        }
    }
}

/* ---------- 热门搜索 ---------- */

private val HotKeywords = listOf("英超", "西甲", "德甲", "意甲", "法甲", "中超", "欧冠", "日职联")

@Composable
private fun HotSuggestions(jcFailed: Boolean, onPick: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.xl)
    ) {
        SectionTitle("热门联赛")
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            HotKeywords.take(4).forEach { kw ->
                HotChip(kw, Modifier.weight(1f), onPick)
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            HotKeywords.drop(4).forEach { kw ->
                HotChip(kw, Modifier.weight(1f), onPick)
            }
        }
        Spacer(Modifier.height(Space.lg))
        Text(
            buildString {
                append("输入时实时联想，支持中文搜索联赛")
                if (jcFailed) append("\n当前无法连接数据服务，请下拉重试")
            },
            style = MaterialTheme.typography.labelSmall,
            color = Tone.textLabel(),
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun HotChip(text: String, modifier: Modifier = Modifier, onClick: (String) -> Unit) {
    Box(
        modifier
            .clip(Corner.pill)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = { onClick(text) })
            .padding(vertical = Space.sm + 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
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
        verticalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        item(key = "sec_l") {
            SectionTitle(
                title = "联赛",
                modifier = Modifier.padding(start = Space.lg, end = Space.lg, top = Space.sm),
                trailing = {
                    Text(
                        "${leagues.size} 条",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = Tone.textLabel()
                    )
                }
            )
        }
        itemsIndexed(leagues, key = { i, l -> "l_${l.id}_$i" }) { _, l ->
            LeagueResultCard(l, onClick = { onLeagueClick(l) })
        }
        item(key = "bottom") { Spacer(Modifier.height(Space.lg)) }
    }
}

@Composable
private fun LeagueResultCard(league: LeagueEntry, onClick: () -> Unit) {
    SurfaceCard(
        modifier = Modifier.padding(horizontal = Space.lg),
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.md)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 联赛图标（去掉头像，用图标替代）
            IconBadge(
                icon = Icons.Outlined.EmojiEvents,
                tint = LeagueAccent,
                size = 36.dp
            )
            Column(Modifier.weight(1f).padding(start = Space.md)) {
                Text(
                    league.name.ifEmpty { "未知联赛" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Tone.textStrong(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
                Text(
                    league.seasons.lastOrNull()?.let { "${it.seasonName} 赛季 · ${league.seasons.size} 个赛季" }
                        ?: "点击查看赛程赛果",
                    style = MaterialTheme.typography.labelSmall,
                    color = Tone.textLabel(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Tone.textHint()
            )
        }
    }
}