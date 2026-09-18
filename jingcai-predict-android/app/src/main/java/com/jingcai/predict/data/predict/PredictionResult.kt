package com.jingcai.predict.data.predict

/**
 * 单场比赛的预测输出。
 *
 * 【重要】这是本架构对外输出的稳定结构化契约：
 * 后续迭代将直接序列化本对象喂给 AI 大模型（大模型以本架构的真实概率/特征为基点做分析），
 * 因此字段保持稳定，不随 UI 变动。
 */
data class PredictionResult(
    val matchId: String,
    val num: String,
    val league: String,
    val home: String,
    val away: String,
    val kickoff: String,            // "HH:mm"
    val homeProb: Double,           // 校准后的主胜概率
    val drawProb: Double,           // 校准后的平局概率
    val awayProb: Double,           // 校准后的客胜概率
    val wdlPick: String,            // 主胜 / 平局 / 客胜
    val hdpPick: String,            // 让球预测：让胜 / 让平 / 让负；数据缺失为 "--"
    val hdpLine: String,            // 让球盘口；缺失为空
    val scorePick: String,          // 最可能比分 "x:y"；统计信号缺失为 "--"
    val hfPick: String,             // 半全场（竞彩官方写法），如 "胜胜" / "平胜"；统计信号缺失为 "--"
    val totalPick: String,          // 总进球 "k球" / "7球+"；统计信号缺失为 "--"
    val conf: Int,                  // 置信度 35~98（诚实计算，不虚高）
    val dataComplete: Double,       // 数据完整度 0~1（可用信号数 / 3）
    val signalNote: String,         // 本次预测采用的信号组合，如 "市场+统计+官方"
    val key: String,                // 要点：由真实数据拼装，缺失则不生成
    val homeOdds: Double,           // 原始主胜赔率（0 表示缺失）
    val drawOdds: Double,
    val awayOdds: Double,
)
