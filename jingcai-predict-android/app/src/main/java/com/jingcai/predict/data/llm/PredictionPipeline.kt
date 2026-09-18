package com.jingcai.predict.data.llm

import com.jingcai.predict.data.predict.LeagueProfile
import com.jingcai.predict.data.predict.OptionProbability
import com.jingcai.predict.data.predict.PredictionEngine
import com.jingcai.predict.data.predict.PredictionResult
import com.jingcai.predict.data.remote.H2hSummary
import com.jingcai.predict.data.remote.InjuryPlayer
import com.jingcai.predict.data.remote.MatchFeature
import com.jingcai.predict.data.remote.MatchOdds
import com.jingcai.predict.data.remote.MatchPreviewApi
import com.jingcai.predict.data.remote.PlayerStat
import com.jingcai.predict.data.remote.RecentTeam
import com.jingcai.predict.data.remote.RemoteMatch
import com.jingcai.predict.data.remote.TeamTables
import com.jingcai.predict.data.search.SearchHit
import com.jingcai.predict.data.search.WebSearch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 统一预测流水线：**模型判断为核心 + 概率计算为基础，产出一个综合结论**。
 *
 * 分工（红线）：
 * - 本地计算负责：真实数据、真实赔率、各选项概率、期望值、置信度口径 —— 全部可复算；
 * - 模型负责：依据上述数据做判断（选型、打分、理由、分节分析、赛前情报），模型不提供任何数字；
 * - 综合结论：选项以模型判断为主（无模型结果时用概率主选），概率/赔率/置信度一律取计算值。
 */
object PredictionPipeline {

    /**
     * 单次模型调用的超时（毫秒）。超时只影响**这一场**的模型部分：
     * 该场照常产出计算结果并如实标注"模型响应超时"，不会阻塞其它比赛，也不会让页面卡死。
     */
    private const val AI_TIMEOUT_MS = 90_000L

    /**
     * 单场总时长预算（毫秒）：与 [PredictionBatchRunner] 的单场硬超时（180s）对齐。
     * 官方前瞻抓取、联网检索与两次模型调用**共享**该预算 ——
     * 联网检索的 20s 上限因此被计入本场预算，模型调用也不会因为检索耗时叠加而撞上外层硬超时。
     */
    private const val MATCH_BUDGET_MS = 180_000L

    /** 联网检索的单次超时（毫秒）：上限 20s，与检索客户端的整体超时口径一致 */
    private const val SEARCH_TIMEOUT_MS = 20_000L

    /** 发起检索时请求的条数（也是落盘来源条数上限） */
    private const val SEARCH_RESULT_COUNT = 5

    /** 检索词长度上限（字符）：服务商（智谱）要求 search_query ≤ 70 字符 */
    private const val SEARCH_QUERY_MAX = 70

    /** 一次联网检索的结果与状态（hits 为空时由 state 说明真实原因，绝不补占位数据） */
    private data class SearchAttempt(val state: String, val hits: List<SearchHit>)

    /** 单场完整预测产物（含计算结果与模型结果的融合），供详情页 / 分析页 / 后台任务统一使用 */
    data class Built(
        val prediction: CombinedPrediction,
        val engine: PredictionResult,
        val ai: AiAnalysis?,
        val aiError: String?,
        val previewError: String?,
    )

    /**
     * 构建单场综合预测（不落盘，由调用方决定是否写入 [AiPredictionStore]）。
     * @param withPreview 是否同时生成前瞻「赛前情报」（后台批量时可开，节约调用）
     * @param previewOnly 仅补前瞻情报（复核时若已有情报可跳过）
     */
    suspend fun build(
        match: RemoteMatch,
        profile: LeagueProfile,
        cfg: LlmConfig?,
        withPreview: Boolean = true,
        reviewCount: Int = 0,
        createdAt: Long = System.currentTimeMillis(),
    ): Built = coroutineScope {
        val startedAt = System.currentTimeMillis()
        val validCfg = cfg?.takeIf { it.ready }

        // 0) 联网检索（真实网络来源）：与官方前瞻抓取**并发**执行，单次上限 20s；
        //    检索失败 / 无结果只记录真实状态，绝不阻断本场预测，也绝不伪造来源。
        val searchJob = validCfg
            ?.takeIf { it.searchEnabled && withPreview }
            ?.let { c -> async { runSearch(c, match) } }

        // 1) 官方前瞻数据（全部真实接口，逐项失败独立降级）
        val head = async { runCatching { MatchPreviewApi.fetchHead(match.matchId) }.getOrNull() }
        val feature = async { runCatching { MatchPreviewApi.fetchFeature(match.matchId) }.getOrNull() }
        val tables = async { runCatching { MatchPreviewApi.fetchTables(match.matchId) }.getOrNull() }
        val results = async { runCatching { MatchPreviewApi.fetchResults(match.matchId) }.getOrNull() }
        val history = async { runCatching { MatchPreviewApi.fetchHistory(match.matchId) }.getOrNull() }
        val players = async { runCatching { MatchPreviewApi.fetchPlayers(match.matchId) }.getOrNull() }
        val injuries = async { runCatching { MatchPreviewApi.fetchInjuries(match.matchId) }.getOrNull() }
        val future = async { runCatching { MatchPreviewApi.fetchFuture(match.matchId) }.getOrNull() }
        val odds = async { runCatching { MatchPreviewApi.fetchOdds(match.matchId) }.getOrNull() }
        val live = async { runCatching { MatchPreviewApi.fetchLive(match.matchId) }.getOrNull() }

        val f = feature.await()
        val t = tables.await()
        val r = results.await()
        val h = history.await()
        val p = players.await()
        val inj = injuries.await()
        val fut = future.await()
        val o = odds.await()
        val lv = live.await()
        val hd = head.await()

        // 2) 本地计算：深度预测
        val engine = PredictionEngine.predictDeep(match, profile, f, t, r, h, o)

        // 3) 本地计算：各玩法全部选项概率 + 真实候选池
        val probs = if (engine.lambdaHome > 0.0 && engine.lambdaAway > 0.0) {
            OptionProbability.all(
                engine.lambdaHome,
                engine.lambdaAway,
                profile.rho,
                engine.hdpLine.ifEmpty { match.goalLine }.toIntOrNull(),
            )
        } else {
            emptyMap()
        }
        val oddsMap = oddsByPlay(match, o)
        val candidates = buildCandidates(oddsMap, probs)

        // 4) 命中判定（仅已完赛）
        val result = resultOf(match, lv)

        // 5) 前瞻情报 + 模型分析（模型不可用时如实记录原因）
        //    联网检索结果在此收口：作为真实网络来源拼进情报 prompt（有则允许引用并注明媒体，无则保留禁止联网规则）
        val searchAttempt = searchJob?.await()
        val searchHits = searchAttempt?.hits.orEmpty()

        var previewErr: String? = null
        var preview = ""
        if (validCfg != null && withPreview) {
            val budget = minOf(AI_TIMEOUT_MS, remainingMs(startedAt))
            if (budget <= 0L) {
                previewErr = "单场时长预算已用尽（含联网检索耗时），已按计算结果继续"
            } else {
                val r = withTimeoutOrNull(budget) {
                    PreviewAnalyzer.generate(
                        validCfg,
                        previewContext(match, hd, f, t, r, h, p, inj, fut, engine),
                        searchHits,
                    )
                }
                if (r == null) previewErr = "模型响应超时（${budget / 1000}秒），已按计算结果继续"
                else r.onSuccess { preview = it }
                    .onFailure { previewErr = it.message ?: "未知错误" }
            }
        }

        var aiErr: String? = null
        var ai: AiAnalysis? = null
        if (validCfg != null && candidates.isNotEmpty()) {
            val budget = minOf(AI_TIMEOUT_MS, remainingMs(startedAt))
            if (budget <= 0L) {
                aiErr = "单场时长预算已用尽（含联网检索耗时），已按计算结果继续"
            } else {
                val ctx = aiContext(match, hd, engine, o, t, r, h, inj, p, result, profile, preview)
                val r2 = withTimeoutOrNull(budget) { LlmAnalyzer.analyze(validCfg, ctx, candidates) }
                if (r2 == null) aiErr = "模型响应超时（${budget / 1000}秒），已按计算结果继续"
                else r2.onSuccess { ai = it }
                    .onFailure { aiErr = it.message ?: "未知错误" }
            }
        } else if (validCfg != null) {
            aiErr = "本场无可用真实赔率，未发起模型分析"
        }

        // 6) 综合结论
        val picks = combinePicks(engine, ai, oddsMap, probs, result)
        val bestValue = bestValuePick(candidates, ai, picks, result)
        val safest = safestPick(candidates, ai, picks, result)
        val sections = ai?.takeIf { it.sections.isNotEmpty() }?.sections
            ?: localSections(match, engine, t, r, h, inj, result, probs.isNotEmpty())

        val now = System.currentTimeMillis()
        val cp = CombinedPrediction(
            matchId = match.matchId,
            matchNum = match.num,
            league = match.league,
            home = match.home,
            away = match.away,
            kickoff = kickoffText(match.time),
            statusText = statusTextOf(match.status),
            picks = picks,
            bestValue = bestValue,
            safest = safest,
            sections = sections,
            preview = preview,
            engineConf = engine.conf,
            signalNote = engine.signalNote,
            dataComplete = engine.dataComplete,
            key = engine.key,
            model = if (ai != null || preview.isNotEmpty()) validCfg?.model.orEmpty() else "",
            createdAt = createdAt,
            updatedAt = now,
            reviewCount = reviewCount,
            // 检索状态与命中的真实来源随情报一起落盘（未启用检索时为空）
            searchState = searchAttempt?.state.orEmpty(),
            searchSources = searchHits.take(SEARCH_RESULT_COUNT),
        )
        Built(cp, engine, ai, aiErr, previewErr)
    }

    /* ================= 联网检索（真实网络来源） ================= */

    /**
     * 执行一次联网检索，并把**真实状态**归一到快照口径：
     * `"ok"`（命中）/ `"empty"`（返回 0 条）/ `"failed:<原因>"`（含 HTTP 状态码与响应体片段）。
     * 失败只记录原因、不抛异常，**不阻断本场预测**；协程取消继续向上抛出。
     */
    private suspend fun runSearch(cfg: LlmConfig, match: RemoteMatch): SearchAttempt {
        val result = try {
            withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                WebSearch.search(cfg, searchQuery(match), SEARCH_RESULT_COUNT)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SearchAttempt("failed:${e.message ?: e.javaClass.simpleName}", emptyList())
        }
        if (result == null) {
            return SearchAttempt("failed:检索超时（${SEARCH_TIMEOUT_MS / 1000}秒）", emptyList())
        }
        return result.fold(
            onSuccess = { hits ->
                if (hits.isEmpty()) SearchAttempt("empty", emptyList())
                else SearchAttempt("ok", hits.take(SEARCH_RESULT_COUNT))
            },
            onFailure = { e -> SearchAttempt("failed:${e.message ?: e.javaClass.simpleName}", emptyList()) },
        )
    }

    /** 检索词：只用**真实已有数据**（主队 / 客队 / 联赛名 + 伤停 / 首发），并截断到服务商上限 70 字符 */
    private fun searchQuery(match: RemoteMatch): String {
        val raw = listOf(match.home, match.away, match.league, "伤停", "首发")
            .filter { it.isNotBlank() }
            .joinToString(" ")
        return if (raw.length <= SEARCH_QUERY_MAX) raw else raw.take(SEARCH_QUERY_MAX)
    }

    /** 本场剩余时长预算（毫秒）：抓取 + 检索 + 模型调用共享 [MATCH_BUDGET_MS] */
    private fun remainingMs(startedAt: Long): Long =
        (MATCH_BUDGET_MS - (System.currentTimeMillis() - startedAt)).coerceAtLeast(0L)

    /* ================= 综合结论 ================= */

    private fun combinePicks(
        engine: PredictionResult,
        ai: AiAnalysis?,
        oddsMap: Map<String, Map<String, String>>,
        probs: Map<String, Map<String, Double>>,
        result: SlipSettlement4Hit?,
    ): List<CombinedPick> {
        val enginePickOf = mapOf(
            SlipPlayCodes.HAD to engine.wdlPick,
            SlipPlayCodes.HHAD to engine.hdpPick,
            SlipPlayCodes.CRS to engine.scorePick,
            SlipPlayCodes.HAFU to engine.hfPick,
            SlipPlayCodes.TTG to engine.totalPick,
        )
        return enginePickOf.map { (play, enginePick) ->
            val probMap = probs[play] ?: emptyMap()
            val odds = oddsMap[play] ?: emptyMap()
            val score = ai?.scores?.firstOrNull { it.play == play }
            // 模型判断优先（模型为核心）；模型未给出或越界则用概率主选（本地计算为基础）
            val aiPick = score?.pick?.takeIf { it.isNotEmpty() && odds.containsKey(it) }
            val option = aiPick ?: enginePick.takeIf { it != "--" }.orEmpty()
            val divergence = aiPick != null && enginePick != "--" && aiPick != enginePick
            val probability = probMap[option] ?: 0.0
            val alt = probMap.entries.sortedByDescending { it.value }
                .firstOrNull { it.key != option }?.key.orEmpty()
            CombinedPick(
                play = play,
                playLabel = playLabelOf(play),
                option = option,
                odds = odds[option]?.toDoubleOrNull() ?: 0.0,
                probability = probability,
                aiScore = score?.score,
                confidence = CombineRule.confidence(probability, divergence, ai != null),
                alt = alt,
                altOdds = odds[alt]?.toDoubleOrNull() ?: 0.0,
                reason = score?.reason.orEmpty(),
                divergence = divergence,
                hit = result?.let { hitOf(play, option, engine.hdpLine.ifEmpty { "" }, it) },
            )
        }
    }

    private fun bestValuePick(
        candidates: List<CandidateOption>,
        ai: AiAnalysis?,
        picks: List<CombinedPick>,
        result: SlipSettlement4Hit?,
    ): CombinedPick? {
        val usable = candidates.filter { it.probability > 0.0 }
        if (usable.isEmpty()) return null
        // 1) 稳健门槛：模型概率 ≥ 45% 且 期望 > 0 —— 排除低概率高赔的投机项
        val solid = usable.filter { it.probability >= ValueRule.MIN_PROB && it.ev > ValueRule.MIN_EV }
        if (solid.isNotEmpty()) {
            val pick = solid.maxByOrNull { it.probability * it.ev } ?: return null
            return toAdvicePick(pick, ai?.bestValue, picks, result, ValueRule.NOTE)
        }
        // 2) 退档：正期望中概率最高者（并明确标注把握偏低）
        val positive = usable.filter { it.ev > ValueRule.MIN_EV }
        if (positive.isNotEmpty()) {
            val pick = positive.maxByOrNull { it.probability } ?: return null
            return toAdvicePick(pick, ai?.bestValue, picks, result, ValueRule.NOTE_FALLBACK)
        }
        // 3) 全为非正期望：如实标注没有价值方向，仅列出概率最高者供参考
        val pick = usable.maxByOrNull { it.probability } ?: return null
        return toAdvicePick(pick, ai?.bestValue, picks, result, ValueRule.NOTE_NONE)
    }

    private fun safestPick(
        candidates: List<CandidateOption>,
        ai: AiAnalysis?,
        picks: List<CombinedPick>,
        result: SlipSettlement4Hit?,
    ): CombinedPick? {
        val best = candidates.filter { it.probability > 0.0 }.maxByOrNull { it.probability } ?: return null
        return toAdvicePick(best, ai?.safest, picks, result, ValueRule.NOTE_SAFE)
    }

    private fun toAdvicePick(
        c: CandidateOption,
        adv: AiAdvice?,
        picks: List<CombinedPick>,
        result: SlipSettlement4Hit?,
        note: String,
    ): CombinedPick {
        val sameAsAi = adv?.grounded == true && adv.option == c.option
        return CombinedPick(
            play = c.play,
            playLabel = c.playLabel,
            option = c.option,
            odds = c.odds,
            probability = c.probability,
            aiScore = if (sameAsAi) adv?.aiScore else null,
            confidence = CombineRule.confidence(c.probability, divergence = false, hasAi = sameAsAi),
            alt = picks.firstOrNull { it.play == c.play }?.alt.orEmpty(),
            altOdds = picks.firstOrNull { it.play == c.play }?.altOdds ?: 0.0,
            // 只有模型也推荐同一项时才写理由，避免把别的选项的解释张冠李戴
            reason = if (sameAsAi) adv?.reason.orEmpty() else "",
            divergence = false,
            hit = result?.let { hitOf(c.play, c.option, "", it) },
            note = note,
        )
    }

    /* ================= 命中判定 ================= */

    /** 命中判定所需的最小赛果结构（避免与 data.slip 循环依赖，逻辑与官方规则一致） */
    data class SlipSettlement4Hit(val homeScore: Int, val awayScore: Int, val halfHome: Int?, val halfAway: Int?)

    private fun resultOf(match: RemoteMatch, live: com.jingcai.predict.data.remote.LiveScore?): SlipSettlement4Hit? {
        if (live != null && live.isFinished) {
            val (h, a) = parseScore(live.score) ?: return null
            val half = parseScore(live.halfScore)
            return SlipSettlement4Hit(h, a, half?.first, half?.second)
        }
        return null
    }

    /** 解析 "H:A" 比分；格式不符返回 null（结算回填复用，避免两套规则不一致） */
    internal fun parseScore(s: String): Pair<Int, Int>? {
        val parts = s.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val a = parts[1].trim().toIntOrNull() ?: return null
        return h to a
    }

    /** 单玩法命中判定（结算回填复用，避免两套规则不一致）；无法判定时返回 null */
    internal fun hitOf(play: String, option: String, goalLine: String, r: SlipSettlement4Hit): Boolean? {
        val wdl = when {
            r.homeScore > r.awayScore -> "主胜"
            r.homeScore < r.awayScore -> "客胜"
            else -> "平局"
        }
        return when (play) {
            SlipPlayCodes.HAD -> option == wdl
            SlipPlayCodes.HHAD -> {
                val line = goalLine.toIntOrNull() ?: return null
                val adj = r.homeScore + line
                val adjWdl = when {
                    adj > r.awayScore -> "让胜"
                    adj < r.awayScore -> "让负"
                    else -> "让平"
                }
                option == adjWdl
            }
            SlipPlayCodes.CRS -> option == "${r.homeScore}:${r.awayScore}"
            SlipPlayCodes.HAFU -> {
                val hh = r.halfHome ?: return null
                val ha = r.halfAway ?: return null
                val half = when {
                    hh > ha -> "胜"
                    hh < ha -> "负"
                    else -> "平"
                }
                val full = when {
                    r.homeScore > r.awayScore -> "胜"
                    r.homeScore < r.awayScore -> "负"
                    else -> "平"
                }
                option == "$half$full"
            }
            SlipPlayCodes.TTG -> {
                val n = option.removeSuffix("球+").removeSuffix("球").toIntOrNull() ?: return null
                val total = (r.homeScore + r.awayScore).coerceAtMost(7)
                (if (n >= 7) 7 else n) == total
            }
            else -> null
        }
    }

    /* ================= 候选池与赔率映射（与官方选项文案一致） ================= */

    /** play → (选项文案 → 官方赔率) */
    fun oddsByPlay(match: RemoteMatch, odds: MatchOdds?): Map<String, Map<String, String>> {
        val out = mutableMapOf<String, Map<String, String>>()
        val had = odds?.had?.let { Triple(it.first.value, it.second.value, it.third.value) } ?: match.had
        had?.let { out[SlipPlayCodes.HAD] = mapOf("主胜" to it.first, "平局" to it.second, "客胜" to it.third) }
        val hhad = odds?.hhad?.let { Triple(it.first.value, it.second.value, it.third.value) } ?: match.hhad
        hhad?.let {
            out[SlipPlayCodes.HHAD] = mapOf("让胜" to it.first, "让平" to it.second, "让负" to it.third)
        }
        val crs = odds?.crs?.mapValues { it.value.value }?.takeIf { it.isNotEmpty() } ?: match.crs
        crs?.let { out[SlipPlayCodes.CRS] = crsByLabel(it) }
        val hafu = odds?.hafu?.mapValues { it.value.value }?.takeIf { it.isNotEmpty() } ?: match.hafu
        hafu?.let { out[SlipPlayCodes.HAFU] = hafuByLabel(it) }
        val ttg = odds?.ttg?.mapValues { it.value.value }?.takeIf { it.isNotEmpty() } ?: match.ttg
        ttg?.let { out[SlipPlayCodes.TTG] = ttgByLabel(it) }
        return out
    }

    private val hafuLabels = mapOf(
        "hh" to "胜胜", "hd" to "胜平", "ha" to "胜负",
        "dh" to "平胜", "dd" to "平平", "da" to "平负",
        "ah" to "负胜", "ad" to "负平", "aa" to "负负",
    )

    /** 官方比分键（两位填充/非填充、s-1sh 等）→ 展示文案 */
    private fun crsByLabel(map: Map<String, String>): Map<String, String> {
        val pairRx = Regex("^s(\\d+)s(\\d+)$")
        val otherRx = Regex("^s-?\\d+s([hda])$")
        val out = mutableMapOf<String, String>()
        map.forEach { (k, v) ->
            pairRx.find(k)?.let { m ->
                val h = m.groupValues[1].toIntOrNull()
                val a = m.groupValues[2].toIntOrNull()
                if (h != null && a != null) out["$h:$a"] = v
            }
            otherRx.find(k)?.let { m ->
                out[when (m.groupValues[1]) {
                    "h" -> "胜其他"
                    "d" -> "平其他"
                    else -> "负其他"
                }] = v
            }
        }
        return out
    }

    private fun hafuByLabel(map: Map<String, String>): Map<String, String> =
        hafuLabels.entries.mapNotNull { (code, label) -> map[code]?.let { label to it } }.toMap()

    private fun ttgByLabel(map: Map<String, String>): Map<String, String> = buildMap {
        (0..7).forEach { k ->
            map["s$k"]?.let { put(if (k == 7) "7球+" else "${k}球", it) }
        }
    }

    /** 候选池：只含真实有赔率的选项；概率取计算值（缺失为 0，绝不猜） */
    fun buildCandidates(
        oddsMap: Map<String, Map<String, String>>,
        probs: Map<String, Map<String, Double>>,
    ): List<CandidateOption> {
        val out = mutableListOf<CandidateOption>()
        oddsMap.forEach { (play, map) ->
            val probMap = probs[play] ?: emptyMap()
            map.forEach opt@{ (label, oddsStr) ->
                val o = oddsStr.toDoubleOrNull()?.takeIf { it > 1.0 } ?: return@opt
                val prob = probMap[label] ?: 0.0
                out += CandidateOption(play, playLabelOf(play), label, o, prob, prob * o - 1.0)
            }
        }
        return out
    }

    /* ================= 上下文与本地分节 ================= */

    /** 前瞻情报上下文（官方权威数据：排名/赛季/交锋/战况/场均/伤停/射手/未来赛程） */
    private fun previewContext(
        match: RemoteMatch,
        head: com.jingcai.predict.data.remote.MatchHead?,
        feature: MatchFeature?,
        tables: Pair<TeamTables?, TeamTables?>?,
        results: Pair<RecentTeam?, RecentTeam?>?,
        history: Pair<List<com.jingcai.predict.data.remote.H2hMatch>, H2hSummary?>?,
        players: Pair<List<PlayerStat>, List<PlayerStat>>?,
        injuries: Pair<List<InjuryPlayer>, List<InjuryPlayer>>?,
        future: Pair<List<com.jingcai.predict.data.remote.FutureMatch>, List<com.jingcai.predict.data.remote.FutureMatch>>?,
        engine: PredictionResult,
    ): List<String> = buildList {
        add("比赛：${match.league} ${match.num} ${match.home}(主) vs ${match.away}(客)，开赛 ${kickoffText(match.time)}")
        head?.let {
            if (it.homeRank.isNotBlank() || it.awayRank.isNotBlank()) {
                add("联赛排名：${match.home} ${it.homeRank.ifBlank { "未知" }} / ${match.away} ${it.awayRank.ifBlank { "未知" }}")
            }
            if (it.homeSeason.isNotBlank()) add("赛季总成绩：${match.home} ${it.homeSeason}；${match.away} ${it.awaySeason}")
            if (it.homeHomeSeason.isNotBlank()) add("主客场成绩：${match.home}（主）${it.homeHomeSeason}；${match.away}（客）${it.awayAwaySeason}")
        }
        tables?.let { (h, a) ->
            h?.total?.let {
                if (it.played > 0) add("积分榜：${match.home} 第${it.ranking}名 ${it.win}胜${it.draw}平${it.loss}负 进${it.goal}失${it.lossGoal}（${it.points}分）")
            }
            a?.total?.let {
                if (it.played > 0) add("积分榜：${match.away} 第${it.ranking}名 ${it.win}胜${it.draw}平${it.loss}负 进${it.goal}失${it.lossGoal}（${it.points}分）")
            }
        }
        results?.let { (h, a) ->
            if (h != null && h.stat.isNotBlank()) add("${match.home} 近10场：${h.stat}")
            if (a != null && a.stat.isNotBlank()) add("${match.away} 近10场：${a.stat}")
        }
        history?.second?.let { s ->
            val n = s.win + s.draw + s.loss
            if (n > 0) add("近${n}次交锋：${s.teamName} ${s.win}胜${s.draw}平${s.loss}负")
        }
        feature?.let { f ->
            if (f.homeGoalAvg.isNotBlank()) add("场均进球/失球：${match.home} 进${f.homeGoalAvg}失${f.homeLossAvg}；${match.away} 进${f.awayGoalAvg}失${f.awayLossAvg}")
            f.last10Form?.let { d ->
                add("近10场胜平负：${match.home} ${d.homeWin}胜${d.homeDraw}平${d.homeLoss}负；${match.away} ${d.awayWin}胜${d.awayDraw}平${d.awayLoss}负")
            }
        }
        injuries?.let { (h, a) ->
            val hs = h.filter { it.injury || it.suspension }.map { "${it.name}(${if (it.suspension) "停赛" else "伤"})" }
            val as_ = a.filter { it.injury || it.suspension }.map { "${it.name}(${if (it.suspension) "停赛" else "伤"})" }
            if (hs.isNotEmpty()) add("${match.home} 伤停：${hs.joinToString("、")}")
            if (as_.isNotEmpty()) add("${match.away} 伤停：${as_.joinToString("、")}")
        }
        players?.let { (h, a) ->
            val hs = h.take(3).filter { it.name.isNotBlank() }.map { "${it.name} ${it.goal}球" }
            val as_ = a.take(3).filter { it.name.isNotBlank() }.map { "${it.name} ${it.goal}球" }
            if (hs.isNotEmpty()) add("${match.home} 主要射手：${hs.joinToString("、")}")
            if (as_.isNotEmpty()) add("${match.away} 主要射手：${as_.joinToString("、")}")
        }
        future?.let { (h, a) ->
            if (h.isNotEmpty()) add("${match.home} 后续赛程：${h.take(2).joinToString("；") { "${it.date} ${it.tournament} ${it.home}vs${it.away}" }}")
            if (a.isNotEmpty()) add("${match.away} 后续赛程：${a.take(2).joinToString("；") { "${it.date} ${it.tournament} ${it.home}vs${it.away}" }}")
        }
        add("本地计算初步结论：倾向 ${engine.wdlPick}（主胜 ${pct(engine.homeProb)} / 平 ${pct(engine.drawProb)} / 客胜 ${pct(engine.awayProb)}），置信度 ${engine.conf}")
    }

    /** 喂给预测模型的上下文（含前瞻情报全文 + 官方数据要点） */
    private fun aiContext(
        match: RemoteMatch,
        head: com.jingcai.predict.data.remote.MatchHead?,
        engine: PredictionResult,
        odds: MatchOdds?,
        tables: Pair<TeamTables?, TeamTables?>?,
        results: Pair<RecentTeam?, RecentTeam?>?,
        history: Pair<List<com.jingcai.predict.data.remote.H2hMatch>, H2hSummary?>?,
        injuries: Pair<List<InjuryPlayer>, List<InjuryPlayer>>?,
        players: Pair<List<PlayerStat>, List<PlayerStat>>?,
        result: SlipSettlement4Hit?,
        profile: LeagueProfile,
        preview: String,
    ): List<String> = buildList {
        add("比赛：${match.league} ${match.num} ${match.home}(主) vs ${match.away}(客)，开赛 ${kickoffText(match.time)}")
        head?.let {
            if (it.homeRank.isNotBlank() || it.awayRank.isNotBlank()) {
                add("联赛排名：${match.home} ${it.homeRank.ifBlank { "未知" }} / ${match.away} ${it.awayRank.ifBlank { "未知" }}")
            }
        }
        if (preview.isNotBlank()) add("【前瞻情报（已由官方前瞻数据整理）】\n$preview")
        add("本地计算结论：倾向 ${engine.wdlPick}；主胜 ${pct(engine.homeProb)} / 平 ${pct(engine.drawProb)} / 客胜 ${pct(engine.awayProb)}；整体置信度 ${engine.conf}；数据完整度 ${pct(engine.dataComplete)}；采用信号 ${engine.signalNote}")
        add("本地计算玩法预测：让球 ${engine.hdpPick}${if (engine.hdpLine.isNotBlank()) "（盘口 ${engine.hdpLine}）" else ""}；比分 ${engine.scorePick}；半全场 ${engine.hfPick}；总进球 ${engine.totalPick}")
        if (engine.lambdaHome > 0.0) {
            add(String.format(Locale.US, "双泊松期望进球：主队 %.2f / 客队 %.2f（联赛基准：场均 %.2f 球、主场优势 %.2f）",
                engine.lambdaHome, engine.lambdaAway, profile.avgGoals, profile.homeAdv))
        }
        oddsByPlay(match, odds)[SlipPlayCodes.HAD]?.let { m ->
            val h = m["主胜"]?.toDoubleOrNull(); val d = m["平局"]?.toDoubleOrNull(); val a = m["客胜"]?.toDoubleOrNull()
            if (h != null && d != null && a != null) {
                val inv = 1 / h + 1 / d + 1 / a
                add("官方赔率隐含概率：主胜 ${pct(1 / h / inv)} / 平 ${pct(1 / d / inv)} / 客胜 ${pct(1 / a / inv)}")
            }
        }
        tables?.let { (hh, aa) ->
            hh?.total?.let { if (it.played > 0) add("积分榜：${match.home} 第${it.ranking}名 ${it.win}胜${it.draw}平${it.loss}负") }
            aa?.total?.let { if (it.played > 0) add("积分榜：${match.away} 第${it.ranking}名 ${it.win}胜${it.draw}平${it.loss}负") }
        }
        results?.let { (hh, aa) ->
            if (hh != null && hh.stat.isNotBlank()) add("${match.home} 近况：${hh.stat}")
            if (aa != null && aa.stat.isNotBlank()) add("${match.away} 近况：${aa.stat}")
        }
        history?.second?.let { s ->
            val n = s.win + s.draw + s.loss
            if (n > 0) add("近${n}次交锋：${s.teamName} ${s.win}胜${s.draw}平${s.loss}负")
        }
        injuries?.let { (hh, aa) ->
            val hs = hh.count { it.injury || it.suspension }
            val as_ = aa.count { it.injury || it.suspension }
            if (hs + as_ > 0) add("伤停人数：${match.home} $hs 人、${match.away} $as_ 人")
        }
        players?.let { (hh, aa) ->
            val hs = hh.take(3).filter { it.name.isNotBlank() }.joinToString("、") { "${it.name} ${it.goal}球" }
            val as_ = aa.take(3).filter { it.name.isNotBlank() }.joinToString("、") { "${it.name} ${it.goal}球" }
            if (hs.isNotBlank()) add("${match.home} 主要射手：$hs")
            if (as_.isNotBlank()) add("${match.away} 主要射手：$as_")
        }
        if (engine.key.isNotBlank()) add("本地计算数据要点：${engine.key}")
        result?.let { add("本场已完赛，赛果 ${it.homeScore}:${it.awayScore}") }
        add("约束：选项只能从下方候选池中选择；禁止输出任何赔率数字（系统会用真实赔率回填）；score 为主观把握度（0-100），不是概率。")
    }

    /** 无模型结果时的本地分节分析（结构与模型输出一致，内容全部来自真实数据） */
    private fun localSections(
        match: RemoteMatch,
        engine: PredictionResult,
        tables: Pair<TeamTables?, TeamTables?>?,
        results: Pair<RecentTeam?, RecentTeam?>?,
        history: Pair<List<com.jingcai.predict.data.remote.H2hMatch>, H2hSummary?>?,
        injuries: Pair<List<InjuryPlayer>, List<InjuryPlayer>>?,
        result: SlipSettlement4Hit?,
        probsAvailable: Boolean,
    ): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out += "市场面" to "模型倾向 ${engine.wdlPick}（主胜 ${pct(engine.homeProb)} / 平 ${pct(engine.drawProb)} / 客胜 ${pct(engine.awayProb)}），置信度 ${engine.conf}，采用信号 ${engine.signalNote}。"
        out += "基本面" to buildString {
            tables?.first?.total?.let { if (it.played > 0) append("${match.home} 第${it.ranking}名；") }
            tables?.second?.total?.let { if (it.played > 0) append("${match.away} 第${it.ranking}名；") }
            results?.first?.let { if (it.stat.isNotBlank()) append("${match.home} 近况 ${it.stat}；") }
            results?.second?.let { if (it.stat.isNotBlank()) append("${match.away} 近况 ${it.stat}；") }
            history?.second?.let { s ->
                val n = s.win + s.draw + s.loss
                if (n > 0) append("近${n}次交锋 ${s.teamName} ${s.win}胜${s.draw}平${s.loss}负；")
            }
            injuries?.let { (h, a) ->
                val n = h.count { it.injury || it.suspension } + a.count { it.injury || it.suspension }
                if (n > 0) append("伤病停赛合计 $n 人；")
            }
            if (isEmpty()) append(engine.key.ifBlank { "官方前瞻数据不完整，无可用基本面要点。" })
        }
        out += "风险点" to buildString {
            append("数据完整度 ${pct(engine.dataComplete)}。")
            if (!probsAvailable) append("统计信号缺失，无法给出概率与期望值建议。")
            if (engine.conf < 70) append("整体置信度偏低（${engine.conf}），不确定性较高。")
            append("足球偶然性大，以上为概率与赔率的数学结果，不构成投注建议。")
        }
        out += "结论" to buildString {
            append("模型倾向 ${engine.wdlPick}（置信度 ${engine.conf}）。")
            if (engine.hdpPick != "--") append("让球 ${engine.hdpLine.ifEmpty { match.goalLine }} 倾向 ${engine.hdpPick}；")
            if (engine.scorePick != "--") append("最可能比分 ${engine.scorePick}、总进球 ${engine.totalPick}、半全场 ${engine.hfPick}。")
            result?.let { append("本场已完赛（${it.homeScore}:${it.awayScore}）。") }
        }
        return out
    }

    /* ================= 小工具 ================= */

    private fun pct(v: Double): String = "${(v * 100).roundToInt()}%"

    private fun kickoffText(time: String): String {
        val parts = time.split(" ")
        if (parts.size < 2) return time
        val date = parts[0]
        val hm = parts[1].take(5)
        val md = if (date.length >= 10) date.substring(5) else date
        return "$md $hm"
    }

    private fun statusTextOf(status: String): String = when (status.uppercase()) {
        "UPCOMING", "0", "SELLING", "PRE_SELL" -> "未开赛"
        "LIVE", "1", "OPEN" -> "进行中"
        "FINISHED", "2", "CLOSED" -> "已完赛"
        else -> status.ifEmpty { "未开赛" }
    }
}
