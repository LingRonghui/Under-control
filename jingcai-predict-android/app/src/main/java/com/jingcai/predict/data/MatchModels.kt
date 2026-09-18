package com.jingcai.predict.data

import androidx.compose.ui.graphics.Color

enum class MatchStatus { UPCOMING, LIVE, FINISHED }

data class MatchInfo(
    val id: String,
    val num: String,
    val league: String,
    val kickoff: String,
    val home: String,
    val away: String,
    val homeColor: Color,
    val awayColor: Color,
    val oddsW: Double,
    val oddsD: Double,
    val oddsL: Double,
    val status: MatchStatus,
    val liveMinute: Int? = null,
    val score: String? = null,
    val htScore: String? = null,
)
