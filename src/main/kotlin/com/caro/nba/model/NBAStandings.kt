package com.caro.nba.model

/**
 * NBA 排名数据模型
 */
data class TeamStanding(
    val teamId: String,
    val teamName: String,          // 中文名
    val abbreviation: String,       // 缩写如 LAL, BOS
    val logo: String,              // 队徽URL
    val wins: Int,                 // 胜场
    val losses: Int,               // 负场
    val winPercent: Double,        // 胜率
    val gamesBehind: String,       // 落后第一名场次
    val homeRecord: String,        // 主场战绩
    val awayRecord: String,        // 客场战绩
    val last10: String,            // 近10场
    val streak: String,            // 连胜/连败
    val conferenceRank: Int,       // 分区排名
    val divisionRank: Int = 0,     // 赛区排名
    val playoffSeed: Int = 0,      // 季后赛种子排名
    val clincher: String = ""      // 季后赛状态 (x: 已锁定, y: 分区冠军, etc.)
) {
    /**
     * 获取战绩显示字符串
     */
    fun getRecordDisplay(): String = "$wins-$losses"
    
    /**
     * 获取胜率显示字符串
     */
    fun getWinPercentDisplay(): String = String.format("%.3f", winPercent)
    
    /**
     * 是否在季后赛区（前6名）
     */
    fun isInPlayoffSpot(): Boolean = conferenceRank in 1..6
    
    /**
     * 是否在附加赛区（7-10名）
     */
    fun isInPlayInTournament(): Boolean = conferenceRank in 7..10
    
    /**
     * 获取排名状态类型
     */
    fun getRankStatus(): RankStatus {
        return when {
            clincher.contains("z") -> RankStatus.DIVISION_LEADER  // 分区冠军
            clincher.contains("y") -> RankStatus.CONFERENCE_LEADER // 分区冠军
            clincher.contains("x") -> RankStatus.PLAYOFF_CLINCHED  // 已锁定季后赛
            conferenceRank in 1..6 -> RankStatus.PLAYOFF_SPOT      // 季后赛区
            conferenceRank in 7..10 -> RankStatus.PLAY_IN          // 附加赛区
            else -> RankStatus.OUT                                  // 淘汰
        }
    }
}

enum class RankStatus {
    DIVISION_LEADER,    // 分区冠军
    CONFERENCE_LEADER,  // 联盟冠军
    PLAYOFF_CLINCHED,   // 已锁定季后赛
    PLAYOFF_SPOT,       // 季后赛区
    PLAY_IN,            // 附加赛区
    OUT                 // 淘汰
}

/**
 * 分区排名
 */
data class ConferenceStandings(
    val conference: String,        // "East" / "West"
    val teams: List<TeamStanding>
)

/**
 * 完整排名数据
 */
data class NBAStandings(
    val eastern: ConferenceStandings,
    val western: ConferenceStandings,
    val lastUpdated: String = ""   // 更新时间
)
