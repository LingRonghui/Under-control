package com.jingcai.predict.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.slip.ParlayMath
import com.jingcai.predict.data.slip.SavedSlip
import com.jingcai.predict.data.slip.SlipAutoSettle
import com.jingcai.predict.data.slip.SlipLeg
import com.jingcai.predict.data.slip.SlipStatus
import com.jingcai.predict.data.slip.SlipStore
import com.jingcai.predict.ui.components.EmptyState
import com.jingcai.predict.ui.components.Hairline
import com.jingcai.predict.ui.components.KeyValueRow
import com.jingcai.predict.ui.components.PillTag
import com.jingcai.predict.ui.components.StatTile
import com.jingcai.predict.ui.components.SurfaceCard
import com.jingcai.predict.ui.theme.Space
import com.jingcai.predict.ui.theme.Tone
import com.jingcai.predict.ui.theme.tabular
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/**
 * 命中口径（全应用统一）：
 * - 命中 / 已中奖 = 竞彩红 [Tone.hit]
 * - 未中 / 未中奖 = 竞彩绿 [Tone.miss]
 * - 待结算 = 中性 [Tone.pending()]
 */

/** 创建时间展示格式 */
private val timeFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/**
 * 方案中心：展示已保存的竞彩方案，并随比赛结束自动判断是否命中（自动结算）。
 */
@Composable
fun SlipCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slips by remember { mutableStateOf<List<SavedSlip>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 进入页面（以及点击「更新赛果」）时自动结算：读方案 → 拉官方赛果 → 判定命中 → 有变化才落盘
    // 结算实现与「我的 · 盈亏仪表盘」共用 SlipAutoSettle，避免两套口径
    LaunchedEffect(refreshKey) {
        slips = SlipAutoSettle.settleAll(context)
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：轻量返回 + 标题 + 更新赛果
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Space.xs, end = Space.sm, top = Space.sm, bottom = Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp),
                    tint = Tone.textLabel()
                )
            }
            Spacer(Modifier.width(Space.xs))
            Text(
                "方案中心",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                color = Tone.textStrong()
            )
            IconButton(onClick = { refreshKey++ }, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "更新赛果",
                    modifier = Modifier.size(19.dp),
                    tint = Tone.textLabel()
                )
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = Space.lg),
            verticalArrangement = Arrangement.spacedBy(Space.md)
        ) {
            if (slips.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.Inbox,
                        title = "暂无方案",
                        description = "在比赛详情页-赔率 中勾选赔率后保存方案",
                    )
                }
            } else {
                items(slips, key = { it.id }) { slip ->
                    SlipCard(
                        slip = slip,
                        onDelete = {
                            val next = slips.filterNot { it.id == slip.id }
                            slips = next
                            scope.launch { runCatching { SlipStore.saveSaved(context, next) } }
                        }
                    )
                }
            }

            item {
                Text(
                    "结算依据官方 90 分钟（含伤停补时）赛果；让球玩法已按盘口计算；比分玩法含胜/平/负其他。",
                    Modifier
                        .fillMaxWidth()
                        .padding(top = Space.sm, bottom = Space.lg),
                    style = MaterialTheme.typography.labelSmall,
                    color = Tone.textHint(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
private fun SlipCard(slip: SavedSlip, onDelete: () -> Unit) {
    val statusColor = when (slip.status) {
        SlipStatus.WON -> Tone.hit
        SlipStatus.LOST -> Tone.miss
        else -> Tone.pending()
    }
    val statusText = when (slip.status) {
        SlipStatus.WON -> "已中奖"
        SlipStatus.LOST -> "未中奖"
        else -> "待结算"
    }

    SurfaceCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PillTag(statusText, color = statusColor)
            Spacer(Modifier.width(Space.sm))
            Text(
                formatTime(slip.createdAt),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = Tone.textLabel()
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除方案",
                    modifier = Modifier.size(16.dp),
                    tint = Tone.textLabel()
                )
            }
        }

        Spacer(Modifier.height(Space.md))

        Row(Modifier.fillMaxWidth()) {
            StatTile(
                label = "投注金额",
                value = ParlayMath.money(slip.stake),
                modifier = Modifier.weight(1f),
                unit = "元",
            )
            StatTile(
                label = "最高可中",
                value = ParlayMath.money(slip.maxPrize),
                modifier = Modifier.weight(1f),
                unit = "元",
                valueColor = Tone.textStrong(),
            )
        }

        if (slip.status == SlipStatus.WON) {
            Spacer(Modifier.height(Space.md))
            Hairline()
            Spacer(Modifier.height(Space.xs))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "中奖金额",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tone.textLabel()
                )
                Text(
                    "${ParlayMath.money(slip.prize)} 元",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp).tabular(),
                    fontWeight = FontWeight.Bold,
                    color = Tone.hit
                )
            }
        }

        Spacer(Modifier.height(Space.md))
        Hairline()
        KeyValueRow("过关方式", ParlayMath.parlayText(slip.parlay))
        Hairline()
        KeyValueRow("倍数", "${slip.multiple} 倍")
        Hairline()
        KeyValueRow("注数", "${slip.noteCount} 注")

        Spacer(Modifier.height(Space.sm))
        Hairline()
        Spacer(Modifier.height(Space.sm))

        slip.legs.forEach { leg -> LegRow(leg) }
    }
}

@Composable
private fun LegRow(leg: SlipLeg) {
    val markText = when (leg.hit) {
        true -> "中"
        false -> "未中"
        null -> "待"
    }
    val markColor = when (leg.hit) {
        true -> Tone.hit
        false -> Tone.miss
        null -> Tone.pending()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${leg.matchNum} ${leg.league} ${leg.home} vs ${leg.away}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Tone.textStrong(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${leg.playLabel} ${leg.optionLabel} @${leg.odds}",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = Tone.textLabel()
            )
        }
        Spacer(Modifier.width(Space.sm))
        PillTag(markText, color = markColor)
    }
}

private fun formatTime(ts: Long): String =
    if (ts <= 0L) "" else timeFormat.format(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()))
