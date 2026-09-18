package com.jingcai.predict.data.predict

/**
 * 三路预测信号的融合权重。
 * 权重缺失信号时会自动归一化到可用信号上（见 PredictionEngine）。
 */
data class SignalWeights(
    val market: Double,   // 信号A：市场赔率隐含概率
    val poisson: Double,  // 信号B：双泊松比分矩阵（Dixon-Coles）
    val stat: Double,     // 信号C：官方统计经验概率
) {
    val total: Double get() = market + poisson + stat

    fun normalized(): SignalWeights {
        if (total <= 0.0) return SignalWeights(1.0, 0.0, 0.0)
        return SignalWeights(market / total, poisson / total, stat / total)
    }
}

/**
 * 联赛参数模板：描述某联赛的进球 / 主场 / 平局特征与三路信号权重。
 *
 * 【数据来源与取值规则】（严格区分实测值与经验值，不混用）
 * 1. avgGoals（联赛场均总进球）、homeAdv（主队场均进球 ÷ 客队场均进球）：
 *    一律取该联赛最新完整赛季的**实测统计**，并在 sourceNote 中标注赛季与来源站点；
 *    同一指标存在多来源冲突时，sourceNote 注明所采信口径。
 * 2. drawBias（平局保底下限，用于校准守卫）：
 *    新收录联赛按「实测平局率 × 0.5」取两位小数；原五大联赛沿用上一轮来源取值。
 * 3. rho（Dixon-Coles τ 修正参数）：**经验分档值，非实测**——
 *    场均 ≥3.0 球取 -0.02，2.5~2.99 取 -0.04，<2.5 取 -0.06（防守型联赛低比分依赖更强）。
 * 4. weights：默认三路等权基准（市场 0.50 / 统计 0.30 / 官方 0.20），用户可在详情页按联赛覆盖。
 *
 * aliases 用于匹配竞彩官方返回的联赛名（包含匹配，兼容"芬超 / 芬兰超级联赛"等写法）。
 */
data class LeagueProfile(
    val league: String,            // 模板名（简称，用于展示）
    val aliases: List<String>,     // 匹配竞彩官方联赛名的别名集合
    val avgGoals: Double,          // μ：联赛场均进球（泊松期望进球基准）
    val homeAdv: Double,           // 主场优势系数 = 主队场均进球 ÷ 客队场均进球
    val rho: Double,               // Dixon-Coles τ 修正参数（经验分档）
    val drawBias: Double,          // 平局保底下限（校准守卫）
    val weights: SignalWeights,
    val sourceNote: String,        // 参数来源说明（赛季 + 实测数值 + 来源站点）
) {
    /** 联赛主队场均进球（由场均总进球与主场优势系数还原） */
    val baseHome: Double get() = avgGoals * homeAdv / (1 + homeAdv)

    /** 联赛客队场均进球 */
    val baseAway: Double get() = avgGoals / (1 + homeAdv)
}

/**
 * 联赛模板表。
 *
 * 覆盖 23 个竞彩常见联赛（五大联赛 + 英冠/德乙/西乙/意乙 + 荷甲/比甲/葡超/瑞士超
 * + 挪超/芬超/瑞典超/丹超 + 巴甲/阿甲/美职联/墨超 + 日职联/韩K联），
 * 每项均标注实测赛季与来源；未收录赛事（杯赛、国家队赛事等）走兜底模板。
 */
object LeagueProfiles {

    private val defaults = listOf(
        /* ---------- 欧洲五大联赛（沿用上一轮来源：StatPair / footbodds / OneFootball-Opta） ---------- */
        LeagueProfile(
            league = "英超", aliases = listOf("英超", "英格兰超级联赛", "英格兰超级"),
            avgGoals = 2.75, homeAdv = 1.32, rho = -0.04, drawBias = 0.10,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 英超 场均2.75球，主场胜率43%，市场效率高（StatPair / footbodds / OneFootball-Opta）",
        ),
        LeagueProfile(
            league = "西甲", aliases = listOf("西甲", "西班牙甲级联赛", "西班牙甲级"),
            avgGoals = 2.51, homeAdv = 1.35, rho = -0.04, drawBias = 0.12,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 西甲 场均2.51球（五大联赛最低），平局偏多，低比分密集（StatPair / OneFootball-Opta）",
        ),
        LeagueProfile(
            league = "德甲", aliases = listOf("德甲", "德国甲级联赛", "德国甲级"),
            avgGoals = 3.18, homeAdv = 1.30, rho = -0.02, drawBias = 0.10,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 德甲 场均3.18球（五大联赛最高），进攻开放（StatPair / OneFootball-Opta / footbodds）",
        ),
        LeagueProfile(
            league = "意甲", aliases = listOf("意甲", "意大利甲级联赛", "意大利甲级"),
            avgGoals = 2.40, homeAdv = 1.33, rho = -0.06, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 意甲 场均2.40球，防守型联赛，平局与低比分多（OneFootball-Opta / footbodds）",
        ),
        LeagueProfile(
            league = "法甲", aliases = listOf("法甲", "法国甲级联赛", "法国甲级"),
            avgGoals = 2.62, homeAdv = 1.30, rho = -0.04, drawBias = 0.11,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 法甲 场均2.62球，强弱悬殊（StatPair / OneFootball-Opta）",
        ),

        /* ---------- 欧洲次级联赛 ---------- */
        LeagueProfile(
            league = "英冠", aliases = listOf("英冠", "英格兰冠军联赛", "英格兰冠军"),
            avgGoals = 2.61, homeAdv = 1.16, rho = -0.04, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 英冠 实测：552场（24队×46轮），场均2.61球（总进球1438），主队1.40/客队1.21，主胜42%/平26%/客胜32%（Footballdatabase / the-sports.org / football-predictions.ai；总进球存1438与1441小差异，采信与552场自洽者）",
        ),
        LeagueProfile(
            league = "德乙", aliases = listOf("德乙", "德国乙级联赛", "德国乙级"),
            avgGoals = 2.92, homeAdv = 1.26, rho = -0.04, drawBias = 0.12,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 德乙 实测：场均2.92球，主队1.63/客队1.29，主胜46%/平24%/客胜30%（Sportradar StatsHub / Footballdatabase；主客拆分采信 Sportradar，其1.63+1.29与总进球自洽）",
        ),
        LeagueProfile(
            league = "西乙", aliases = listOf("西乙", "西班牙乙级联赛", "西班牙乙级"),
            avgGoals = 2.63, homeAdv = 1.23, rho = -0.04, drawBias = 0.12,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 西乙 实测：462场（22队×42轮），场均2.63球，主队1.45/客队1.18（ReadFootball / Wedtips.ai 进球口径一致）；胜平负44.8/24.9/30.3 为单源（ReadFootball）",
        ),
        LeagueProfile(
            league = "意乙", aliases = listOf("意乙", "意大利乙级联赛", "意大利乙级"),
            avgGoals = 2.54, homeAdv = 1.40, rho = -0.04, drawBias = 0.16,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 意乙 实测：常规赛380场，场均2.54球，主队1.48/客队1.06，主胜45%/平31%/客胜24%（the-sports.org / football-predictions.ai；场次口径含附加赛为389-390）",
        ),

        /* ---------- 欧洲其他主流联赛 ---------- */
        LeagueProfile(
            league = "荷甲", aliases = listOf("荷甲", "荷兰甲级联赛", "荷兰甲级"),
            avgGoals = 3.17, homeAdv = 1.33, rho = -0.02, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 荷甲 实测：306场，场均3.17球（总进球971-972），主队1.81/客队1.36，主胜44%/平26%/客胜29%（Besoccer / Footballdatabase / footbodds；主客拆分为 the-sports.org 单源）",
        ),
        LeagueProfile(
            league = "比甲", aliases = listOf("比甲", "比利时甲级联赛", "比利时甲级"),
            avgGoals = 2.68, homeAdv = 1.16, rho = -0.04, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 比甲 实测：场均2.68球（三源一致），主场优势1.16（Footballdatabase / foot.be 等；因含季后赛口径差异，场次312-321，主场优势系数可信度中等）",
        ),
        LeagueProfile(
            league = "瑞士超", aliases = listOf("瑞士超", "瑞士超级联赛", "瑞士超级"),
            avgGoals = 2.98, homeAdv = 1.37, rho = -0.04, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2024/25 瑞士超 实测：228场（含争冠/保级组），场均2.98球，主队1.72/客队1.26，主胜47.8%/平26.8%/客胜25.4%（FBref / Footballdatabase / Sports Mole，主场优势为本次收录联赛中最强）",
        ),
        LeagueProfile(
            league = "葡超", aliases = listOf("葡超", "葡萄牙超级联赛", "葡萄牙超级"),
            avgGoals = 2.57, homeAdv = 1.20, rho = -0.04, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2024/25 葡超 实测：306场（18队×34轮），场均2.57球，主队1.40/客队1.17，主胜43.1%/平25.8%/客胜31.0%（FBref / Footballdatabase / 维基赛季页，四源一致）",
        ),

        /* ---------- 北欧联赛（自然年赛季，主场优势普遍偏弱） ---------- */
        LeagueProfile(
            league = "挪超", aliases = listOf("挪超", "挪威超级联赛", "挪威超级"),
            avgGoals = 3.18, homeAdv = 1.32, rho = -0.02, drawBias = 0.09,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 挪超 实测：240场，场均3.18球，主队1.81/客队1.37，主胜49.2%/平18.8%/客胜32.1%（FBref 逐队主客求和 / Footballdatabase，两源完全一致；平局率为本次收录联赛最低）",
        ),
        LeagueProfile(
            league = "芬超", aliases = listOf("芬超", "芬兰超级联赛", "芬兰超级"),
            avgGoals = 3.29, homeAdv = 1.02, rho = -0.02, drawBias = 0.11,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 芬超 实测：177场，场均3.29球（583球），主队1.66/客队1.63，主胜41%/平22%/客胜37%（Sportradar / Footballdatabase / European Leagues；主场优势1.02 为各联赛最弱，需特别留意）",
        ),
        LeagueProfile(
            league = "瑞典超", aliases = listOf("瑞典超", "瑞超", "瑞典超级联赛", "瑞典超级"),
            avgGoals = 2.85, homeAdv = 1.08, rho = -0.04, drawBias = 0.11,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 瑞典超 实测：240场，场均2.85球，主队1.48/客队1.37，主胜40.0%/平22.1%/客胜37.9%（FBref 逐队主客求和 / Footballdatabase / TheFishy，三源一致，主场优势偏弱）",
        ),
        LeagueProfile(
            league = "丹超", aliases = listOf("丹超", "丹麦超级联赛", "丹麦超级"),
            avgGoals = 3.14, homeAdv = 1.18, rho = -0.02, drawBias = 0.14,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2024/25 丹超 实测：192场，场均3.14球（602球），主队1.70/客队1.44，主胜45.3%/平28.1%/客胜26.6%（FBref 逐队主客求和 / BettingScore / TopScorersFootball，三源一致；平局率偏高）",
        ),

        /* ---------- 美洲联赛 ---------- */
        LeagueProfile(
            league = "巴甲", aliases = listOf("巴甲", "巴西甲级联赛", "巴西甲级"),
            avgGoals = 2.52, homeAdv = 1.53, rho = -0.04, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 巴甲 实测：380场（20队×38轮），场均2.52球（959球），主队1.53/客队1.00，主胜50.3%/平26.1%/客胜23.7%（Footballdatabase / TheFishy 逐队主客求和，两源进球数完全相等；主场优势为各联赛最高）",
        ),
        LeagueProfile(
            league = "阿甲", aliases = listOf("阿甲", "阿根廷甲级联赛", "阿根廷甲级"),
            avgGoals = 1.95, homeAdv = 1.35, rho = -0.06, drawBias = 0.16,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 阿甲 实测：场均1.95球（510场口径994球，FBref常规赛口径1.96），主胜41%/平32%/客胜28%（Footballdatabase / FBref 交叉验证进球；主场优势1.35 仅 Sports Mole 单源、样本486场，可信度偏低，建议谨慎）",
        ),
        LeagueProfile(
            league = "美职联", aliases = listOf("美职联", "美国职业足球大联盟", "美职业大联盟", "美国大联盟", "MLS"),
            avgGoals = 3.00, homeAdv = 1.20, rho = -0.02, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 美职联 实测：常规赛510场（30队×34场），场均3.00球（1530球），主队1.64/客队1.36，主胜43.7%/平25.1%/客胜31.2%（TheFishy 逐队主客求和 / Footballdatabase / foot.be；含季后赛口径为540场3.02球）",
        ),
        LeagueProfile(
            league = "墨超", aliases = listOf("墨超", "墨西哥超级联赛", "墨西哥超级"),
            avgGoals = 2.90, homeAdv = 1.40, rho = -0.04, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025/26 墨超 实测：336-337场（Apertura+Clausura），场均2.90球，主队1.69/客队1.21，主胜47%/平25%/客胜28%（Footballdatabase / Footballnation，胜平负两源一致）",
        ),

        /* ---------- 亚洲联赛 ---------- */
        LeagueProfile(
            league = "日职联", aliases = listOf("日职联", "日职", "日本职业联赛", "日本J1联赛", "J1联赛"),
            avgGoals = 2.40, homeAdv = 1.22, rho = -0.06, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 日职联 实测：380场（20队×38轮），场均2.40球（911球），主队1.32/客队1.08，主胜44%/平26%/客胜30%（FootyStats / Footballdatabase / the-sports.org，三源完全一致）",
        ),
        LeagueProfile(
            league = "韩K联", aliases = listOf("韩K联", "韩职联", "韩国K联赛", "韩国K联", "K联赛1"),
            avgGoals = 2.54, homeAdv = 1.16, rho = -0.04, drawBias = 0.13,
            weights = SignalWeights(0.50, 0.30, 0.20),
            sourceNote = "2025 韩K联 实测：228场（12队×38场），场均2.54球（578球），主队1.36/客队1.17，主胜41.7%/平26.3%/客胜32.0%（TheStatsDontLie 逐队主客求和 / Footballdatabase，两源一致；主场优势偏弱）",
        ),
    )

    /**
     * 兜底模板：未收录赛事（各国杯赛、洲际杯赛、国家队赛事等）。
     * 明示为通用基准而非任何单一联赛的实测值，避免误导。
     */
    private val fallback = LeagueProfile(
        league = "默认", aliases = emptyList(),
        avgGoals = 2.75, homeAdv = 1.30, rho = -0.04, drawBias = 0.10,
        weights = SignalWeights(0.50, 0.30, 0.20),
        sourceNote = "未收录赛事兜底：通用基准 2.75 球/场、主场优势 1.30（非单一联赛实测值，仅供参考；如需精确请按联赛覆盖参数）",
    )

    /** 按竞彩官方联赛名匹配模板（别名包含匹配）；未收录赛事返回兜底模板 */
    fun forLeague(name: String): LeagueProfile {
        if (name.isBlank()) return fallback
        val upper = name.uppercase()
        // 先按更长的别名匹配，避免短别名抢先（如"瑞典超"与"瑞典超级联赛"）
        val candidates = defaults.flatMap { p -> p.aliases.map { it to p } }
            .sortedByDescending { it.first.length }
        return candidates.firstOrNull { (alias, _) -> upper.contains(alias.uppercase()) }?.second
            ?: fallback
    }

    /** 已收录联赛模板列表（用于展示与校验） */
    fun all(): List<LeagueProfile> = defaults
}
