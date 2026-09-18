package com.jingcai.predict.data.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * 「预测分析增强」分析器：把**本地引擎结果 + 真实候选**喂给大模型，解析其结构化输出并严格校验。
 *
 * 【红线约束（必须严格执行）】
 * 1. 大模型只能从传入的真实候选中选择，选到候选之外的选项一律丢弃并记入 droppedNotes（如实告知，不静默忽略）；
 * 2. 大模型返回的任何赔率都不采信，赔率 / 引擎概率 / 期望值一律用本地候选的真实数据回填；
 * 3. 解析失败、模型返回为空、内容全空时返回 Result.failure，由界面明确提示「AI 不可用」并展示本地结果；
 * 4. 不缓存、不补写：任何情况下都不返回模型没有给出的分析文字。
 */
object LlmAnalyzer {

    /** 合法玩法代码（其余玩法一律视为越界） */
    private val PLAYS = listOf("HAD", "HHAD", "CRS", "HAFU", "TTG")

    /**
     * 生成 AI 增强分析；失败时返回 Result.failure。
     * @param contextLines 比赛与引擎要点（逐行文本，如 "西甲 马拉加 vs 比利亚雷亚尔 开赛 09-18 03:30"、引擎概率、信号、要点、命中情况等）
     * @param candidates 全部真实候选项（option 展示文案 + odds 真实赔率 + probability 引擎概率 + ev 期望值）
     */
    suspend fun analyze(
        cfg: LlmConfig,
        contextLines: List<String>,
        candidates: List<CandidateOption>,
    ): Result<AiAnalysis> = withContext(Dispatchers.IO) {
        try {
            if (!cfg.ready) {
                return@withContext Result.failure(
                    IOException("模型未启用或配置不完整（需要服务地址 / API Key / 模型名）"),
                )
            }
            if (candidates.isEmpty()) {
                return@withContext Result.failure(IOException("没有可用的真实候选选项，无法进行模型增强分析"))
            }
            // 按玩法分组，后续所有校验都以这里的真实候选为唯一依据
            val grouped = candidates.groupBy { it.play.trim().uppercase() }

            val content = LlmClient.chat(cfg, SYSTEM_PROMPT, buildUserPrompt(cfg, contextLines, grouped))
                .getOrElse { e -> return@withContext Result.failure(e) }

            val root = extractJsonObject(content) ?: return@withContext Result.failure(
                IOException("模型未返回可解析的 JSON：${content.trim().take(200)}"),
            )
            parse(cfg, root, grouped)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /* ================= prompt ================= */

    /** 系统提示词：明确「只能选候选、禁止编造、禁写赔率、只输出 JSON、结论保守」 */
    private const val SYSTEM_PROMPT = """你是资深竞彩足球分析助手，只能依据用户提供的数据作答。必须严格遵守以下规则：
1. 只能从用户给出的候选项中挑选，禁止编造任何玩法、选项、球队、伤停、阵容、天气或数据；用户没有提供的信息一律不要臆造。
2. 禁止在输出中书写任何赔率数字（系统会用官方真实赔率回填），也不要复述候选里的赔率。
3. 只输出一个 JSON 对象，不要输出任何解释文字、开场白、结尾语，也不要使用 markdown 代码块。
4. 结论必须保守、明确风险，不承诺收益；对把握不足的玩法给出较低分数并说明原因。
5. 你写出的每个选项文案必须与候选项文案逐字一致（含大小写与标点），不要改写、简写或翻译。
6. score 是你对命中的主观把握（0~100 的整数），不是概率，不要与「引擎概率」混为一谈。"""

    /** 用户提示词：比赛上下文 + 每个玩法的真实候选 + 参考本金 + 输出 JSON 结构与字段含义 */
    private fun buildUserPrompt(
        cfg: LlmConfig,
        contextLines: List<String>,
        grouped: Map<String, List<CandidateOption>>,
    ): String {
        val sb = StringBuilder()

        sb.append("【比赛与引擎要点】\n")
        val ctx = contextLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (ctx.isEmpty()) {
            sb.append("- （未提供）\n")
        } else {
            ctx.forEach { sb.append("- ").append(it).append('\n') }
        }

        sb.append("\n【真实候选池】以下为全部可选条目（赔率为官方真实赔率，概率为本地引擎概率，期望值 = 概率 × 赔率 − 1）；只能在此范围内选择：\n")
        sb.append(candidatePoolText(grouped))

        sb.append("\n【参考本金】").append(fmt2(cfg.bankroll)).append(" 元\n")

        sb.append(
            """
            
            【输出要求】只输出下面这一个 JSON 对象（不要 markdown 代码块、不要任何多余文字）：
            {"scores":[{"play":"HAD","pick":"主胜","alternative":"平局","score":72,"reason":"一句理由"}],
             "bestValue":{"play":"HHAD","option":"让负","score":65,"reason":"一句理由"},
             "safest":{"play":"TTG","option":"4球","score":70,"reason":"一句理由"},
             "sections":[{"title":"市场面","content":"..."},{"title":"基本面","content":"..."},{"title":"风险点","content":"..."},{"title":"结论","content":"..."}]}

            字段说明：
            - scores：你愿意评估的玩法各一条；play 取 HAD/HHAD/CRS/HAFU/TTG；pick 与 alternative 必须逐字等于候选池中的选项文案；score 为 0~100 的主观把握；reason 限一句话。
            - bestValue（最具价值）：综合赔率与预测结果、兼顾较高赔率与较高把握的选项，option 必须出自候选池。
            - safest（最稳健）：不考虑赔率、只看把握最高的选项，option 必须出自候选池。
            - sections：至少包含 市场面 / 基本面 / 风险点 / 结论 四节，content 为务实的分析文字，不得编造未提供的伤停、阵容等信息。
            - 没有把握的玩法可以不给 score，但 sections 必须给出。
            """.trimIndent(),
        )
        return sb.toString()
    }

    /** 候选池文本：按固定玩法顺序列出 选项 / 真实赔率 / 引擎概率 / 期望值 */
    private fun candidatePoolText(grouped: Map<String, List<CandidateOption>>): String {
        if (grouped.isEmpty()) return "（无候选）\n"
        val sb = StringBuilder()
        val order = PLAYS + grouped.keys.filter { it !in PLAYS }.sorted()
        for (play in order) {
            val pool = grouped[play] ?: continue
            if (pool.isEmpty()) continue
            sb.append('[').append(play).append(' ').append(playLabelOf(play)).append("]\n")
            pool.forEach { c ->
                sb.append("  ").append(c.option)
                    .append(" | 赔率 ").append(fmt2(c.odds))
                    .append(" | 引擎概率 ").append(fmtPct(c.probability))
                    .append(" | 期望值 ").append(fmtEv(c.ev))
                    .append('\n')
            }
        }
        return sb.toString()
    }

    /* ================= 解析与校验 ================= */

    /**
     * 解析模型返回的 JSON 并逐项校验：
     * - scores：玩法非法 / 选项不在候选 → 丢弃该条并记 droppedNotes；
     * - bestValue / safest：玩法非法 → 丢弃；选项不在候选 → 保留但 grounded=false、赔率等置 0 并记账；
     * - 命中候选的项一律用真实 odds / probability / ev 回填（忽略模型返回的任何赔率）。
     */
    private fun parse(
        cfg: LlmConfig,
        root: JSONObject,
        grouped: Map<String, List<CandidateOption>>,
    ): Result<AiAnalysis> {
        val dropped = mutableListOf<String>()

        /* ---------- scores ---------- */
        val scores = mutableListOf<AiTargetScore>()
        val scoreArr = root.optJSONArray("scores")
        if (scoreArr != null) {
            for (i in 0 until scoreArr.length()) {
                val o = scoreArr.optJSONObject(i) ?: continue
                val play = o.optString("play").trim().uppercase()
                if (play !in PLAYS) {
                    dropped.add("AI 给出的玩法「${play.ifBlank { "(空)" }}」不在支持范围（HAD/HHAD/CRS/HAFU/TTG），已丢弃该条打分")
                    continue
                }
                val pool = grouped[play].orEmpty()
                if (pool.isEmpty()) {
                    dropped.add("真实候选中没有${playLabelOf(play)}玩法的候选，已丢弃 AI 的该条打分")
                    continue
                }
                val pickText = o.optString("pick").trim()
                val pick = matchCandidate(pool, pickText)
                if (pick == null) {
                    dropped.add("AI 给出的 ${playLabelOf(play)} 选项「${pickText.ifBlank { "(空)" }}」不在真实候选中，已丢弃")
                    continue
                }
                val altText = o.optString("alternative").trim()
                var alternative = ""
                if (altText.isNotEmpty()) {
                    val alt = matchCandidate(pool, altText)
                    if (alt == null) {
                        dropped.add("AI 给出的 ${playLabelOf(play)} 备选「$altText」不在真实候选中，已忽略")
                    } else {
                        alternative = alt.option
                    }
                }
                scores.add(
                    AiTargetScore(
                        play = play,
                        playLabel = playLabelOf(play),
                        pick = pick.option,
                        score = clampScore(o.optInt("score", 0)),
                        alternative = alternative,
                        reason = o.optString("reason").trim(),
                    ),
                )
            }
        }

        /* ---------- bestValue / safest ---------- */
        val bestValue = parseAdvice("VALUE", root.opt("bestValue"), grouped, dropped)
        val safest = parseAdvice("SAFE", root.opt("safest"), grouped, dropped)

        /* ---------- sections ---------- */
        val sections = parseSections(root)

        val analysis = AiAnalysis(
            model = cfg.model,
            scores = scores,
            bestValue = bestValue,
            safest = safest,
            sections = sections,
        )
        if (!analysis.ok) {
            val msg = if (dropped.isEmpty()) {
                "模型返回的 JSON 中没有有效内容（scores / bestValue / safest / sections 均为空）"
            } else {
                "模型给出的选项均不在真实候选范围内，已全部丢弃，无可展示内容（${dropped.size} 条）"
            }
            return Result.failure(IOException(msg))
        }
        return Result.success(analysis)
    }

    /** 解析一条建议（最具价值 / 最稳健），赔率与概率一律用真实候选回填 */
    private fun parseAdvice(
        kind: String,
        raw: Any?,
        grouped: Map<String, List<CandidateOption>>,
        dropped: MutableList<String>,
    ): AiAdvice? {
        val o = raw as? JSONObject ?: return null
        val tag = if (kind == "VALUE") "最具价值" else "最稳健"
        val play = o.optString("play").trim().uppercase()
        if (play !in PLAYS) {
            dropped.add("AI 给出的$tag 玩法「${play.ifBlank { "(空)" }}」不在支持范围（HAD/HHAD/CRS/HAFU/TTG），已丢弃该条建议")
            return null
        }
        val optionText = o.optString("option").trim()
        val matched = matchCandidate(grouped[play].orEmpty(), optionText)
        if (matched == null) {
            dropped.add("AI 给出的$tag 选项「${optionText.ifBlank { "(空)" }}」不在真实候选中，已标注为「无真实赔率支撑」")
        }
        return AiAdvice(
            kind = kind,
            play = play,
            playLabel = playLabelOf(play),
            // 未命中候选时保留模型原文，同时 grounded=false，由界面明确标注其无本地数据支撑
            option = matched?.option ?: optionText,
            reason = o.optString("reason").trim(),
            aiScore = clampScore(o.optInt("score", 0)),
            odds = matched?.odds ?: 0.0,
            probability = matched?.probability ?: 0.0,
            ev = matched?.ev ?: 0.0,
            grounded = matched != null,
        )
    }

    /** 解析 sections：兼容数组形式 [{title,content}]、{title,content} 对象内的 map 形式与字符串条目 */
    private fun parseSections(root: JSONObject): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        when (val sec = root.opt("sections")) {
            is JSONArray -> for (i in 0 until sec.length()) {
                val item = sec.opt(i)
                if (item is JSONObject) {
                    val title = item.optString("title").trim()
                    val content = item.optString("content").trim()
                    if (content.isNotEmpty()) {
                        out.add(title.ifEmpty { "分析" } to content)
                    } else {
                        addMapSections(item, out)   // 兼容 {"市场面":"..."}
                    }
                } else if (item is String && item.isNotBlank()) {
                    out.add("分析" to item.trim())
                }
            }
            is JSONObject -> addMapSections(sec, out)   // 兼容 {"sections":{"市场面":"..."}}
            else -> Unit
        }
        return out
    }

    /** 把 {"市场面":"..."} 形式的对象按 键 → 值 展开为分节，空内容自动过滤 */
    private fun addMapSections(o: JSONObject, out: MutableList<Pair<String, String>>) {
        val keys = o.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = o.optString(k).trim()
            if (v.isNotEmpty()) out.add(k.trim().ifEmpty { "分析" } to v)
        }
    }

    /** 容错提取 JSON：先整段解析，失败则截取第一个 { 到最后一个 } 再解析（可容忍 ```json 代码块等包裹） */
    private fun extractJsonObject(raw: String): JSONObject? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        parseObject(text)?.let { return it }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            parseObject(text.substring(start, end + 1))?.let { return it }
        }
        return null
    }

    private fun parseObject(text: String): JSONObject? = try {
        JSONObject(text)
    } catch (e: Exception) {
        null
    }

    /** 选项匹配：文案去除全部空白（含全角空格）并转小写后精确比较 */
    private fun matchCandidate(pool: List<CandidateOption>, text: String): CandidateOption? {
        val key = normalize(text)
        if (key.isEmpty()) return null
        return pool.firstOrNull { normalize(it.option) == key }
    }

    private fun normalize(s: String): String = s.filterNot { it.isWhitespace() }.lowercase()

    private fun clampScore(v: Int): Int = v.coerceIn(0, 100)

    private fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

    /** 引擎概率为 0~1 的小数，展示为百分比更直观 */
    private fun fmtPct(v: Double): String = String.format(Locale.US, "%.1f%%", v * 100)

    private fun fmtEv(v: Double): String = (if (v >= 0) "+" else "") + fmt2(v)
}
