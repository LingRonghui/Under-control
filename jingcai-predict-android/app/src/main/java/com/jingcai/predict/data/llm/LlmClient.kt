package com.jingcai.predict.data.llm

import com.jingcai.predict.data.search.SearchHit
import com.jingcai.predict.data.search.ZhipuWebSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI 兼容大模型客户端（DeepSeek / 智谱 GLM / Kimi / OpenAI / 本地 Ollama、vLLM 等通用协议）。
 *
 * 【设计约束】
 * 1. 只使用 OpenAI 兼容的两个标准端点：GET {base}/models、POST {base}/chat/completions（URL 由 LlmConfig 拼接）；
 * 2. 不发送 response_format 字段（各厂商兼容性不一致），JSON 输出改由 prompt 约束；
 * 3. 错误一律如实返回 Result.failure（含 HTTP 状态码与响应体片段），**绝不吞掉错误、绝不伪造内容**。
 *
 * 【工具调用】[chatWithTools] 只在协议层工作：声明工具、解析 `tool_calls`、把执行结果回填成
 * `tool` 消息循环下去；具体检索由调用方通过 [ToolExecutor] 真实执行（本类不关心检索实现）。
 */
object LlmClient {

    /** 工具模式下单轮模型往返的超时（毫秒）：一轮 = 一次 `/chat/completions` 请求 */
    private const val TOOL_ROUND_TIMEOUT_MS = 30_000L

    /**
     * 工具执行器：由调用方实现（真实执行模型发起的函数调用）。
     */
    fun interface ToolExecutor {
        /**
         * @param name 模型请求的函数名（与请求里声明的 `tools[].function.name` 一致）
         * @param argumentsJson 模型给出的参数 JSON 字符串（可能为空串 / 非合法 JSON）
         * @return 回填进 `{"role":"tool"}` 消息的内容：真实结果，或 `{"error":"<原始原因>"}`
         */
        suspend fun execute(name: String, argumentsJson: String): String
    }

    /** 带工具一轮（或多轮）对话的产物 */
    data class ToolChat(
        val content: String,
        /** 实际执行并把结果回填给模型的工具轮次（模型未调用工具时为 0） */
        val rounds: Int,
        /** 响应中**真实返回**的检索来源（智谱内置 `web_search` 工具在响应顶层 `web_search[]` 返回） */
        val hits: List<SearchHit>,
    )


    /**
     * 专用 HTTP 客户端：大模型响应慢，读超时放宽到 90s。
     * 不改动全局 HttpClient（其读超时仅 12s，会把正常的大模型长响应误判为超时）。
     */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * 拉取服务端可用模型列表：GET {base}/models → data[].id。
     * @return 去空、去重、按字母序排序后的模型名列表；请求失败返回 Result.failure（原因原样返回）。
     */
    suspend fun listModels(cfg: LlmConfig): Result<List<String>> = withContext(Dispatchers.IO) {
        safeCall {
            requireBase(cfg)
            val request = Request.Builder()
                .url(cfg.modelsUrl)
                .get()
                .header("Authorization", "Bearer ${cfg.apiKey}")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw httpError(resp.code, body)
                val data = JSONObject(body).optJSONArray("data")
                    ?: throw IOException("模型列表响应缺少 data 字段：${body.trim().take(200)}")
                val ids = LinkedHashSet<String>()
                for (i in 0 until data.length()) {
                    val item = data.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isNotEmpty()) ids.add(id)
                }
                ids.sorted()
            }
        }
    }

    /**
     * 连通性测试：优先用 GET {base}/models 校验地址与 Key，并核对配置的模型名是否在列表中；
     * 部分自建兼容服务不实现模型列表接口，此时退回一次最小对话请求验证对话链路。
     * @return 成功时返回一句可直接展示给用户的中文说明；失败时返回真实的失败原因。
     */
    suspend fun testConnection(cfg: LlmConfig): Result<String> = withContext(Dispatchers.IO) {
        if (cfg.baseUrl.isBlank() || cfg.apiKey.isBlank()) {
            return@withContext Result.failure(IOException("配置不完整：请先填写服务地址与 API Key"))
        }
        val models = listModels(cfg)
        if (models.isSuccess) {
            val list = models.getOrDefault(emptyList())
            val model = cfg.model.trim()
            val msg = when {
                list.isEmpty() -> "连接成功：服务端未返回任何模型，请手动核对模型名"
                model.isBlank() -> "连接成功：鉴权通过，服务端共 ${list.size} 个模型（尚未填写模型名）"
                list.any { it.equals(model, ignoreCase = true) } ->
                    "连接成功：模型 $model 可用（服务端共 ${list.size} 个模型）"
                else ->
                    "连接成功：鉴权通过，但服务端模型列表中没有「$model」（共 ${list.size} 个），请核对模型名"
            }
            return@withContext Result.success(msg)
        }
        val modelsWhy = models.exceptionOrNull()?.message ?: "未知原因"
        val ping = chat(cfg, "你是连通性测试助手。", "只回复两个字：可用")
        if (ping.isSuccess) {
            return@withContext Result.success("连接成功：模型 ${cfg.model} 对话正常（模型列表接口不可用：$modelsWhy）")
        }
        val chatWhy = ping.exceptionOrNull()?.message ?: "未知原因"
        Result.failure(IOException("连接失败：模型列表接口 $modelsWhy；对话接口 $chatWhy"))
    }

    /**
     * 对话补全：POST {base}/chat/completions，取 choices[0].message.content。
     * @return 模型返回的正文（已 trim）；为空或请求失败时返回 Result.failure。
     */
    suspend fun chat(cfg: LlmConfig, system: String, user: String): Result<String> =
        chatWithTools(cfg, system, user).map { it.content }

    /**
     * 带工具声明的对话补全（支持标准的函数调用 tool calling 循环）。
     *
     * 【协议依据（2026-09 已核实）】
     * - 请求：`tools: [...]` + `tool_choice: "auto"`（智谱文档：`tool_choice` 默认且**仅支持** auto）。
     * - `tools` 支持函数工具 `{"type":"function","function":{...}}` 与厂商内置检索工具
     *   （智谱 `{"type":"web_search","web_search":{...}}`）。**两者不可同时声明**：
     *   智谱 FAQ 明确「函数调用、知识库检索、网络搜索 3 个功能互斥，优先级：函数调用 > 知识库检索 > 网络搜索」
     *   （https://docs.bigmodel.cn/cn/faq/api-issues），故调用方必须二选一。
     * - 响应：`choices[0].message.tool_calls[]`，每项含 `id` / `function.name` /
     *   `function.arguments`（JSON 字符串）；执行后以
     *   `{"role":"tool","tool_call_id":"...","content":"..."}` 追加，并把含 `tool_calls` 的
     *   assistant 消息**原样**追加回 messages（依据官方「工具调用」示例：
     *   https://docs.bigmodel.cn/cn/guide/capabilities/function-calling）。
     * - 内置检索工具的来源在响应**顶层 `web_search[]`** 返回（依据对话补全接口文档）。
     *
     * 【超时】工具模式下每一轮模型往返的上限为 [TOOL_ROUND_TIMEOUT_MS]（30s），超时即失败并如实返回原因；
     * 「检索 + 生成」的**整段**上限由调用方另行约束（本方法不做全局超时）。
     *
     * @param tools 工具声明；`null` = 不声明工具（等价于普通对话，此时 [maxRounds] / [executor] 无效）
     * @param maxRounds 工具**执行**轮次上限；`<= 0` 表示只发一次请求（检索由服务端自行多轮完成，如智谱内置检索）。
     *   轮次用尽后请求不再声明工具，让模型用已有信息收尾。
     * @param executor 工具执行器；为 null 时不会执行任何工具调用（只取模型正文）
     */
    suspend fun chatWithTools(
        cfg: LlmConfig,
        system: String,
        user: String,
        tools: JSONArray? = null,
        maxRounds: Int = 0,
        executor: ToolExecutor? = null,
    ): Result<ToolChat> = withContext(Dispatchers.IO) {
        safeCall {
            requireBase(cfg)
            if (cfg.model.isBlank()) throw IOException("未配置模型名（model）")

            val messages = JSONArray().apply {
                put(msgOf("system", system))
                put(msgOf("user", user))
            }
            // 真实检索来源去重（键 = 链接，缺失时用标题）
            val hits = LinkedHashMap<String, SearchHit>()
            var rounds = 0

            var result: ToolChat? = null
            while (result == null) {
                // 轮次用尽后不再声明工具，让模型用已有信息收尾
                val sendTools = tools != null && (maxRounds <= 0 || rounds < maxRounds)
                val payload = JSONObject().apply {
                    put("model", cfg.model)
                    put("messages", messages)
                    put("stream", false)
                    put("temperature", 0.3)
                    if (sendTools) {
                        put("tools", tools)
                        put("tool_choice", "auto")
                    }
                }
                val obj = if (sendTools) {
                    withTimeoutOrNull(TOOL_ROUND_TIMEOUT_MS) { postChat(cfg, payload) }
                        ?: throw IOException("模型单轮响应超时（${TOOL_ROUND_TIMEOUT_MS / 1000}秒）")
                } else {
                    postChat(cfg, payload)
                }

                // 厂商内置检索工具：来源在响应顶层 web_search[] 返回（智谱「对话中的网络搜索」）
                ZhipuWebSearch.parseToolHits(obj.optJSONArray("web_search")).forEach { h ->
                    val key = h.url.ifBlank { h.title }
                    if (!hits.containsKey(key)) hits[key] = h
                }

                val message = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                    ?: throw IOException("模型响应缺少 choices[0].message：${obj.toString().take(200)}")
                val content = message.optString("content", "").trim()
                val calls = message.optJSONArray("tool_calls")

                if (calls == null || calls.length() == 0 || executor == null || !sendTools) {
                    if (content.isEmpty()) {
                        throw IOException(
                            if (rounds > 0) "模型在 $rounds 轮检索后仍未返回正文：${obj.toString().take(200)}"
                            else "模型返回为空"
                        )
                    }
                    result = ToolChat(content, rounds, hits.values.toList())
                } else {
                    // 原样追加 assistant 消息（含 tool_calls），再逐个真实执行并回填 tool 消息
                    messages.put(message)
                    for (i in 0 until calls.length()) {
                        val call = calls.optJSONObject(i) ?: continue
                        val fn = call.optJSONObject("function")
                        val out = executor.execute(
                            fn?.optString("name", "").orEmpty(),
                            fn?.optString("arguments", "").orEmpty(),
                        )
                        messages.put(
                            JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", call.optString("id", ""))
                                put("content", out)
                            }
                        )
                    }
                    rounds++
                }
            }
            // 循环退出时 result 必已赋值（其余分支均抛异常）
            requireNotNull(result)
        }
    }

    /** 组装一条消息（role + content） */
    private fun msgOf(role: String, content: String): JSONObject = JSONObject().apply {
        put("role", role)
        put("content", content)
    }

    /**
     * 发起一次对话补全请求并返回**响应体 JSON**：
     * 非 2xx → 「HTTP 状态码 + 响应体前 300 字符」；响应不是合法 JSON 时同样如实抛出。
     */
    private fun postChat(cfg: LlmConfig, payload: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(cfg.chatUrl)
            .post(payload.toString().toRequestBody(JSON_TYPE))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${cfg.apiKey}")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw httpError(resp.code, body)
            return runCatching { JSONObject(body) }
                .getOrElse { throw IOException("模型响应不是合法 JSON：${body.trim().take(300)}") }
        }
    }

    /** 基础配置校验：提前给出明确错误，避免发出畸形请求 */
    private fun requireBase(cfg: LlmConfig) {
        if (cfg.baseUrl.isBlank()) throw IOException("未配置服务地址（baseUrl）")
        if (cfg.apiKey.isBlank()) throw IOException("未配置 API Key")
    }

    /** 非 2xx：状态码 + 响应体前 300 字符（如实反映服务端错误） */
    private fun httpError(code: Int, body: String): IOException {
        val detail = body.trim().take(300).ifEmpty { "（响应体为空）" }
        return IOException("HTTP $code: $detail")
    }

    /**
     * 统一异常包装：网络/解析异常原样放进 Result.failure；协程取消必须继续向上抛出。
     * 声明为 `suspend inline`：内联后调用方可在 lambda 内直接使用挂起函数（工具循环需要）。
     */
    private suspend inline fun <T> safeCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
