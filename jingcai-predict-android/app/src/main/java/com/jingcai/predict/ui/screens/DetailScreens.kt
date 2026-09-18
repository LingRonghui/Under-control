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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.jingcai.predict.data.remote.RecentTeam
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.predict.LeagueProfile
import com.jingcai.predict.data.predict.LeagueProfiles
import com.jingcai.predict.data.llm.AiPredictionStore
import com.jingcai.predict.data.llm.CombinedPick
import com.jingcai.predict.data.llm.CombinedPrediction
import com.jingcai.predict.data.llm.CombineRule
import com.jingcai.predict.data.llm.LlmConfig
import com.jingcai.predict.data.llm.LlmConfigStore
import com.jingcai.predict.data.llm.PredictionBatchRunner
import com.jingcai.predict.data.llm.SlipPlayCodes
import com.jingcai.predict.data.llm.ValueRule
import com.jingcai.predict.data.search.SearchHit
import com.jingcai.predict.data.predict.ProfileRepository
import com.jingcai.predict.data.predict.SignalWeights
import com.jingcai.predict.data.remote.SearchPlayer
import com.jingcai.predict.data.remote.SearchTeam
import com.jingcai.predict.data.remote.TeamTables
import com.jingcai.predict.data.slip.ParlayMath
import com.jingcai.predict.data.slip.SavedSlip
import com.jingcai.predict.data.slip.SlipCalc
import com.jingcai.predict.data.slip.SlipHolder
import com.jingcai.predict.data.slip.SlipLeg
import com.jingcai.predict.data.slip.SlipPlay
import com.jingcai.predict.data.slip.SlipSelection
import com.jingcai.predict.data.slip.SlipStatus
import com.jingcai.predict.data.slip.SlipStore
import com.jingcai.predict.ui.components.EmptyState
import com.jingcai.predict.ui.components.Hairline
import com.jingcai.predict.ui.components.SearchSourcesBlock
import com.jingcai.predict.ui.components.openUrl
import com.jingcai.predict.ui.components.KeyValueRow
import com.jingcai.predict.ui.components.PillTag
import com.jingcai.predict.ui.components.SectionTitle
import com.jingcai.predict.ui.components.SegmentedTabs
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.components.ThinProgress
import com.jingcai.predict.ui.components.UiMessage
import com.jingcai.predict.ui.theme.Corner
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
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
            NetworkImage(team.badge, Modifier.size(112.dp), fallback = "⚽", round = true)
            Spacer(Modifier.height(Space.lg))
            Text(
                team.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.sm))
            PillTag("球队", color = MaterialTheme.colorScheme.secondary)
        }
        Spacer(Modifier.height(Space.xl))
        InfoCard(
            listOf(
                "联赛" to team.league.ifEmpty { "未知" },
                "国家/地区" to team.country.ifEmpty { "未知" },
                "球队 ID" to team.id.ifEmpty { "未知" },
            )
        )
        Spacer(Modifier.height(Space.xl))
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
            NetworkImage(player.photo, Modifier.size(112.dp), fallback = "👤", round = true)
            Spacer(Modifier.height(Space.lg))
            Text(
                player.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Space.sm))
            PillTag("球员", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Space.xl))
        InfoCard(
            listOf(
                "所属球队" to player.team.ifEmpty { "未知" },
                "场上位置" to player.position.ifEmpty { "未知" },
                "国籍" to player.nationality.ifEmpty { "未知" },
                "球员 ID" to player.id.ifEmpty { "未知" },
            )
        )
        Spacer(Modifier.height(Space.xl))
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
        // 顶栏：轻量返回（对阵与开赛信息由下方头图区承载）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.xs, end = Space.lg, top = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 头图区（整页视觉锚点）：联赛 + 状态 + 对阵 + 开赛时间
        MatchStatusCard(match)

        // 分类栏
        SegmentedTabs(
            items = matchTabs,
            selectedIndex = tabIndex,
            onSelect = { tabIndex = it },
            modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)
        )

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

    SurfaceCard(
        modifier = Modifier.padding(horizontal = Space.lg),
        accent = true,
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.lg),
    ) {
        // 上：联赛 + 编号 + 状态
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                match.league.ifEmpty { "竞彩足球" },
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                match.num.ifEmpty { "" },
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Space.sm))
            PillTag(
                text = live?.statusName?.takeIf { it.isNotBlank() } ?: status,
                color = when {
                    isLive -> MaterialTheme.colorScheme.primary
                    isFinished -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }

        Spacer(Modifier.height(Space.xl))

        // 中：主队 | 比分/VS | 客队
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                match.home,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isLive || isFinished) {
                Column(
                    Modifier.padding(horizontal = Space.md),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        score ?: " -- : -- ",
                        style = MaterialTheme.typography.headlineMedium.tabular(),
                        color = if (isLive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    if (half != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "半场 $half",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (penalty != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "点球 $penalty",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Text(
                    "VS",
                    Modifier.padding(horizontal = Space.xl),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Text(
                match.away,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(Space.md))

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
                else -> "开赛时间：${kickoffText(match.time)}"
            },
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        // 实时事件列表（进球/红牌等）
        live?.takeIf { it.events.isNotEmpty() }?.let { liveNow ->
            Spacer(Modifier.height(Space.md))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(Corner.md)
                    .background(Tone.fill())
                    .padding(horizontal = Space.md, vertical = Space.sm)
            ) {
                Text(
                    "关键事件",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(Space.xs))
                liveNow.events.forEach { e ->
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
            style = MaterialTheme.typography.labelSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp)
        )
        Text(
            (if (isHome) match.home else match.away) + " ",
            Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (isHome) TextAlign.End else TextAlign.Start
        )
        Text(
            e.name.ifEmpty { "事件" },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
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
    val context = LocalContext.current
    var preview by remember { mutableStateOf<PreviewData?>(null) }
    var failed by remember { mutableStateOf(false) }
    // 赛前情报：优先展示已缓存的模型情报，无则退回本地赔率简析（不发起任何网络请求）
    var modelBrief by remember { mutableStateOf<String?>(null) }
    // 情报引用的真实网络检索来源与检索状态（未启用联网检索时为空，不展示该区块）
    var searchState by remember { mutableStateOf("") }
    var searchSources by remember { mutableStateOf<List<SearchHit>>(emptyList()) }
    LaunchedEffect(match.matchId) {
        preview = runCatching { buildPreview(match) }.getOrNull()
        failed = preview == null
        AiPredictionStore.loadOnce(context)
        val snap = AiPredictionStore.get(match.matchId)
        modelBrief = snap?.preview?.takeIf { it.isNotBlank() }
        searchState = snap?.searchState.orEmpty()
        searchSources = snap?.searchSources.orEmpty()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
            .padding(top = Space.md, bottom = Space.xl)
    ) {
        if (failed) {
            EmptyState(
                icon = Icons.Outlined.CloudOff,
                title = "前瞻数据暂不可用",
                description = "官方接口未返回，稍后重试",
            )
            return@Column
        }
        val p = preview ?: run {
            Box(
                Modifier.fillMaxWidth().padding(vertical = Space.xxl),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "正在加载前瞻数据…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }
        val homeName = p.head?.homeName ?: match.home
        val awayName = p.head?.awayName ?: match.away

        // 特征分析
        SectionTitle("特征分析")
        Spacer(Modifier.height(Space.md))
        FeatureCard(p.feature, homeName, awayName)
        Spacer(Modifier.height(Space.section))

        // 历史交锋
        SectionTitle("历史交锋")
        Spacer(Modifier.height(Space.md))
        HistoryCard(p.h2hSum, p.history)
        Spacer(Modifier.height(Space.section))

        // 积分榜
        SectionTitle("积分榜")
        Spacer(Modifier.height(Space.md))
        TablesCard(p.homeTables, p.awayTables)
        Spacer(Modifier.height(Space.section))

        // 比赛近况
        SectionTitle("比赛近况")
        Spacer(Modifier.height(Space.md))
        RecentCard(p.homeRecent, homeName)
        Spacer(Modifier.height(Space.sm))
        RecentCard(p.awayRecent, awayName)
        Spacer(Modifier.height(Space.section))

        // 未来赛事
        SectionTitle("未来赛事")
        Spacer(Modifier.height(Space.md))
        FutureCard(p.homeFuture, homeName)
        Spacer(Modifier.height(Space.sm))
        FutureCard(p.awayFuture, awayName)
        Spacer(Modifier.height(Space.section))

        // 射手信息
        SectionTitle("射手信息")
        Spacer(Modifier.height(Space.md))
        PlayerCard(p.homePlayers, homeName)
        Spacer(Modifier.height(Space.sm))
        PlayerCard(p.awayPlayers, awayName)
        Spacer(Modifier.height(Space.section))

        // 伤停一览
        SectionTitle("伤停一览")
        Spacer(Modifier.height(Space.md))
        InjuryCard(p.homeInjuries, homeName)
        Spacer(Modifier.height(Space.sm))
        InjuryCard(p.awayInjuries, awayName)
        Spacer(Modifier.height(Space.section))

        // 赛前情报（模型情报优先，无则本地简析并标注）
        SectionTitle("赛前情报")
        Spacer(Modifier.height(Space.md))
        SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
            Text(
                modelBrief ?: preMatchBrief(match),
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (modelBrief == null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "（情报未生成）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 参考来源：仅当本次预测启用过联网检索时展示；无结果或失败如实说明，绝不填充占位来源
        if (searchState.isNotEmpty()) {
            Spacer(Modifier.height(Space.section))
            SearchSourcesBlock(
                sources = searchSources,
                onOpen = { openUrl(context, it) },
            )
            if (searchState.startsWith("failed")) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    "本次检索失败：${searchState.removePrefix("failed:").trim()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Space.lg))
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
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)) {
        Row(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
            Text(
                homeName,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                awayName,
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Hairline()
        @Composable
        fun dimRow(label: String, d: FeatureDim?, homeV: String = "", awayV: String = "") {
            if (d == null && homeV.isEmpty()) return
            Row(Modifier.fillMaxWidth().padding(vertical = Space.sm)) {
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (d != null) "${d.homeWin}胜${d.homeDraw}平${d.homeLoss}负" else homeV,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    if (d != null) "${d.awayWin}胜${d.awayDraw}平${d.awayLoss}负" else awayV,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        dimRow("近10场交锋", f.last10)
        dimRow("同主客交锋", f.sameHomeAway)
        dimRow("近10场战况", f.last10Form)
        dimRow("同主客战况", f.sameHomeAwayForm)
        dimRow("场均进球", null, "${f.homeGoalAvg}个", "${f.awayGoalAvg}个")
        dimRow("场均失球", null, "${f.homeLossAvg}个", "${f.awayLossAvg}个")
        Spacer(Modifier.height(Space.xs))
    }
}

/** 历史交锋：汇总 + 交锋列表 */
@Composable
private fun HistoryCard(sum: H2hSummary?, list: List<H2hMatch>) {
    if (sum == null && list.isEmpty()) {
        EmptyHint("暂无两队交锋记录")
        return
    }
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
        sum?.let { s ->
            Text(
                "近${s.win + s.draw + s.loss}场 ${s.teamName} ${s.win}胜 (${s.winProb}) | ${s.draw}平 (${s.drawProb}) | ${s.loss}负 (${s.lossProb})",
                style = MaterialTheme.typography.bodySmall.tabular(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(Space.md))
            Hairline()
        }
        if (list.isEmpty()) {
            Spacer(Modifier.height(Space.md))
            Text(
                "暂无数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            list.forEachIndexed { i, m ->
                if (i > 0) Hairline(Modifier.padding(vertical = Space.sm))
                Column(Modifier.padding(vertical = Space.sm)) {
                    Text(
                        "${m.date} · ${m.tournament}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${m.home} ${m.score} ${m.away}",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(Space.sm))
                        Text(
                            "半场${m.halfScore.ifEmpty { "-" }} · 总${m.totalGoal}球",
                            style = MaterialTheme.typography.labelSmall.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
        if (home != null && away != null) Spacer(Modifier.height(Space.sm))
        away?.let { TablePanel(it) }
    }
}

@Composable
private fun TablePanel(t: TeamTables) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.md)) {
        Text(
            "${t.name} 第${t.total.ranking}名",
            style = MaterialTheme.typography.titleSmall.tabular(),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth()) {
            val head = listOf("", "场次", "胜/平/负", "进/失", "净", "积分", "排名")
            head.forEach { h ->
                Text(
                    h,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Hairline()
        listOf(t.total, t.home, t.away).forEach { r ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    r.scope,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Text("${r.played}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                Text("${r.win}/${r.draw}/${r.loss}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                Text("${r.goal}/${r.lossGoal}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                Text("${r.netGoal}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                Text(r.points, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(r.ranking, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
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
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
        Text(
            "${t.name} ${t.stat}",
            style = MaterialTheme.typography.titleSmall.tabular(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (t.matches.isEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "暂无比赛记录",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            t.matches.forEachIndexed { i, m ->
                if (i > 0) Hairline(Modifier.padding(vertical = Space.sm))
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${m.home} ${m.score} ${m.away}",
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${m.date} · ${m.tournament}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        if (m.halfScore.isNotEmpty()) "半${m.halfScore}" else "",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        m.result,
                        style = MaterialTheme.typography.labelLarge,
                        color = when (m.result) {
                            "胜" -> Tone.hit
                            "负" -> Tone.miss
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

/** 未来赛事：单队列表 */
@Composable
private fun FutureCard(list: List<FutureMatch>, name: String) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        if (list.isEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "暂无未来赛事",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            list.forEachIndexed { i, m ->
                if (i > 0) Hairline(Modifier.padding(vertical = Space.sm))
                Row(
                    Modifier.fillMaxWidth().padding(top = if (i == 0) Space.sm else 0.dp, bottom = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${m.home} vs ${m.away}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "${m.date}${if (m.round.isNotEmpty()) " · ${m.round}" else ""}",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 射手信息：单队射手榜 */
@Composable
private fun PlayerCard(list: List<PlayerStat>, name: String) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.md)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        if (list.isEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "暂无射手数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth()) {
                val head = listOf("球员", "出场(首/替)", "进球", "助攻", "场均进/助")
                head.forEach { h ->
                    Text(
                        h,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            Hairline()
            list.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${if (p.no.isNotEmpty()) "${p.no}-" else ""}${p.name}(${p.position})",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${p.played}(${p.started}/${p.sub})", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                    Text("${p.goal} (${p.goalProb})", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                    Text("${p.assist} (${p.assistProb})", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                    Text(
                        if (p.goalAvg.isNotEmpty() || p.assistAvg.isNotEmpty()) "${p.goalAvg}/${p.assistAvg}" else "-",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/** 伤停一览：单队伤停名单 */
@Composable
private fun InjuryCard(list: List<InjuryPlayer>, name: String) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.md, vertical = Space.md)) {
        Text(name, style = MaterialTheme.typography.titleSmall)
        if (list.isEmpty()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "暂无伤停信息",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.fillMaxWidth()) {
                val head = listOf("球员", "总出场", "首发", "替补", "状态")
                head.forEach { h ->
                    Text(
                        h,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(Modifier.height(Space.sm))
            Hairline()
            list.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${if (p.no.isNotEmpty()) "${p.no}-" else ""}${p.name}(${p.position})",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${p.played}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                    Text("${p.started}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                    Text("${p.sub}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.tabular(), textAlign = TextAlign.Center)
                    Text(
                        when {
                            p.injury && p.suspension -> "伤停"
                            p.injury -> "伤"
                            p.suspension -> "停"
                            else -> "-"
                        },
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = if (p.injury || p.suspension) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

/**
 * 一个可点选的赔率选项：玩法代码 + 官方选项键 + 展示标签 + 赔率单元格。
 * 官方选项键原样保留（比分玩法即官方 map 的原始 key，如 s01s01 / s-1sh），
 * 直接作为 SlipSelection.optionCode，不做二次改写。
 */
private data class OddsOption(
    val play: String,
    val code: String,
    val label: String,
    val cell: OddsCell,
) {
    /** 本场内的唯一键（与 SlipSelection 的 玩法|选项键 对应） */
    val key: String get() = "$play|$code"

    /** 可投注赔率：为空或不大于 1 视为不可选 */
    val odds: Double? get() = cell.value.toDoubleOrNull()?.takeIf { it > 1.0 }
}

@Composable
private fun OddsTab(match: RemoteMatch) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 最新赔率（zqdz 详情页同源，取最后一条时间线），失败时降级到静态售彩赔率
    var odds by remember { mutableStateOf<MatchOdds?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 方案计算器弹层开关
    var sheetOpen by remember { mutableStateOf(false) }
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

    // 本场已选选项键（玩法|选项键；读取可观察方案单，选中变化即刷新选中态）
    val mySelected = SlipHolder.selections
        .filter { it.matchId == match.matchId }
        .map { "${it.play}|${it.optionCode}" }

    // 点选 / 取消一个赔率选项，并立即落盘
    fun toggleOption(opt: OddsOption) {
        val v = opt.odds ?: return
        SlipHolder.toggle(
            SlipSelection(
                matchId = match.matchId,
                matchNum = match.num,
                league = match.league,
                home = match.home,
                away = match.away,
                kickoff = kickoffText(match.time),
                play = opt.play,
                playLabel = SlipPlay.of(opt.play)?.label ?: opt.play,
                optionCode = opt.code,
                optionLabel = opt.label,
                odds = v,
                // 仅让球胜平负带盘口
                goalLine = if (opt.play == SlipPlay.HHAD.code) goalLine else "",
                singleAllowed = match.singleAllowed(opt.play),
            )
        )
        scope.launch { SlipStore.saveCurrent(context, SlipHolder.snapshot()) }
    }

    // 底部方案栏数据（注数 / 金额）
    val brief = slipBrief()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                // 底部留出方案栏高度，避免遮挡最后一块赔率
                .padding(top = Space.md, bottom = if (brief != null) 76.dp else Space.lg)
        ) {
            if (loading) {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = Space.xxl),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // 1. 胜平负
            SectionTitle("胜平负")
            Spacer(Modifier.height(Space.md))
            if (had != null) {
                TripleOddsCard(
                    listOf(
                        OddsOption(SlipPlay.HAD.code, "h", "胜", had.first),
                        OddsOption(SlipPlay.HAD.code, "d", "平", had.second),
                        OddsOption(SlipPlay.HAD.code, "a", "负", had.third),
                    ),
                    selected = mySelected,
                    onToggle = ::toggleOption,
                )
            } else EmptyHint("本场未开售")
            Spacer(Modifier.height(Space.section))

            // 2. 让球胜平负
            SectionTitle("让球胜平负${goalLine}")
            Spacer(Modifier.height(Space.md))
            if (hhad != null) {
                TripleOddsCard(
                    listOf(
                        OddsOption(SlipPlay.HHAD.code, "h", "让球胜", hhad.first),
                        OddsOption(SlipPlay.HHAD.code, "d", "让球平", hhad.second),
                        OddsOption(SlipPlay.HHAD.code, "a", "让球负", hhad.third),
                    ),
                    selected = mySelected,
                    onToggle = ::toggleOption,
                )
            } else EmptyHint("本场未开售")
            Spacer(Modifier.height(Space.section))

            // 3. 全场比分
            SectionTitle("全场比分")
            Spacer(Modifier.height(Space.md))
            if (crs.isEmpty()) EmptyHint("本场比分玩法未开售")
            else OddsGrid(crsList(crs), columns = 4, selected = mySelected, onToggle = ::toggleOption)
            Spacer(Modifier.height(Space.section))

            // 4. 半全场胜平负
            SectionTitle("半全场胜平负")
            Spacer(Modifier.height(Space.md))
            if (hafu.isEmpty()) EmptyHint("本场半全场玩法未开售")
            else OddsGrid(hafuList(hafu), columns = 3, selected = mySelected, onToggle = ::toggleOption)
            Spacer(Modifier.height(Space.section))

            // 5. 总进球数
            SectionTitle("总进球数")
            Spacer(Modifier.height(Space.md))
            if (ttg.isEmpty()) EmptyHint("本场总进球玩法未开售")
            else OddsGrid(ttgList(ttg), columns = 4, selected = mySelected, onToggle = ::toggleOption)
            Spacer(Modifier.height(Space.lg))
        }

        // 底部方案栏：固定不随内容滚动
        if (brief != null) {
            SchemeBar(
                matchCount = brief.matchCount,
                calc = brief.calc,
                onClear = {
                    SlipHolder.clear()
                    scope.launch { SlipStore.saveCurrent(context, SlipHolder.snapshot()) }
                },
                onOpen = { sheetOpen = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (sheetOpen && brief != null) {
        SlipCalculatorSheet(onDismiss = { sheetOpen = false })
    }
}

/** 方案栏/计算器公用：按比赛分组后的注数与金额计算结果 */
private data class SlipBrief(
    val matchCount: Int,
    val calc: SlipCalc,
    /** 实际生效的关次集合（已过滤非法关次） */
    val useParlay: List<Int>,
)

/**
 * 汇总当前方案单：每场已选赔率列表 → ParlayMath.calc。
 * 未选任何选项返回 null；未手动指定过关方式时按场次数推荐（≥2 场用 N串1，否则单关）。
 */
private fun slipBrief(): SlipBrief? {
    val optionOdds = SlipHolder.selections.groupBy { it.matchId }.values
        .map { legs -> legs.map { it.odds } }
    if (optionOdds.isEmpty()) return null
    val want = SlipHolder.parlay.ifEmpty { listOf(optionOdds.size.coerceAtLeast(1)) }
    val calc = ParlayMath.calc(
        optionOdds = optionOdds,
        playCodes = SlipHolder.selections.map { it.play },
        parlay = want,
        multiple = SlipHolder.multiple,
        singleAllowed = SlipHolder.singleAllowed(),
    )
    // 与 ParlayMath.calc 内部的过滤规则保持一致
    val useParlay = want.filter { it in calc.validParlay }.ifEmpty { calc.validParlay.takeLast(1) }
    return SlipBrief(optionOdds.size, calc, useParlay)
}

/** 全场比分选项解析：遍历官方返回的全部赔率（s{H}s{A} → H:A），胜/平/负其他放末尾；选项键用官方原始 key */
private fun crsList(crs: Map<String, OddsCell>): List<OddsOption> {
    val out = mutableListOf<OddsOption>()
    val rx = Regex("s(\\d+)s(\\d+)")
    // 普通比分按 (主队 H, 客队 A) 数值升序
    crs.filterKeys { rx.containsMatchIn(it) }
        .mapNotNull { e ->
            val m = rx.find(e.key) ?: return@mapNotNull null
            val h = m.groupValues[1].toIntOrNull() ?: return@mapNotNull null
            val a = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            Triple(h, a, e)
        }
        .sortedWith(compareBy({ it.first }, { it.second }))
        .forEach { (h, a, e) -> out += OddsOption(SlipPlay.CRS.code, e.key, "$h:$a", e.value) }
    // 官方"其他"键实际为 s-1sh / s-1sd / s-1sa（兼容 s1sh 等写法）
    val otherRx = Regex("^s-?\\d+s([hda])$")
    crs.forEach { (k, v) ->
        val m = otherRx.find(k) ?: return@forEach
        out += OddsOption(
            SlipPlay.CRS.code,
            k,
            when (m.groupValues[1]) {
                "h" -> "胜其他"
                "d" -> "平其他"
                else -> "负其他"
            },
            v,
        )
    }
    return out
}

/** 半全场选项解析（标签为竞彩官方写法，选项键 hh/hd/ha/dh/dd/da/ah/ad/aa） */
private fun hafuList(hafu: Map<String, OddsCell>): List<OddsOption> {
    val labels = mapOf(
        "hh" to "胜胜", "hd" to "胜平", "ha" to "胜负",
        "dh" to "平胜", "dd" to "平平", "da" to "平负",
        "ah" to "负胜", "ad" to "负平", "aa" to "负负",
    )
    return labels.mapNotNull { (k, label) ->
        val v = hafu[k] ?: return@mapNotNull null
        OddsOption(SlipPlay.HAFU.code, k, label, v)
    }
}

/** 总进球解析：s0~s7 → 0球…7球+（选项键为官方 s0~s7） */
private fun ttgList(ttg: Map<String, OddsCell>): List<OddsOption> {
    return (0..7).mapNotNull { i ->
        val v = ttg["s$i"] ?: return@mapNotNull null
        OddsOption(SlipPlay.TTG.code, "s$i", if (i == 7) "7球+" else "${i}球", v)
    }
}

/* ---------- 预测分析 Tab ---------- */

/**
 * 预测分析：直接读取本地缓存中的「综合结论」（模型结论与本地概率综合的整体结论）。
 * - 进入页面只读缓存，**绝不重新预测**（不发起任何模型调用）；
 * - 无缓存且比赛未开赛时才生成一次，生成结果落盘，之后进入本页直接读取；
 * - 只有「模型复核」会强制重算该场（reviewCount + 1）。
 * 另附联赛参数配置面板（保存参数后可用「模型复核」按新参数重算）。
 */
@Composable
private fun PredictionTab(match: RemoteMatch) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<CombinedPrediction?>(null) }
    var building by remember { mutableStateOf(false) }
    var buildError by remember { mutableStateOf<String?>(null) }
    var cfg by remember { mutableStateOf<LlmConfig?>(null) }
    var tick by remember { mutableIntStateOf(0) }   // 复核后触发重读
    var profile by remember { mutableStateOf<LeagueProfile?>(null) }
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

    LaunchedEffect(match.matchId, tick) {
        AiPredictionStore.loadOnce(context)
        cfg = LlmConfigStore.load(context)
        val cached = AiPredictionStore.get(match.matchId)
        if (cached != null) {
            snapshot = cached
            buildError = null
            return@LaunchedEffect
        }
        // 已开赛/已结束：不再预测
        val s = match.status.uppercase()
        if (s == "1" || s == "LIVE" || s == "OPEN" || s == "2" || s == "CLOSED" || s == "FINISHED") {
            snapshot = null
            buildError = "本场${if (s == "1" || s == "LIVE" || s == "OPEN") "已经开始" else "已经结束"}，未在赛前保留预测"
            return@LaunchedEffect
        }
        building = true
        buildError = null
        PredictionBatchRunner.ensure(context, match)
            .onSuccess { snapshot = it }
            .onFailure { buildError = it.message ?: "预测生成失败" }
        building = false
    }

    // 联赛参数（配置面板展示与保存用，不参与预测计算）
    LaunchedEffect(match.matchId) {
        val p = ProfileRepository.getProfile(context, match.league)
        profile = p
        syncEdits(p)
    }

    fun review() {
        scope.launch {
            building = true
            buildError = null
            PredictionBatchRunner.reviewWithDetail(context, match)
                .onSuccess {
                    snapshot = it.prediction
                    tick += 1
                    UiMessage.success("复核完成（第${it.prediction.reviewCount}次）")
                }
                .onFailure { e ->
                    UiMessage.error(e.message ?: "复核失败")
                }
            building = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.lg)
    ) {
        // 「预测目标」标题行 + 「模型复核」按钮
        Row(
            Modifier.fillMaxWidth().padding(top = Space.md, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "预测目标",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            OutlinedButton(
                onClick = { review() },
                enabled = !building,
                shape = Corner.pill,
                contentPadding = PaddingValues(horizontal = Space.md, vertical = 4.dp)
            ) {
                if (building) {
                    CircularProgressIndicator(
                        Modifier.size(13.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("复核中…", style = MaterialTheme.typography.labelMedium)
                } else {
                    Text("模型复核", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        val cp = snapshot
        when {
            cp != null -> {
                cp.picks.forEachIndexed { i, pick ->
                    if (i > 0) Spacer(Modifier.height(Space.sm))
                    CombinedPickRow(pick)
                }
                Spacer(Modifier.height(Space.lg))

                // 综合置信度说明（口径原文照呈，可复算）
                SectionTitle("综合置信度")
                Spacer(Modifier.height(Space.sm))
                Text(
                    CombineRule.NOTE,
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.xl))

                // 实时建议：最具价值 / 最稳健（数字全部本地可复算，模型仅作解读）
                SectionTitle("实时建议")
                Spacer(Modifier.height(Space.md))
                val bankroll = cfg?.bankroll ?: 100.0
                if (cp.bestValue == null && cp.safest == null) {
                    EmptyHint("统计信号缺失，不具备给出建议的数据基础")
                } else {
                    cp.bestValue?.let {
                        AdviceCard("最具价值", it, bankroll)
                        if (cp.safest != null) Spacer(Modifier.height(Space.sm))
                    }
                    cp.safest?.let { AdviceCard("最稳健", it, bankroll) }
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "${ValueRule.NOTE}；最稳健不设门槛，取模型概率最高者。期望值 = 模型概率 × 官方真实赔率 − 1（>0 才有盈利空间）；建议投入按凯利公式（全凯利）计算，实盘通常再取 1/4~1/2 折扣。均为概率与赔率的数学结果，不构成投注建议。",
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(Space.xl))

                // 思路分析（分节，全部来自综合结论快照；快照无分节时用快照数据兜底，不整块消失）
                SectionTitle("思路分析")
                Spacer(Modifier.height(Space.md))
                val sections = cp.sections
                if (sections.isEmpty()) {
                    AnalysisSection("结论", fallbackConclusion(cp))
                } else {
                    sections.forEachIndexed { i, (title, content) ->
                        if (i > 0) Spacer(Modifier.height(Space.sm))
                        AnalysisSection(title, content)
                    }
                }
                Spacer(Modifier.height(Space.xl))

                // 状态行（无条件渲染）
                Text(
                    if (cp.model.isNotBlank())
                        "综合置信度由模型与本地概率运算综合得出；概率、赔率取官方真实数据（可复算），理由为主观评估"
                    else "未配置模型，以上为模型结果",
                    style = MaterialTheme.typography.labelSmall,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                buildError?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                // 模型侧未出结果时如实提示（超时 / 不可用），不静默降级
                modelSideNote(cp, cfg)?.let {
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    "生成 ${stampText(cp.updatedAt)} · 复核 ${cp.reviewCount} 次",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.lg))
            }

            building -> Box(
                Modifier.fillMaxWidth().padding(vertical = Space.xxl),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(Space.md))
                    Text(
                        "正在生成综合预测…（首次生成，之后进入本页将直接读取结果）",
                        style = MaterialTheme.typography.bodySmall,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> Box(
                Modifier.fillMaxWidth().padding(vertical = Space.xxl),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "暂无综合预测",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    buildError?.let {
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
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
                        UiMessage.info("参数已保存，点击「模型复核」按新参数重算")
                    }
                },
                onReset = {
                    scope.launch {
                        ProfileRepository.reset(context, match.league)
                        val base = LeagueProfiles.forLeague(match.league)
                        profile = base
                        syncEdits(base)
                        UiMessage.info("已恢复联赛默认参数，点击「模型复核」重算")
                    }
                },
            )
        }

        Spacer(Modifier.height(Space.sm))
        Text(
            "预测结果仅供参考，不构成投注建议。",
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(Space.xl))
    }
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
    SurfaceCard(contentPadding = PaddingValues(0.dp)) {
        // 头部（点击展开/收起）
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = Space.lg, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("${base.league} 联赛参数配置", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (base.sourceNote.startsWith("用户自定义"))
                        "当前为自定义参数，已本地保存"
                    else "联赛默认模板，调整后立即重算",
                    Modifier.padding(top = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (open) "▾" else "▸",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (open) {
            Hairline(startPadding = Space.lg, endPadding = Space.lg)
            Column(Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
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
                Spacer(Modifier.height(Space.md))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedButton(onClick = onReset, Modifier.weight(1f), shape = Corner.sm) {
                        Text("恢复联赛默认", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = onApply, Modifier.weight(1f), shape = Corner.sm) {
                        Text("应用并重新计算", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Tone.fill())
                .clickable(onClick = onMinus),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "−",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            value,
            Modifier.width(64.dp),
            style = MaterialTheme.typography.titleSmall.tabular(),
            textAlign = TextAlign.Center
        )
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Tone.fill())
                .clickable(onClick = onPlus),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "＋",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun pctOf(v: Double): String = "${(v * 100).roundToInt()}%"
private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)
private fun fmt3(v: Double): String = String.format(Locale.US, "%.3f", v)

/** 时间戳 → "MM-dd HH:mm"（生成时间展示用） */
private fun stampText(ms: Long): String =
    if (ms <= 0L) "--" else SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(ms))

/**
 * 模型侧失败原因的如实提示（状态行附近展示，不静默降级）：
 * 已配置可用模型（[LlmConfig.ready]）却没有模型产出时，说明该场模型部分未成功；
 * 只陈述可核实的事实（快照里没有模型结果标识 / 没有赛前情报）与可能原因，不臆造具体错误信息。
 */
private fun modelSideNote(cp: CombinedPrediction, cfg: LlmConfig?): String? {
    if (cfg?.ready != true) return null
    if (cp.model.isBlank()) {
        return "本场未取得模型结果（模型响应超时 / 不可用，或本场无可用真实赔率），以上为模型结果"
    }
    if (cp.preview.isBlank()) {
        return "本场赛前情报未生成（模型响应超时或未返回），其余结论仍为模型综合运算结果"
    }
    return null
}

/* ---------- 通用小组件 ---------- */

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        Modifier
            .fillMaxWidth()
            .clip(Corner.md)
            .background(Tone.fill())
            .padding(horizontal = Space.lg, vertical = Space.xl),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        lineHeight = 19.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 胜平负 / 让球胜平负：三个可点选选项同处一张卡片 */
@Composable
private fun TripleOddsCard(
    options: List<OddsOption>,
    selected: List<String>,
    onToggle: (OddsOption) -> Unit,
) {
    if (options.isEmpty()) {
        EmptyHint("本场未开售")
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.sm)
    ) {
        options.forEach { opt ->
            val isOn = opt.key in selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(Corner.sm)
                    .background(
                        if (isOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Tone.fill()
                    )
                    .border(
                        1.dp,
                        // 未选中画透明边框，仅用于避免选中后尺寸跳动
                        if (isOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else Color.Transparent,
                        Corner.sm
                    )
                    .clickable(enabled = opt.odds != null) { onToggle(opt) }
                    .padding(vertical = Space.md),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        opt.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isOn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(Space.xs))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            opt.cell.value,
                            style = MaterialTheme.typography.titleMedium.tabular(),
                            color = if (isOn) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(2.dp))
                        TrendArrow(opt.cell)
                    }
                }
                if (isOn) CheckBadge(Modifier.align(Alignment.TopStart).padding(start = Space.sm, top = Space.xs))
            }
        }
    }
}

/** 网格玩法（比分 / 半全场 / 总进球）：每个赔率方块均为可点选选项 */
@Composable
private fun OddsGrid(
    items: List<OddsOption>,
    columns: Int,
    selected: List<String>,
    onToggle: (OddsOption) -> Unit,
) {
    items.chunked(columns).forEach { rowItems ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            rowItems.forEach { opt ->
                OddsGridCell(
                    opt = opt,
                    isOn = opt.key in selected,
                    modifier = Modifier.weight(1f),
                    onToggle = onToggle,
                )
            }
            repeat(columns - rowItems.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OddsGridCell(
    opt: OddsOption,
    isOn: Boolean,
    modifier: Modifier,
    onToggle: (OddsOption) -> Unit,
) {
    Box(
        modifier
            .clip(Corner.sm)
            .background(
                if (isOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Tone.fill()
            )
            .border(
                1.dp,
                if (isOn) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else Color.Transparent,
                Corner.sm
            )
            .clickable(enabled = opt.odds != null) { onToggle(opt) }
            .padding(vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                opt.label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isOn) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    opt.cell.value,
                    style = MaterialTheme.typography.titleSmall.tabular(),
                    color = if (isOn) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(2.dp))
                TrendArrow(opt.cell)
            }
        }
        if (isOn) CheckBadge(Modifier.align(Alignment.TopStart).padding(start = Space.xs, top = 2.dp))
    }
}

/** 选中角标（左上角 ✓） */
@Composable
private fun CheckBadge(modifier: Modifier = Modifier) {
    Text(
        "✓",
        modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

/* ---------- 方案栏 / 方案计算器 ---------- */

/** 底部方案栏：固定在赔率页底部，展示场次/注数/金额并进入计算器 */
@Composable
private fun SchemeBar(
    matchCount: Int,
    calc: SlipCalc,
    onClear: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.lg, end = Space.sm, top = Space.sm, bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (calc.noteCount == 0L) "已选 $matchCount 场 · 未成单（官方未开单关，请再选 1 场）"
                else "已选 $matchCount 场 · ${calc.noteCount} 注 · ${ParlayMath.money(calc.stake)} 元",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge.tabular(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(onClick = onClear, Modifier.size(36.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "清空方案",
                    Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onOpen,
                shape = Corner.sm,
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)
            ) {
                Text("查看方案", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/**
 * 方案计算器弹层：已选选项按比赛分组增删、过关方式（自由过关 / M串N 套餐）、
 * 倍数、注数金额与奖金区间实时计算，并提供保存到方案中心与清空。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlipCalculatorSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 读取可观察方案单，增删选项后弹层内容即时刷新
    val selections = SlipHolder.selections.toList()
    val brief = slipBrief()
    val maxK = SlipHolder.maxParlay()
    val singleOk = SlipHolder.singleAllowed()
    val parlay = SlipHolder.parlay
    // 场次数：同一场比赛多选只算一场（串关必须跨场）
    val matchCount = selections.map { it.matchId }.distinct().size

    // 关次上限同时受「官方最大过关数」与「已选场次数」约束
    val kMax = maxK.coerceAtMost(matchCount.coerceAtLeast(1))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
                .padding(bottom = Space.lg)
        ) {
            Text("方案计算器", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Space.md))

            /* ---- 已选列表（按比赛分组） ---- */
            selections.groupBy { it.matchId }.values.forEach { legs ->
                val head = legs.first()
                Row(
                    Modifier.fillMaxWidth().padding(top = Space.sm, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${head.matchNum} · ${head.league} · ${head.home} vs ${head.away}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        head.kickoff,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                legs.forEach { sel ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = Space.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${sel.playLabel} ${sel.optionLabel}" +
                                if (sel.goalLine.isNotEmpty()) "（${sel.goalLine}）" else "",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            String.format(Locale.US, "%.2f", sel.odds),
                            style = MaterialTheme.typography.bodyMedium.tabular(),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                SlipHolder.remove(sel)
                                scope.launch { SlipStore.saveCurrent(context, SlipHolder.snapshot()) }
                                // 已选清空则关闭弹层
                                if (SlipHolder.selections.isEmpty()) onDismiss()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = "删除",
                                Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Hairline(Modifier.padding(vertical = Space.sm))
            }

            /* ---- 过关方式 ---- */
            SectionTitle("过关方式")
            Spacer(Modifier.height(Space.sm))
            Text(
                "自由过关",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Space.sm))
            if (!singleOk) {
                Text(
                    "本场所选玩法官方未开单关",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(Space.sm))
            }
            (1..kMax).toList().chunked(6).forEach { rowKs ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.sm)
                ) {
                    rowKs.forEach { k ->
                        ParlayChip(
                            label = if (k == 1) "单关" else "${k}串1",
                            selected = k in parlay,
                            enabled = k != 1 || singleOk,
                            modifier = Modifier.weight(1f)
                        ) {
                            SlipHolder.parlay = if (k in parlay) parlay - k else (parlay + k).sorted()
                        }
                    }
                    repeat(6 - rowKs.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // M串N 容错套餐（需要至少 2 场，套餐名与注数按官方注数分配表）
            if (matchCount >= 2) {
                Spacer(Modifier.height(Space.sm))
                Text(
                    "M串N 套餐",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(Space.sm))
                ParlayMath.presets(matchCount).chunked(3).forEach { rowPresets ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = Space.sm),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        rowPresets.forEach { (name, keys) ->
                            ParlayChip(
                                label = name,
                                selected = parlay.sorted() == keys.sorted(),
                                enabled = keys.all { it <= kMax } && (singleOk || 1 !in keys),
                                modifier = Modifier.weight(1f)
                            ) { SlipHolder.parlay = keys }
                        }
                        repeat(3 - rowPresets.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            /* ---- 倍数 ---- */
            Spacer(Modifier.height(Space.sm))
            SectionTitle("倍数")
            Spacer(Modifier.height(Space.sm))
            StepRow(
                "投注倍数",
                "${SlipHolder.multiple}",
                { SlipHolder.multiple = (SlipHolder.multiple - 1).coerceIn(1, 99) },
                { SlipHolder.multiple = (SlipHolder.multiple + 1).coerceIn(1, 99) },
            )

            /* ---- 计算结果 ---- */
            Spacer(Modifier.height(Space.md))
            SectionTitle("计算结果")
            Spacer(Modifier.height(Space.sm))
            SurfaceCard(
                accent = true,
                contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm),
            ) {
                val c = brief?.calc
                KeyValueRow("过关方式", ParlayMath.parlayText(brief?.useParlay ?: emptyList()))
                KeyValueRow("注数", "${c?.noteCount ?: 0L} 注")
                KeyValueRow("金额", "${ParlayMath.money(c?.stake ?: 0.0)} 元（注数 × 2 × 倍数）")
                KeyValueRow(
                    "单注奖金",
                    "${ParlayMath.money(c?.minNotePrize ?: 0.0)} ~ ${ParlayMath.money(c?.maxNotePrize ?: 0.0)} 元"
                )
                KeyValueRow("全部命中合计", "${ParlayMath.money(c?.totalIfAllHit ?: 0.0)} 元")
                if ((c?.capPerNote ?: 0.0) > 0) {
                    Text(
                        "单注最高奖金限额 ${ParlayMath.money(c!!.capPerNote)} 元",
                        Modifier.padding(top = Space.xs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (c?.estimated == true) {
                    Text(
                        "注数过多，奖金为理论值",
                        Modifier.padding(top = Space.xs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (c?.overStakeLimit == true) {
                    Text(
                        "超过单张彩票 20000 元限额，请减少注数或倍数",
                        Modifier.padding(top = Space.xs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            /* ---- 操作 ---- */
            Spacer(Modifier.height(Space.lg))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedButton(
                    onClick = {
                        SlipHolder.clear()
                        scope.launch { SlipStore.saveCurrent(context, SlipHolder.snapshot()) }
                        onDismiss()
                    },
                    Modifier.weight(1f),
                    shape = Corner.sm
                ) { Text("清空", style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = {
                        val b = slipBrief()
                        if (b != null) {
                            val now = System.currentTimeMillis()
                            val saved = SavedSlip(
                                id = now.toString(),
                                createdAt = now,
                                legs = SlipHolder.selections.map { s ->
                                    SlipLeg(
                                        matchId = s.matchId,
                                        matchNum = s.matchNum,
                                        league = s.league,
                                        home = s.home,
                                        away = s.away,
                                        play = s.play,
                                        playLabel = s.playLabel,
                                        optionCode = s.optionCode,
                                        optionLabel = s.optionLabel,
                                        odds = s.odds,
                                        goalLine = s.goalLine,
                                        hit = null,
                                    )
                                },
                                parlay = b.useParlay,
                                multiple = SlipHolder.multiple,
                                stake = b.calc.stake,
                                noteCount = b.calc.noteCount,
                                maxPrize = b.calc.totalIfAllHit,
                                status = SlipStatus.PENDING,
                            )
                            scope.launch {
                                val list = SlipStore.loadSaved(context)
                                SlipStore.saveSaved(context, listOf(saved) + list)
                                SlipHolder.clear()
                                SlipStore.saveCurrent(context, SlipHolder.snapshot())
                                onDismiss()
                                UiMessage.success("方案已保存到 我的-方案中心")
                            }
                        }
                    },
                    Modifier.weight(1f),
                    shape = Corner.sm
                ) { Text("保存方案", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/** 过关方式/套餐选择用的小胶囊按钮 */
@Composable
private fun ParlayChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(Corner.sm)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Tone.fill()
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                else Color.Transparent,
                Corner.sm
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.sm),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                selected -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/** 赔率变化箭头：上升红 ↑，下降绿 ↓ */
@Composable
private fun TrendArrow(c: OddsCell) {
    if (c.trend == 0) return
    Text(
        if (c.trend == 1) "↑" else "↓",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = if (c.trend == 1) Tone.hit else Tone.miss
    )
}

/* ---------- 综合结论：预测目标行 ---------- */

/** 预测目标一行：综合选项（模型判断优先）+ 模型概率/官方赔率 + 备选 + 模型理由 + 命中状态（红只用于命中、绿只用于未中） */
@Composable
private fun CombinedPickRow(p: CombinedPick) {
    val stateColor = when (p.hit) {
        true -> Tone.hit
        false -> Tone.miss
        null -> Tone.pending()
    }
    val stateText = when (p.hit) {
        true -> "命中"
        false -> "未中"
        null -> "待结算"
    }
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(p.playLabel, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            PillTag(stateText, color = stateColor)
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            if (p.option.isNotBlank()) {
                Text(
                    p.option,
                    style = MaterialTheme.typography.titleLarge,
                    color = stateColor
                )
                if (p.odds > 0.0) {
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "赔率 ${ParlayMath.money(p.odds)}",
                        Modifier.padding(bottom = 2.dp),
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Text(
                    "暂无数据",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            if (p.alt.isNotEmpty()) {
                Text(
                    "备选 ${p.alt}${if (p.altOdds > 0.0) " @${ParlayMath.money(p.altOdds)}" else ""}",
                    Modifier.padding(bottom = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "综合置信度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Space.sm))
            ThinProgress(
                progress = p.confidence / 100f,
                modifier = Modifier.weight(1f),
                color = stateColor,
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                "${p.confidence}",
                style = MaterialTheme.typography.labelMedium.tabular(),
                fontWeight = FontWeight.SemiBold,
                color = stateColor
            )
        }
        if (p.reason.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "理由：${p.reason}",
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        if (p.divergence) {
            Spacer(Modifier.height(Space.xs))
            Text(
                "与模型主选分歧，综合置信度已按 0.85 折扣",
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ---------- 实时建议 ---------- */

/** 凯利公式：f = (b·p − q) / b，b = 赔率 − 1，q = 1 − p；夹取到 0..1 */
private fun kellyFraction(p: Double, odds: Double): Double {
    val b = odds - 1.0
    if (b <= 0.0 || p <= 0.0) return 0.0
    return ((b * p - (1.0 - p)) / b).coerceIn(0.0, 1.0)
}

private fun fmtSigned(v: Double): String =
    (if (v >= 0) "+" else "−") + String.format(Locale.US, "%.2f", kotlin.math.abs(v))

/** 建议卡片：强调底 + 主色标签（严禁红色，红色只代表命中） */
@Composable
private fun AdviceCard(kind: String, pick: CombinedPick, bankroll: Double) {
    val color = MaterialTheme.colorScheme.primary
    val f = kellyFraction(pick.probability, pick.odds)
    val stake = bankroll * f
    SurfaceCard(
        accent = true,
        contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.md),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PillTag(kind, color = color)
            Spacer(Modifier.weight(1f))
            Text(
                "概率 ${pctOf(pick.probability)} · 期望 ${fmtSigned(pick.probability * pick.odds - 1.0)}",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.md))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "${pick.playLabel} ${pick.option}",
                style = MaterialTheme.typography.titleLarge,
                color = color
            )
            Spacer(Modifier.width(Space.sm))
            Text(
                "赔率 ${ParlayMath.money(pick.odds)}",
                Modifier.padding(bottom = 2.dp),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(Space.sm))
        Text(
            if (f > 0.0) {
                "参考本金 ${ParlayMath.money(bankroll)} 元 → 全凯利建议投入 ${ParlayMath.money(stake)} 元（f = ${pctOf(f)}）"
            } else {
                "按凯利公式本项不建议投入"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (pick.note.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                pick.note,
                style = MaterialTheme.typography.labelSmall,
                lineHeight = 16.sp,
                color = color
            )
        }
        if (pick.reason.isNotBlank()) {
            Spacer(Modifier.height(Space.sm))
            Text(
                "理由：${pick.reason}",
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/* ---------- 思路分析（分节展示） ---------- */

@Composable
private fun AnalysisSection(title: String, content: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Corner.sm)
            .background(Tone.fill())
            .padding(horizontal = Space.lg, vertical = Space.md)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            content,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 分节分析为空时的兜底「结论」：只用快照里已有的真实数据（主选/概率/赔率/置信度/数据要点）拼装，
 * 不编造任何内容，保证「思路分析」在任何情况下都有内容可读。
 */
private fun fallbackConclusion(cp: CombinedPrediction): String = buildString {
    val main = cp.picks.firstOrNull { it.play == SlipPlayCodes.HAD } ?: cp.picks.firstOrNull()
    if (main != null) {
        append("综合结论倾向 ${main.playLabel} ${main.option.ifBlank { "暂无数据" }}")
        if (main.probability > 0.0) {
            append("（模型概率 ${pctOf(main.probability)}")
            if (main.odds > 0.0) append("，官方赔率 ${ParlayMath.money(main.odds)}")
            append("）")
        }
        append("，综合置信度 ${main.confidence}。")
    }
    cp.bestValue?.let { append("最具价值方向 ${it.playLabel} ${it.option}。") }
    append("模型整体置信度 ${cp.engineConf}，数据完整度 ${pctOf(cp.dataComplete)}。")
    if (cp.key.isNotBlank()) append("\n数据要点：${cp.key}")
    append("\n以上为概率与赔率的数学结果，不构成投注建议。")
}

/** "2026-09-18 18:30:00" → "09-18 18:30"；仅时间或异常格式时原样返回 */
private fun kickoffText(time: String): String {
    val parts = time.split(" ")
    if (parts.size < 2) return time
    val date = parts[0]
    val hm = parts[1].take(5)
    return (if (date.length >= 10) date.substring(5) else date) + " " + hm
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
                .padding(start = Space.xs, end = Space.lg, top = Space.sm, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Space.xs))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.lg)
        ) {
            content()
        }
    }
}

@Composable
private fun InfoCard(rows: List<Pair<String, String>>, title: String? = null) {
    SurfaceCard(contentPadding = PaddingValues(horizontal = Space.lg, vertical = Space.sm)) {
        if (title != null) {
            Text(
                title,
                Modifier.padding(top = Space.sm),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        rows.forEachIndexed { i, (k, v) ->
            if (i > 0) Hairline()
            KeyValueRow(k, v, bold = true)
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
