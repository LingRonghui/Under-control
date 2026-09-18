package com.jingcai.predict.data.predict

/**
 * 赔率数学：隐含概率与去水。
 *
 * 采用业界标准的比例法（inverse-odds / proportional）去水：
 * p_i = (1/odds_i) / Σ(1/odds_j)，将博彩公司 margin 按比例分摊回各结果。
 * 市场赔率是足球预测公认的最强单一信号源（Dixon & Coles 论文即用赔率验证模型有效性）。
 */
object OddsMath {

    /**
     * 比例法去水。非法（<=1.0）的赔率视为缺失，自动跳过并重新归一化其余项；
     * 全部非法时返回全 0（表示该信号不可用）。
     */
    fun impliedProb(odds: List<Double>): List<Double> {
        val inv = odds.map { if (it > 1.0) 1.0 / it else 0.0 }
        val sum = inv.sum()
        if (sum <= 0.0) return List(odds.size) { 0.0 }
        return inv.map { it / sum }
    }

    /** 胜平负三向去水便捷方法，返回 (主胜, 平, 客胜) 隐含概率 */
    fun impliedWdl(home: Double, draw: Double, away: Double): Triple<Double, Double, Double> {
        val p = impliedProb(listOf(home, draw, away))
        return Triple(p[0], p[1], p[2])
    }
}
