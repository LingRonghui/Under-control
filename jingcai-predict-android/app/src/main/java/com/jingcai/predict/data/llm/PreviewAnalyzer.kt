package com.jingcai.predict.data.llm

import java.io.IOException

/**
 * 前瞻「赛前情报」生成器。
 *
 * 【数据来源说明（红线）】本实现只使用**官方前瞻接口的真实数据**（积分榜/近况/交锋/伤停/射手/未来赛程）
 * 作为 AI 的输入；当前不具备联网抓取第三方站点的能力，因此 prompt 中明确要求模型
 * **不得声称访问过网站或检索过网络**，避免把推测包装成"网络情报"。
 * 若后续接入带联网能力的大模型或搜索 API，只需在 [lines] 中追加真实抓取到的第三方信息即可。
 */
object PreviewAnalyzer {

    private const val SYSTEM = """
你是竞彩足球赛前情报编辑。规则（必须严格遵守）：
1. 只能使用用户提供的数据，禁止编造任何事实、数字、球员状态或消息；
2. 禁止输出赔率数字，也禁止给出投注建议；
3. 禁止声称你访问过网站、搜索过网络或引用外部报道——你没有联网能力；
4. 输出 3~5 条要点，每行一条，以「·」开头，每条不超过 40 字；
5. 只输出要点本身，不要标题、不要 markdown、不要多余说明；
6. 数据缺失的维度不要提及，不要用推测填补。
"""

    /**
     * @param lines 官方前瞻数据（逐行文本，由 PredictionPipeline 组装）
     * @return 成功返回要点文本；失败返回真实原因（HTTP 状态/解析失败等）
     */
    suspend fun generate(cfg: LlmConfig, lines: List<String>): Result<String> {
        if (!cfg.ready) return Result.failure(IOException("大模型未配置"))
        if (lines.isEmpty()) return Result.failure(IOException("无可用前瞻数据"))
        val user = buildString {
            appendLine("【官方前瞻数据】")
            lines.forEach { appendLine(it) }
            appendLine()
            appendLine("请按规则输出赛前情报要点。")
        }.trim()
        return LlmClient.chat(cfg, SYSTEM.trim(), user).map { it.trim() }
            .mapCatching { text ->
                if (text.isBlank()) throw IOException("模型返回为空")
                text
            }
    }
}
