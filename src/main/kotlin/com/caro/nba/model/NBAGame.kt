package com.caro.nba.model

/**
 * NBA 比赛数据模型
 */
data class NBAGame(
    val gameId: String,
    val status: String,           // 状态: scheduled, in_progress, finished
    val period: Int = 0,          // 当前节次
    val clock: String = "",       // 比赛时间显示
    val startTime: String,        // 开始时间 ISO格式
    val detail: String = "",      // 详细时间描述
    
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
        val shortName: String = "",  // 短名称如 Lakers, Warriors
        val logo: String = ""
    )
    
    fun getScoreDisplay(): String {
        return "${awayTeam.abbreviation} ${awayScore} - ${homeScore} ${homeTeam.abbreviation}"
    }
    
    /**
     * 获取中文状态显示
     */
    fun getStatusDisplay(): String {
        return when {
            status == "scheduled" -> "未开始"
            status == "finished" -> "已结束"
            status == "in_progress" -> {
                when {
                    period > 4 -> "加时赛第${period - 4}节"
                    period in 1..4 -> "第${CN_NUMBERS[period]}节"
                    else -> "进行中"
                }
            }
            else -> status
        }
    }
    
    /**
     * 获取比赛时钟显示
     */
    fun getClockDisplay(): String {
        return if (status == "in_progress") {
            clock
        } else {
            detail.ifEmpty { 
                startTime.take(16).replace("T", " ") 
            }
        }
    }
    
    companion object {
        private val CN_NUMBERS = mapOf(1 to "一", 2 to "二", 3 to "三", 4 to "四")
    }
}

/**
 * NBA 比赛日数据
 */
data class NBAScoreboard(
    val date: String,
    val games: List<NBAGame>
)