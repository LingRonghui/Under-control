package com.jingcai.predict.data

import androidx.compose.ui.graphics.Color

enum class MatchStatus { UPCOMING, LIVE, FINISHED }

data class MatchInfo(
    val id: String,
    val num: String,
    val league: String,
    val round: String,
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
    val wdl: String = "",
    val conf: Int = 0,
)

/* 模拟数据：对齐单文件原型的首屏内容 */
val TodayMatches = listOf(
    MatchInfo("m1", "周三001", "英超", "第8轮", "19:35", "曼联", "热刺",
        Color(0xFFD93A2B), Color(0xFF2A3C66), 1.85, 3.40, 3.95, MatchStatus.UPCOMING, wdl = "胜", conf = 88),
    MatchInfo("m2", "周三002", "西甲", "第9轮", "20:00", "皇家马德里", "塞维利亚",
        Color(0xFFB8C0CC), Color(0xFFC8102E), 1.32, 5.20, 7.60, MatchStatus.UPCOMING, wdl = "胜", conf = 76),
    MatchInfo("m3", "周三003", "意甲", "第8轮", "21:45", "国际米兰", "尤文图斯",
        Color(0xFF2B6CB0), Color(0xFF2B2B2B), 2.10, 3.20, 3.40, MatchStatus.UPCOMING, wdl = "胜", conf = 91),
    MatchInfo("m4", "周三004", "德甲", "第7轮", "03:30", "拜仁慕尼黑", "勒沃库森",
        Color(0xFFDC052D), Color(0xFFE32221), 1.55, 4.40, 5.20, MatchStatus.LIVE, liveMinute = 53, score = "2:1", htScore = "1:0", wdl = "胜", conf = 85),
    MatchInfo("m5", "周三005", "英超", "第8轮", "04:00", "曼城", "利物浦",
        Color(0xFF6CABD9), Color(0xFFD11F2E), 1.90, 3.60, 3.70, MatchStatus.LIVE, liveMinute = 71, score = "1:1", htScore = "0:1", wdl = "胜", conf = 82),
    MatchInfo("m6", "周三006", "法甲", "第8轮", "02:45", "巴黎圣日耳曼", "马赛",
        Color(0xFF004170), Color(0xFF2FA4D6), 1.30, 5.60, 8.20, MatchStatus.FINISHED, score = "3:1", htScore = "1:1", wdl = "胜", conf = 93),
    MatchInfo("m7", "周三007", "西甲", "第9轮", "03:00", "巴塞罗那", "马德里竞技",
        Color(0xFFA50044), Color(0xFFCB3524), 1.75, 3.70, 4.20, MatchStatus.FINISHED, score = "1:1", htScore = "0:1", wdl = "胜", conf = 78),
)

val TomorrowMatches = listOf(
    MatchInfo("t1", "周四001", "英超", "第8轮", "19:30", "阿森纳", "切尔西",
        Color(0xFFE6454A), Color(0xFF3D6CB3), 1.95, 3.50, 3.85, MatchStatus.UPCOMING, wdl = "胜", conf = 81),
    MatchInfo("t2", "周四002", "意甲", "第8轮", "21:00", "AC米兰", "罗马",
        Color(0xFFC8102E), Color(0xFF8E1F2F), 1.78, 3.60, 4.40, MatchStatus.UPCOMING, wdl = "胜", conf = 84),
    MatchInfo("t3", "周四003", "德甲", "第7轮", "22:30", "多特蒙德", "法兰克福",
        Color(0xFFF5D10C), Color(0xFFE1000F), 1.62, 4.00, 5.20, MatchStatus.UPCOMING, wdl = "胜", conf = 79),
    MatchInfo("t4", "周四004", "西甲", "第9轮", "23:00", "马德里竞技", "皇家社会",
        Color(0xFFCB3524), Color(0xFF0B4EA2), 1.85, 3.30, 4.60, MatchStatus.UPCOMING, wdl = "平", conf = 72),
)

/* 预测分析：综合信心榜（按置信度排序的模拟数据） */
data class Prediction(
    val match: MatchInfo,
    val wdl: String,
    val hdp: String,
    val score: String,
    val hf: String,
    val total: String,
    val conf: Int,
    val key: String,
)

val Predictions = listOf(
    Prediction(TodayMatches[5], "胜", "让负", "3:1", "平/胜", "3-4球", 93, "巴黎锋线全面压制，马赛客场战绩疲软"),
    Prediction(TodayMatches[2], "胜", "胜", "1:0", "平/胜", "1-2球", 91, "国米主场联赛不败金身，尤文锋线连续两轮颗粒无收"),
    Prediction(TodayMatches[0], "胜", "让平", "2:1", "胜/胜", "2-3球", 88, "曼联主场对热刺近5次交锋全胜，热刺防线伤缺两名主力中卫"),
    Prediction(TodayMatches[3], "胜", "让平", "2:1", "胜/胜", "3-4球", 85, "拜仁主场压制明显，勒沃库森防线回撤过深"),
    Prediction(TodayMatches[4], "胜", "让平", "2:1", "平/胜", "2-3球", 82, "曼城控球优势明显，利物浦客场主打防守反击"),
    Prediction(TodayMatches[1], "胜", "让胜", "3:1", "胜/胜", "3-4球", 76, "皇马主场火力全开，塞维利亚主力中卫停赛防线吃紧"),
    Prediction(TodayMatches[6], "胜", "让负", "2:1", "平/平", "2-3球", 78, "巴萨主场控球压制，马竞低位防守伺机反击"),
    Prediction(TomorrowMatches[1], "胜", "让平", "2:0", "胜/胜", "2-3球", 84, "米兰主场进攻效率回升，罗马客场防守不稳"),
    Prediction(TomorrowMatches[0], "胜", "让平", "2:1", "平/胜", "3-4球", 81, "阿森纳边路速度优势，切尔西防线磨合不足"),
    Prediction(TomorrowMatches[2], "胜", "让平", "2:1", "胜/胜", "3-4球", 79, "多特主场青春风暴，法兰克福客场韧性有限"),
)
