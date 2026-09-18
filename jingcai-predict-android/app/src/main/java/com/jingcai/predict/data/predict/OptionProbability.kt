package com.jingcai.predict.data.predict

/** 某个玩法某个选项的模型概率（纯本地计算，可复算） */
data class OptionProb(
    val play: String,       // HAD / HHAD / CRS / HAFU / TTG
    val option: String,     // 展示文案，必须与 App 内既有写法一致
    val probability: Double // 0..1
)

/**
 * 从泊松比分矩阵推导「每个玩法每个选项」的模型概率。
 *
 * 设计约束：
 * 1. 纯函数：无 Android 依赖、无网络、无状态，给定同样的 λ/ρ/盘口必可复算出同样的概率。
 * 2. 复用既有数学层 [Poisson]（Dixon-Coles 双泊松 + τ 修正，矩阵已归一化），不重复实现分布。
 * 3. 选项集合与展示文案严格对齐 App 既有写法：
 *    - 比分 31 选项与 [com.jingcai.predict.data.slip.SlipSettlement] 的判定口径一致；
 *    - 半全场 / 总进球文案与详情页 [com.jingcai.predict.ui.screens] 的解析函数一致。
 *
 * 归一化说明（为什么每个玩法的概率和都是 1）：
 * - HAD / HHAD：对已归一化矩阵（和 = 1）按互斥且完备的三分类求和，故三项和 = 1。
 * - CRS：矩阵 maxGoals=9（10×10=100 个格子且和 = 1）；31 个选项把 100 个格子**恰好划满**
 *   （28 个具体比分 + 3 个「其他」按胜/平/负方向收口），故 31 项和 = 1。
 * - HAFU：半场三项和 = 1、全场三项和 = 1，9 个联合概率为二者乘积，故九项和 = 1×1 = 1。
 * - TTG：总进球分布由矩阵按 (x+y) 汇聚而来，各档与「7球+」之和 = 矩阵总和 = 1。
 * 上述均为代数恒等，实际仅存在浮点舍入误差（量级 1e-15），远小于 0.5%。
 */
object OptionProbability {

    /* ---------- 官方选项集 ---------- */

    /**
     * 竞彩比分玩法的具体比分（官方 31 选项 = 胜 12 + 平 4 + 负 12 + 「胜/平/负其他」）。
     * 与 SlipSettlement 的 HOME_SCORES / DRAW_SCORES / AWAY_SCORES 完全一致。
     */
    private val HOME_SCORES = listOf(
        1 to 0, 2 to 0, 2 to 1, 3 to 0, 3 to 1, 3 to 2,
        4 to 0, 4 to 1, 4 to 2, 5 to 0, 5 to 1, 5 to 2,
    )
    private val DRAW_SCORES = listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3)
    private val AWAY_SCORES = listOf(
        0 to 1, 0 to 2, 1 to 2, 0 to 3, 1 to 3, 2 to 3,
        0 to 4, 1 to 4, 2 to 4, 0 to 5, 1 to 5, 2 to 5,
    )

    /** 半全场 9 选项文案（行 = 半场 胜/平/负，列 = 全场 胜/平/负），竞彩官方写法 */
    private val HAFU_LABELS = listOf(
        "胜胜", "胜平", "胜负",
        "平胜", "平平", "平负",
        "负胜", "负平", "负负",
    )

    /**
     * 计算全部玩法的选项概率。
     * @param lambdaHome 主队期望进球
     * @param lambdaAway 客队期望进球
     * @param rho Dixon-Coles τ 参数（联赛模板）
     * @param goalLine 让球盘口整数（官方 hhad 的 goalLine，直接加在主队比分上，如 -1 表示主队 -1）；
     *                 取不到时传 null，此时 HHAD 对应空 map（不猜）
     * @return play（HAD/HHAD/CRS/HAFU/TTG）-> (option 文案 -> 概率)
     */
    fun all(
        lambdaHome: Double,
        lambdaAway: Double,
        rho: Double,
        goalLine: Int?,
    ): Map<String, Map<String, Double>> {
        // 全场比分矩阵：maxGoals 取默认 9，已含 τ 修正并归一化
        val matrix = Poisson.scoreMatrix(lambdaHome, lambdaAway, rho)
        val out = LinkedHashMap<String, Map<String, Double>>()
        out["HAD"] = had(matrix)
        // 盘口缺失时不推导让球玩法（与 SlipSettlement.hit 在 line==null 时返回 null 的降级口径一致）
        out["HHAD"] = if (goalLine == null) emptyMap() else hhad(matrix, goalLine)
        out["CRS"] = crs(matrix)
        out["HAFU"] = hafu(lambdaHome, lambdaAway, matrix)
        out["TTG"] = ttg(matrix)
        return out
    }

    /* ---------- 各玩法 ---------- */

    /**
     * 胜平负（HAD）：官方 3 选项「主胜 / 平局 / 客胜」。
     * 直接取矩阵边缘概率 Poisson.wdl(matrix)：主胜 = ΣP(x>y)、平 = ΣP(x=y)、客胜 = ΣP(x<y)，
     * 三分类互斥完备，故三项和 = 矩阵总和 = 1。
     */
    private fun had(matrix: Array<DoubleArray>): Map<String, Double> {
        val (win, draw, lose) = Poisson.wdl(matrix)
        val out = LinkedHashMap<String, Double>()
        out["主胜"] = win
        out["平局"] = draw
        out["客胜"] = lose
        return out
    }

    /**
     * 让球胜平负（HHAD）：官方 3 选项「让胜 / 让平 / 让负」。
     * 按盘口平移主队进球后重算：有效进球 x' = x + goalLine，
     * x' > y → 让胜，x' == y → 让平，x' < y → 让负。
     * 判定口径与 SlipSettlement.hit 中 homeScore + line 的比较完全一致；三分类互斥完备，三项和 = 1。
     */
    private fun hhad(matrix: Array<DoubleArray>, goalLine: Int): Map<String, Double> {
        var win = 0.0
        var draw = 0.0
        var lose = 0.0
        for (x in matrix.indices) {
            for (y in matrix[x].indices) {
                val p = matrix[x][y]
                val adjusted = x + goalLine
                when {
                    adjusted > y -> win += p
                    adjusted == y -> draw += p
                    else -> lose += p
                }
            }
        }
        val out = LinkedHashMap<String, Double>()
        out["让胜"] = win
        out["让平"] = draw
        out["让负"] = lose
        return out
    }

    /**
     * 比分（CRS）：官方 31 选项 —— 胜 13（12 个具体比分 + 胜其他）、平 5（4 个 + 平其他）、负 13（12 个 + 负其他）。
     * 具体比分直接取矩阵格子；「其他」= 该胜负方向下未列入具体比分的其余格子之和
     * （如 4:4~9:9 全归「平其他」，6:0、3:4 等归「胜其他 / 负其他」）。
     * 文案用 "1:0"（无前导零、无空格），与 App 内比分显示一致。
     * 31 个选项把 100 个格子恰好划满，故 31 项和 = 矩阵总和 = 1。
     */
    private fun crs(matrix: Array<DoubleArray>): Map<String, Double> {
        var homeOther = 0.0
        var drawOther = 0.0
        var awayOther = 0.0
        for (x in matrix.indices) {
            for (y in matrix[x].indices) {
                val score = x to y
                if (HOME_SCORES.contains(score) || DRAW_SCORES.contains(score) || AWAY_SCORES.contains(score)) continue
                val p = matrix[x][y]
                when {
                    x > y -> homeOther += p
                    x == y -> drawOther += p
                    else -> awayOther += p
                }
            }
        }
        // 输出顺序对齐详情页：胜 → 平 → 负，最后三个「其他」
        val out = LinkedHashMap<String, Double>()
        HOME_SCORES.forEach { (x, y) -> out["$x:$y"] = matrix[x][y] }
        DRAW_SCORES.forEach { (x, y) -> out["$x:$y"] = matrix[x][y] }
        AWAY_SCORES.forEach { (x, y) -> out["$x:$y"] = matrix[x][y] }
        out["胜其他"] = homeOther
        out["平其他"] = drawOther
        out["负其他"] = awayOther
        return out
    }

    /**
     * 半全场胜平负（HAFU）：官方 9 选项「胜胜…负负」。
     * 半场进球近似 Pois(λ/2) 独立泊松（泊松过程可加性的简化假设，ρ 取 0），
     * 与 Poisson.halfFull 的既有假设保持一致；半场结果、全场结果各自取边缘概率后相乘。
     * 半场三项和 = 1、全场三项和 = 1，故九项和 = 1。
     */
    private fun hafu(lambdaHome: Double, lambdaAway: Double, full: Array<DoubleArray>): Map<String, Double> {
        val half = Poisson.wdl(Poisson.scoreMatrix(lambdaHome / 2, lambdaAway / 2, 0.0))
        val whole = Poisson.wdl(full)
        val halfP = doubleArrayOf(half.first, half.second, half.third)
        val fullP = doubleArrayOf(whole.first, whole.second, whole.third)
        val out = LinkedHashMap<String, Double>()
        for (h in 0..2) {
            for (f in 0..2) {
                out[HAFU_LABELS[h * 3 + f]] = halfP[h] * fullP[f]
            }
        }
        return out
    }

    /**
     * 总进球数（TTG）：官方 8 选项「0球…6球」+「7球+」。
     * 由 Poisson.totalGoals(matrix) 汇聚（out[k] = ΣP(x+y=k)），「7球+」= P(总进球 ≥ 7)，
     * 与详情页 ttgList 的标签写法（s0~s7 → 0球…7球+）一致。
     * 各档互斥完备地覆盖全部格子，故 8 项和 = 1。
     */
    private fun ttg(matrix: Array<DoubleArray>): Map<String, Double> {
        val dist = Poisson.totalGoals(matrix)
        val out = LinkedHashMap<String, Double>()
        for (k in 0..6) out["${k}球"] = dist[k]
        var sevenPlus = 0.0
        for (k in 7 until dist.size) sevenPlus += dist[k]
        out["7球+"] = sevenPlus
        return out
    }
}
