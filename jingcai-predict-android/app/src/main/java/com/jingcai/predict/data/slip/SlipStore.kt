package com.jingcai.predict.data.slip

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/** 当前方案单（未保存的草稿）：选择 + 过关方式 + 倍数 */
data class CurrentSlip(
    val selections: List<SlipSelection> = emptyList(),
    val parlay: List<Int> = emptyList(),
    val multiple: Int = 1,
)

/**
 * 方案单内存状态（Compose 可观察，跨页面共享）。
 * 变化后用 [SlipStore] 落盘，保证退出应用后仍在。
 */
object SlipHolder {

    val selections = mutableStateListOf<SlipSelection>()

    /** 已选关次（1 = 单关）；为空表示未手动指定，由计算器按当前场次数推荐 */
    var parlay by mutableStateOf<List<Int>>(emptyList())
    var multiple by mutableIntStateOf(1)

    /** 按比赛分组：比赛 → 该场已选选项 */
    fun byMatch(): Map<String, List<SlipSelection>> =
        selections.groupBy { it.matchId }.toMap()

    fun contains(sel: SlipSelection): Boolean = selections.any { it.key == sel.key }

    /** 点击选项：已选则取消，未选则加入。返回 true 表示现在是选中状态 */
    fun toggle(sel: SlipSelection): Boolean {
        val idx = selections.indexOfFirst { it.key == sel.key }
        return if (idx >= 0) {
            selections.removeAt(idx)
            false
        } else {
            selections.add(sel)
            true
        }
    }

    fun remove(sel: SlipSelection) {
        selections.removeAll { it.key == sel.key }
    }

    fun clear() {
        selections.clear()
        parlay = emptyList()
        multiple = 1
    }

    /** 是否所有已选都支持单关（官方标记） */
    fun singleAllowed(): Boolean = selections.isNotEmpty() && selections.all { it.singleAllowed }

    /** 当前方案单涉及的最高过关关数（木桶原则） */
    fun maxParlay(): Int = ParlayMath.maxParlay(selections.map { it.play })

    fun snapshot(): CurrentSlip = CurrentSlip(selections.toList(), parlay, multiple)

    fun restore(slip: CurrentSlip) {
        selections.clear()
        selections.addAll(slip.selections)
        parlay = slip.parlay
        multiple = slip.multiple
    }
}

/** 方案单与已保存方案的本地持久化（DataStore） */
object SlipStore {

    private val Context.dataStore by preferencesDataStore(name = "bet_slip")
    private val KEY_CURRENT = stringPreferencesKey("current")
    private val KEY_SAVED = stringPreferencesKey("saved")

    suspend fun loadCurrent(context: Context): CurrentSlip {
        val json = context.dataStore.data.map { it[KEY_CURRENT] ?: "" }.first()
        if (json.isBlank()) return CurrentSlip()
        return runCatching { parseCurrent(JSONObject(json)) }.getOrDefault(CurrentSlip())
    }

    suspend fun saveCurrent(context: Context, slip: CurrentSlip) {
        context.dataStore.edit { prefs -> prefs[KEY_CURRENT] = currentJson(slip).toString() }
    }

    suspend fun loadSaved(context: Context): List<SavedSlip> {
        val json = context.dataStore.data.map { it[KEY_SAVED] ?: "[]" }.first()
        return runCatching { parseSaved(JSONArray(json)) }.getOrDefault(emptyList())
    }

    suspend fun saveSaved(context: Context, slips: List<SavedSlip>) {
        context.dataStore.edit { prefs -> prefs[KEY_SAVED] = savedJson(slips).toString() }
    }

    /* ---------- 序列化 ---------- */

    private fun currentJson(slip: CurrentSlip): JSONObject = JSONObject().apply {
        put("parlay", JSONArray(slip.parlay))
        put("multiple", slip.multiple)
        put("selections", JSONArray().apply { slip.selections.forEach { put(selJson(it)) } })
    }

    private fun parseCurrent(o: JSONObject): CurrentSlip {
        val arr = o.optJSONArray("selections") ?: JSONArray()
        return CurrentSlip(
            selections = (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { parseSel(it) }
            },
            parlay = intList(o.optJSONArray("parlay")),
            multiple = o.optInt("multiple", 1).coerceAtLeast(1),
        )
    }

    private fun selJson(s: SlipSelection): JSONObject = JSONObject().apply {
        put("matchId", s.matchId)
        put("matchNum", s.matchNum)
        put("league", s.league)
        put("home", s.home)
        put("away", s.away)
        put("kickoff", s.kickoff)
        put("play", s.play)
        put("playLabel", s.playLabel)
        put("optionCode", s.optionCode)
        put("optionLabel", s.optionLabel)
        put("odds", s.odds)
        put("goalLine", s.goalLine)
        put("singleAllowed", s.singleAllowed)
    }

    private fun parseSel(o: JSONObject): SlipSelection = SlipSelection(
        matchId = o.optString("matchId", ""),
        matchNum = o.optString("matchNum", ""),
        league = o.optString("league", ""),
        home = o.optString("home", ""),
        away = o.optString("away", ""),
        kickoff = o.optString("kickoff", ""),
        play = o.optString("play", ""),
        playLabel = o.optString("playLabel", ""),
        optionCode = o.optString("optionCode", ""),
        optionLabel = o.optString("optionLabel", ""),
        odds = o.optDouble("odds", 0.0),
        goalLine = o.optString("goalLine", ""),
        singleAllowed = o.optBoolean("singleAllowed", false),
    )

    private fun savedJson(slips: List<SavedSlip>): JSONArray = JSONArray().apply {
        slips.forEach { s ->
            put(
                JSONObject().apply {
                    put("id", s.id)
                    put("createdAt", s.createdAt)
                    put("parlay", JSONArray(s.parlay))
                    put("multiple", s.multiple)
                    put("stake", s.stake)
                    put("noteCount", s.noteCount)
                    put("maxPrize", s.maxPrize)
                    put("status", s.status)
                    put("prize", s.prize)
                    s.settledAt?.let { put("settledAt", it) }
                    put(
                        "legs",
                        JSONArray().apply {
                            s.legs.forEach { l ->
                                put(
                                    JSONObject().apply {
                                        put("matchId", l.matchId)
                                        put("matchNum", l.matchNum)
                                        put("league", l.league)
                                        put("home", l.home)
                                        put("away", l.away)
                                        put("play", l.play)
                                        put("playLabel", l.playLabel)
                                        put("optionCode", l.optionCode)
                                        put("optionLabel", l.optionLabel)
                                        put("odds", l.odds)
                                        put("goalLine", l.goalLine)
                                        l.hit?.let { put("hit", it) }
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }

    private fun parseSaved(arr: JSONArray): List<SavedSlip> =
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val legArr = o.optJSONArray("legs") ?: JSONArray()
            SavedSlip(
                id = o.optString("id", ""),
                createdAt = o.optLong("createdAt", 0L),
                legs = (0 until legArr.length()).mapNotNull { j ->
                    val l = legArr.optJSONObject(j) ?: return@mapNotNull null
                    SlipLeg(
                        matchId = l.optString("matchId", ""),
                        matchNum = l.optString("matchNum", ""),
                        league = l.optString("league", ""),
                        home = l.optString("home", ""),
                        away = l.optString("away", ""),
                        play = l.optString("play", ""),
                        playLabel = l.optString("playLabel", ""),
                        optionCode = l.optString("optionCode", ""),
                        optionLabel = l.optString("optionLabel", ""),
                        odds = l.optDouble("odds", 0.0),
                        goalLine = l.optString("goalLine", ""),
                        hit = if (l.has("hit")) l.optBoolean("hit") else null,
                    )
                },
                parlay = intList(o.optJSONArray("parlay")),
                multiple = o.optInt("multiple", 1).coerceAtLeast(1),
                stake = o.optDouble("stake", 0.0),
                noteCount = o.optLong("noteCount", 0L),
                maxPrize = o.optDouble("maxPrize", 0.0),
                status = o.optInt("status", SlipStatus.PENDING),
                prize = o.optDouble("prize", 0.0),
                settledAt = if (o.has("settledAt")) o.optLong("settledAt") else null,
            )
        }

    private fun intList(arr: JSONArray?): List<Int> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optInt(it).takeIf { v -> v > 0 } }
    }
}
