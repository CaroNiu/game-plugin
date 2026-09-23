package com.caro.nba.model

/**
 * 季后赛对阵图数据模型
 */
data class PlayoffBracketResponse(
    val data: PlayoffBracketData
)

data class PlayoffBracketData(
    val top: List<List<PlayoffBracketSeries>>,    // 西部，按轮次分组
    val bottom: List<List<PlayoffBracketSeries>>, // 东部，按轮次分组
    val finals: PlayoffBracketSeries?             // 总决赛
)

data class PlayoffBracketSeries(
    val teams: List<PlayoffTeam>?,        // 对阵双方
    val info1: String?,                    // 系列赛比分（上队）
    val info2: String?,                    // 系列赛比分（下队）
    val win_threshold: Int,                // 获胜所需场次
    val schedule: PlayoffSchedule?         // 赛程列表
)

data class PlayoffTeam(
    val name: String?,   // 队名
    val img: String?,    // 队徽URL
    val rank: String?    // 种子排名
)

data class PlayoffSchedule(
    val list: List<PlayoffGame>  // 每场比赛
)

data class PlayoffGame(
    val left_team: String?,   // 左队名
    val right_team: String?,  // 右队名
    val left_logo: String?,   // 左队徽
    val right_logo: String?,  // 右队徽
    val score: String?        // 比分，格式 "123-110"
)
