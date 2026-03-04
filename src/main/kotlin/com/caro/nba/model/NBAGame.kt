package com.caro.nba.model

/**
 * NBA 比赛数据模型
 */
data class NBAGame(
    val gameId: String,
    val status: String,           // 状态: scheduled, in_progress, finished
    val period: Int = 0,          // 当前节次
    val clock: String = "",       // 比赛时间
    val startTime: String,       // 开始时间
    
    // 主队信息
    val homeTeam: Team,
    val homeScore: Int = 0,
    
    // 客队信息
    val awayTeam: Team,
    val awayScore: Int = 0
) {
    data class Team(
        val id: String,
        val name: String,
        val abbreviation: String,
        val logo: String = ""
    )
    
    fun getScoreDisplay(): String {
        return "${awayTeam.abbreviation} ${awayScore} - ${homeScore} ${homeTeam.abbreviation}"
    }
    
    fun getStatusDisplay(): String {
        return when {
            status == "scheduled" -> "未开始"
            status == "finished" -> "已结束"
            period > 4 -> "OT$${period - 4}"
            period in 1..4 -> "第${when(period) { 1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; else -> "" }}节 $clock"
            else -> status
        }
    }
}

/**
 * NBA 比赛日数据
 */
data class NBAScoreboard(
    val date: String,
    val games: List<NBAGame>
)