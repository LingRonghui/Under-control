package com.jingcai.predict.data.slip

import android.content.Context
import com.jingcai.predict.data.remote.MatchPreviewApi

/**
 * 方案自动结算（全应用唯一实现，供「方案中心」与「我的 · 盈亏仪表盘」共用）。
 *
 * 口径（与方案中心完全一致）：
 * 1. 用官方赛果接口取最近已完赛场次，只保留比分可解析的场次；
 * 2. 逐条选择按 [SlipSettlement] 判定命中（让球按盘口、比分含胜/平/负其他）；
 * 3. 奖金按 [ParlayMath.settlePrize] 官方口径计算；
 * 4. 命中即 WON、有未判定项即 PENDING（不下结论）、其余 LOST；
 * 5. 只有结果发生变化才落盘，避免无意义写入。
 *
 * 任何环节失败都不抛出异常（返回已读到的数据），避免影响页面渲染。
 */
object SlipAutoSettle {

    /** 结算全部方案并返回最新列表（无变化时返回原列表） */
    suspend fun settleAll(context: Context): List<SavedSlip> {
        val saved = runCatching { SlipStore.loadSaved(context) }.getOrDefault(emptyList())
        if (saved.isEmpty()) return saved

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

        val settled = saved.map { settle(it, resultMap) }
        if (settled != saved) runCatching { SlipStore.saveSaved(context, settled) }
        return settled
    }

    /**
     * 依据赛果重新结算一个方案：刷新每条选择的命中标记、中奖金额与状态。
     * @param results 已完赛场次（matchId → 赛果），未完赛或比分解析失败的场次不在表内
     */
    fun settle(slip: SavedSlip, results: Map<String, SlipSettlement.Result>): SavedSlip {
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
}
