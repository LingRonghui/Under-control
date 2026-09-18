package com.jingcai.predict.data.slip

/**
 * 方案命中判定（严格按竞彩官方玩法规则，基于 90 分钟含伤停补时的赛果）。
 *
 * 官方比分玩法只设 31 个选项：胜 13 个、平 4 个、负 13 个，其余归入「胜/平/负其他」。
 */
object SlipSettlement {

    private val HOME_SCORES = listOf(
        1 to 0, 2 to 0, 2 to 1, 3 to 0, 3 to 1, 3 to 2,
        4 to 0, 4 to 1, 4 to 2, 5 to 0, 5 to 1, 5 to 2,
    )
    private val DRAW_SCORES = listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3)
    private val AWAY_SCORES = listOf(
        0 to 1, 0 to 2, 1 to 2, 0 to 3, 1 to 3, 2 to 3,
        0 to 4, 1 to 4, 2 to 4, 0 to 5, 1 to 5, 2 to 5,
    )

    /** 单场赛果 */
    data class Result(
        val homeScore: Int,
        val awayScore: Int,
        val halfHome: Int?,
        val halfAway: Int?,
    ) {
        val total: Int get() = homeScore + awayScore
        val wdl: String get() = when {
            homeScore > awayScore -> "h"
            homeScore < awayScore -> "a"
            else -> "d"
        }
    }

    /** 解析 "2:1" → (2,1) */
    fun parseScore(score: String): Pair<Int, Int>? {
        val parts = score.split(":")
        if (parts.size != 2) return null
        val h = parts[0].trim().toIntOrNull() ?: return null
        val a = parts[1].trim().toIntOrNull() ?: return null
        return h to a
    }

    /**
     * 判定一条选择是否命中。
     * @param optionCode 官方选项键（h/d/a、s01s01、hh、s2 等）
     * @param goalLine 让球盘口（仅让球胜平负，如 "-1"、"+1"）
     */
    fun hit(play: String, optionCode: String, goalLine: String, r: Result): Boolean? = when (play) {
        SlipPlay.HAD.code -> optionCode == r.wdl

        SlipPlay.HHAD.code -> {
            val line = goalLine.toIntOrNull()
            if (line == null) null
            else {
                val adjusted = r.homeScore + line
                val wdl = when {
                    adjusted > r.awayScore -> "h"
                    adjusted < r.awayScore -> "a"
                    else -> "d"
                }
                optionCode == wdl
            }
        }

        SlipPlay.CRS.code -> crsHit(optionCode, r)

        SlipPlay.HAFU.code -> {
            val (hh, ha) = r.halfHome to r.halfAway
            if (hh == null || ha == null) null
            else {
                val half = when {
                    hh > ha -> "h"
                    hh < ha -> "a"
                    else -> "d"
                }
                optionCode == "$half${r.wdl}"
            }
        }

        SlipPlay.TTG.code -> {
            val total = r.total.coerceAtMost(7)
            optionCode == "s$total"
        }

        else -> null
    }

    /** 比分玩法判定：官方 31 个选项，其余归「胜/平/负其他」 */
    private fun crsHit(optionCode: String, r: Result): Boolean {
        val mine = r.homeScore to r.awayScore
        val inHome = HOME_SCORES.contains(mine)
        val inDraw = DRAW_SCORES.contains(mine)
        val inAway = AWAY_SCORES.contains(mine)
        val expected = when {
            inHome -> key(mine)
            inDraw -> key(mine)
            inAway -> key(mine)
            r.homeScore > r.awayScore -> "OTHER_H"
            r.homeScore == r.awayScore -> "OTHER_D"
            else -> "OTHER_A"
        }
        val target = when {
            optionCode.endsWith("sh") -> "OTHER_H"
            optionCode.endsWith("sd") -> "OTHER_D"
            optionCode.endsWith("sa") -> "OTHER_A"
            else -> normalizeKey(optionCode)
        }
        return target == expected
    }

    /** "1:1" → "s01s01" */
    private fun key(pair: Pair<Int, Int>): String =
        "s%02ds%02d".format(pair.first, pair.second)

    /** 官方比分键归一到 "s{HH}s{AA}"（兼容两位填充与非填充写法） */
    private fun normalizeKey(code: String): String {
        val m = Regex("^s(\\d+)s(\\d+)$").find(code) ?: return code
        val h = m.groupValues[1].toIntOrNull() ?: return code
        val a = m.groupValues[2].toIntOrNull() ?: return code
        return key(h to a)
    }
}
