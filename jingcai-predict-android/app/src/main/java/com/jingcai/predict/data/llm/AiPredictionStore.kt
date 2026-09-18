package com.jingcai.predict.data.llm

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jingcai.predict.data.search.SearchHit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * 综合预测结果的本地仓库（DataStore）。
 *
 * 目的：**所有预测内容都保留，避免二次预测**。
 * - 详情页 / 预测分析页 / 后台批量预测统一从这里读写；
 * - 已存在的记录默认直接复用，只有「模型复核」或数据指纹变化时才重新计算。
 */
object AiPredictionStore {

    private val Context.dataStore by preferencesDataStore(name = "ai_predictions")
    private val KEY = stringPreferencesKey("items")

    private val _items = MutableStateFlow<List<CombinedPrediction>>(emptyList())

    /** 内存中的快照列表（Compose 可观察），首次使用请先调用 [loadOnce] */
    val items: StateFlow<List<CombinedPrediction>> = _items.asStateFlow()

    private var loaded = false

    suspend fun loadOnce(context: Context) {
        if (loaded) return
        _items.value = readAll(context)
        loaded = true
    }

    private suspend fun readAll(context: Context): List<CombinedPrediction> {
        val json = context.dataStore.data.map { it[KEY] ?: "[]" }.first()
        val all = runCatching { parse(json) }.getOrDefault(emptyList())
        // 逻辑版本不一致的历史快照直接丢弃（口径已变，旧结果会误导），下次进入会自动重算
        val fresh = all.filter { it.logicVersion == CombinedPrediction.LOGIC_VERSION }
        if (fresh.size != all.size) {
            context.dataStore.edit { prefs -> prefs[KEY] = toJson(fresh).toString() }
        }
        return fresh
    }

    /** 强制从磁盘重读（不影响内存中已更新的内容） */
    suspend fun reload(context: Context) {
        _items.value = readAll(context)
        loaded = true
    }

    fun get(matchId: String): CombinedPrediction? = _items.value.firstOrNull { it.matchId == matchId }

    /** 写入或更新一条快照（按 matchId 覆盖），同时落盘 */
    suspend fun upsert(context: Context, item: CombinedPrediction) {
        val list = _items.value.toMutableList()
        val idx = list.indexOfFirst { it.matchId == item.matchId }
        if (idx >= 0) list[idx] = item else list.add(item)
        _items.value = list
        persist(context)
    }

    suspend fun remove(context: Context, matchId: String) {
        _items.value = _items.value.filterNot { it.matchId == matchId }
        persist(context)
    }

    /** 清空全部（UI 若需要，需明确告知用户） */
    suspend fun clear(context: Context) {
        _items.value = emptyList()
        persist(context)
    }

    private suspend fun persist(context: Context) {
        val json = toJson(_items.value).toString()
        context.dataStore.edit { prefs -> prefs[KEY] = json }
    }

    /* ---------- 序列化 ---------- */

    /** 落盘的检索摘要上限（字符）：检索摘要仅用于展示与溯源，截断以控制快照体积 */
    private const val SOURCE_SNIPPET_MAX = 300

    private fun pickJson(p: CombinedPick): JSONObject = JSONObject().apply {
        put("play", p.play)
        put("playLabel", p.playLabel)
        put("option", p.option)
        put("odds", p.odds)
        put("probability", p.probability)
        p.aiScore?.let { put("aiScore", it) }
        put("confidence", p.confidence)
        put("alt", p.alt)
        put("altOdds", p.altOdds)
        put("reason", p.reason)
        put("divergence", p.divergence)
        p.hit?.let { put("hit", it) }
        put("note", p.note)
    }

    private fun parsePick(o: JSONObject): CombinedPick = CombinedPick(
        play = o.optString("play", ""),
        playLabel = o.optString("playLabel", ""),
        option = o.optString("option", ""),
        odds = o.optDouble("odds", 0.0),
        probability = o.optDouble("probability", 0.0),
        aiScore = if (o.has("aiScore")) o.optInt("aiScore") else null,
        confidence = o.optInt("confidence", 0),
        alt = o.optString("alt", ""),
        altOdds = o.optDouble("altOdds", 0.0),
        reason = o.optString("reason", ""),
        divergence = o.optBoolean("divergence", false),
        hit = if (o.has("hit")) o.optBoolean("hit") else null,
        note = o.optString("note", ""),
    )

    private fun toJson(list: List<CombinedPrediction>): JSONArray = JSONArray().apply {
        list.forEach { c ->
            put(
                JSONObject().apply {
                    put("matchId", c.matchId)
                    put("matchNum", c.matchNum)
                    put("league", c.league)
                    put("home", c.home)
                    put("away", c.away)
                    put("kickoff", c.kickoff)
                    put("statusText", c.statusText)
                    put("picks", JSONArray().apply { c.picks.forEach { put(pickJson(it)) } })
                    c.bestValue?.let { put("bestValue", pickJson(it)) }
                    c.safest?.let { put("safest", pickJson(it)) }
                    put(
                        "sections",
                        JSONArray().apply {
                            c.sections.forEach { (t, v) ->
                                put(JSONObject().apply { put("title", t); put("content", v) })
                            }
                        }
                    )
                    put("preview", c.preview)
                    put("engineConf", c.engineConf)
                    put("signalNote", c.signalNote)
                    put("dataComplete", c.dataComplete)
                    put("key", c.key)
                    put("model", c.model)
                    put("createdAt", c.createdAt)
                    put("updatedAt", c.updatedAt)
                    put("reviewCount", c.reviewCount)
                    put("searchState", c.searchState)
                    put("searchMode", c.searchMode)
                    put("searchRounds", c.searchRounds)
                    put(
                        "searchSources",
                        JSONArray().apply {
                            c.searchSources.forEach { h ->
                                put(
                                    JSONObject().apply {
                                        put("title", h.title)
                                        put("url", h.url)
                                        put("source", h.source)
                                        put("publishDate", h.publishDate)
                                        put("snippet", h.snippet.take(SOURCE_SNIPPET_MAX))
                                    }
                                )
                            }
                        }
                    )
                    put("logicVersion", c.logicVersion)
                }
            )
        }
    }

    private fun parse(json: String): List<CombinedPrediction> {
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val picks = o.optJSONArray("picks") ?: JSONArray()
            val sections = o.optJSONArray("sections") ?: JSONArray()
            // 旧版本快照没有 searchState / searchMode / searchRounds / searchSources：
            // 一律用默认值兜底（未启用、0 次、无来源），向后兼容、不崩
            val searchSources = o.optJSONArray("searchSources") ?: JSONArray()
            CombinedPrediction(
                matchId = o.optString("matchId", ""),
                matchNum = o.optString("matchNum", ""),
                league = o.optString("league", ""),
                home = o.optString("home", ""),
                away = o.optString("away", ""),
                kickoff = o.optString("kickoff", ""),
                statusText = o.optString("statusText", ""),
                picks = (0 until picks.length()).mapNotNull { j ->
                    picks.optJSONObject(j)?.let { parsePick(it) }
                },
                bestValue = o.optJSONObject("bestValue")?.let { parsePick(it) },
                safest = o.optJSONObject("safest")?.let { parsePick(it) },
                sections = (0 until sections.length()).mapNotNull { j ->
                    val s = sections.optJSONObject(j) ?: return@mapNotNull null
                    s.optString("title", "") to s.optString("content", "")
                },
                preview = o.optString("preview", ""),
                engineConf = o.optInt("engineConf", 0),
                signalNote = o.optString("signalNote", ""),
                dataComplete = o.optDouble("dataComplete", 0.0),
                key = o.optString("key", ""),
                model = o.optString("model", ""),
                createdAt = o.optLong("createdAt", 0L),
                updatedAt = o.optLong("updatedAt", 0L),
                reviewCount = o.optInt("reviewCount", 0),
                searchState = o.optString("searchState", ""),
                searchMode = o.optString("searchMode", ""),
                searchRounds = o.optInt("searchRounds", 0),
                searchSources = (0 until searchSources.length()).mapNotNull { j ->
                    val s = searchSources.optJSONObject(j) ?: return@mapNotNull null
                    val url = s.optString("url", "")
                    val title = s.optString("title", "")
                    if (url.isEmpty() && title.isEmpty()) return@mapNotNull null
                    SearchHit(
                        title = title,
                        url = url,
                        snippet = s.optString("snippet", ""),
                        source = s.optString("source", ""),
                        publishDate = s.optString("publishDate", ""),
                    )
                },
                logicVersion = o.optInt("logicVersion", 0),
            )
        }
    }
}
