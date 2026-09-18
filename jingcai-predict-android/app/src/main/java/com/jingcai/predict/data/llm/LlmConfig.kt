package com.jingcai.predict.data.llm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * 大模型服务预设。
 *
 * 【来源说明】以下 baseUrl 与默认模型名均取自各厂商**官方文档/官方发布说明**（2026-09 核实）：
 * - DeepSeek：base_url https://api.deepseek.com（OpenAI 兼容）；deepseek-v4-pro / deepseek-v4-flash；
 *   旧模型名 deepseek-chat / deepseek-reasoner 已于 2026-07-24 废弃。
 * - 智谱 GLM：OpenAI Chat Completion 协议端点 https://open.bigmodel.cn/api/paas/v4；glm-5.3 / glm-5。
 * - Kimi（月之暗面）：https://api.moonshot.ai/v1；kimi-k3 / kimi-k2.6；moonshot-v1 系列已下线。
 * - OpenAI：https://api.openai.com/v1；**官方 API 的模型 ID 未在本项目可核实的文档中明确列出，
 *   因此默认模型名留空**，请用配置页的「拉取模型列表」从官方接口取真实模型名，避免写死过时信息。
 * - 自定义：任意 OpenAI 兼容服务（本地 Ollama / vLLM 等）。
 */
data class LlmProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val note: String,
)

val LlmPresets: List<LlmProvider> = listOf(
    LlmProvider(
        id = "deepseek",
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        defaultModel = "deepseek-v4-pro",
        note = "官方兼容接口；deepseek-v4-pro / deepseek-v4-flash",
    ),
    LlmProvider(
        id = "glm",
        name = "智谱 GLM",
        baseUrl = "https://open.bigmodel.cn/api/paas/v4",
        defaultModel = "glm-5.3",
        note = "Chat Completion 协议；glm-5.3 / glm-5",
    ),
    LlmProvider(
        id = "kimi",
        name = "Kimi（月之暗面）",
        baseUrl = "https://api.moonshot.ai/v1",
        defaultModel = "kimi-k3",
        note = "kimi-k3 / kimi-k2.6；moonshot-v1 系列已下线",
    ),
    LlmProvider(
        id = "gpt",
        name = "GPT",
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "",
        note = "模型 ID 请点「拉取模型列表」从官方接口获取，避免使用已停用模型",
    ),
    LlmProvider(
        id = "custom",
        name = "自定义（兼容接口）",
        baseUrl = "",
        defaultModel = "",
        note = "可填任意兼容服务（如本地 Ollama / vLLM）",
    ),
)

/**
 * 大模型配置。
 * 【安全说明】apiKey 仅保存在本机 DataStore，**未加密**，应用不会把它用于除该 baseUrl 之外的任何请求。
 */
data class LlmConfig(
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val enabled: Boolean = true,
    val bankroll: Double = 100.0,   // 参考本金（元），用于「最具价值」的本金计算
) {
    /** 配置是否可用（三项齐全才允许发起请求） */
    val ready: Boolean get() = enabled && baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

    /** 规范化 baseUrl：去掉尾部斜杠 */
    val base: String get() = baseUrl.trim().trimEnd('/')

    val chatUrl: String
        get() = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

    val modelsUrl: String
        get() = if (base.endsWith("/models")) base else "$base/models"
}

/** 大模型配置的本地持久化（DataStore） */
object LlmConfigStore {

    private val Context.dataStore by preferencesDataStore(name = "llm_config")
    private val KEY = stringPreferencesKey("config")

    suspend fun load(context: Context): LlmConfig {
        val json = context.dataStore.data.map { it[KEY] ?: "" }.first()
        if (json.isBlank()) return LlmConfig()
        return runCatching {
            val o = JSONObject(json)
            LlmConfig(
                baseUrl = o.optString("baseUrl", ""),
                apiKey = o.optString("apiKey", ""),
                model = o.optString("model", ""),
                enabled = o.optBoolean("enabled", true),
                bankroll = o.optDouble("bankroll", 100.0),
            )
        }.getOrDefault(LlmConfig())
    }

    suspend fun save(context: Context, cfg: LlmConfig) {
        val json = JSONObject().apply {
            put("baseUrl", cfg.baseUrl)
            put("apiKey", cfg.apiKey)
            put("model", cfg.model)
            put("enabled", cfg.enabled)
            put("bankroll", cfg.bankroll)
        }.toString()
        context.dataStore.edit { prefs -> prefs[KEY] = json }
    }
}
