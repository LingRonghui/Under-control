package com.jingcai.predict.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/* ================= 数据模型 ================= */

/** 比赛头部信息（getMatchHeadV1） */
data class MatchHead(
    val matchNum: String,
    val league: String,          // 赛事简称，如 西甲
    val homeName: String, val awayName: String,
    val matchDateTime: String,
    val homeLogo: String, val awayLogo: String,
    val homeRank: String, val awayRank: String,
    val homeSeason: String, val awaySeason: String,   // "1胜2平2负"
    val homeHomeSeason: String, val awayAwaySeason: String, // 主场/客场成绩
)

/** 特征分析：某一维度下两队胜平负计数 */
data class FeatureDim(
    val homeWin: Int, val homeDraw: Int, val homeLoss: Int,
    val awayWin: Int, val awayDraw: Int, val awayLoss: Int,
    val totalLeg: Int,
)

/** 特征分析（getMatchFeatureV1） */
data class MatchFeature(
    val last10: FeatureDim?,        // 近10场交锋
    val sameHomeAway: FeatureDim?,  // 同主客交锋
    val last10Form: FeatureDim?,    // 近10场战况
    val sameHomeAwayForm: FeatureDim?, // 同主客战况
    val homeGoalAvg: String, val awayGoalAvg: String,
    val homeLossAvg: String, val awayLossAvg: String,
)

/** 历史交锋单场（getResultHistoryV1） */
data class H2hMatch(
    val date: String, val tournament: String,
    val home: String, val away: String,
    val score: String, val halfScore: String, val totalGoal: Int,
)

/** 历史交锋汇总（主队视角） */
data class H2hSummary(
    val teamName: String,
    val win: Int, val draw: Int, val loss: Int,
    val winProb: String, val drawProb: String, val lossProb: String,
)

/** 积分榜单行（总/主/客） */
data class TableRow(
    val scope: String, // 总/主/客
    val played: Int, val win: Int, val draw: Int, val loss: Int,
    val goal: Int, val lossGoal: Int, val netGoal: Int,
    val points: String, val ranking: String, val winProb: String,
)

/** 积分榜（getMatchTablesV2） */
data class TeamTables(
    val name: String,
    val total: TableRow, val home: TableRow, val away: TableRow,
)

/** 比赛近况单场（getMatchResultV1） */
data class RecentMatch(
    val date: String, val tournament: String,
    val home: String, val away: String,
    val score: String, val halfScore: String,
    val result: String, // 胜/平/负（本队视角）
)

/** 比赛近况整队（含汇总统计） */
data class RecentTeam(
    val name: String,
    val stat: String, // 如 "4胜2平4负  进14球 失14球 净0球"
    val matches: List<RecentMatch>,
)

/** 未来赛事单场（getFutureMatchesV1） */
data class FutureMatch(
    val date: String, val tournament: String,
    val home: String, val away: String, val round: String,
)

/** 射手信息（getMatchPlayerV1） */
data class PlayerStat(
    val no: String, val name: String, val position: String,
    val played: Int, val started: Int, val sub: Int,
    val goal: Int, val goalProb: String,
    val assist: Int, val assistProb: String,
    val goalAvg: String, val assistAvg: String,
)

/** 伤停一览（getInjurySuspensionV1） */
data class InjuryPlayer(
    val no: String, val name: String, val position: String,
    val injury: Boolean, val suspension: Boolean,
    val played: Int, val started: Int, val sub: Int,
)

/* ================= 实时比分（比分直播页 zqbfzb 同源） ================= */

/** 比分直播列表单场（getMatchDataPageListV1，含队名，用于补充已结束比赛） */
data class LiveMatchBrief(
    val matchId: String,
    val num: String,
    val league: String,
    val home: String,
    val away: String,
    val matchDate: String,
    val matchTime: String,
    val statusName: String,
)

/** 实时比分事件（进球/红牌等） */
data class LiveEvent(
    val minute: String,      // 事件发生分钟
    val name: String,        // 事件名：进球/红牌/点球…
    val homeScore: String, val awayScore: String,
    val teamType: String,    // home / away
)

/** 实时比分（getMatchLiveV1.qry） */
data class LiveScore(
    val matchId: Long,
    val status: String,      // matchStatus：4 直播中 / 5 直播中 / 6 直播结束 …
    val statusName: String,  // 直播中 / 直播结束 …
    val phaseName: String,   // matchPhaseTcName：上半场 / 中场 / 比赛结束 …
    val minute: String,      // matchMinute：当前比赛分钟
    val score: String,       // sectionsNo999：当前/全场比分
    val halfScore: String,   // sectionsNo1：半场比分
    val penalty: String,     // sectionsPenalty：点球比分
    val events: List<LiveEvent>,
) {
    val isLive: Boolean get() = status == "4" || status == "5"
    val isFinished: Boolean get() = status == "6"
}

/* ================= 赔率（zqdz 详情页 getFixedBonusV1 同源） ================= */

/** 单个赔率格：数值 + 变化方向（1 上升 / -1 下降 / 0 无变化 / 初盘） */
data class OddsCell(val value: String, val trend: Int) {
    val isUp: Boolean get() = trend == 1
    val isDown: Boolean get() = trend == -1
}

/** 赔率历史快照（value.oddsHistory 取最后一条时间线 = 最新赔率，自带方向字段） */
data class MatchOdds(
    val had: Triple<OddsCell, OddsCell, OddsCell>?,   // 胜/平/负
    val hhad: Triple<OddsCell, OddsCell, OddsCell>?,  // 让球胜/平/负
    val goalLine: String,                            // 让球盘口
    val crs: Map<String, OddsCell>?,                 // 全场比分 s{H}s{A} / s1sh…
    val hafu: Map<String, OddsCell>?,                // 半全场 hh/hd/ha…
    val ttg: Map<String, OddsCell>?,                 // 总进球 s0~s7
)

/* ================= 接口 ================= */

/**
 * 中国体育彩票 · 竞彩官网「赛事前瞻」数据接口（webapi.sporttery.cn/gateway/uniform/football）
 * 与官方 zqdz 详情页同源，需携带完整浏览器请求头规避 WAF。
 * 所有方法失败时抛出异常，由调用方降级。
 */
object MatchPreviewApi {

    private const val BASE = "https://webapi.sporttery.cn/gateway/uniform/football/"
    private const val BASE_LIVE = "https://webapi.sporttery.cn/gateway/uniform/fb/"

    private fun buildRequest(url: String, mid: String, referer: String = "https://www.sporttery.cn/jc/zqdz/index.html?showType=2&mid=$mid"): Request {
        return Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", referer)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
            .header("Accept-Encoding", "identity")
            .header("Connection", "keep-alive")
            .header("Origin", "https://www.sporttery.cn")
            .build()
    }

    /**
     * 实时比分（比分直播页同源接口 getMatchLiveV1.qry）
     * 返回 null 表示该场无直播/赛果数据；失败抛出异常。
     */
    suspend fun fetchLive(matchId: String): LiveScore? = withContext(Dispatchers.IO) {
        val url = "https://webapi.sporttery.cn/gateway/uniform/fb/getMatchLiveV1.qry" +
            "?matchIds=$matchId&eventTc="
        val resp = HttpClient.client.newCall(
            buildRequest(url, matchId, "https://www.sporttery.cn/jc/zqbfzb/")
        ).execute()
        resp.use {
            if (!it.isSuccessful) throw IOException("实时比分接口 HTTP ${it.code}")
            val body = it.body?.string() ?: throw IOException("实时比分响应为空")
            if (!body.trimStart().startsWith("{")) throw IOException("实时比分返回异常内容")
            val root = JSONObject(body)
            if (root.optString("errorCode") != "0") {
                throw IOException("实时比分返回错误: ${root.optString("errorMessage")}")
            }
            val arr = root.optJSONArray("value") ?: return@withContext null
            // 注意：接口返回当天全部比赛数组（不按 matchIds 过滤），必须自行匹配
            var o: JSONObject? = null
            for (i in 0 until arr.length()) {
                val it = arr.optJSONObject(i) ?: continue
                if (it.optLong("matchId").toString() == matchId) { o = it; break }
            }
            o ?: return@withContext null
            val events = mutableListOf<LiveEvent>()
            val el = o.optJSONArray("eventList")
            if (el != null) {
                for (i in 0 until el.length()) {
                    val e = el.optJSONObject(i) ?: continue
                    events.add(
                        LiveEvent(
                            minute = e.optString("eventMinute", ""),
                            name = e.optString("eventName", ""),
                            homeScore = e.optString("homeScore", ""),
                            awayScore = e.optString("awayScore", ""),
                            teamType = e.optString("teamType", ""),
                        )
                    )
                }
            }
            LiveScore(
                matchId = o.optLong("matchId"),
                status = o.optString("matchStatus", ""),
                statusName = o.optString("matchStatusName", ""),
                phaseName = o.optString("matchPhaseTcName", ""),
                minute = o.optString("matchMinute", ""),
                score = o.optString("sectionsNo999", ""),
                halfScore = o.optString("sectionsNo1", ""),
                penalty = o.optString("sectionsPenalty", ""),
                events = events,
            )
        }
    }

    /**
     * 拉取当天全部比分直播比赛（列表 + 实时比分），【已结束/进行中】比赛均在本期售彩列表之外，
     * 由这里补充到赛事中心。失败抛出异常。
     * @return Pair(列表简报, 实时比分map: matchId -> LiveScore)
     */
    suspend fun fetchLiveBriefs(): Pair<List<LiveMatchBrief>, Map<String, LiveScore>> =
        withContext(Dispatchers.IO) {
            val referer = "https://www.sporttery.cn/jc/zqbfzb/"
                // 1) 列表
                val listResp = HttpClient.client.newCall(
                    buildRequest(BASE_LIVE + "getMatchDataPageListV1.qry?method=live", "", referer)
                ).execute()
                val briefs = handleValue(listResp) { v ->
                    val out = mutableListOf<LiveMatchBrief>()
                    val dayArr = v.optJSONArray("matchInfoList")
                    if (dayArr != null) {
                        for (d in 0 until dayArr.length()) {
                            val day = dayArr.optJSONObject(d) ?: continue
                            val sub = day.optJSONArray("subMatchList") ?: continue
                            for (s in 0 until sub.length()) {
                                val m = sub.optJSONObject(s) ?: continue
                                out.add(
                                    LiveMatchBrief(
                                        matchId = m.optLong("matchId").toString(),
                                        num = m.optString("matchNumStr", ""),
                                        league = m.optString("leagueAbbName", ""),
                                        home = m.optString("homeTeamAbbName", ""),
                                        away = m.optString("awayTeamAbbName", ""),
                                        matchDate = m.optString("matchDate", "").take(10),
                                        matchTime = m.optString("matchTime", "").take(5),
                                        statusName = m.optString("matchStatusName", ""),
                                    )
                                )
                            }
                        }
                    }
                    out
                }
                // 2) 实时比分（getMatchLiveV1 的 value 是数组，不能走 handleValue；返回全部比赛，按 matchId 匹配）
                val scores = mutableMapOf<String, LiveScore>()
                val liveResp = HttpClient.client.newCall(
                    buildRequest(BASE_LIVE + "getMatchLiveV1.qry?matchIds=&eventTc=", "", referer)
                ).execute()
                liveResp.use {
                    if (!it.isSuccessful) throw IOException("比分直播接口 HTTP ${it.code}")
                    val body = it.body?.string() ?: throw IOException("比分直播响应为空")
                    if (!body.trimStart().startsWith("{")) throw IOException("比分直播返回异常内容")
                    val root = JSONObject(body)
                    if (root.optString("errorCode") != "0") {
                        throw IOException("比分直播返回错误: ${root.optString("errorMessage")}")
                    }
                    val arr = root.optJSONArray("value") ?: throw IOException("比分直播 value 为空")
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        if (o.optString("matchStatus", "").isBlank()) continue
                        val mid = o.optLong("matchId").toString()
                        val events = mutableListOf<LiveEvent>()
                        val el = o.optJSONArray("eventList")
                        if (el != null) {
                            for (k in 0 until el.length()) {
                                val e = el.optJSONObject(k) ?: continue
                                events.add(
                                    LiveEvent(
                                        minute = e.optString("eventMinute", ""),
                                        name = e.optString("eventName", ""),
                                        homeScore = e.optString("homeScore", ""),
                                        awayScore = e.optString("awayScore", ""),
                                        teamType = e.optString("teamType", ""),
                                    )
                                )
                            }
                        }
                        scores[mid] = LiveScore(
                            matchId = o.optLong("matchId"),
                            status = o.optString("matchStatus", ""),
                            statusName = o.optString("matchStatusName", ""),
                            phaseName = o.optString("matchPhaseTcName", ""),
                            minute = o.optString("matchMinute", ""),
                            score = o.optString("sectionsNo999", ""),
                            halfScore = o.optString("sectionsNo1", ""),
                            penalty = o.optString("sectionsPenalty", ""),
                            events = events,
                        )
                    }
                }
            briefs to scores
        }

    /**
     * 赔率历史（zqdz 详情页同源 getFixedBonusV1.qry），取 oddsHistory 最后一条时间线 = 最新赔率，
     * 各玩法自带方向字段（h/d/a → hf/df/af；键值玩法 → 值键名 + "f"）。失败抛出异常。
     */
    suspend fun fetchOdds(matchId: String): MatchOdds? = withContext(Dispatchers.IO) {
        val url = BASE + "getFixedBonusV1.qry?clientCode=3001&matchId=$matchId"
        val resp = HttpClient.client.newCall(buildRequest(url, matchId)).execute()
        handleValue(resp) { v ->
            val h = v.optJSONObject("oddsHistory") ?: return@handleValue null
            fun lastO(key: String): JSONObject? {
                val arr = h.optJSONArray(key) ?: return null
                return if (arr.length() > 0) arr.optJSONObject(arr.length() - 1) else null
            }
            fun triple(obj: JSONObject?): Triple<OddsCell, OddsCell, OddsCell>? =
                if (obj == null) null else Triple(
                    OddsCell(obj.optString("h", ""), obj.optInt("hf", 0)),
                    OddsCell(obj.optString("d", ""), obj.optInt("df", 0)),
                    OddsCell(obj.optString("a", ""), obj.optInt("af", 0)),
                )
            // 键值玩法：把非 f 后缀的值键收集，方向取「值键名 + "f"」
            fun cellMap(obj: JSONObject?): Map<String, OddsCell> {
                if (obj == null) return emptyMap()
                val out = mutableMapOf<String, OddsCell>()
                val names = obj.names() ?: return emptyMap()
                for (i in 0 until names.length()) {
                    val k = names.getString(i)
                    if (k.endsWith("f")) continue
                    val valStr = obj.optString(k, "")
                    if (valStr.isEmpty()) continue
                    out[k] = OddsCell(valStr, obj.optInt("${k}f", 0))
                }
                return out
            }
            val hhadO = lastO("hhadList")
            MatchOdds(
                had = triple(lastO("hadList")),
                hhad = triple(hhadO),
                // 让球数：官方 hhad 记录的字段名为 goalLine（未开赛/已完赛均为此；gl 仅部分赛事存在）
                goalLine = hhadO?.optString("goalLine")?.takeIf { it.isNotEmpty() }
                    ?: (hhadO?.optString("gl", "") ?: ""),
                crs = cellMap(lastO("crsList")),
                hafu = cellMap(lastO("hafuList")),
                ttg = cellMap(lastO("ttgList")),
            )
        }
    }

    /** 拉取 value 字段并交给 parse 处理 */
    private inline fun <T> handleValue(resp: okhttp3.Response, parse: (JSONObject) -> T): T {
        resp.use {
            if (!it.isSuccessful) throw IOException("比分直播接口 HTTP ${it.code}")
            val body = it.body?.string() ?: throw IOException("比分直播响应为空")
            if (!body.trimStart().startsWith("{")) throw IOException("比分直播返回异常内容")
            val root = JSONObject(body)
            if (root.optString("errorCode") != "0") {
                throw IOException("比分直播返回错误: ${root.optString("errorMessage")}")
            }
            val v = root.optJSONObject("value") ?: throw IOException("比分直播 value 为空")
            return parse(v)
        }
    }

    private suspend fun getJson(url: String, mid: String): JSONObject = withContext(Dispatchers.IO) {
        val resp = HttpClient.client.newCall(buildRequest(url, mid)).execute()
        resp.use {
            if (!it.isSuccessful) throw IOException("前瞻接口 HTTP ${it.code}")
            val body = it.body?.string() ?: throw IOException("前瞻接口响应为空")
            if (!body.trimStart().startsWith("{")) throw IOException("前瞻接口返回异常内容（可能被反爬拦截）")
            val root = JSONObject(body)
            if (root.optString("errorCode") != "0") {
                throw IOException("前瞻接口返回错误: ${root.optString("errorMessage")}")
            }
            root.optJSONObject("value") ?: throw IOException("前瞻接口 value 为空")
        }
    }

    /** 比赛头部：编号/赛事/队名/排名/赛季成绩 */
    suspend fun fetchHead(mid: String): MatchHead {
        val v = getJson(
            BASE + "getMatchHeadV1.qry?source=web&sportteryMatchId=$mid", mid
        )
        val homeStats = v.optJSONObject("wbsjStats")?.optJSONObject("home")
        val awayStats = v.optJSONObject("wbsjStats")?.optJSONObject("away")
        return MatchHead(
            matchNum = v.optString("matchNum", ""),
            league = v.optString("tournamentCnShortName", ""),
            homeName = v.optString("homeTeamShortName", ""),
            awayName = v.optString("awayTeamShortName", ""),
            matchDateTime = v.optString("matchDateTime", ""),
            homeLogo = v.optString("homeTeamLogoPath", ""),
            awayLogo = v.optString("awayTeamLogoPath", ""),
            homeRank = homeStats?.optString("ranking", "") ?: "",
            awayRank = awayStats?.optString("ranking", "") ?: "",
            homeSeason = seasonStats(homeStats),
            awaySeason = seasonStats(awayStats),
            homeHomeSeason = homeStats?.let { "${it.optInt("sHomeWinGoalMatchCnt")}胜${it.optInt("sHomeDrawMatchCnt")}平${it.optInt("sHomeLossGoalMatchCnt")}负" } ?: "",
            awayAwaySeason = awayStats?.let { "${it.optInt("sAwayWinGoalMatchCnt")}胜${it.optInt("sAwayDrawMatchCnt")}平${it.optInt("sAwayLossGoalMatchCnt")}负" } ?: "",
        )
    }

    private fun seasonStats(s: JSONObject?): String = s?.let {
        "${it.optInt("sWinGoalMatchCnt")}胜${it.optInt("sDrawMatchCnt")}平${it.optInt("sLossGoalMatchCnt")}负"
    } ?: ""

    /** 特征分析：交锋/战况/场均进球失球 */
    suspend fun fetchFeature(mid: String): MatchFeature {
        val v = getJson(
            BASE + "getMatchFeatureV1.qry?termLimits=10&sportteryMatchId=$mid", mid
        )
        fun dim(key: String): FeatureDim? {
            val o = v.optJSONObject(key) ?: return null
            return FeatureDim(
                homeWin = o.optInt("homeWinGoalMatchCnt"),
                homeDraw = o.optInt("homeDrawMatchCnt"),
                homeLoss = o.optInt("homeLossGoalMatchCnt"),
                awayWin = o.optInt("awayWinGoalMatchCnt"),
                awayDraw = o.optInt("awayDrawMatchCnt"),
                awayLoss = o.optInt("awayLossGoalMatchCnt"),
                totalLeg = o.optInt("totalLegCnt"),
            )
        }
        val goalAvg = v.optJSONObject("goalAvg")
        val lossAvg = v.optJSONObject("lossGoalAvg")
        return MatchFeature(
            last10 = dim("last"),
            sameHomeAway = dim("sameHomeAway"),
            last10Form = dim("eachHomeAway"),
            sameHomeAwayForm = dim("eachSameHomeAway"),
            homeGoalAvg = goalAvg?.optString("homeGoalAvgCnt", "") ?: "",
            awayGoalAvg = goalAvg?.optString("awayGoalAvgCnt", "") ?: "",
            homeLossAvg = lossAvg?.optString("homeLossGoalAvgCnt", "") ?: "",
            awayLossAvg = lossAvg?.optString("awayLossGoalAvgCnt", "") ?: "",
        )
    }

    /** 历史交锋：列表 + 汇总 */
    suspend fun fetchHistory(mid: String): Pair<List<H2hMatch>, H2hSummary?> {
        val v = getJson(
            BASE + "getResultHistoryV1.qry?sportteryMatchId=$mid&termLimits=10&tournamentFlag=0&homeAwayFlag=0", mid
        )
        val list = parseMatchArray(v.optJSONArray("matchList")) { o ->
            H2hMatch(
                date = o.optString("matchDate", "").take(10),
                tournament = o.optString("tournamentShortName", ""),
                home = o.optString("homeTeamShortName", ""),
                away = o.optString("awayTeamShortName", ""),
                score = o.optString("fullCourtGoal", ""),
                halfScore = o.optString("halfTimeGoal", ""),
                totalGoal = o.optInt("totalTeamFullCourtGoalCnt", 0),
            )
        }
        val s = v.optJSONObject("statistics")
        val summary = s?.let {
            H2hSummary(
                teamName = it.optString("teamShortName", ""),
                win = it.optInt("winGoalMatchCnt"),
                draw = it.optInt("drawMatchCnt"),
                loss = it.optInt("lossGoalMatchCnt"),
                winProb = it.optString("winProbability", ""),
                drawProb = it.optString("drawProbability", ""),
                lossProb = it.optString("lossProbability", ""),
            )
        }
        return list to summary
    }

    /** 积分榜：主/客两队 */
    suspend fun fetchTables(mid: String): Pair<TeamTables?, TeamTables?> {
        val v = getJson(
            BASE + "getMatchTablesV2.qry?gmMatchId=$mid", mid
        )
        fun tables(key: String): TeamTables? {
            val o = v.optJSONObject(key) ?: return null
            fun row(scope: String, r: JSONObject?): TableRow = r?.let {
                TableRow(
                    scope = scope,
                    played = it.optInt("totalLegCnt"),
                    win = it.optInt("winGoalMatchCnt"),
                    draw = it.optInt("drawMatchCnt"),
                    loss = it.optInt("lossGoalMatchCnt"),
                    goal = it.optInt("goalCnt"),
                    lossGoal = it.optInt("lossGoalCnt"),
                    netGoal = it.optInt("netGoal"),
                    points = it.optString("points", ""),
                    ranking = it.optString("ranking", ""),
                    winProb = it.optString("winProbability", ""),
                )
            } ?: TableRow(scope, 0, 0, 0, 0, 0, 0, 0, "", "", "")
            return TeamTables(
                name = o.optJSONObject("total")?.optString("teamShortName", "") ?: "",
                total = row("总", o.optJSONObject("total")),
                home = row("主", o.optJSONObject("home")),
                away = row("客", o.optJSONObject("away")),
            )
        }
        return tables("homeTables") to tables("awayTables")
    }

    /** 比赛近况：主/客两队近 10 场 */
    suspend fun fetchResults(mid: String): Pair<RecentTeam?, RecentTeam?> {
        val v = getJson(
            BASE + "getMatchResultV1.qry?sportteryMatchId=$mid&termLimits=10&tournamentFlag=0&homeAwayFlag=0", mid
        )
        fun team(key: String): RecentTeam? {
            val o = v.optJSONObject(key) ?: return null
            val stat = o.optJSONObject("statistics")
            val name = stat?.optString("teamShortName", "") ?: o.optJSONArray("matchList")?.optJSONObject(0)?.optString("homeTeamShortName", "") ?: ""
            val matches = parseMatchArray(o.optJSONArray("matchList")) { m ->
                RecentMatch(
                    date = m.optString("matchDate", "").take(10),
                    tournament = m.optString("tournamentShortName", ""),
                    home = m.optString("homeTeamShortName", ""),
                    away = m.optString("awayTeamShortName", ""),
                    score = m.optString("fullCourtGoal", ""),
                    halfScore = m.optString("halfTimeGoal", ""),
                    result = when (m.optString("teamMatchResult", "")) {
                        "home" -> "胜"
                        "draw" -> "平"
                        "away" -> "负"
                        else -> "-"
                    },
                )
            }
            val statLine = stat?.let {
                "${it.optInt("winGoalMatchCnt")}胜${it.optInt("drawMatchCnt")}平${it.optInt("lossGoalMatchCnt")}负  进${it.optInt("goalCnt")}球 失${it.optInt("lossGoalCnt")}球 净${it.optInt("netGoal")}球"
            } ?: ""
            return RecentTeam(name, statLine, matches)
        }
        return team("home") to team("away")
    }

    /** 未来赛事：主/客两队各 4 场 */
    suspend fun fetchFuture(mid: String): Pair<List<FutureMatch>, List<FutureMatch>> {
        val v = getJson(
            BASE + "getFutureMatchesV1.qry?sportteryMatchId=$mid&termLimits=4", mid
        )
        fun list(key: String): List<FutureMatch> =
            parseMatchArray(v.optJSONObject(key)?.optJSONArray("matchList")) { m ->
                FutureMatch(
                    date = m.optString("matchDateTime", "").take(10),
                    tournament = m.optString("tournamentShortName", ""),
                    home = m.optString("homeTeamShortName", ""),
                    away = m.optString("awayTeamShortName", ""),
                    round = m.optString("phaseName", "").let { p ->
                        val g = m.optString("gameweek", "")
                        if (p.isNotEmpty() && g.isNotEmpty()) "$p 第${g}轮" else p
                    },
                )
            }
        return list("home") to list("away")
    }

    /** 射手信息：主/客两队 */
    suspend fun fetchPlayers(mid: String): Pair<List<PlayerStat>, List<PlayerStat>> {
        val v = getJson(
            BASE + "getMatchPlayerV1.qry?sportteryMatchId=$mid&termLimits=3", mid
        )
        fun list(key: String): List<PlayerStat> =
            parseMatchArray(v.optJSONObject(key)?.optJSONArray("playerList")) { p ->
                PlayerStat(
                    no = p.optString("uniformNo", ""),
                    name = p.optString("personName", ""),
                    position = p.optString("playerPositionDesc", ""),
                    played = p.optInt("appearanceCnt"),
                    started = p.optInt("startedMatchCnt"),
                    sub = p.optInt("substituteMatchCnt"),
                    goal = p.optInt("goalCnt"),
                    goalProb = p.optString("goalProbability", ""),
                    assist = p.optInt("assistCnt"),
                    assistProb = p.optString("assistProbability", ""),
                    goalAvg = p.optString("goalAvgCnt", ""),
                    assistAvg = p.optString("assistAvgCnt", ""),
                )
            }
        return list("home") to list("away")
    }

    /** 伤停一览：主/客两队 */
    suspend fun fetchInjuries(mid: String): Pair<List<InjuryPlayer>, List<InjuryPlayer>> {
        val v = getJson(
            BASE + "getInjurySuspensionV1.qry?sportteryMatchId=$mid", mid
        )
        fun list(key: String): List<InjuryPlayer> =
            parseMatchArray(v.optJSONObject(key)?.optJSONArray("injuriesAndSuspensionsList")) { p ->
                InjuryPlayer(
                    no = p.optString("uniformNo", ""),
                    name = p.optString("personName", ""),
                    position = p.optString("playerPositionDesc", ""),
                    injury = p.optInt("injuryFlag") == 1,
                    suspension = p.optInt("suspensionFlag") == 1,
                    played = p.optInt("appearanceCnt"),
                    started = p.optInt("startedMatchCnt"),
                    sub = p.optInt("substituteMatchCnt"),
                )
            }
        return list("home") to list("away")
    }

    private inline fun <T> parseMatchArray(arr: JSONArray?, parse: (JSONObject) -> T): List<T> {
        if (arr == null) return emptyList()
        val out = mutableListOf<T>()
        for (i in 0 until arr.length()) {
            out.add(parse(arr.optJSONObject(i) ?: continue))
        }
        return out
    }
}
