package com.jingcai.predict.data.llm

import com.jingcai.predict.data.search.SearchHit
import java.io.IOException

/**
 * 前瞻「赛前情报」生成器。
 *
 * 【数据来源说明（红线）】模型只使用两类**真实输入**：
 * 1. 官方前瞻接口的真实数据（积分榜/近况/交锋/伤停/射手/未来赛程）；
 * 2. 用户开启「联网检索」后由检索服务商**真实返回**的网络结果（[searchHits]）。
 * 没有检索结果时，prompt 明确要求模型**不得声称访问过网站或检索过网络**；
 * 有检索结果时，只允许引用给定名单内的来源并注明媒体名，禁止编造来源媒体名。
 */
object PreviewAnalyzer {

    /** 拼进 prompt 的检索结果条数上限（控制 prompt 长度） */
    private const val MAX_HITS = 5

    /** 单条检索摘要截断长度（控制 prompt 长度） */
    private const val MAX_SNIPPET_CHARS = 180

    /**
     * 系统提示词。规则 3 随「是否有真实检索结果」切换：
     * - 有：允许引用【网络检索结果（真实抓取）】并注明来源媒体名；
     * - 无：保留原有的「禁止声称联网」硬规则。
     * 规则 4 为统一硬规则：禁止编造来源媒体名。
     */
    private fun systemPrompt(hasSearch: Boolean): String {
        val netRule = if (hasSearch) {
            "可以引用用户提供的【网络检索结果（真实抓取）】中的信息，并注明来源媒体名；" +
                "但不得引用该名单之外的任何报道，也不得声称访问过其它网站；"
        } else {
            "禁止声称你访问过网站、搜索过网络或引用外部报道——你没有联网能力；"
        }
        return """
你是竞彩足球赛前情报编辑。规则（必须严格遵守）：
1. 只能使用用户提供的数据，禁止编造任何事实、数字、球员状态或消息；
2. 禁止输出赔率数字，也禁止给出投注建议；
3. $netRule
4. 禁止编造来源媒体名，只能引用【网络检索结果（真实抓取）】中出现的媒体；
5. 输出 3~5 条要点，每行一条，以「·」开头，每条不超过 40 字；
6. 只输出要点本身，不要标题、不要 markdown、不要多余说明；
7. 数据缺失的维度不要提及，不要用推测填补。
""".trim()
    }

    /**
     * @param lines 官方前瞻数据（逐行文本，由 PredictionPipeline 组装）
     * @param searchHits 真实检索结果（未启用检索或检索失败时为空列表）
     * @return 成功返回要点文本；失败返回真实原因（HTTP 状态/解析失败等）
     */
    suspend fun generate(
        cfg: LlmConfig,
        lines: List<String>,
        searchHits: List<SearchHit> = emptyList(),
    ): Result<String> {
        if (!cfg.ready) return Result.failure(IOException("模型未配置"))
        if (lines.isEmpty()) return Result.failure(IOException("无可用前瞻数据"))
        val hits = searchHits.take(MAX_HITS)
        val user = buildString {
            appendLine("【官方前瞻数据】")
            lines.forEach { appendLine(it) }
            if (hits.isNotEmpty()) {
                appendLine()
                appendLine("【网络检索结果（真实抓取）】")
                hits.forEachIndexed { index, h ->
                    val source = h.source.ifBlank { "来源站点未标注" }
                    val snippet = h.snippet.replace(Regex("\\s+"), " ").trim().take(MAX_SNIPPET_CHARS)
                    appendLine("${index + 1}. ${h.title}（$source）${if (snippet.isNotEmpty()) "：$snippet" else ""}")
                }
                appendLine("以上均为真实抓取的网络摘要，引用时请注明来源媒体名；名单之外的来源一律不得提及。")
            }
            appendLine()
            appendLine("请按规则输出赛前情报要点。")
        }.trim()
        return LlmClient.chat(cfg, systemPrompt(hits.isNotEmpty()), user).map { it.trim() }
            .mapCatching { text ->
                if (text.isBlank()) throw IOException("模型返回为空")
                text
            }
    }
}
