package com.jingcai.predict.data.slip

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * 竞彩过关计算引擎（严格按官方口径，不用近似估算）。
 *
 * 规则来源（中国体育彩票·竞彩官方）：
 * 1. 单注金额 2 元；单注奖金 = 2 元 × 所选场次固定奖金连乘，保留 2 位小数
 *    （第 3 位 0~4 舍、6~9 入、5 时看第 2 位奇偶：偶数舍、奇数进）。
 * 2. 单票奖金 = 所有单注奖金之和 × 倍数；单张彩票最大投注金额 20000 元。
 * 3. 单注最高奖金限额：单关 10 万；2~3 关 20 万；4~5 关 50 万；6 关及以上 100 万。
 * 4. 最高过关关数：胜平负/让球胜平负 8 关、总进球 6 关、比分/半全场 4 关；
 *    混合过关取所选玩法中的最小值（木桶原则）。同一场比赛不同玩法不可串关。
 *
 * 注数用「多项式系数」法精算（Π(1 + o_i·x) 的 x^k 系数），无需枚举；
 * 奖金在注数可控时逐注按官方封顶折算，注数过大时退回理论上界并标注 estimated。
 */
object ParlayMath {

    const val UNIT = 2.0            // 单注 2 元
    const val MAX_STAKE = 20000.0   // 单票最大投注金额
    private const val COMBO_ENUM_LIMIT = 200L   // 单个组合内的注数超过此值不再逐注折算

    /** 官方进位：保留 2 位小数 */
    fun round2(v: Double): Double {
        if (v <= 0) return 0.0
        val scaled = v * 100
        val floorV = floor(scaled)
        val frac = scaled - floorV
        val base = floorV.toLong()
        val result = when {
            frac < 0.5 -> base
            frac > 0.5 -> base + 1
            else -> if (base % 2 == 0L) base else base + 1   // 恰为 .5：看前一位奇偶
        }
        return result / 100.0
    }

    /** 单注最高奖金限额（按该注的关数） */
    fun capPerNote(k: Int): Double = when {
        k <= 1 -> 100_000.0
        k <= 3 -> 200_000.0
        k <= 5 -> 500_000.0
        else -> 1_000_000.0
    }

    /** 混合过关的最高关数（木桶原则） */
    fun maxParlay(plays: Collection<String>): Int =
        plays.mapNotNull { SlipPlay.of(it)?.maxParlay }.minOrNull() ?: 8

    /** 各关次注数：dp[k] = Π(1 + o_i·x) 展开后 x^k 的系数 */
    fun noteCounts(optionCounts: List<Int>): LongArray {
        val n = optionCounts.size
        val dp = LongArray(n + 1)
        dp[0] = 1L
        for (c in optionCounts) {
            for (k in n downTo 1) dp[k] += dp[k - 1] * c.toLong()
        }
        return dp
    }

    fun totalNotes(optionCounts: List<Int>, parlay: List<Int>): Long {
        val dp = noteCounts(optionCounts)
        return parlay.sumOf { k -> if (k in dp.indices) dp[k] else 0L }
    }

    /** 某个关次下，单注奖金的最低/最高（该关数中各场最低/最高赔率连乘，已折封顶） */
    fun notePrizeRange(optionOdds: List<List<Double>>, k: Int): Pair<Double, Double>? {
        if (k <= 0 || optionOdds.size < k) return null
        val mins = optionOdds.map { it.minOrNull() ?: return null }.sorted()
        val maxs = optionOdds.map { it.maxOrNull() ?: return null }.sortedDescending()
        val cap = capPerNote(k)
        val lo = mins.take(k).fold(1.0) { a, b -> a * b }
        val hi = maxs.take(k).fold(1.0) { a, b -> a * b }
        return round2(UNIT * lo).coerceAtMost(cap) to round2(UNIT * hi).coerceAtMost(cap)
    }

    /**
     * 全部命中时的单票奖金（未乘倍数）。
     * 注数可控时逐注按封顶折算（精确），否则用「组合注数 × 封顶」上界（estimated）。
     */
    private fun totalIfAllHit(optionOdds: List<List<Double>>, parlay: List<Int>): Pair<Double, Boolean> {
        var sum = 0.0
        var estimated = false
        parlay.sorted().forEach { k ->
            if (k <= 0 || k > optionOdds.size) return@forEach
            val cap = capPerNote(k)
            combinations(optionOdds.indices.toList(), k).forEach { idx ->
                val notesInCombo = idx.fold(1L) { a, mi -> a * optionOdds[mi].size }
                if (notesInCombo <= COMBO_ENUM_LIMIT) {
                    // 逐注展开赔率连乘 → 单注奖金（官方进位）→ 单注封顶
                    var products = listOf(1.0)
                    idx.forEach { mi ->
                        val next = ArrayList<Double>(products.size * optionOdds[mi].size)
                        products.forEach { p -> optionOdds[mi].forEach { o -> next.add(p * o) } }
                        products = next
                    }
                    products.forEach { p -> sum += minOf(round2(UNIT * p), cap) }
                } else {
                    estimated = true
                    var prod = 1.0
                    idx.forEach { mi -> prod *= optionOdds[mi].sum() }
                    sum += minOf(round2(UNIT * prod), notesInCombo * cap)
                }
            }
        }
        return sum to estimated
    }

    /** 结算用：给定每场「命中选项赔率之和」（未命中为 0），算出中奖金额（未乘倍数） */
    fun winningSum(hitOddsSums: List<Double>, parlay: List<Int>): Double {
        val n = hitOddsSums.size
        val dp = DoubleArray(n + 1)
        dp[0] = 1.0
        for (s in hitOddsSums) {
            for (k in n downTo 1) dp[k] += dp[k - 1] * s
        }
        return parlay.sumOf { k -> if (k in dp.indices) dp[k] else 0.0 }
    }

    /**
     * 结算金额（精确口径）：只统计「每场都有命中选项」的注，
     * 逐注按官方进位（保留 2 位）与单注封顶折算后求和，最后乘倍数。
     * @param hitOddsByMatch 每场比赛「已命中选项」的赔率列表（未命中即空列表）
     */
    fun settlePrize(hitOddsByMatch: List<List<Double>>, parlay: List<Int>, multiple: Int): Double {
        var total = 0.0
        parlay.sorted().forEach { k ->
            if (k <= 0 || k > hitOddsByMatch.size) return@forEach
            val cap = capPerNote(k)
            combinations(hitOddsByMatch.indices.toList(), k).forEach { idx ->
                // 该组合内若有一场未命中，则该组合下没有任何一注中奖
                if (idx.any { hitOddsByMatch[it].isEmpty() }) return@forEach
                val notesInCombo = idx.fold(1L) { a, mi -> a * hitOddsByMatch[mi].size }
                if (notesInCombo <= COMBO_ENUM_LIMIT) {
                    var products = listOf(1.0)
                    idx.forEach { mi ->
                        val next = ArrayList<Double>(products.size * hitOddsByMatch[mi].size)
                        products.forEach { p -> hitOddsByMatch[mi].forEach { o -> next.add(p * o) } }
                        products = next
                    }
                    products.forEach { p -> total += minOf(round2(UNIT * p), cap) }
                } else {
                    // 命中注数极大（极端复式）：用组合上界，避免卡顿
                    var prod = 1.0
                    idx.forEach { mi -> prod *= hitOddsByMatch[mi].sum() }
                    total += minOf(round2(UNIT * prod), notesInCombo * cap)
                }
            }
        }
        return round2(total * multiple)
    }

    /**
     * 完整方案计算。
     * @param optionOdds 每场比赛的「已选选项赔率」列表
     * @param playCodes 所选玩法代码（用于木桶原则取最高关数）
     * @param parlay 关次集合（1 表示单关）
     * @param multiple 倍数
     * @param singleAllowed 所有选择是否都支持单关
     */
    fun calc(
        optionOdds: List<List<Double>>,
        playCodes: Collection<String>,
        parlay: List<Int>,
        multiple: Int,
        singleAllowed: Boolean,
    ): SlipCalc {
        val optionCounts = optionOdds.map { it.size }
        val maxK = maxParlay(playCodes).coerceAtMost(optionCounts.size.coerceAtLeast(1))
        val valid = (1..maxK).filter { k -> (k >= 2 || singleAllowed) && k <= optionCounts.size }
        // 过滤掉非法关次（如单关不允许、关次超过场次数）
        val useParlay = parlay.filter { it in valid }.ifEmpty { valid.takeLast(1) }

        val notes = totalNotes(optionCounts, useParlay)
        val stake = notes * UNIT * multiple

        var minNote = Double.MAX_VALUE
        var maxNote = 0.0
        var cap = 0.0
        useParlay.forEach { k ->
            notePrizeRange(optionOdds, k)?.let { (lo, hi) ->
                if (lo < minNote) minNote = lo
                if (hi > maxNote) maxNote = hi
            }
            cap = maxOf(cap, capPerNote(k))
        }
        if (minNote == Double.MAX_VALUE) minNote = 0.0

        val (perNote, estimated) = totalIfAllHit(optionOdds, useParlay)

        return SlipCalc(
            noteCount = notes,
            stake = stake,
            minNotePrize = minNote,
            maxNotePrize = maxNote,
            totalIfAllHit = round2(perNote * multiple),
            capPerNote = cap,
            estimated = estimated,
            overStakeLimit = stake > MAX_STAKE,
            maxParlay = maxK,
            validParlay = valid,
            singleAllowed = singleAllowed,
        )
    }

    private fun <T> combinations(list: List<T>, k: Int): List<List<T>> {
        if (k <= 0 || k > list.size) return emptyList()
        val out = mutableListOf<List<T>>()
        val idx = IntArray(k) { it }
        while (true) {
            out += idx.map { list[it] }
            var i = k - 1
            while (i >= 0 && idx[i] == list.size - k + i) i--
            if (i < 0) break
            idx[i]++
            for (j in i + 1 until k) idx[j] = idx[j - 1] + 1
        }
        return out
    }

    /**
     * 官方「M串N」容错套餐：仅收录官方注数分配表列出的套餐，
     * 套餐名中的 N = Σ C(M,k)（已与官方表核对一致）。
     */
    fun presets(m: Int): List<Pair<String, List<Int>>> {
        val all = (2..m).toList()
        val rows = when (m) {
            2 -> listOf(listOf(2))
            3 -> listOf(listOf(3), listOf(2), listOf(2, 3), listOf(1, 2), listOf(1, 2, 3))
            4 -> listOf(
                listOf(4), listOf(3), listOf(3, 4), listOf(2), listOf(1, 2),
                listOf(2, 3, 4), listOf(1, 2, 3), listOf(1, 2, 3, 4),
            )
            5 -> listOf(
                listOf(5), listOf(4), listOf(4, 5), listOf(2), listOf(1, 2), listOf(3, 4, 5),
                listOf(2, 3), listOf(1, 2, 3), listOf(2, 3, 4, 5), listOf(1, 2, 3, 4), listOf(1, 2, 3, 4, 5),
            )
            6 -> listOf(
                listOf(6), listOf(5), listOf(5, 6), listOf(2), listOf(3), listOf(1, 2), listOf(4, 5, 6),
                listOf(2, 3), listOf(1, 2, 3), listOf(3, 4, 5, 6), listOf(2, 3, 4), listOf(1, 2, 3, 4),
                listOf(2, 3, 4, 5, 6), listOf(1, 2, 3, 4, 5), all,
            )
            7 -> listOf(listOf(7), listOf(6), listOf(6, 7), listOf(5), listOf(4), all, listOf(1, 2, 3, 4, 5, 6, 7))
            8 -> listOf(
                listOf(8), listOf(7), listOf(7, 8), listOf(6), listOf(5), listOf(4),
                all, listOf(1, 2, 3, 4, 5, 6, 7, 8),
            )
            else -> listOf(all)
        }
        return rows.map { k ->
            val n = k.sumOf { c -> comb(m, c) }
            "${m}串$n" to k
        }
    }

    private fun comb(n: Int, k: Int): Int {
        if (k < 0 || k > n) return 0
        var r = 1L
        for (i in 1..k) r = r * (n - k + i) / i
        return r.toInt()
    }

    /** 关次集合文案，如 [2,3] → "2串1 + 3串1" */
    fun parlayText(parlay: List<Int>): String =
        parlay.sorted().joinToString(" + ") { if (it == 1) "单关" else "${it}串1" }

    /** 金额展示：整数不显示小数 */
    fun money(v: Double): String {
        val i = v.roundToInt()
        return if (abs(v - i) < 0.005) "$i" else String.format("%.2f", v)
    }
}
