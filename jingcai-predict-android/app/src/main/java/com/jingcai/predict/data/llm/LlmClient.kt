package com.jingcai.predict.data.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 */
object LlmClient {

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
        withContext(Dispatchers.IO) {
            safeCall {
                requireBase(cfg)
                if (cfg.model.isBlank()) throw IOException("未配置模型名（model）")

                // 不发送 response_format：各厂商实现不一致，改由 prompt 约束只输出 JSON
                val payload = JSONObject().apply {
                    put("model", cfg.model)
                    put(
                        "messages",
                        JSONArray().apply {
                            put(JSONObject().apply { put("role", "system"); put("content", system) })
                            put(JSONObject().apply { put("role", "user"); put("content", user) })
                        },
                    )
                    put("stream", false)
                    put("temperature", 0.3)
                }.toString()

                val request = Request.Builder()
                    .url(cfg.chatUrl)
                    .post(payload.toRequestBody(JSON_TYPE))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${cfg.apiKey}")
                    .header("Accept", "application/json")
                    .build()

                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw httpError(resp.code, body)
                    val choices = JSONObject(body).optJSONArray("choices")
                        ?: throw IOException("模型响应缺少 choices 字段：${body.trim().take(200)}")
                    val content = choices.optJSONObject(0)
                        ?.optJSONObject("message")
                        ?.optString("content")
                        ?.trim()
                        .orEmpty()
                    if (content.isEmpty()) throw IOException("模型返回为空")
                    content
                }
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

    /** 统一异常包装：网络/解析异常原样放进 Result.failure；协程取消必须继续向上抛出 */
    private inline fun <T> safeCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
