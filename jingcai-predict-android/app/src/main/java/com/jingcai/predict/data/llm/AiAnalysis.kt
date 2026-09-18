package com.jingcai.predict.data.llm

/**
 * 供大模型选型的**真实候选项**（赔率取自官方接口，概率由本地引擎计算）。
 * 大模型只能在这些候选里选，选到候选之外的选项一律丢弃。
 */
data class CandidateOption(
    val play: String,          // HAD / HHAD / CRS / HAFU / TTG
    val playLabel: String,     // 胜平负 / 让球胜平负 / 比分 / 半全场胜平负 / 总进球数
    val option: String,        // 展示文案：主胜 / 1:1 / 胜胜 / 2球
    val odds: Double,          // 官方真实赔率
    val probability: Double,   // 本地引擎算出的概率（可复算；无则 0）
    val ev: Double,            // 期望值 = 概率 × 赔率 − 1（可复算）
)

/** 大模型对某个玩法的打分（主观评估，界面必须标注来源，不得当作概率） */
data class AiTargetScore(
    val play: String,
    val playLabel: String,
    val pick: String,          // 大模型认可的主选（须命中候选，否则该项被丢弃）
    val score: Int,            // 0~100
    val alternative: String = "",   // 备选（须命中候选）
    val reason: String = "",
)

/** 大模型给出的建议（赔率不采信，一律用本地真实赔率回填） */
data class AiAdvice(
    val kind: String,          // VALUE（最具价值）/ SAFE（最稳健）
    val play: String,
    val playLabel: String,
    val option: String,
    val reason: String,
    val aiScore: Int,
    /** 本地回填的真实赔率（大模型返回的赔率一律忽略，0 表示候选里没有该选项） */
    val odds: Double = 0.0,
    /** 本地回填的引擎概率与期望值（可复算） */
    val probability: Double = 0.0,
    val ev: Double = 0.0,
    /** 该选项是否真实存在于候选中（false 表示大模型给的是虚构选项，界面需明确标注） */
    val grounded: Boolean = false,
)

/**
 * 大模型输出的结构化分析结果。
 *
 * 【红线约束】
 * 1. 所有赔率由本地真实数据回填，大模型返回的赔率被丢弃；
 * 2. 选项必须存在于候选中，否则丢弃（scores/advice）或标记 grounded=false；
 * 3. AI 打分标注为「主观评估」，与「引擎概率」并列展示，不得混为一谈。
 */
data class AiAnalysis(
    val model: String,
    val scores: List<AiTargetScore> = emptyList(),
    val bestValue: AiAdvice? = null,
    val safest: AiAdvice? = null,
    /** 思路分析分节：市场面 / 基本面 / 风险点 / 结论 */
    val sections: List<Pair<String, String>> = emptyList(),
) {
    val ok: Boolean get() = sections.isNotEmpty() || scores.isNotEmpty() || bestValue != null || safest != null
}

/** 玩法代码 → 中文名 */
fun playLabelOf(play: String): String = when (play) {
    "HAD" -> "胜平负"
    "HHAD" -> "让球胜平负"
    "CRS" -> "比分"
    "HAFU" -> "半全场胜平负"
    "TTG" -> "总进球数"
    else -> play
}
