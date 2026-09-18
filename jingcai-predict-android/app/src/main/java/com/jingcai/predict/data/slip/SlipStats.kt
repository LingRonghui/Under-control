package com.jingcai.predict.data.slip

/**
 * 方案盈亏统计（口径固定、可复算）。
 *
 * 【统计口径（界面展示必须与此一致）】
 * - 「已结算」= status ≠ PENDING（即已中奖或未中奖），其数据才有真实盈亏；
 * - 总投入 = Σ 已结算方案 stake；总回报 = Σ 已结算方案 prize；
 * - 净盈亏 = 总回报 − 总投入；ROI = 净盈亏 ÷ 总投入；
 * - 命中率 = 已中奖单数 ÷ 已结算单数；
 * - 单笔最大盈利/亏损 = max/min(prize − stake)；
 * - 连红/连黑 = 按结算时间排序后，连续中奖/未中奖的最长段（结算时间缺失时用创建时间兜底）；
 * - 「未结算」单独统计，**不并入上面任何盈亏指标**（避免把未开奖的方案算成 0 收益）：
 *   占用本金 = Σ stake，理论最高回报 = Σ maxPrize（全部命中时的奖金，非预期收益）。
 */
object SlipStats {

    /** 盈亏统计结果（金额单位：元） */
    data class Pnl(
        // 已结算
        val settledCount: Int = 0,
        val wonCount: Int = 0,
        val lostCount: Int = 0,
        val stake: Double = 0.0,
        val payout: Double = 0.0,
        val net: Double = 0.0,
        val roi: Double = 0.0,
        val hitRate: Double = 0.0,
        val bestProfit: Double = 0.0,
        val worstLoss: Double = 0.0,
        val maxWinStreak: Int = 0,
        val maxLoseStreak: Int = 0,
        // 未结算
        val pendingCount: Int = 0,
        val pendingStake: Double = 0.0,
        val pendingMaxPrize: Double = 0.0,
    ) {
        /** 是否有任何已结算样本（无样本时 ROI/命中率无意义，界面需降级展示） */
        val hasSettled: Boolean get() = settledCount > 0
    }

    fun compute(slips: List<SavedSlip>): Pnl {
        val settled = slips.filter { it.status != SlipStatus.PENDING }
        val pending = slips.filter { it.status == SlipStatus.PENDING }

        val stake = settled.sumOf { it.stake }
        val payout = settled.sumOf { it.prize }
        val net = payout - stake
        val won = settled.count { it.status == SlipStatus.WON }
        val lost = settled.count { it.status == SlipStatus.LOST }

        val profits = settled.map { it.prize - it.stake }
        val ordered = settled.sortedBy { it.settledAt ?: it.createdAt }
        var winStreak = 0
        var loseStreak = 0
        var curWin = 0
        var curLose = 0
        ordered.forEach { s ->
            if (s.status == SlipStatus.WON) {
                curWin++
                curLose = 0
                if (curWin > winStreak) winStreak = curWin
            } else {
                curLose++
                curWin = 0
                if (curLose > loseStreak) loseStreak = curLose
            }
        }

        return Pnl(
            settledCount = settled.size,
            wonCount = won,
            lostCount = lost,
            stake = stake,
            payout = payout,
            net = net,
            roi = if (stake > 0.0) net / stake else 0.0,
            hitRate = if (settled.isNotEmpty()) won.toDouble() / settled.size else 0.0,
            bestProfit = profits.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0,
            worstLoss = profits.minOrNull()?.coerceAtMost(0.0) ?: 0.0,
            maxWinStreak = winStreak,
            maxLoseStreak = loseStreak,
            pendingCount = pending.size,
            pendingStake = pending.sumOf { it.stake },
            pendingMaxPrize = pending.sumOf { it.maxPrize },
        )
    }
}
