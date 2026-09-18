package com.jingcai.predict.data.predict

import kotlin.math.exp
import kotlin.math.pow

/**
 * 泊松分布与比分矩阵。
 *
 * 基于 Dixon-Coles（1997, JRSS-C）经典框架：
 * 双泊松独立分布 + τ 修正因子，校正 0-0 / 1-0 / 0-1 / 1-1 低比分格的依赖，
 * 使平局与低比分概率更贴近真实足球数据。
 *
 * 全部为纯函数，不依赖网络与状态，可独立单元测试。
 */
object Poisson {

    /** 泊松概率质量函数 P(X=k) = e^(-λ) · λ^k / k! */
    fun pmf(k: Int, lambda: Double): Double {
        if (lambda <= 0.0) return if (k == 0) 1.0 else 0.0
        return exp(-lambda) * lambda.pow(k) / factorial(k)
    }

    private fun factorial(n: Int): Double {
        var r = 1.0
        for (i in 2..n) r *= i
        return r
    }

    /**
     * Dixon-Coles τ 修正因子。
     * @param x 主队进球数 @param y 客队进球数
     * @param lh 主队期望进球 @param la 客队期望进球 @param rho 依赖参数（防守联赛更负）
     */
    fun tau(x: Int, y: Int, lh: Double, la: Double, rho: Double): Double {
        val t = when {
            x == 0 && y == 0 -> 1.0 - lh * la * rho
            x == 0 && y == 1 -> 1.0 + lh * rho
            x == 1 && y == 0 -> 1.0 + la * rho
            x == 1 && y == 1 -> 1.0 - rho
            else -> 1.0
        }
        // 极值保护：修正因子不允许为负
        return if (t < 0.0) 0.0 else t
    }

    /**
     * 构建 0..maxGoals 的比分概率矩阵（含 τ 修正，已归一化，概率和 = 1）。
     */
    fun scoreMatrix(lambdaHome: Double, lambdaAway: Double, rho: Double, maxGoals: Int = 9): Array<DoubleArray> {
        val m = Array(maxGoals + 1) { DoubleArray(maxGoals + 1) }
        for (x in 0..maxGoals) {
            for (y in 0..maxGoals) {
                m[x][y] = pmf(x, lambdaHome) * pmf(y, lambdaAway) * tau(x, y, lambdaHome, lambdaAway, rho)
            }
        }
        val sum = m.sumOf { row -> row.sum() }
        if (sum > 0.0) {
            for (x in 0..maxGoals) for (y in 0..maxGoals) m[x][y] /= sum
        }
        return m
    }

    /** 胜平负边缘概率（主胜 / 平 / 客胜） */
    fun wdl(matrix: Array<DoubleArray>): Triple<Double, Double, Double> {
        var home = 0.0
        var draw = 0.0
        var away = 0.0
        for (x in matrix.indices) {
            for (y in matrix[x].indices) {
                when {
                    x > y -> home += matrix[x][y]
                    x == y -> draw += matrix[x][y]
                    else -> away += matrix[x][y]
                }
            }
        }
        return Triple(home, draw, away)
    }

    /** 总进球分布：out[k] = P(总进球 = k)，k ∈ 0..2*maxGoals */
    fun totalGoals(matrix: Array<DoubleArray>): DoubleArray {
        val n = matrix.size
        val out = DoubleArray(2 * n - 1)
        for (x in 0 until n) for (y in 0 until n) out[x + y] += matrix[x][y]
        return out
    }

    /** 总进球数的最可能档位，返回 (标签, 概率)。7 球及以上合并为 "7球+" */
    fun mostLikelyTotal(matrix: Array<DoubleArray>): Pair<String, Double> {
        val dist = totalGoals(matrix)
        var best = 0
        for (k in 1 until dist.size) if (dist[k] > dist[best]) best = k
        val label = if (best >= 7) "7球+" else "${best}球"
        return label to dist[best]
    }

    /** 最可能比分，返回 ("x:y", 概率) */
    fun mostLikelyScore(matrix: Array<DoubleArray>): Pair<String, Double> {
        var bx = 0
        var by = 0
        for (x in matrix.indices) {
            for (y in matrix[x].indices) {
                if (matrix[x][y] > matrix[bx][by]) {
                    bx = x; by = y
                }
            }
        }
        return "$bx:$by" to matrix[bx][by]
    }

    /**
     * 半全场推导：半场进球近似 Pois(λ/2)（泊松过程可加性的简化假设），
     * 半场结果与全场结果联合概率取最高组合。
     * 标签采用竞彩官方写法：胜胜 / 胜平 / 胜负 / 平胜 / 平平 / 平负 / 负胜 / 负平 / 负负
     */
    fun halfFull(lambdaHome: Double, lambdaAway: Double, fullMatrix: Array<DoubleArray>): Pair<String, Double> {
        val halfMatrix = scoreMatrix(lambdaHome / 2, lambdaAway / 2, 0.0)
        val half = wdl(halfMatrix)
        val full = wdl(fullMatrix)
        val halfP = listOf(half.first, half.second, half.third)
        val fullP = listOf(full.first, full.second, full.third)
        val labels = listOf("胜", "平", "负")
        var best = "平平"
        var bestP = -1.0
        for (h in 0..2) {
            for (f in 0..2) {
                val p = halfP[h] * fullP[f]
                if (p > bestP) {
                    bestP = p
                    best = labels[h] + labels[f]
                }
            }
        }
        return best to bestP
    }
}
