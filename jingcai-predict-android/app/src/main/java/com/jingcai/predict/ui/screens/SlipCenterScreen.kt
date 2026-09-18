package com.jingcai.predict.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jingcai.predict.data.remote.MatchPreviewApi
import com.jingcai.predict.data.slip.ParlayMath
import com.jingcai.predict.data.slip.SavedSlip
import com.jingcai.predict.data.slip.SlipLeg
import com.jingcai.predict.data.slip.SlipSettlement
import com.jingcai.predict.data.slip.SlipStatus
import com.jingcai.predict.data.slip.SlipStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** 命中标记配色：命中绿 / 未中灰 / 待结算黄 */
private val hitColor = Color(0xFF0E9F6E)
private val missColor = Color(0xFF9AA0A6)
private val pendingColor = Color(0xFFF59E0B)
private val winColor = Color(0xFF0E9F6E)
private val lostColor = Color(0xFFD93A2B)

/** 创建时间展示格式 */
private val timeFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

/**
 * 方案中心：展示已保存的竞彩方案，并随比赛结束自动判断是否命中（自动结算）。
 */
@Composable
fun SlipCenterScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var slips by remember { mutableStateOf<List<SavedSlip>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 进入页面（以及点击「更新赛果」）时：读已保存方案 → 拉赛果 → 自动结算 → 有变化才落盘
    LaunchedEffect(refreshKey) {
        val saved = runCatching { SlipStore.loadSaved(context) }.getOrDefault(emptyList())
        val results = runCatching { MatchPreviewApi.fetchResultList(want = 60, maxPages = 6) }
            .getOrDefault(emptyList())
        // matchId → 赛果；比分或半场比分解析失败的场次视为无赛果
        val resultMap = results.mapNotNull { r ->
            val full = SlipSettlement.parseScore(r.score) ?: return@mapNotNull null
            val half = SlipSettlement.parseScore(r.htScore)
            r.matchId to SlipSettlement.Result(
                homeScore = full.first,
                awayScore = full.second,
                halfHome = half?.first,
                halfAway = half?.second
            )
        }.toMap()

        val settled = saved.map { settleSlip(it, resultMap) }
        slips = settled
        if (settled != saved) runCatching { SlipStore.saveSaved(context, settled) }
    }

    Column(Modifier.fillMaxSize()) {
        // 顶栏：标题 + 返回 + 更新赛果
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text(
                "方案中心",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { refreshKey++ }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "更新赛果")
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (slips.isEmpty()) {
                item {
                    Text(
                        "暂无方案\n在比赛详情页-赔率 中勾选赔率后保存方案",
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
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
                        .padding(top = 6.dp, bottom = 14.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 依据赛果重新结算一个方案：刷新每条选择的命中标记、中奖金额与状态。
 * @param results 已完赛场次（matchId → 赛果），未完赛或比分解析失败的场次不在表内
 */
private fun settleSlip(
    slip: SavedSlip,
    results: Map<String, SlipSettlement.Result>,
): SavedSlip {
    val legs = slip.legs.map { leg ->
        val r = results[leg.matchId]
        if (r == null) leg.copy(hit = null)
        else leg.copy(hit = SlipSettlement.hit(leg.play, leg.optionCode, leg.goalLine, r))
    }
    // 按比赛分组：每场「已命中选项」的赔率列表（该场无命中则为空列表）
    val hitOddsByMatch = legs.groupBy { it.matchId }.values.map { group ->
        group.filter { it.hit == true }.map { it.odds }
    }
    val prize = ParlayMath.settlePrize(hitOddsByMatch, slip.parlay, slip.multiple)
    val status = when {
        prize > 0.0 -> SlipStatus.WON
        legs.any { it.hit == null } -> SlipStatus.PENDING
        else -> SlipStatus.LOST
    }
    return slip.copy(
        legs = legs,
        status = status,
        prize = prize,
        settledAt = if (status == SlipStatus.PENDING) null
        else (slip.settledAt ?: System.currentTimeMillis())
    )
}

@Composable
private fun SlipCard(slip: SavedSlip, onDelete: () -> Unit) {
    val statusColor = when (slip.status) {
        SlipStatus.WON -> winColor
        SlipStatus.LOST -> lostColor
        else -> pendingColor
    }
    val statusText = when (slip.status) {
        SlipStatus.WON -> "已中奖"
        SlipStatus.LOST -> "未中奖"
        else -> "待结算"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    statusText,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                formatTime(slip.createdAt),
                Modifier
                    .padding(start = 8.dp)
                    .weight(1f),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除方案",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            InfoItem("过关方式", ParlayMath.parlayText(slip.parlay), Modifier.weight(1.2f))
            InfoItem("倍数", "${slip.multiple} 倍", Modifier.weight(1f))
            InfoItem("注数", "${slip.noteCount} 注", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            InfoItem("投注金额", "${ParlayMath.money(slip.stake)} 元", Modifier.weight(1f))
            InfoItem("最高可中", "${ParlayMath.money(slip.maxPrize)} 元", Modifier.weight(1f))
        }
        if (slip.status == SlipStatus.WON) {
            Text(
                "中奖 ${ParlayMath.money(slip.prize)} 元",
                Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = winColor,
                fontWeight = FontWeight.Bold
            )
        }

        // 分割线
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        )

        slip.legs.forEach { leg -> LegRow(leg) }
    }
}

@Composable
private fun InfoItem(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(
            label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun LegRow(leg: SlipLeg) {
    val (markText, markColor) = when (leg.hit) {
        true -> "中" to hitColor
        false -> "未中" to missColor
        null -> "待" to pendingColor
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${leg.matchNum} ${leg.league} ${leg.home} vs ${leg.away}",
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${leg.playLabel} ${leg.optionLabel} @${leg.odds}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            markText,
            Modifier.padding(start = 8.dp),
            fontSize = 11.sp,
            color = markColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatTime(ts: Long): String =
    if (ts <= 0L) "" else timeFormat.format(Date(ts))
