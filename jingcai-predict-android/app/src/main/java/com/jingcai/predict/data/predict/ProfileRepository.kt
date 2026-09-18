package com.jingcai.predict.data.predict

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * 联赛参数模板的本地覆盖仓库（DataStore 持久化）。
 *
 * 默认模板来自 2025/26 赛季真实统计（见 LeagueProfiles 来源标注）；
 * 用户可在详情页"预测分析"栏调整参数，此处保存覆盖值并立即生效。
 */
object ProfileRepository {

    private val Context.dataStore by preferencesDataStore(name = "league_profiles")
    private val KEY = stringPreferencesKey("overrides")

    /** 读取某联赛生效的模板：用户覆盖优先，否则返回联赛默认模板 */
    suspend fun getProfile(context: Context, league: String): LeagueProfile {
        val base = LeagueProfiles.forLeague(league)
        val json = context.dataStore.data.map { it[KEY] ?: "{}" }.first()
        val root = runCatching { JSONObject(json) }.getOrDefault(JSONObject())
        val key = keyOf(league, base.league)
        val over = root.optString(key, "")
        if (over.isBlank()) return base
        return parseOverride(over) ?: base
    }

    /** 保存某联赛的模板覆盖（传 null 表示恢复默认） */
    suspend fun saveProfile(context: Context, league: String, profile: LeagueProfile?) {
        val base = LeagueProfiles.forLeague(league)
        context.dataStore.edit { prefs ->
            val root = runCatching { JSONObject(prefs[KEY] ?: "{}") }.getOrDefault(JSONObject())
            val key = keyOf(league, base.league)
            if (profile == null) {
                root.remove(key)
            } else {
                root.put(key, profile.toJson())
            }
            prefs[KEY] = root.toString()
        }
    }

    /** 恢复默认 */
    suspend fun reset(context: Context, league: String) = saveProfile(context, league, null)

    /** 键：联赛名（避免中文 key 冲突问题，用原始名） */
    private fun keyOf(league: String, baseLeague: String): String = "lp_$league"

    private fun LeagueProfile.toJson(): JSONObject = JSONObject().apply {
        put("league", league)
        put("avgGoals", avgGoals)
        put("homeAdv", homeAdv)
        put("rho", rho)
        put("drawBias", drawBias)
        put("wMarket", weights.market)
        put("wPoisson", weights.poisson)
        put("wStat", weights.stat)
    }

    private fun parseOverride(json: String): LeagueProfile? = runCatching {
        val o = JSONObject(json)
        LeagueProfile(
            league = o.optString("league", "默认"),
            aliases = emptyList(),
            avgGoals = o.optDouble("avgGoals", 2.75),
            homeAdv = o.optDouble("homeAdv", 1.30),
            rho = o.optDouble("rho", -0.04),
            drawBias = o.optDouble("drawBias", 0.10),
            weights = SignalWeights(
                market = o.optDouble("wMarket", 0.50),
                poisson = o.optDouble("wPoisson", 0.30),
                stat = o.optDouble("wStat", 0.20),
            ),
            sourceNote = "用户自定义参数",
        )
    }.getOrNull()
}
