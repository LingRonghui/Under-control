package com.jingcai.predict.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** TheSportsDB 全球足球数据库搜索结果 */
data class SearchTeam(
    val id: String,
    val name: String,
    val league: String,
    val country: String,
    val badge: String,
)

data class SearchPlayer(
    val id: String,
    val name: String,
    val team: String,
    val nationality: String,
    val position: String,
    val photo: String,
)

data class SearchLeague(
    val id: String,
    val name: String,
    val country: String,
    val badge: String,
)

/** TheSportsDB 单场比赛（近期战绩/后续赛事） */
data class TeamEvent(
    val home: String,
    val away: String,
    val homeScore: String,   // 空串表示未开赛/无比分
    val awayScore: String,
    val date: String,
    val status: String,      // Match Finished / Not Started ...
)

object TeamDbApi {

    private const val BASE = "https://www.thesportsdb.com/api/v1/json/3/"

    /** 独立短超时客户端：海外数据源失败时快速返回，避免长时间卡 loading */
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /** 搜索球队，失败时抛出异常 */
    suspend fun searchTeams(query: String): List<SearchTeam> = withContext(Dispatchers.IO) {
        val url = BASE + "searchteams.php?t=" + URLEncoder.encode(query, "UTF-8")
        val json = get(url)
        parseTeams(json)
    }

    /** 搜索球员，失败时抛出异常 */
    suspend fun searchPlayers(query: String): List<SearchPlayer> = withContext(Dispatchers.IO) {
        val url = BASE + "searchplayers.php?p=" + URLEncoder.encode(query, "UTF-8")
        val json = get(url)
        parsePlayers(json)
    }

    /** 球队近期赛事（eventslast，通常近 5 场），失败时抛出异常 */
    suspend fun lastEvents(teamId: String): List<TeamEvent> = withContext(Dispatchers.IO) {
        val url = BASE + "eventslast.php?id=" + URLEncoder.encode(teamId, "UTF-8")
        val json = get(url)
        parseEvents(json)
    }

    /** 球队后续赛事（eventsnext 下一场），失败时抛出异常 */
    suspend fun nextEvent(teamId: String): TeamEvent? = withContext(Dispatchers.IO) {
        val url = BASE + "eventsnext.php?id=" + URLEncoder.encode(teamId, "UTF-8")
        val json = get(url)
        parseEvents(json).firstOrNull()
    }

    /**
     * 搜索联赛：TheSportsDB 的 search_all_leagues.php 在 v3 key 下仅支持 s=Soccer 全量拉取，
     * 因此一次性拉取足球联赛列表后本地按名称/国家过滤，并缓存结果。
     */
    private var leagueCache: List<SearchLeague>? = null

    suspend fun searchLeagues(query: String): List<SearchLeague> = withContext(Dispatchers.IO) {
        val all = leagueCache ?: run {
            val url = BASE + "search_all_leagues.php?s=Soccer"
            val json = get(url)
            parseLeagues(json).also { leagueCache = it }
        }
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        all.filter {
            it.name.lowercase().contains(q) || it.country.lowercase().contains(q)
        }
    }

    private fun get(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("TheSportsDB HTTP ${resp.code}")
            return resp.body?.string() ?: throw IOException("TheSportsDB 响应为空")
        }
    }

    private fun parseTeams(json: String): List<SearchTeam> {
        val arr: JSONArray = JSONObject(json).optJSONArray("teams") ?: return emptyList()
        val out = mutableListOf<SearchTeam>()
        for (i in 0 until arr.length()) {
            val t = arr.optJSONObject(i) ?: continue
            if (t.optString("strSport", "Soccer") != "Soccer") continue
            out.add(
                SearchTeam(
                    id = t.optString("idTeam", ""),
                    name = t.optString("strTeam", ""),
                    league = t.optString("strLeague", ""),
                    country = t.optString("strCountry", ""),
                    badge = t.optString("strBadge", ""),
                )
            )
        }
        return out
    }

    private fun parsePlayers(json: String): List<SearchPlayer> {
        val arr: JSONArray = JSONObject(json).optJSONArray("player") ?: return emptyList()
        val out = mutableListOf<SearchPlayer>()
        for (i in 0 until arr.length()) {
            val p = arr.optJSONObject(i) ?: continue
            if (p.optString("strSport", "Soccer") != "Soccer") continue
            out.add(
                SearchPlayer(
                    id = p.optString("idPlayer", ""),
                    name = p.optString("strPlayer", ""),
                    team = p.optString("strTeam", ""),
                    nationality = p.optString("strNationality", ""),
                    position = p.optString("strPosition", ""),
                    photo = p.optString("strCutout", p.optString("strThumb", "")),
                )
            )
        }
        return out
    }

    private fun parseLeagues(json: String): List<SearchLeague> {
        val arr: JSONArray = JSONObject(json).optJSONArray("countries") ?: return emptyList()
        val out = mutableListOf<SearchLeague>()
        for (i in 0 until arr.length()) {
            val l = arr.optJSONObject(i) ?: continue
            if (l.optString("strSport", "Soccer") != "Soccer") continue
            out.add(
                SearchLeague(
                    id = l.optString("idLeague", ""),
                    name = l.optString("strLeague", ""),
                    country = l.optString("strCountry", ""),
                    badge = "",
                )
            )
        }
        return out
    }

    private fun parseEvents(json: String): List<TeamEvent> {
        val arr: JSONArray = JSONObject(json).optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<TeamEvent>()
        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            if (e.optString("strSport", "Soccer") != "Soccer") continue
            out.add(
                TeamEvent(
                    home = e.optString("strHomeTeam", ""),
                    away = e.optString("strAwayTeam", ""),
                    homeScore = e.optString("intHomeScore", ""),
                    awayScore = e.optString("intAwayScore", ""),
                    date = e.optString("dateEvent", "").take(10),
                    status = e.optString("strStatus", ""),
                )
            )
        }
        return out
    }
}
