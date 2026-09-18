package com.jingcai.predict.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** 某玩法的可售方式（官方 poolList）：single = 支持单关，allUp = 支持过关 */
data class PoolFlag(val single: Boolean, val allUp: Boolean)

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
    val crs: Map<String, String>? = null,   // 全场比分玩法：键 s{H}s{A} → 赔率；s1sh/s1sd/s1sa=胜/平/负其他
    val hafu: Map<String, String>? = null,  // 半全场玩法：hh/hd/ha/dh/dd/da/ah/ad/aa → 赔率
    val ttg: Map<String, String>? = null,   // 总进球数：s0~s7 → 赔率
    val homeRank: String = "",              // 主队联赛排名
    val awayRank: String = "",              // 客队联赛排名
    val poolFlags: Map<String, PoolFlag> = emptyMap(),  // 各玩法单关/过关可售标记
) {
    /** 该玩法本场是否支持单关（官方未标记则视为不支持） */
    fun singleAllowed(play: String): Boolean = poolFlags[play]?.single == true

    /** 该玩法本场是否支持过关（官方未标记则视为支持） */
    fun allUpAllowed(play: String): Boolean = poolFlags[play]?.allUp != false
}

/** 竞彩某一日期的赛事集合 */
data class MatchDay(
    val date: String,
    val matches: List<RemoteMatch>,
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

    /** 拉取全部竞彩足球赛事（近两日合并），失败时抛出异常 */
    suspend fun fetchMatches(): List<RemoteMatch> = fetchMatchDays().flatMap { it.matches }

    /** 拉取按日期分组的竞彩足球赛事（通常为今日+明日），失败时抛出异常 */
    suspend fun fetchMatchDays(): List<MatchDay> {
        val body = fetchBody()
        return parseDays(body)
    }

    private suspend fun fetchBody(): String = withContext(Dispatchers.IO) {
        HttpClient.client.newCall(buildRequest()).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IOException("竞彩接口 HTTP ${resp.code}")
            }
            val body = resp.body?.string() ?: throw IOException("竞彩接口响应为空")
            // WAF 拦截时会返回 HTML 而非 JSON
            if (!body.trimStart().startsWith("{")) {
                throw IOException("竞彩接口返回异常内容（可能被反爬拦截）")
            }
            body
        }
    }

    private fun parseDays(json: String): List<MatchDay> {
        val root = JSONObject(json)
        if (root.optString("errorCode") != "0") {
            throw IOException("竞彩接口返回错误: ${root.optString("errorMessage")}")
        }
        val value = root.optJSONObject("value") ?: return emptyList()
        val dayList = value.optJSONArray("matchInfoList") ?: return emptyList()
        val result = mutableListOf<MatchDay>()
        for (i in 0 until dayList.length()) {
            val day = dayList.optJSONObject(i) ?: continue
            val subs = day.optJSONArray("subMatchList") ?: continue
            val matches = mutableListOf<RemoteMatch>()
            for (j in 0 until subs.length()) {
                val m = subs.optJSONObject(j) ?: continue
                matches.add(parseMatch(m))
            }
            result.add(
                MatchDay(
                    // 官方日期字段为 businessDate（部分响应不再返回 matchDate），需兜底
                    date = day.optString("matchDate", "").ifEmpty { day.optString("businessDate", "") },
                    matches = matches,
                )
            )
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
            // 官方把日期与时间拆为 matchDate / matchTime 两个字段，这里组装成完整开赛时间 "yyyy-MM-dd HH:mm:ss"
            time = listOf(m.optString("matchDate", ""), m.optString("matchTime", ""))
                .filter { it.isNotEmpty() }
                .joinToString(" "),
            home = m.optString("homeTeamAllName", m.optString("homeTeamAbbName", "")),
            away = m.optString("awayTeamAllName", m.optString("awayTeamAbbName", "")),
            had = parseOdds(hadObj),
            hhad = parseOdds(hhadObj),
            goalLine = hhadObj?.optString("goalLine", "") ?: "",
            status = m.optString("matchStatus", ""),
            crs = parseOddsMap(m.optJSONObject("crs")),
            hafu = parseOddsMap(m.optJSONObject("hafu")),
            ttg = parseOddsMap(m.optJSONObject("ttg")),
            homeRank = m.optString("homeRank", ""),
            awayRank = m.optString("awayRank", ""),
            poolFlags = parsePoolFlags(m.optJSONArray("poolList")),
        )
    }

    /** 各玩法的单关/过关可售标记（官方 poolList，bettingSingle=1 可单关、bettingAllup=1 可过关） */
    private fun parsePoolFlags(arr: JSONArray?): Map<String, PoolFlag> {
        if (arr == null) return emptyMap()
        val out = mutableMapOf<String, PoolFlag>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val code = o.optString("poolCode", "")
            if (code.isEmpty()) continue
            out[code] = PoolFlag(
                single = o.optInt("bettingSingle", 0) == 1,
                allUp = o.optInt("bettingAllup", 0) == 1,
            )
        }
        return out
    }

    private fun parseOdds(obj: JSONObject?): Triple<String, String, String>? {
        if (obj == null) return null
        val h = obj.optString("h", "")
        val d = obj.optString("d", "")
        val a = obj.optString("a", "")
        if (h.isEmpty() || d.isEmpty() || a.isEmpty()) return null
        return Triple(h, d, a)
    }

    /** 玩法赔率表：过滤掉 flag 字段（以 f 结尾）与元信息字段，保留 选项→赔率 */
    private fun parseOddsMap(obj: JSONObject?): Map<String, String>? {
        if (obj == null) return null
        val map = mutableMapOf<String, String>()
        val meta = setOf("goalLine", "goalLineValue", "updateDate", "updateTime", "id", "remark")
        obj.keys().forEach { k ->
            if (k in meta || k.endsWith("f")) return@forEach
            val v = obj.optString(k)
            if (v.isNotEmpty() && v != "0") map[k] = v
        }
        return map.ifEmpty { null }
    }
}
