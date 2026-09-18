package com.jingcai.predict.data.backtest

import android.content.Context
import com.jingcai.predict.data.llm.AiPredictionStore
import com.jingcai.predict.data.llm.PredictionPipeline
import com.jingcai.predict.data.remote.MatchPreviewApi

/**
 * 结算回填：用**真实赛果**对已存的预测快照做命中判定并写回（hit）。
 *
 * ⚠️ 本节只做本地结算判定，**不发起任何模型请求，不重新预测**：
 * - 预测快照是赛前生成的，那时 `CombinedPick.hit` 必然为 null；
 * - 比赛完赛后，这里只取官方赛果（实时比分接口）与盘口（赔率接口），
 *   复用 [PredictionPipeline] 的判定函数算出 hit 并写回快照，不会重复消耗 token；
 * - 「已结束的比赛不再预测」的规定不受影响：本节不调用大模型、不产出新的预测结论。
 *
 * 拿不到的数据一律判为「无法判定」（hit = null），**绝不臆测赛果或盘口**：
 * - 比赛未结束 / 无比分 → 整场跳过；
 * - 半全场缺半场比分、让球缺盘口 → 该玩法判 null。
 */
object SettlementRunner {

    /** 非 force 时的最小执行间隔（毫秒）：页面轮询调用时不会频繁请求接口 */
    private const val THROTTLE_MS = 60_000L

    private val lock = Any()

    /** 上次开始执行的时间戳（节流用） */
    private var lastRunAt = 0L

    /** 是否已有一次结算在执行（避免重入） */
    private var running = false

    /**
     * 结算全部可结算的快照。
     * @param force true = 忽略 60 秒节流（用户手动点「刷新」时用）
     * @return 本次**新结算**的场次数量（只有当至少一项 hit 由 null 变为非 null 时才算一场）
     */
    suspend fun settleAll(context: Context, force: Boolean = false): Int {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (running) return 0
            if (!force && now - lastRunAt < THROTTLE_MS) return 0
            running = true
            lastRunAt = now
        }
        return try {
            settle(context)
        } finally {
            synchronized(lock) { running = false }
        }
    }

    private suspend fun settle(context: Context): Int {
        AiPredictionStore.loadOnce(context)
        var settled = 0
        // 取快照的不可变列表引用：upsert 只替换 StateFlow 的值，不会影响本次遍历
        AiPredictionStore.items.value.forEach { snap ->
            // 已完赛且所有玩法都已判定 → 无需再查
            if (snap.statusText == "已完赛" && snap.picks.all { it.hit != null }) return@forEach

            // 1) 真实赛果：拿不到或未结束一律跳过（不猜比分）
            val live = runCatching { MatchPreviewApi.fetchLive(snap.matchId) }.getOrNull()
            if (live == null || !live.isFinished) return@forEach
            val full = PredictionPipeline.parseScore(live.score) ?: return@forEach
            val half = PredictionPipeline.parseScore(live.halfScore)
            val result = PredictionPipeline.SlipSettlement4Hit(
                homeScore = full.first,
                awayScore = full.second,
                halfHome = half?.first,
                halfAway = half?.second,
            )

            // 2) 让球盘口：拿不到时为空串，hitOf 会把让球玩法判为无法判定（null）
            val goalLine = runCatching { MatchPreviewApi.fetchOdds(snap.matchId)?.goalLine }.getOrNull().orEmpty()

            // 3) 逐项判定（判定规则完全复用 PredictionPipeline，避免两套口径）
            val picks = snap.picks.map { p ->
                p.copy(hit = PredictionPipeline.hitOf(p.play, p.option, goalLine, result))
            }
            val bestValue = snap.bestValue?.let { b ->
                b.copy(hit = PredictionPipeline.hitOf(b.play, b.option, goalLine, result))
            }
            val safest = snap.safest?.let { s ->
                s.copy(hit = PredictionPipeline.hitOf(s.play, s.option, goalLine, result))
            }

            // 4) 只有出现「新判定」（hit 由 null 变为非 null）才写盘，避免无意义写盘
            var gained = 0
            picks.forEachIndexed { i, p ->
                if (snap.picks[i].hit == null && p.hit != null) gained++
            }
            if (snap.bestValue?.hit == null && bestValue?.hit != null) gained++
            if (snap.safest?.hit == null && safest?.hit != null) gained++
            if (gained == 0) return@forEach

            AiPredictionStore.upsert(
                context,
                snap.copy(picks = picks, bestValue = bestValue, safest = safest, statusText = "已完赛"),
            )
            settled++
        }
        return settled
    }
}
