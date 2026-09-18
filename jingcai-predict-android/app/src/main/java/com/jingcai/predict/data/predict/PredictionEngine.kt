package com.jingcai.predict.data.predict

import com.jingcai.predict.data.remote.H2hMatch
import com.jingcai.predict.data.remote.H2hSummary
import com.jingcai.predict.data.remote.MatchFeature
import com.jingcai.predict.data.remote.MatchOdds
import com.jingcai.predict.data.remote.RecentTeam
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.remote.TeamTables
import kotlin.math.roundToInt

/**
 * 预测引擎：三路真实数据信号（市场赔率 / 双泊松统计 / 官方统计）→ 加权融合 → 校准 → 置信度。
 *
 * 红线约束（严格遵守）：
 * 1. 输入全部来自竞彩官方真实接口，禁止任何虚构数据。
 * 2. 信号缺失时降级（重归一化剩余权重），绝不编造。
 * 3. 置信度基于概率差距与数据完整度诚实计算，禁止虚高。
 */
object PredictionEngine {

    /** 轻量预测：仅市场赔率信号（综合信心榜用，覆盖全部比赛，零额外请求） */
    fun predictLight(match: RemoteMatch, profile: LeagueProfile): PredictionResult =
        runPrediction(match, profile, null, null, null, null, null)

    /** 深度预测：三路信号完整融合（详情页用，需先并行拉取官方前瞻数据） */
    fun predictDeep(
        match: RemoteMatch,
        profile: LeagueProfile,
        feature: MatchFeature?,
        tables: Pair<TeamTables?, TeamTables?>?,
        results: Pair<RecentTeam?, RecentTeam?>?,
        history: Pair<List<H2hMatch>, H2hSummary?>?,
        odds: MatchOdds?,
    ): PredictionResult = runPrediction(match, profile, feature, tables, results, history, odds)

    private fun runPrediction(
        match: RemoteMatch,
        profile: LeagueProfile,
        feature: MatchFeature?,
        tables: Pair<TeamTables?, TeamTables?>?,
        results: Pair<RecentTeam?, RecentTeam?>?,
        history: Pair<List<H2hMatch>, H2hSummary?>?,
        odds: MatchOdds?,
    ): PredictionResult {
        val home = match.home
        val away = match.away

        /* ---------- 信号A：市场赔率隐含概率（优先赔率历史，其次列表赔率） ---------- */
        val hadRaw: Triple<Double, Double, Double>? =
            odds?.had?.let { (h, d, a) ->
                Triple(h.value.toDoubleOrNull() ?: 0.0, d.value.toDoubleOrNull() ?: 0.0, a.value.toDoubleOrNull() ?: 0.0)
            } ?: match.had?.let { (h, d, a) ->
                Triple(h.toDoubleOrNull() ?: 0.0, d.toDoubleOrNull() ?: 0.0, a.toDoubleOrNull() ?: 0.0)
            }
        val aAvail = hadRaw != null && listOf(hadRaw.first, hadRaw.second, hadRaw.third).count { it > 1.0 } >= 2
        val pA = hadRaw?.takeIf { aAvail }?.let { OddsMath.impliedWdl(it.first, it.second, it.third) }

        /* ---------- 信号B：双泊松比分矩阵（Dixon-Coles τ 修正） ---------- */
        var matrix: Array<DoubleArray>? = null
        var lh = 0.0
        var la = 0.0
        val bAvail = feature != null &&
            feature.homeGoalAvg.toDoubleOrNull() != null &&
            feature.awayGoalAvg.toDoubleOrNull() != null &&
            feature.homeLossAvg.toDoubleOrNull() != null &&
            feature.awayLossAvg.toDoubleOrNull() != null
        if (bAvail) {
            val hg = feature.homeGoalAvg.toDoubleOrNull()!!
            val ag = feature.awayGoalAvg.toDoubleOrNull()!!
            val hl = feature.homeLossAvg.toDoubleOrNull()!!
            val al = feature.awayLossAvg.toDoubleOrNull()!!
            // 攻击力/防守力相对联赛基准估计（Dixon-Coles 的简化 log-linear 形式）
            lh = hg * al / profile.baseAway
            la = ag * hl / profile.baseHome
            matrix = Poisson.scoreMatrix(lh, la, profile.rho)
        }
        val pB = matrix?.let { Poisson.wdl(it) }

        /* ---------- 信号C：官方统计经验概率（交锋胜率 + 近10场战况计数） ---------- */
        val cList = mutableListOf<Triple<Double, Double, Double>>()
        history?.second?.let { s ->
            val wp = s.winProb.toDoubleOrNull()
            val dp = s.drawProb.toDoubleOrNull()
            val lp = s.lossProb.toDoubleOrNull()
            if (wp != null && dp != null && lp != null && wp + dp + lp > 0.0) {
                cList += normalize(wp, dp, lp)
            }
        }
        feature?.last10Form?.let { f ->
            val hTotal = f.homeWin + f.homeDraw + f.homeLoss
            val aTotal = f.awayWin + f.awayDraw + f.awayLoss
            if (hTotal > 0 && aTotal > 0) {
                val d = (hTotal + aTotal).toDouble()
                // 主队胜：主队赢或客队输；客队胜：客队赢或主队输；平：双方打平
                cList += normalize(
                    (f.homeWin + f.awayLoss) / d,
                    (f.homeDraw + f.awayDraw) / d,
                    (f.awayWin + f.homeLoss) / d,
                )
            }
        }
        val cAvail = cList.isNotEmpty()
        val pC = if (cAvail) average(cList) else null

        /* ---------- 加权融合（缺失信号自动重归一化） ---------- */
        val w = profile.weights.normalized()
        val wA = if (aAvail) w.market else 0.0
        val wB = if (bAvail) w.poisson else 0.0
        val wC = if (cAvail) w.stat else 0.0
        val tw = wA + wB + wC

        var ph: Double
        var pd: Double
        var pa: Double
        if (tw <= 0.0) {
            // 全部信号缺失（几乎不可能，防御性兜底）：均匀概率
            ph = 1.0 / 3; pd = 1.0 / 3; pa = 1.0 / 3
        } else {
            var accH = 0.0; var accD = 0.0; var accA = 0.0
            if (aAvail && pA != null) {
                accH += wA * pA.first; accD += wA * pA.second; accA += wA * pA.third
            }
            if (bAvail && pB != null) {
                accH += wB * pB.first; accD += wB * pB.second; accA += wB * pB.third
            }
            if (cAvail && pC != null) {
                accH += wC * pC.first; accD += wC * pC.second; accA += wC * pC.third
            }
            val (h, d, a) = calibrate(accH / tw, accD / tw, accA / tw, profile.drawBias)
            ph = h; pd = d; pa = a
        }

        /* ---------- 置信度（margin + 数据完整度 + 信号一致性，诚实计算） ---------- */
        val complete = ((if (aAvail) 1 else 0) + (if (bAvail) 1 else 0) + (if (cAvail) 1 else 0)) / 3.0
        val probs = listOf(ph, pd, pa)
        val maxIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        val sorted = probs.sortedDescending()
        val margin = sorted[0] - sorted[1]
        var conf = 55.0 + margin * 110.0
        conf = 55.0 + (conf - 55.0) * complete
        val dirs = listOf(
            pA?.let { argmaxOf(it) },
            pB?.let { argmaxOf(it) },
            pC?.let { argmaxOf(it) },
        ).filterNotNull()
        when {
            dirs.isNotEmpty() && dirs.all { it == maxIdx } -> conf += 4
            dirs.count { it == maxIdx } >= 2 -> conf += 2
            dirs.size >= 2 -> conf -= 6
        }
        conf = conf.coerceIn(35.0, 98.0)
        val confInt = conf.roundToInt()

        /* ---------- 玩法预测 ---------- */
        val hhadRaw: Triple<Double, Double, Double>? =
            odds?.hhad?.let { (h, d, a) ->
                Triple(h.value.toDoubleOrNull() ?: 0.0, d.value.toDoubleOrNull() ?: 0.0, a.value.toDoubleOrNull() ?: 0.0)
            } ?: match.hhad?.let { (h, d, a) ->
                Triple(h.toDoubleOrNull() ?: 0.0, d.toDoubleOrNull() ?: 0.0, a.toDoubleOrNull() ?: 0.0)
            }
        val hdpLine = (odds?.goalLine ?: match.goalLine).takeIf { it.isNotBlank() } ?: ""
        val hdpPick = hhadRaw?.let { hh ->
            val p = OddsMath.impliedProb(listOf(hh.first, hh.second, hh.third))
            if (p.any { it > 0.0 }) {
                listOf("让胜", "让平", "让负")[p.indices.maxByOrNull { p[it] } ?: 0]
            } else "--"
        } ?: "--"

        val (scorePick, _) = if (matrix != null) Poisson.mostLikelyScore(matrix) else "--" to 0.0
        val (hfPick, _) = if (matrix != null) Poisson.halfFull(lh, la, matrix) else "--" to 0.0
        val (totalPick, _) = if (matrix != null) Poisson.mostLikelyTotal(matrix) else "--" to 0.0

        /* ---------- 信号说明与要点 ---------- */
        val signalNote = buildList {
            if (aAvail) add("市场")
            if (bAvail) add("统计")
            if (cAvail) add("官方")
        }.joinToString("+").ifEmpty { "无数据" }

        val key = buildKey(match, feature, tables, results, history)

        val oddsH = hadRaw?.first ?: 0.0
        val oddsD = hadRaw?.second ?: 0.0
        val oddsA = hadRaw?.third ?: 0.0

        return PredictionResult(
            matchId = match.matchId,
            num = match.num,
            league = match.league,
            home = home,
            away = away,
            kickoff = kickoffOf(match.time),
            homeProb = ph, drawProb = pd, awayProb = pa,
            wdlPick = listOf("主胜", "平局", "客胜")[maxIdx],
            hdpPick = hdpPick,
            hdpLine = hdpLine,
            scorePick = scorePick,
            hfPick = hfPick,
            totalPick = totalPick,
            conf = confInt,
            dataComplete = complete,
            signalNote = signalNote,
            key = key,
            homeOdds = oddsH, drawOdds = oddsD, awayOdds = oddsA,
        )
    }

    /* ================= 工具函数 ================= */

    private fun calibrate(h: Double, d: Double, a: Double, drawBias: Double): Triple<Double, Double, Double> {
        var hh = h.coerceIn(0.02, 0.80)
        var dd = d.coerceAtLeast(drawBias).coerceIn(0.02, 0.80)
        var aa = a.coerceIn(0.02, 0.80)
        val s = hh + dd + aa
        if (s <= 0.0) return Triple(1.0 / 3, 1.0 / 3, 1.0 / 3)
        return Triple(hh / s, dd / s, aa / s)
    }

    private fun normalize(h: Double, d: Double, a: Double): Triple<Double, Double, Double> {
        val s = h + d + a
        if (s <= 0.0) return Triple(1.0 / 3, 1.0 / 3, 1.0 / 3)
        return Triple(h / s, d / s, a / s)
    }

    private fun average(list: List<Triple<Double, Double, Double>>): Triple<Double, Double, Double> {
        var h = 0.0; var d = 0.0; var a = 0.0
        list.forEach { h += it.first; d += it.second; a += it.third }
        return Triple(h / list.size, d / list.size, a / list.size)
    }

    private fun argmaxOf(t: Triple<Double, Double, Double>): Int =
        listOf(t.first, t.second, t.third).indices.maxByOrNull { listOf(t.first, t.second, t.third)[it] } ?: 0

    /** "2026-09-18 02:30:00" → "02:30"；"19:35" → "19:35" */
    private fun kickoffOf(time: String): String {
        val t = time.substringAfter(' ', time)
        return t.take(5)
    }

    /** 要点：由真实数据拼装，某数据缺失则该句不生成（绝不编造） */
    private fun buildKey(
        match: RemoteMatch,
        feature: MatchFeature?,
        tables: Pair<TeamTables?, TeamTables?>?,
        results: Pair<RecentTeam?, RecentTeam?>?,
        history: Pair<List<H2hMatch>, H2hSummary?>?,
    ): String {
        val sb = StringBuilder()
        val homeRank = match.homeRank.takeIf { it.isNotBlank() }
            ?: tables?.first?.total?.ranking?.takeIf { it.isNotBlank() }
        val awayRank = match.awayRank.takeIf { it.isNotBlank() }
            ?: tables?.second?.total?.ranking?.takeIf { it.isNotBlank() }
        if (homeRank != null && awayRank != null) {
            sb.append("联赛排名 ${match.home}$homeRank  vs ${match.away}$awayRank；")
        }
        history?.second?.let { h ->
            val n = h.win + h.draw + h.loss
            if (n > 0) {
                sb.append("近$n 次交锋 ${h.teamName} ${h.win}胜${h.draw}平${h.loss}负；")
            }
        }
        results?.first?.let { r ->
            if (r.stat.isNotEmpty()) sb.append("${match.home} 近况 ${r.stat}；")
        }
        results?.second?.let { r ->
            if (r.stat.isNotEmpty()) sb.append("${match.away} 近况 ${r.stat}；")
        }
        feature?.let { f ->
            if (f.homeGoalAvg.isNotBlank() && f.awayLossAvg.isNotBlank()) {
                sb.append("${match.home} 场均进球 ${f.homeGoalAvg} / ${match.away} 场均失球 ${f.awayLossAvg}；")
            }
        }
        if (sb.isEmpty()) sb.append("本场深度数据暂不完整，预测基于官方赔率市场信号。")
        else sb.setLength(sb.length - 1) // 去掉末尾分号
        sb.append("。")
        return sb.toString()
    }
}
