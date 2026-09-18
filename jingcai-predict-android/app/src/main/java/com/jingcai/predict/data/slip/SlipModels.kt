package com.jingcai.predict.data.slip

/**
 * 竞彩足球玩法。maxParlay = 官方允许的最大过关关数，
 * 混合过关时按「木桶原则」取所选玩法中的最小值。
 */
enum class SlipPlay(val code: String, val label: String, val maxParlay: Int) {
    HAD("HAD", "胜平负", 8),
    HHAD("HHAD", "让球胜平负", 8),
    TTG("TTG", "总进球数", 6),
    CRS("CRS", "比分", 4),
    HAFU("HAFU", "半全场", 4);

    companion object {
        fun of(code: String): SlipPlay? = entries.firstOrNull { it.code == code }
    }
}

/** 方案单中的一条选择（同一场比赛的同一玩法同一选项只允许存在一条） */
data class SlipSelection(
    val matchId: String,
    val matchNum: String,          // 周五001
    val league: String,
    val home: String,
    val away: String,
    val kickoff: String,           // "MM-dd HH:mm"
    val play: String,              // SlipPlay.code
    val playLabel: String,
    val optionCode: String,        // 官方选项键（h/d/a、s01s01、hh、s2 …）
    val optionLabel: String,       // 展示文案（主胜 / 1:1 / 胜胜 / 2球）
    val odds: Double,
    val goalLine: String = "",     // 让球盘口（仅 HHAD）
    val singleAllowed: Boolean = false,   // 官方标记该场该玩法是否支持单关
) {
    /** 唯一键：同一场比赛 + 玩法 + 选项 */
    val key: String get() = "$matchId|$play|$optionCode"
}

/** 结算状态 */
object SlipStatus {
    const val PENDING = 0    // 待结算（比赛未结束）
    const val WON = 1        // 已中奖
    const val LOST = -1      // 未中奖
}

/** 已保存方案中的一条选择（含命中判定结果） */
data class SlipLeg(
    val matchId: String,
    val matchNum: String,
    val league: String,
    val home: String,
    val away: String,
    val play: String,
    val playLabel: String,
    val optionCode: String,
    val optionLabel: String,
    val odds: Double,
    val goalLine: String = "",
    /** null = 待结算；true = 命中；false = 未中 */
    val hit: Boolean? = null,
)

/** 已保存的方案 */
data class SavedSlip(
    val id: String,
    val createdAt: Long,
    val legs: List<SlipLeg>,
    val parlay: List<Int>,       // 关次集合，如 [2,3]；单关为 [1]
    val multiple: Int,
    val stake: Double,           // 总金额（元）
    val noteCount: Long,         // 注数
    val maxPrize: Double,        // 全部命中时的奖金（已含倍数）
    val status: Int = SlipStatus.PENDING,
    val prize: Double = 0.0,     // 结算后实际中奖金额
    val settledAt: Long? = null,
)

/** 方案计算结果（用于计算器展示） */
data class SlipCalc(
    val noteCount: Long = 0,
    val stake: Double = 0.0,
    val minNotePrize: Double = 0.0,   // 单注最低奖金
    val maxNotePrize: Double = 0.0,   // 单注最高奖金
    val totalIfAllHit: Double = 0.0,  // 全部命中合计（含倍数）
    val capPerNote: Double = 0.0,     // 单注封顶
    val estimated: Boolean = false,   // 注数过多时为理论值（未逐注按封顶折算）
    val overStakeLimit: Boolean = false,
    val maxParlay: Int = 8,
    val validParlay: List<Int> = emptyList(),
    val singleAllowed: Boolean = false,
)
