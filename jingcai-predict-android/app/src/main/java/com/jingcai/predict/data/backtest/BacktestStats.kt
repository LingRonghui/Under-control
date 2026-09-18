package com.jingcai.predict.data.backtest

import com.jingcai.predict.data.llm.CombinedPick
import com.jingcai.predict.data.llm.CombinedPrediction
import com.jingcai.predict.data.llm.SlipPlayCodes
import com.jingcai.predict.data.predict.LeagueProfile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 回测复盘的统计口径（**纯统计，不涉及任何模型训练/在线学习**）。
 *
 * 数据来源：本地已保存的综合预测快照（[CombinedPrediction]）中**已回写真实赛果**的部分，
 * 即「该场至少有一个玩法 hit != null」。所有数字都由真实赛果算出来，无数据一律返回 null，
 * 由界面显示 `--` 或「暂无数据」，绝不填充占位数字。
 *
 * 核心口径：
 * 1. 已结算场次 = `picks.any { it.hit != null }` 的快照数；
 * 2. 整体命中率 = 命中玩法数 ÷ 已结算玩法数；
 * 3. 单玩法命中率 = 该玩法 hit == true 项数 ÷ 该玩法 hit != null 项数；
 * 4. 最具价值/最稳健命中率 = bestValue / safest 的 hit != null 项里 true 的占比；
 * 5. 校准分析 = 把「预测概率」按区间分箱，比较箱内**平均预测概率**与**实际命中频率**，偏差 = 实际 − 预测；
 * 6. 参数建议 = 按联赛聚合（已结算 ≥3 场），只用 HAD + HHAD 两项算「实际命中率 − 平均预测概率」，
 *    偏差 ≤ −5pp 说明预测偏乐观（更保守、更依赖统计），≥ +5pp 说明预测偏保守（反之）。
 */
object BacktestStats {

    /** 联赛建议的最小样本：已结算快照 < 3 场不给建议（避免小样本噪声） */
    const val MIN_LEAGUE_MATCHES = 3

    /** 偏差显著性阈值（5 个百分点） */
    const val SIGNIFICANT_PP = 0.05

    /** 逐场复盘最多展示的场次 */
    const val REVIEW_LIMIT = 20

    /** 玩法展示顺序与中文简称（与排行卡/复盘卡一致） */
    val playOrder: List<Pair<String, String>> = listOf(
        SlipPlayCodes.HAD to "胜平负",
        SlipPlayCodes.HHAD to "让球",
        SlipPlayCodes.CRS to "比分",
        SlipPlayCodes.HAFU to "半全场",
        SlipPlayCodes.TTG to "总进球",
    )

    /** 参数校准只用样本更大的两项（胜平负 + 让球） */
    private val calibPlays = listOf(SlipPlayCodes.HAD, SlipPlayCodes.HHAD)

    fun playLabel(play: String): String =
        playOrder.firstOrNull { it.first == play }?.second ?: play

    /** 已结算判定：该场至少有一个玩法回写了真实赛果 */
    fun isSettled(p: CombinedPrediction): Boolean = p.picks.any { it.hit != null }

    /** 该场已结算的玩法项 */
    fun settledPicks(p: CombinedPrediction): List<CombinedPick> = p.picks.filter { it.hit != null }

    /** 是否已按用户校准参数覆盖（仓库写入的覆盖会标注为用户自定义来源） */
    fun isUserOverride(profile: LeagueProfile): Boolean = profile.sourceNote == "用户自定义参数"

    /* ================= 总览 ================= */

    fun overview(all: List<CombinedPrediction>): BacktestOverview {
        var matches = 0
        var settled = 0
        var hit = 0
        var confSum = 0.0
        val perPlay = playOrder.associate { it.first to intArrayOf(0, 0) }  // [已结算, 命中]
        var bvSettled = 0
        var bvHit = 0
        var sfSettled = 0
        var sfHit = 0

        all.forEach { p ->
            val judged = settledPicks(p)
            if (judged.isEmpty()) return@forEach
            matches += 1
            settled += judged.size
            hit += judged.count { it.hit == true }
            confSum += p.rankConfidence
            judged.forEach { k ->
                perPlay[k.play]?.let {
                    it[0] += 1
                    if (k.hit == true) it[1] += 1
                }
            }
            p.bestValue?.hit?.let { if (it) bvHit += 1; bvSettled += 1 }
            p.safest?.hit?.let { if (it) sfHit += 1; sfSettled += 1 }
        }

        return BacktestOverview(
            settledMatches = matches,
            settledPicks = settled,
            hitPicks = hit,
            playStats = playOrder.map { (code, label) ->
                val cell = perPlay[code] ?: intArrayOf(0, 0)
                PlayStat(play = code, label = label, settled = cell[0], hit = cell[1])
            },
            bestValueSettled = bvSettled,
            bestValueHit = bvHit,
            safestSettled = sfSettled,
            safestHit = sfHit,
            avgRankConfidence = if (matches == 0) null else confSum / matches,
        )
    }

    /* ================= 校准分析（概率 vs 实际） ================= */

    /** 分箱边界：左闭右开；最后一箱上界取 1.01 以纳入概率 1.0 */
    private data class BinSpec(val label: String, val from: Double, val to: Double)

    private val binSpecs = listOf(
        BinSpec("<40%", 0.0, 0.40),
        BinSpec("40-54%", 0.40, 0.55),
        BinSpec("55-69%", 0.55, 0.70),
        BinSpec("70-84%", 0.70, 0.85),
        BinSpec("≥85%", 0.85, 1.01),
    )

    fun calibrationBins(all: List<CombinedPrediction>): List<CalibrationBin> {
        val buckets = binSpecs.map { mutableListOf<CombinedPick>() }
        all.forEach { p ->
            p.picks.filter { it.hit != null }.forEach { k ->
                val idx = binSpecs.indexOfFirst { k.probability >= it.from && k.probability < it.to }
                if (idx >= 0) buckets[idx].add(k)
            }
        }
        return binSpecs.mapIndexed { i, spec ->
            val list = buckets[i]
            CalibrationBin(
                label = spec.label,
                count = list.size,
                avgProbability = if (list.isEmpty()) null else list.sumOf { it.probability } / list.size,
                actualRate = if (list.isEmpty()) null else list.count { it.hit == true }.toDouble() / list.size,
            )
        }
    }

    /* ================= 参数校准建议（按联赛） ================= */

    fun leagueSuggestions(all: List<CombinedPrediction>): List<LeagueCalibration> {
        return all.filter { isSettled(it) }
            .groupBy { it.league }
            .mapNotNull { (league, list) ->
                val matches = list.size
                if (matches < MIN_LEAGUE_MATCHES) return@mapNotNull null
                val picks = list.flatMap { p ->
                    p.picks.filter { it.play in calibPlays && it.hit != null }
                }
                if (picks.isEmpty()) return@mapNotNull null
                val hit = picks.count { it.hit == true }
                val avg = picks.sumOf { it.probability } / picks.size
                val actual = hit.toDouble() / picks.size
                LeagueCalibration(
                    league = league,
                    settledMatches = matches,
                    settledPicks = picks.size,
                    hitPicks = hit,
                    avgProbability = avg,
                    deviation = actual - avg,
                )
            }
            .sortedByDescending { abs(it.deviation) }
    }

    /** 预测偏乐观（实际低于预测）时的建议参数：更保守、更依赖统计 */
    fun adjustedForOptimistic(p: LeagueProfile): LeagueProfile = p.copy(
        drawBias = round2((p.drawBias + 0.01).coerceIn(0.0, 1.0)),
        weights = p.weights.copy(
            market = round2((p.weights.market - 0.05).coerceIn(0.0, 1.0)),
            stat = round2((p.weights.stat + 0.05).coerceIn(0.0, 1.0)),
        ),
    )

    /** 预测偏保守（实际高于预测）时的建议参数：更信任市场、减少统计依赖 */
    fun adjustedForConservative(p: LeagueProfile): LeagueProfile = p.copy(
        drawBias = round2((p.drawBias - 0.01).coerceIn(0.0, 1.0)),
        weights = p.weights.copy(
            market = round2((p.weights.market + 0.05).coerceIn(0.0, 1.0)),
            stat = round2((p.weights.stat - 0.05).coerceIn(0.0, 1.0)),
        ),
    )

    /* ================= 逐场复盘 ================= */

    /** 已结算快照按更新时间倒序（最多 [limit] 场） */
    fun reviewList(all: List<CombinedPrediction>, limit: Int = REVIEW_LIMIT): List<CombinedPrediction> =
        all.filter { isSettled(it) }.sortedByDescending { it.updatedAt }.take(limit)

    /* ================= 文案格式化（统一口径，界面直接调用） ================= */

    /** 比率文本：null → `--` */
    fun rateText(v: Double?): String = if (v == null) "--" else "${(v * 100).roundToInt()}%"

    /** 概率文本 */
    fun probText(v: Double): String = "${(v * 100).roundToInt()}%"

    /** 偏差文本：带正负号，保留一位小数（pp = 百分点） */
    fun deviationText(v: Double?): String =
        if (v == null) "--" else String.format(Locale.US, "%+.1fpp", v * 100)

    /** 参数数值文本：两位小数 */
    fun paramText(v: Double): String = String.format(Locale.US, "%.2f", v)

    private fun round2(v: Double): Double = (v * 100).roundToInt() / 100.0
}

/** 单玩法命中统计 */
data class PlayStat(val play: String, val label: String, val settled: Int, val hit: Int) {
    /** null 表示该玩法没有已结算项（界面显示 --） */
    val rate: Double? get() = if (settled == 0) null else hit.toDouble() / settled
}

/** 回测总览（全部为真实统计，无数据时对应字段为 null/0） */
data class BacktestOverview(
    val settledMatches: Int,
    val settledPicks: Int,
    val hitPicks: Int,
    val playStats: List<PlayStat>,
    val bestValueSettled: Int,
    val bestValueHit: Int,
    val safestSettled: Int,
    val safestHit: Int,
    val avgRankConfidence: Double?,
) {
    val overallRate: Double? get() = if (settledPicks == 0) null else hitPicks.toDouble() / settledPicks
    val bestValueRate: Double? get() = if (bestValueSettled == 0) null else bestValueHit.toDouble() / bestValueSettled
    val safestRate: Double? get() = if (safestSettled == 0) null else safestHit.toDouble() / safestSettled
    val hasData: Boolean get() = settledMatches > 0
}

/** 校准分箱：预测概率区间内的实际命中表现 */
data class CalibrationBin(
    val label: String,
    val count: Int,
    val avgProbability: Double?,
    val actualRate: Double?,
) {
    /** 偏差 = 实际命中率 − 平均预测概率（PP） */
    val deviation: Double? get() = if (avgProbability == null || actualRate == null) null else actualRate - avgProbability
}

/** 参数校准的方向：偏乐观（实际低于预测）/ 偏保守（实际高于预测） */
enum class CalibDirection { OPTIMISTIC, CONSERVATIVE }

/** 某联赛的参数校准依据（HAD + HHAD 两项） */
data class LeagueCalibration(
    val league: String,
    val settledMatches: Int,
    val settledPicks: Int,
    val hitPicks: Int,
    val avgProbability: Double,
    val deviation: Double,
) {
    /** 偏差 ≤ −5pp → 偏乐观；≥ +5pp → 偏保守；否则 null（无需调整） */
    val direction: CalibDirection? get() = when {
        deviation <= -BacktestStats.SIGNIFICANT_PP -> CalibDirection.OPTIMISTIC
        deviation >= BacktestStats.SIGNIFICANT_PP -> CalibDirection.CONSERVATIVE
        else -> null
    }
}
