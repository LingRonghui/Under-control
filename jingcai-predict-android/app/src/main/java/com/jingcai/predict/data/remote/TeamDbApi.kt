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
}
