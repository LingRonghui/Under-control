package com.jingcai.predict.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

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

object TeamDbApi {

    private const val BASE = "https://www.thesportsdb.com/api/v1/json/3/"

    /** 搜索球队 */
    suspend fun searchTeams(query: String): List<SearchTeam> = withContext(Dispatchers.IO) {
        try {
            val url = BASE + "searchteams.php?t=" + java.net.URLEncoder.encode(query, "UTF-8")
            val json = get(url) ?: return@withContext emptyList()
            parseTeams(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 搜索球员 */
    suspend fun searchPlayers(query: String): List<SearchPlayer> = withContext(Dispatchers.IO) {
        try {
            val url = BASE + "searchplayers.php?p=" + java.net.URLEncoder.encode(query, "UTF-8")
            val json = get(url) ?: return@withContext emptyList()
            parsePlayers(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            .build()
        HttpClient.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
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
}
