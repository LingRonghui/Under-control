package com.jingcai.predict.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/**
 * 中国体育彩票 · 竞彩足球官方接口（webapi.sporttery.cn）
 * 提供真实的竞彩比赛场次、球队名称与赔率数据。
 */
data class RemoteMatch(
    val matchId: String,
    val num: String,
    val league: String,
    val time: String,
    val home: String,
    val away: String,
    val had: Triple<String, String, String>?,   // 胜 平 负
    val hhad: Triple<String, String, String>?,  // 让球 胜 平 负
    val goalLine: String,
    val status: String,
)

object JingCaiApi {

    private const val MATCH_URL =
        "https://webapi.sporttery.cn/gateway/jc/football/getMatchCalculatorV1.qry?clientCode=3001&channel=c"

    /** 完整浏览器请求头，规避竞彩官网 WAF 反爬（UA 单独会命中拦截页） */
    private fun buildRequest(): Request {
        return Request.Builder()
            .url(MATCH_URL)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .header("Referer", "https://www.sporttery.cn/")
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Connection", "keep-alive")
            .header("Origin", "https://www.sporttery.cn")
            .build()
    }

    /**
     * 拉取近两日全部竞彩足球赛事（真实数据）。
     * 失败时抛出异常，由调用方决定降级策略。
     */
    suspend fun fetchMatches(): List<RemoteMatch> = withContext(Dispatchers.IO) {
        HttpClient.client.newCall(buildRequest()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("竞彩接口 HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IOException("竞彩接口响应为空")
            // WAF 拦截时会返回 HTML 而非 JSON
            if (!body.trimStart().startsWith("{")) {
                throw IOException("竞彩接口返回异常内容（可能被反爬拦截）")
            }
            parse(body)
        }
    }

    private fun parse(json: String): List<RemoteMatch> {
        val root = JSONObject(json)
        if (root.optString("errorCode") != "0") {
            throw IOException("竞彩接口返回错误: ${root.optString("errorMessage")}")
        }
        val value = root.optJSONObject("value") ?: return emptyList()
        val dayList = value.optJSONArray("matchInfoList") ?: return emptyList()
        val result = mutableListOf<RemoteMatch>()
        for (i in 0 until dayList.length()) {
            val day = dayList.optJSONObject(i) ?: continue
            val subs = day.optJSONArray("subMatchList") ?: continue
            for (j in 0 until subs.length()) {
                val m = subs.optJSONObject(j) ?: continue
                result.add(parseMatch(m))
            }
        }
        return result
    }

    private fun parseMatch(m: JSONObject): RemoteMatch {
        val hadObj = m.optJSONObject("had")
        val hhadObj = m.optJSONObject("hhad")
        return RemoteMatch(
            matchId = m.optString("matchId", ""),
            num = m.optString("matchNumStr", ""),
            league = m.optString("leagueAllName", m.optString("leagueAbbName", "")),
            time = m.optString("matchTime", ""),
            home = m.optString("homeTeamAllName", m.optString("homeTeamAbbName", "")),
            away = m.optString("awayTeamAllName", m.optString("awayTeamAbbName", "")),
            had = parseOdds(hadObj),
            hhad = parseOdds(hhadObj),
            goalLine = hhadObj?.optString("goalLine", "") ?: "",
            status = m.optString("matchStatus", ""),
        )
    }

    private fun parseOdds(obj: JSONObject?): Triple<String, String, String>? {
        if (obj == null) return null
        val h = obj.optString("h", "")
        val d = obj.optString("d", "")
        val a = obj.optString("a", "")
        if (h.isEmpty() || d.isEmpty() || a.isEmpty()) return null
        return Triple(h, d, a)
    }
}
