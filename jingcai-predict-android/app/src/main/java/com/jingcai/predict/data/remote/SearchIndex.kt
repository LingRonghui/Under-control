package com.jingcai.predict.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/* ================= 联赛数据模型（竞彩官网「联赛历史数据」zqlszl 同源） ================= */

/** 联赛下的一个赛季 */
data class LeagueSeason(
    val seasonId: String,
    val seasonName: String,   // 如 "2026/2027"
)

/** 联赛条目（getLeagueListV1） */
data class LeagueEntry(
    val id: String,          // uniformLeagueId
    val name: String,        // leagueAbbCnName，如 "德甲"
    val logo: String,        // logoUrl
    val areaId: String,      // areaId
    val seasons: List<LeagueSeason>,
)

/** 赛程赛果单场（getMatchResultV1.subMatch） */
data class LeagueMatch(
    val matchDate: String,   // 2026-09-19
    val matchTime: String,   // 19:30
    val home: String,        // homeAbbCnName
    val away: String,        // awayAbbCnName
    val fullScore: String,   // sectionsNo999，如 "2:1"；空或 "-1:-1" 表示未赛
    val halfScore: String,   // sectionsNo1，半场 "0:1"
    val phaseName: String,   // 如 "Regular Season"
    val gameweek: String,    // 如 "4" = 第4轮
    val groupName: String,
    val matchId: String,
    val homeId: String,      // uniformHomeTeamId
) {
    /** 是否已完赛（是否有比分） */
    val isScore: Boolean
        get() = fullScore.isNotBlank() && fullScore != "-1:-1"
}

/** 按比赛日期分组的赛程赛果 */
data class LeagueDateGroup(
    val matchDate: String,
    val isToday: Boolean,
    val matches: List<LeagueMatch>,
)

/**
 * 竞彩官网「联赛历史数据」接口（zqlszl 页面同源）。
 * 与 zqdz 前瞻接口同域名，同样需完整浏览器请求头规避 WAF；
 * 与前瞻接口不同，league 系列接口无需 clientCode 参数。
 */
object LeagueApi {

    private const val BASE = "https://webapi.sporttery.cn/gateway/uniform/football/league/"

    private fun buildRequest(url: String): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://www.sporttery.cn/zqlszl/")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Connection", "keep-alive")
            .header("Origin", "https://www.sporttery.cn")
            .build()
    }

    /**
     * 拉取全部联赛列表（搜索"联赛"的数据源）。
     * value: { hot[], normal[], other[] }。hot 为平铺数组；normal 为嵌套 {
     *   countryList[]{ leagueList[]{...} } }，需递归压平。失败抛出异常。
     */
    suspend fun fetchLeagueList(): List<LeagueEntry> = withContext(Dispatchers.IO) {
        val url = BASE + "getLeagueListV1.qry"
        val resp = HttpClient.client.newCall(buildRequest(url)).execute()
        resp.use {
            if (!it.isSuccessful) throw IOException("联赛列表接口 HTTP ${it.code}")
            val body = it.body?.string() ?: throw IOException("联赛列表响应为空")
            if (!body.trimStart().startsWith("{")) throw IOException("联赛列表返回异常内容")
            val root = JSONObject(body)
            if (root.optString("errorCode") != "0") {
                throw IOException("联赛列表返回错误: ${root.optString("errorMessage")}")
            }
            val v = root.optJSONObject("value") ?: throw IOException("联赛列表 value 为空")
            val out = mutableListOf<LeagueEntry>()
            val seen = mutableSetOf<String>()

            fun parseLeague(o: JSONObject?) {
                if (o == null) return
                val id = o.optString("uniformLeagueId", "")
                if (id.isBlank() || !seen.add(id)) return
                val seasons = mutableListOf<LeagueSeason>()
                val sl = o.optJSONArray("seasonList")
                if (sl != null) {
                    for (i in 0 until sl.length()) {
                        val s = sl.optJSONObject(i) ?: continue
                        seasons.add(
                            LeagueSeason(
                                seasonId = s.optString("seasonId", ""),
                                seasonName = s.optString("seasonName", ""),
                            )
                        )
                    }
                }
                out.add(
                    LeagueEntry(
                        id = id,
                        name = o.optString("leagueAbbCnName", ""),
                        logo = o.optString("logoUrl", ""),
                        areaId = o.optString("areaId", ""),
                        seasons = seasons,
                    )
                )
            }

            // hot / other：平铺数组
            for (key in listOf("hot", "other")) {
                val arr = v.optJSONArray(key)
                if (arr != null) {
                    for (i in 0 until arr.length()) parseLeague(arr.optJSONObject(i))
                }
            }
            // normal：嵌套 { countryList[]{ leagueList[] } }
            fun flattenLevel(valObj: JSONObject?) {
                if (valObj == null) return
                val countries = valObj.optJSONArray("countryList") ?: return
                for (c in 0 until countries.length()) {
                    val country = countries.optJSONObject(c) ?: continue
                    val leagues = country.optJSONArray("leagueList")
                    if (leagues != null) {
                        for (l in 0 until leagues.length()) {
                            parseLeague(leagues.optJSONObject(l))
                        }
                    } else {
                        // 更深的嵌套：递归压平
                        flattenLevel(country)
                    }
                }
            }
            flattenLevel(v.optJSONObject("normal"))
            out
        }
    }

    /**
     * 拉取某联赛某赛季的赛程赛果（联赛详情页=比赛列表）。
     * 返回按日期分组的赛程赛果；失败抛出异常。
     */
    suspend fun fetchMatchResult(leagueId: String, seasonId: String): List<LeagueDateGroup> =
        withContext(Dispatchers.IO) {
            val url = BASE + "getMatchResultV1.qry?seasonId=$seasonId&uniformLeagueId=$leagueId"
            val resp = HttpClient.client.newCall(buildRequest(url)).execute()
            resp.use {
                if (!it.isSuccessful) throw IOException("赛程赛果接口 HTTP ${it.code}")
                val body = it.body?.string() ?: throw IOException("赛程赛果响应为空")
                if (!body.trimStart().startsWith("{")) throw IOException("赛程赛果返回异常内容")
                val root = JSONObject(body)
                if (root.optString("errorCode") != "0") {
                    throw IOException("赛程赛果返回错误: ${root.optString("errorMessage")}")
                }
                val v = root.optJSONObject("value") ?: throw IOException("赛程赛果 value 为空")
                val groups = mutableListOf<LeagueDateGroup>()
                val arr = v.optJSONArray("matchList")
                if (arr != null) {
                    for (d in 0 until arr.length()) {
                        val day = arr.optJSONObject(d) ?: continue
                        val matches = mutableListOf<LeagueMatch>()
                        val sub = day.optJSONArray("subMatchList")
                        if (sub != null) {
                            for (s in 0 until sub.length()) {
                                val m = sub.optJSONObject(s) ?: continue
                                matches.add(
                                    LeagueMatch(
                                        matchDate = m.optString("matchDate", "").take(10),
                                        matchTime = m.optString("matchTime", "").take(5)
                                            .let { if (it.length >= 5) it else m.optString("matchTime", "") },
                                        home = m.optString("homeAbbCnName", ""),
                                        away = m.optString("awayAbbCnName", ""),
                                        fullScore = m.optString("sectionsNo999", ""),
                                        halfScore = m.optString("sectionsNo1", ""),
                                        phaseName = m.optString("phaseName", ""),
                                        gameweek = m.optString("gameweek", ""),
                                        groupName = m.optString("groupName", ""),
                                        matchId = m.optString("uniformMatchId", ""),
                                        homeId = m.optString("uniformHomeTeamId", ""),
                                    )
                                )
                            }
                        }
                        groups.add(
                            LeagueDateGroup(
                                matchDate = day.optString("matchDate", "").take(10),
                                isToday = day.optBoolean("isToday"),
                                matches = matches,
                            )
                        )
                    }
                }
                groups
            }
        }
}