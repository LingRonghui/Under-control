package com.jingcai.predict.data.llm

import com.jingcai.predict.data.search.SearchHit
import com.jingcai.predict.data.search.TavilyWebSearch
import com.jingcai.predict.data.search.WebSearch
import java.io.IOException
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * 前瞻「赛前情报」生成器。
 *
 * 【数据来源说明（红线）】模型只使用两类**真实输入**：
 * 1. 官方前瞻接口的真实数据（积分榜/近况/交锋/伤停/射手/未来赛程）；
 * 2. **模型自己发起的联网检索**真实返回的结果（见 [generateWithModelSearch]）。
 * 拿不到检索结果时如实记录状态，并明确要求模型不得声称访问过网站；能拿到时只允许引用
 * 真实结果里出现的来源媒体，禁止编造来源媒体名/链接。
 */
object PreviewAnalyzer {

    /** 拼进 prompt 的检索结果条数上限（控制 prompt 长度） */
    private const val MAX_HITS = 5

    /** 单条检索摘要截断长度（控制 prompt 长度） */
    private const val MAX_SNIPPET_CHARS = 180

    /** 模型自主检索的**工具执行轮次**上限；用尽后不再声明工具，让模型用已有信息收尾 */
    private const val MAX_TOOL_ROUNDS = 3

    /** 声明的检索工具名（请求参数与执行回填必须同名） */
    private const val TOOL_NAME = "web_search"

    /** 单次检索条数上限（同时是工具参数 max_results 的上限） */
    private const val TOOL_HIT_COUNT = 5

    /**
     * 模型自主检索生成情报的结果。
     * - [mode]：`"model"` = 模型自主检索；`"app"` = 应用侧固定检索词检索一次；`""` = 未启用检索；
     * - [state]：`"ok"` / `"empty"` / `"no_call"` / `"failed:<原因>"`（口径见 [CombinedPrediction.searchState]）；
     * - [rounds]：模型自主检索的往返次数（Tavily 为实际执行的函数调用轮次；智谱内置检索命中时记 1）；
     * - [hits]：**真实**来源，随情报一起落盘。
     */
    data class Outcome(
        val text: String,
        val mode: String,
        val state: String,
        val rounds: Int,
        val hits: List<SearchHit>,
    )

    /** 情报的检索语境（决定 prompt 的联网规则怎么写） */
    private enum class SearchContext { MODEL_TOOL, PROVIDED_HITS, NONE }

    /**
     * 系统提示词。规则 3 随检索语境切换：
     * - [SearchContext.MODEL_TOOL]：模型可自主调用检索工具，但只准引用工具真实返回的来源；
     * - [SearchContext.PROVIDED_HITS]：允许引用用户提供的真实检索结果并注明媒体名；
     * - [SearchContext.NONE]：硬规则「禁止声称联网」。
     * 规则 4 为统一硬规则：禁止编造来源媒体名。
     */
    private fun systemPrompt(context: SearchContext): String {
        val netRule = when (context) {
            SearchContext.MODEL_TOOL ->
                "你可以调用 $TOOL_NAME 工具联网检索最新信息（伤停/首发/状态/新闻）：是否检索、" +
                    "检索什么词、检索几次由你自己决定；只能引用检索结果中真实出现的来源媒体与链接；" +
                    "检索没有返回结果、或你没有检索时，禁止声称访问过网站或检索过网络；"
            SearchContext.PROVIDED_HITS ->
                "可以引用用户提供的【网络检索结果（真实抓取）】中的信息，并注明来源媒体名；" +
                    "但不得引用该名单之外的任何报道，也不得声称访问过其它网站；"
            SearchContext.NONE ->
                "禁止声称你访问过网站、搜索过网络或引用外部报道——你没有联网能力；"
        }
        return """
你是竞彩足球赛前情报编辑。规则（必须严格遵守）：
1. 只能使用用户提供的数据，禁止编造任何事实、数字、球员状态或消息；
2. 禁止输出赔率数字，也禁止给出投注建议；
3. $netRule
4. 禁止编造来源媒体名，只能引用真实检索结果中出现的媒体与链接；
5. 输出 3~5 条要点，每行一条，以「·」开头，每条不超过 40 字；
6. 只输出要点本身，不要标题、不要 markdown、不要多余说明；
7. 数据缺失的维度不要提及，不要用推测填补。
""".trim()
    }

    /**
     * 组装用户提示词：官方前瞻数据 + （可选）真实检索结果 + 检索指引。
     */
    private fun userPrompt(lines: List<String>, hits: List<SearchHit>, context: SearchContext): String =
        buildString {
            appendLine("【官方前瞻数据】")
            lines.forEach { appendLine(it) }
            if (hits.isNotEmpty()) {
                appendLine()
                appendLine("【网络检索结果（真实抓取）】")
                WebSearch.promptLines(hits, MAX_HITS, MAX_SNIPPET_CHARS).forEach { appendLine(it) }
                appendLine("以上均为真实抓取的网络摘要，引用时请注明来源媒体名；名单之外的来源一律不得提及。")
            }
            if (context == SearchContext.MODEL_TOOL) {
                appendLine()
                appendLine(
                    "如官方前瞻数据不足以判断，可调用 $TOOL_NAME 工具检索最新信息" +
                        "（最多 $MAX_TOOL_ROUNDS 次），拿到结果后再给出情报要点。"
                )
            }
            appendLine()
            appendLine("请按规则输出赛前情报要点。")
        }.trim()

    /**
     * **模型自主检索**模式生成情报（新链路）：
     * 由模型自己决定是否检索、检索什么词、检索几次——
     * Tavily 走标准函数调用（应用真实执行检索后回填），智谱走官方内置检索工具。
     *
     * 【两条链路的官方依据（2026-09 核实）】
     * - 智谱：`tools: [{"type":"web_search","web_search":{"enable":true,"search_result":true,...}}]`；
     *   FAQ 明确「使用 Web_Search 工具时 `enable` 参数默认 false，必须显式传 true 才开启联网」，
     *   且「`search_result` 传 true 才能看到返回的搜索来源」；
     *   来源在**响应顶层 `web_search[]`** 返回（见 [com.jingcai.predict.data.search.ZhipuWebSearch.parseToolHits]）。
     *   **不写死 `search_query`**：官方示例里检索词来自 messages，由模型自行决定。
     * - Tavily：`tools: [{"type":"function",...}]` 标准函数调用循环，执行器真实调用
     *   [TavilyWebSearch] 并把结果 JSON 回填为 `role=tool` 消息。
     * - 两者**不可混用**（智谱 FAQ：函数调用、知识库检索、网络搜索三者互斥，优先级 函数调用 > 网络搜索）。
     *
     * 状态口径：[Outcome.state] 只在结果真实可得时记为 `"ok"`；模型未发起检索记 `"no_call"`；
     * 发起过检索但 0 条记 `"empty"`；检索本身失败记 `"failed:<原始原因>"`（含 HTTP 状态码与响应体片段）。
     */
    suspend fun generateWithModelSearch(cfg: LlmConfig, lines: List<String>): Result<Outcome> {
        if (!cfg.ready) return Result.failure(IOException("模型未配置"))
        if (lines.isEmpty()) return Result.failure(IOException("无可用前瞻数据"))
        val provider = cfg.searchProvider.trim().lowercase(Locale.US)
        val tools = when (provider) {
            LlmConfig.SEARCH_ZHIPU -> zhipuTool()
            LlmConfig.SEARCH_TAVILY -> tavilyTool()
            else -> return Result.failure(IOException("未启用联网检索"))
        }

        // Tavily 链路：收集真实结果与失败原因（用于状态口径）
        val toolHits = mutableListOf<SearchHit>()
        var toolError: String? = null
        val executor = if (provider == LlmConfig.SEARCH_TAVILY) {
            LlmClient.ToolExecutor { name, argumentsJson ->
                val reply = executeTavily(cfg, name, argumentsJson, toolHits)
                if (reply.error != null) toolError = reply.error
                reply.content
            }
        } else {
            null
        }

        val chat = LlmClient.chatWithTools(
            cfg = cfg,
            system = systemPrompt(SearchContext.MODEL_TOOL),
            user = userPrompt(lines, emptyList(), SearchContext.MODEL_TOOL),
            tools = tools,
            // 智谱内置检索由服务端自行完成多轮检索，本地只发一次；Tavily 由本地循环执行工具
            maxRounds = if (executor != null) MAX_TOOL_ROUNDS else 1,
            executor = executor,
        ).getOrElse { return Result.failure(it) }

        val hits = (chat.hits + toolHits)
            .distinctBy { it.url.ifBlank { it.title } }
            .take(MAX_HITS)
        // 取到本地 val 再判断：toolError 在 lambda 里被赋值，无法参与智能转换
        val toolFailure = toolError
        val state = when {
            hits.isNotEmpty() -> "ok"
            toolFailure != null -> "failed:$toolFailure"
            // 函数调用确实执行过（本地轮次 > 0），但服务商返回 0 条
            chat.rounds > 0 -> "empty"
            // 模型未发起检索。智谱内置检索若未返回 web_search 字段，无法区分「未检索」与
            // 「检索到 0 条」，故统一按「未发起检索」如实记录，不臆断为 empty
            else -> "no_call"
        }
        val rounds = when {
            chat.rounds > 0 -> chat.rounds
            // 智谱内置检索命中：服务端已完成检索（本地往返 1 次）
            hits.isNotEmpty() -> 1
            else -> 0
        }
        return Result.success(Outcome(chat.content, "model", state, rounds, hits))
    }

    /**
     * 生成情报（**不声明工具**）：把 [searchHits]（可为空）作为真实检索结果拼进 prompt。
     * 用于「未启用联网检索」以及「模型自主检索不可用 → 应用侧检索一次」的降级路径。
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
        val context = if (hits.isNotEmpty()) SearchContext.PROVIDED_HITS else SearchContext.NONE
        return LlmClient.chat(cfg, systemPrompt(context), userPrompt(lines, hits, context))
            .map { it.trim() }
            .mapCatching { text ->
                if (text.isBlank()) throw IOException("模型返回为空")
                text
            }
    }

    /* ================= 工具声明与执行 ================= */

    /**
     * 智谱官方内置检索工具声明。
     *
     * 【官方文档核实（2026-09）】
     * 文档：https://docs.bigmodel.cn/cn/guide/tools/web-search （对话中的网络搜索）
     * 字段：`type=web_search` + `web_search{ enable, search_engine, search_result,
     * search_prompt, count, search_domain_filter, search_recency_filter, content_size }`。
     * 本实现只传必要项：
     * - `enable=true`（FAQ 明确默认 false，不传就不会联网）；
     * - `search_result=true`（FAQ 明确开启后才会返回检索来源）；
     * - `search_engine="search_pro"`（官方示例取值）、`count`、`search_recency_filter="noLimit"`、
     *   `content_size="medium"`（摘要长度适中，避免 prompt 过长）。
     * **不传 `search_query`**：官方示例中检索词来自 messages，由模型自行决定（这正是「模型自主检索」）。
     */
    private fun zhipuTool(): JSONArray = JSONArray().put(
        JSONObject().apply {
            put("type", "web_search")
            put("web_search", JSONObject().apply {
                put("enable", true)
                put("search_engine", "search_pro")
                put("search_result", true)
                put("count", TOOL_HIT_COUNT)
                put("search_recency_filter", "noLimit")
                put("content_size", "medium")
                put(
                    "search_prompt",
                    "请从网络搜索{search_result}中筛选与比赛双方伤停、首发、状态与近期新闻相关的信息，" +
                        "标注来源媒体与发布日期；没有相关信息的方面不要提及。"
                )
            })
        }
    )

    /** Tavily 链路的标准函数工具声明（参数名与官方函数调用约定一致） */
    private fun tavilyTool(): JSONArray = JSONArray().put(
        JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", TOOL_NAME)
                put("description", "联网检索最新信息（赛前伤停/首发/状态/新闻）")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "检索关键词，建议含球队名与关注点，如「曼城 伤停 首发」")
                        })
                        put("max_results", JSONObject().apply {
                            put("type", "integer")
                            put("description", "返回条数，1~$TOOL_HIT_COUNT")
                        })
                    })
                    put("required", JSONArray().put("query"))
                })
            })
        }
    )

    /** 一次工具调用的回填内容与失败原因 */
    private class ToolReply(val content: String, val error: String?)

    /**
     * 执行模型发起的 `web_search` 函数调用：解析参数 → 真实调用 Tavily → 回填**真实**结果。
     * - 参数非法 / 未知函数名 / 检索失败：回填 `{"error": "<原始原因>"}`，绝不编造结果；
     * - 检索成功但 0 条：回填空的 results（模型据此可知「没搜到」，不得虚构）。
     */
    private suspend fun executeTavily(
        cfg: LlmConfig,
        name: String,
        argumentsJson: String,
        collect: MutableList<SearchHit>,
    ): ToolReply {
        if (name != TOOL_NAME) {
            return ToolReply(JSONObject().put("error", "未声明的函数：$name").toString(), null)
        }
        val args = runCatching { JSONObject(argumentsJson.ifBlank { "{}" }) }.getOrNull()
        val query = args?.optString("query", "")?.trim().orEmpty()
        if (query.isEmpty()) {
            return ToolReply(JSONObject().put("error", "检索关键词为空").toString(), "检索关键词为空")
        }
        val count = (args?.optInt("max_results", TOOL_HIT_COUNT) ?: TOOL_HIT_COUNT)
            .coerceIn(1, TOOL_HIT_COUNT)
        return TavilyWebSearch.search(cfg, query, count).fold(
            onSuccess = { hits ->
                collect += hits
                ToolReply(WebSearch.toolContent(query, hits), null)
            },
            onFailure = { e ->
                val why = e.message ?: e.javaClass.simpleName
                ToolReply(JSONObject().put("error", "检索失败：$why").toString(), why)
            },
        )
    }
}
