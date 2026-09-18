package com.jingcai.predict.data.predict

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地回测记录：每次预测保存 → 开赛后结算实际赛果 → 统计真实命中率。
 *
 * 这是"预测效果有据可查"的诚实机制：命中率由真实赛果计算，不掺水分。
 */
data class BacktestRecord(
    val matchId: String,
    val league: String,
    val home: String,
    val away: String,
    val kickoff: String,
    val pick: String,          // 主胜 / 平局 / 客胜
    val homeProb: Double,
    val conf: Int,
    val predictedAt: Long,
    val settled: Boolean = false,
    val result: String = "",   // 实际结果：主胜 / 平局 / 客胜
    val hit: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("matchId", matchId)
        put("league", league)
        put("home", home)
        put("away", away)
        put("kickoff", kickoff)
        put("pick", pick)
        put("homeProb", homeProb)
        put("conf", conf)
        put("predictedAt", predictedAt)
        put("settled", settled)
        put("result", result)
        put("hit", hit)
    }

    companion object {
        fun fromJson(o: JSONObject): BacktestRecord = BacktestRecord(
            matchId = o.optString("matchId"),
            league = o.optString("league"),
            home = o.optString("home"),
            away = o.optString("away"),
            kickoff = o.optString("kickoff"),
            pick = o.optString("pick"),
            homeProb = o.optDouble("homeProb", 0.0),
            conf = o.optInt("conf", 0),
            predictedAt = o.optLong("predictedAt", 0L),
            settled = o.optBoolean("settled", false),
            result = o.optString("result"),
            hit = o.optBoolean("hit", false),
        )
    }
}

/** 回测统计（按总体 / 置信度分档 / 联赛） */
data class BacktestStats(
    val total: Int,            // 累计预测总数
    val settled: Int,          // 已结算数
    val hit: Int,              // 命中数
    val settledHigh: Int, val hitHigh: Int,   // 置信度 >= 85
    val settledMid: Int, val hitMid: Int,     // 70 <= 置信度 < 85
    val settledLow: Int, val hitLow: Int,     // 置信度 < 70
) {
    val hitRate: Double get() = if (settled > 0) hit.toDouble() / settled else 0.0
    val highRate: Double get() = if (settledHigh > 0) hitHigh.toDouble() / settledHigh else 0.0
    val midRate: Double get() = if (settledMid > 0) hitMid.toDouble() / settledMid else 0.0
    val lowRate: Double get() = if (settledLow > 0) hitLow.toDouble() / settledLow else 0.0

    companion object {
        fun of(records: List<BacktestRecord>): BacktestStats {
            val settled = records.filter { it.settled }
            val high = settled.filter { it.conf >= 85 }
            val mid = settled.filter { it.conf in 70 until 85 }
            val low = settled.filter { it.conf < 70 }
            return BacktestStats(
                total = records.size,
                settled = settled.size,
                hit = settled.count { it.hit },
                settledHigh = high.size, hitHigh = high.count { it.hit },
                settledMid = mid.size, hitMid = mid.count { it.hit },
                settledLow = low.size, hitLow = low.count { it.hit },
            )
        }
    }
}

object PredictionStore {

    private val Context.dataStore by preferencesDataStore(name = "predictions")
    private val KEY = stringPreferencesKey("backtest_records")

    fun recordsFlow(context: Context): Flow<List<BacktestRecord>> =
        context.dataStore.data.map { prefs -> parse(prefs[KEY] ?: "[]") }

    suspend fun records(context: Context): List<BacktestRecord> =
        context.dataStore.data.map { prefs -> parse(prefs[KEY] ?: "[]") }.first()

    /** 保存或更新一条记录（按 matchId + predictedAt 匹配） */
    suspend fun upsert(context: Context, rec: BacktestRecord) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY] ?: "[]").toMutableList()
            val idx = list.indexOfFirst { it.matchId == rec.matchId && it.predictedAt == rec.predictedAt }
            if (idx >= 0) list[idx] = rec else list.add(rec)
            prefs[KEY] = toJsonArray(list).toString()
        }
    }

    /** 结算某场比赛的最新一条未结算预测 */
    suspend fun settle(context: Context, matchId: String, result: String) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY] ?: "[]").toMutableList()
            val idx = list.indexOfLast { it.matchId == matchId && !it.settled }
            if (idx >= 0) {
                val old = list[idx]
                list[idx] = old.copy(
                    settled = true,
                    result = result,
                    hit = old.pick == result,
                )
                prefs[KEY] = toJsonArray(list).toString()
            }
        }
    }

    private fun parse(json: String): List<BacktestRecord> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { BacktestRecord.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun toJsonArray(list: List<BacktestRecord>): JSONArray =
        JSONArray().apply { list.forEach { put(it.toJson()) } }
}
