package com.jingcai.predict.data.search

import com.jingcai.predict.data.llm.LlmConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.URI
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 联网检索：为「赛前情报」提供**真实可溯源的网络来源**。
 *
 * 【红线】本模块只做一件事——把服务商真实返回的结果原样解析出来：
 * - 失败一律 `Result.failure`，错误文本包含 **HTTP 状态码 + 响应体前 300 字符**；
 * - 请求成功但结果为空时返回 `Result.success(emptyList())`（如实表达「检索到 0 条」，
 *   由调用方决定展示为「无结果」，绝不伪造占位结果）；
 * - 不编造标题、链接、站点名、时间；解析不到的字段留空字符串。
 */

/** 一条真实的检索结果 */
data class SearchHit(
    val title: String,
    val url: String,
    val snippet: String,
    /** 站点名 / 媒体名（智谱返回 media；Tavily 无此字段时回退为链接域名） */
    val source: String,
    /** 发布时间（服务商未返回则为空字符串） */
    val publishDate: String = "",
)

/** 检索服务商（可扩展） */
interface SearchProvider {
    val id: String
    val label: String

    suspend fun search(cfg: LlmConfig, query: String, count: Int): Result<List<SearchHit>>
}

/* ================= 公共 HTTP 口径 ================= */

/**
 * 独立 HTTP 客户端（与模型客户端分离，避免 90s 读超时被检索误用）：
 * - 连接 15s / 读 20s / 写 20s；
 * - 额外加 `callTimeout(20s)`：它约束「连接 + 读写」的**整次调用**总时长，
 *   保证「检索本身超时不超过 20s」这一硬约束（仅靠 connect/read 相加会超过 20s）。
 */
private val searchClient: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .writeTimeout(20, TimeUnit.SECONDS)
    .callTimeout(20, TimeUnit.SECONDS)
    .build()

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

/** 智谱官方文档约定：search_query 最长 70 字符 */
private const val MAX_QUERY_CHARS = 70

/** 非 2xx：状态码 + 响应体前 300 字符（如实反映服务端错误） */
private fun httpError(code: Int, body: String): IOException {
    val detail = body.trim().take(300).ifEmpty { "（响应体为空）" }
    return IOException("HTTP $code: $detail")
}

/** 解析失败等本地错误：带响应体片段，便于定位 */
private fun parseError(body: String, why: String): IOException =
    IOException("$why：${body.trim().take(300).ifEmpty { "（响应体为空）" }}")

/** 统一异常包装：网络/解析异常原样返回；协程取消必须继续向上抛出 */
private inline fun <T> safeCall(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** 截断到 n 个字符（中文按 1 个字符计） */
private fun clip(s: String, n: Int): String = if (s.length <= n) s else s.take(n)

/** 从链接取站点域名（去掉 www. 前缀）；解析失败返回空字符串，绝不猜 */
private fun hostOf(url: String): String = runCatching {
    val host = URI(url.trim()).host ?: return@runCatching ""
    host.lowercase(Locale.US).removePrefix("www.")
}.getOrDefault("")

/* ================= 智谱网络搜索 ================= */

/**
 * 智谱 Web Search。
 *
 * 【官方文档核实（2026-09，https://docs.bigmodel.cn/api-reference/工具-api/网络搜索）】
 * - 端点：`POST https://open.bigmodel.cn/api/paas/v4/web_search`（**与对话端点 /chat/completions 不同**）
 * - 鉴权：`Authorization: Bearer <API Key>`（复用智谱开放平台同一个 Key）
 * - 请求字段：`search_query`(≤70 字符)、`search_engine`(search_std / search_pro /
 *   search_pro_sogou / search_pro_quark)、`search_intent`(bool)、`count`(1~50)、
 *   `search_domain_filter`(白名单域名)、`search_recency_filter`、`content_size`
 * - `search_recency_filter` 合法取值（默认 noLimit）：`oneDay` / `oneWeek` /
 *   `oneMonth` / `oneYear` / `noLimit`（文档标注支持 search_std、search_pro、search_pro_Sogou、search_pro_quark）
 * - `content_size` 合法取值：`medium`（摘要信息，满足模型基础推理）/ `high`（最大化上下文，内容更详细）
 * - 响应：`search_result[]`，每项含 `title` / `content` / `link` / `media` / `icon` /
 *   `refer` / `publish_date`；另有顶层 `id` / `created` / `request_id` / `search_intent`
 * - 失败响应：`{"error": {"code": ..., "message": ...}}`
 */
object ZhipuWebSearch : SearchProvider {

    override val id = "zhipu"
    override val label = "智谱"

    /** 官方端点（固定域名） */
    private const val OFFICIAL_ENDPOINT = "https://open.bigmodel.cn/api/paas/v4/web_search"

    /**
     * 端点推导口径（写明理由）：
     * 检索端点与对话端点**路径不同**（/web_search vs /chat/completions），不能直接拿 chatUrl 拼。
     * 因此：**仅当用户配置的 baseUrl 本身属于智谱 open.bigmodel.cn 域时**才由它推导
     * （这样企业/自建网关改域名时依然可用，且与对话走同一网关）；其余情况一律用
     * **官方固定域名**——否则用户拿 DeepSeek/Kimi 的 baseUrl 选择智谱检索时，
     * 会把请求发到对方域名下不存在的路径上（必然 404），那是更糟的失败方式。
     */
    private fun endpointOf(cfg: LlmConfig): String {
        val base = cfg.base.removeSuffix("/chat/completions").removeSuffix("/models")
        return if (base.contains("bigmodel.cn")) "$base/web_search" else OFFICIAL_ENDPOINT
    }

    override suspend fun search(cfg: LlmConfig, query: String, count: Int): Result<List<SearchHit>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val key = cfg.apiKey.trim()
                if (key.isEmpty()) throw IOException("智谱检索未配置 API Key（请在「API Key」中填写智谱开放平台 Key）")
                val q = clip(query.trim(), MAX_QUERY_CHARS)
                if (q.isEmpty()) throw IOException("检索关键词为空")

                val payload = JSONObject().apply {
                    put("search_query", q)
                    // 基础版检索（search_std）：count 支持 1~50 的连续取值（高阶版/搜狗为枚举值）
                    put("search_engine", "search_std")
                    // 关键词已是明确检索词，跳过意图识别，避免多一次意图处理带来的不确定延迟
                    put("search_intent", false)
                    put("count", count.coerceIn(1, 50))
                    // 默认不限时间；如需收紧可改为 oneDay / oneWeek / oneMonth / oneYear
                    put("search_recency_filter", "noLimit")
                    // medium 返回摘要，足够模型做情报引用且 prompt 不易过长
                    put("content_size", "medium")
                }.toString()

                val request = Request.Builder()
                    .url(endpointOf(cfg))
                    .post(payload.toRequestBody(JSON_TYPE))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .build()

                searchClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw httpError(resp.code, body)
                    val obj = runCatching { JSONObject(body) }
                        .getOrElse { throw parseError(body, "响应不是合法 JSON") }
                    // 业务错误对象（HTTP 200 但带 error）也如实抛出
                    obj.optJSONObject("error")?.let { e ->
                        throw parseError(body, "服务端返回错误 ${e.optString("code", "-")}: ${e.optString("message", "")}")
                    }
                    val arr = obj.optJSONArray("search_result")
                        ?: throw parseError(body, "响应缺少 search_result 字段")
                    (0 until arr.length()).mapNotNull { i ->
                        val item = arr.optJSONObject(i) ?: return@mapNotNull null
                        val link = item.optString("link", "").trim()
                        val title = item.optString("title", "").trim()
                        if (link.isEmpty() && title.isEmpty()) return@mapNotNull null
                        val media = item.optString("media", "").trim()
                        val refer = item.optString("refer", "").trim()
                        SearchHit(
                            title = title.ifEmpty { link },
                            url = link,
                            snippet = item.optString("content", "").trim(),
                            source = media.ifEmpty { refer.ifEmpty { hostOf(link) } },
                            publishDate = item.optString("publish_date", "").trim(),
                        )
                    }
                }
            }
        }
}

/* ================= Tavily 搜索 ================= */

/**
 * Tavily Search。
 *
 * 【官方文档核实（2026-09，https://docs.tavily.com/documentation/api-reference/endpoint/search）】
 * - 端点：`POST https://api.tavily.com/search`
 * - 鉴权：**请求头** `Authorization: Bearer <tvly-...>`（文档明确写 Bearer authentication header；
 *   **不是** body 里的 api_key，旧版文档里的 `api_key` 字段已不在当前参数表中）
 * - 请求字段（本次使用）：`query`(必填)、`search_depth`(basic / advanced / fast / ultra-fast)、
 *   `max_results`(0~20，默认 10)、`topic`(general / news / finance；news 面向体育/时事等实时报道，
 *   且会自动开启 published_date)、`include_published_date`(bool)、`include_answer`(bool)
 * - 时间范围字段为 `time_range`(day/week/month/year/d/w/m/y)、`start_date`、`end_date`；
 *   **当前文档已没有 `days` 字段**（更早版本才有），故实现不使用 days，避免发无效参数
 * - 响应：`results[]`，每项含 `title` / `url` / `content` / `score` / `raw_content` /
 *   `published_date` / `favicon` / `images` / `id`；另有 `query` / `answer` / `response_time` / `request_id`
 * - 失败响应：`{"detail": {"error": "..."}}`（400 / 401 / 429 / 432 / 433 / 500）
 * - 该接口**不返回媒体名**，因此 source 用链接域名回填（可溯源、不编造）
 */
object TavilyWebSearch : SearchProvider {

    override val id = "tavily"
    override val label = "Tavily"

    private const val ENDPOINT = "https://api.tavily.com/search"

    override suspend fun search(cfg: LlmConfig, query: String, count: Int): Result<List<SearchHit>> =
        withContext(Dispatchers.IO) {
            safeCall {
                val key = cfg.tavilyKey.trim()
                if (key.isEmpty()) throw IOException("Tavily 检索未配置 API Key（请在 tavily.com 注册后获取 tvly- 开头的 Key）")
                val q = query.trim()
                if (q.isEmpty()) throw IOException("检索关键词为空")

                val payload = JSONObject().apply {
                    put("query", q)
                    // basic：相关性与延迟平衡，1 credit，满足赛前情报需求
                    put("search_depth", "basic")
                    put("max_results", count.coerceIn(1, 20))
                    // news：面向实时报道（体育/时事），并自动包含 published_date
                    put("topic", "news")
                    put("include_published_date", true)
                    put("include_answer", false)
                }.toString()

                val request = Request.Builder()
                    .url(ENDPOINT)
                    .post(payload.toRequestBody(JSON_TYPE))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .build()

                searchClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw httpError(resp.code, body)
                    val obj = runCatching { JSONObject(body) }
                        .getOrElse { throw parseError(body, "响应不是合法 JSON") }
                    val arr = obj.optJSONArray("results")
                        ?: throw parseError(body, "响应缺少 results 字段")
                    (0 until arr.length()).mapNotNull { i ->
                        val item = arr.optJSONObject(i) ?: return@mapNotNull null
                        val url = item.optString("url", "").trim()
                        val title = item.optString("title", "").trim()
                        if (url.isEmpty() && title.isEmpty()) return@mapNotNull null
                        SearchHit(
                            title = title.ifEmpty { url },
                            url = url,
                            snippet = item.optString("content", "").trim(),
                            source = hostOf(url),
                            publishDate = item.optString("published_date", "").trim(),
                        )
                    }
                }
            }
        }
}

/* ================= 统一入口 ================= */

/**
 * 按 [LlmConfig.searchProvider] 分发检索请求。
 * - `""`（或未知取值）→ `Result.failure(IOException("未启用联网检索"))`，不发起任何网络请求；
 * - `"zhipu"` / `"tavily"` → 对应服务商（失败原因原样返回）。
 */
object WebSearch {

    /** 单次检索默认条数 */
    const val DEFAULT_COUNT = 5

    suspend fun search(cfg: LlmConfig, query: String, count: Int = DEFAULT_COUNT): Result<List<SearchHit>> {
        val provider = when (cfg.searchProvider.trim().lowercase(Locale.US)) {
            ZhipuWebSearch.id -> ZhipuWebSearch
            TavilyWebSearch.id -> TavilyWebSearch
            else -> return Result.failure(IOException("未启用联网检索"))
        }
        return provider.search(cfg, query, count)
    }
}
