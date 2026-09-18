package com.jingcai.predict.data.llm

import com.jingcai.predict.data.search.SearchHit

/**
 * 综合结论（模型判断为核心 + 概率计算为基础，两者构成一个整体）：
 * - 选项由模型依据以下数据做出判断（模型为核心）
 * - 概率、赔率、置信度口径全部来自本地计算与官方赔率（可复算）
 * - 综合置信度按固定规则计算，规则公开写在界面说明里，绝不虚高
 */
data class CombinedPick(
    val play: String,              // HAD / HHAD / CRS / HAFU / TTG
    val playLabel: String,
    val option: String,            // 综合结论的选项（模型判断优先，无模型结果时取概率主选）
    val odds: Double,              // 官方真实赔率（0 表示未开售）
    val probability: Double,       // 模型概率（计算依据）
    val aiScore: Int?,             // 模型打分（主观评估）
    val confidence: Int,           // 综合置信度（0~100，可复算）
    val alt: String = "",          // 综合备选（次高概率）
    val altOdds: Double = 0.0,
    val reason: String = "",       // 模型给出的理由（无模型结果时为空）
    val divergence: Boolean = false,  // 模型判断与概率主选是否分歧
    val hit: Boolean? = null,      // 命中判定（仅已完赛有值）
    /** 规则说明（如最具价值的筛选口径与是否退档），界面原样展示，保证可解释 */
    val note: String = "",
)

/** 一场比赛的完整综合预测（持久化单位，避免二次预测） */
data class CombinedPrediction(
    val matchId: String,
    val matchNum: String,
    val league: String,
    val home: String,
    val away: String,
    val kickoff: String,           // "MM-dd HH:mm"
    val statusText: String,        // 未开赛 / 进行中 / 已完赛（生成时的状态，用于展示）
    val picks: List<CombinedPick>,
    val bestValue: CombinedPick?,  // 最具价值（期望值最高，本地可复算）
    val safest: CombinedPick?,     // 最稳健（概率最高）
    val sections: List<Pair<String, String>>,  // 思路分析分节
    val preview: String,           // 前瞻「赛前情报」（模型依据官方前瞻数据与真实检索结果整理）
    val engineConf: Int,           // 整体置信度
    val signalNote: String,        // 采用的信号组合
    val dataComplete: Double,      // 数据完整度
    val key: String,               // 真实数据要点
    val model: String,             // 生成所用模型名；空字符串表示无模型结果（纯本地计算）
    val createdAt: Long,
    val updatedAt: Long,
    val reviewCount: Int,          // 模型复核次数（首次为 0）
    /**
     * 联网检索状态（如实记录，供界面说明情报来源）：
     * - `""` = 本次未启用联网检索；
     * - `"ok"` = 检索命中，情报可引用 [searchSources] 中的来源；
     * - `"empty"` = 检索成功但返回 0 条；
     * - `"failed:<原因>"` = 检索失败（原因含 HTTP 状态码与响应体片段等原始信息）。
     */
    val searchState: String = "",
    /** 检索命中的**真实**来源（最多 5 条），随情报一起持久化，供详情页展示并可点击打开 */
    val searchSources: List<SearchHit> = emptyList(),
    /** 计算逻辑版本：与当前版本不一致的历史快照会被丢弃并重算（避免旧口径误导） */
    val logicVersion: Int = LOGIC_VERSION,
) {
    companion object {
        /** 每次修改综合结论/建议口径时 +1，旧缓存自动失效重算 */
        const val LOGIC_VERSION = 3
    }

    /** 排名依据：综合置信度（胜平负项；缺失时退回整体置信度） */
    val rankConfidence: Int
        get() = picks.firstOrNull { it.play == SlipPlayCodes.HAD }?.confidence ?: engineConf
}

/** 玩法代码常量（避免各模块重复写字符串） */
object SlipPlayCodes {
    const val HAD = "HAD"
    const val HHAD = "HHAD"
    const val CRS = "CRS"
    const val HAFU = "HAFU"
    const val TTG = "TTG"
}

/**
 * 「最具价值」筛选规则（公开、可复算，界面会原样展示）：
 * 1. **稳健门槛**：只在「模型概率 ≥ 45%」且「期望值 > 0」的候选中挑选 —— 排除低概率高赔的投机项；
 * 2. 在满足门槛的候选中，取 `概率 × 期望值` 最高者（兼顾把握与盈利空间）；
 * 3. 若本场没有同时满足门槛的选项，退而取「期望值 > 0 中概率最高者」，并在卡片上**明确标注为退档结果**；
 * 4. 若连正期望选项都没有，则如实提示「本场无正期望选项，不建议作为价值方向」。
 */
object ValueRule {
    const val MIN_PROB = 0.45
    const val MIN_EV = 0.0

    const val NOTE = "最具价值筛选：模型概率 ≥ 45% 且 期望 > 0 中，取「概率 × 期望」最高者（排除低概率高赔投机项）"

    const val NOTE_FALLBACK = "本场无「概率 ≥ 45% 且正期望」的选项，此处退档为「正期望中概率最高者」，把握偏低请注意风险"

    const val NOTE_NONE = "本场全部选项期望值均非正（无盈利空间），此处仅列出概率最高者，不建议作为价值方向"

    const val NOTE_SAFE = "最稳健口径：不看赔率，取模型概率最高的选项"
}

/**
 * 综合置信度规则（公开、可复算，界面会原样展示给用户）：
 * 综合置信度 = 模型概率 × 100 × 系数，系数取值：
 * - 模型判断与概率主选一致 → 1.00（不额外加成，避免虚高）
 * - 模型判断与概率主选分歧 → 0.85（分歧折扣）
 * - 无模型结果（纯本地计算） → 1.00
 * 上限 95（不给满分），下限 5。
 */
object CombineRule {
    const val DIVERGENCE_DISCOUNT = 0.85
    const val NO_AI_FACTOR = 1.0
    const val MIN_CONF = 5
    const val MAX_CONF = 95

    const val NOTE = "综合置信度 = 模型概率 × 分歧系数（判断与模型方向一致 1.00，分歧 0.85），上限 95"

    fun confidence(engineProbability: Double, divergence: Boolean, hasAi: Boolean): Int {
        val factor = when {
            !hasAi -> NO_AI_FACTOR
            divergence -> DIVERGENCE_DISCOUNT
            else -> 1.0
        }
        return (engineProbability * 100.0 * factor)
            .toInt()
            .coerceIn(MIN_CONF, MAX_CONF)
    }
}
