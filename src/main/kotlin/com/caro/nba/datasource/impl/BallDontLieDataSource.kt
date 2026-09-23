package com.caro.nba.datasource.impl

import com.caro.nba.datasource.DataSourceCommon
import com.caro.nba.datasource.GameDetailDataSource
import com.caro.nba.datasource.ScoreDataSource
import com.caro.nba.datasource.StandingsDataSource
import com.caro.nba.model.*
import com.google.gson.JsonObject
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Ball Don't Lie API 数据源实现
 *
 * 免费开源 NBA API，社区维护。
 * 文档：https://app.balldontlie.io/
 *
 * API 端点：
 * - 比分/比赛: https://api.balldontlie.io/v1/games
 * - 排名:     https://api.balldontlie.io/v1/standings
 * - 球员统计: https://api.balldontlie.io/v1/stats
 *
 * 注意：此 API 需要 API Key（免费注册获取），通过 header 传递：Authorization
 */
class BallDontLieScoreDataSource(private val apiKey: String = "") : ScoreDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson
    private val teamNameMap = DataSourceCommon.teamNameMap

    private val baseUrl = "https://api.balldontlie.io/v1/games"

    // BallDontLie 球队 ID 到中英文队名映射
    private val teamIdToInfo = mapOf(
        1 to Pair("ATL", "老鹰"), 2 to Pair("BOS", "凯尔特人"), 3 to Pair("BKN", "篮网"),
        4 to Pair("CHA", "黄蜂"), 5 to Pair("CHI", "公牛"), 6 to Pair("CLE", "骑士"),
        7 to Pair("DAL", "独行侠"), 8 to Pair("DEN", "掘金"), 9 to Pair("DET", "活塞"),
        10 to Pair("GSW", "勇士"), 11 to Pair("HOU", "火箭"), 12 to Pair("IND", "步行者"),
        13 to Pair("LAC", "快船"), 14 to Pair("LAL", "湖人"), 15 to Pair("MEM", "灰熊"),
        16 to Pair("MIA", "热火"), 17 to Pair("MIL", "雄鹿"), 18 to Pair("MIN", "森林狼"),
        19 to Pair("NOP", "鹈鹕"), 20 to Pair("NYK", "尼克斯"), 21 to Pair("OKC", "雷霆"),
        22 to Pair("ORL", "魔术"), 23 to Pair("PHI", "76人"), 24 to Pair("PHX", "太阳"),
        25 to Pair("POR", "开拓者"), 26 to Pair("SAC", "国王"), 27 to Pair("SAS", "马刺"),
        28 to Pair("TOR", "猛龙"), 29 to Pair("UTA", "爵士"), 30 to Pair("WAS", "奇才")
    )

    override fun getScores(date: LocalDate): Result<NBAScoreboard> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Ball Don't Lie API Key 未配置，请在设置中填写"))
        }

        return try {
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val url = "$baseUrl?dates[]=$dateStr"

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", apiKey)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("BallDontLie API请求失败: ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("BallDontLie响应为空"))
                val scoreboard = parseScoreboard(body, date)
                Result.success(scoreboard)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseScoreboard(json: String, date: LocalDate): NBAScoreboard {
        val root = gson.fromJson(json, JsonObject::class.java)
        val gamesArray = root.getAsJsonArray("data") ?: return NBAScoreboard(date.toString(), emptyList())

        val games = gamesArray.mapNotNull { gameElement ->
            try {
                val gameObj = gameElement.asJsonObject
                val gameId = gameObj.get("id")?.asString ?: ""
                val status = gameObj.get("status")?.asString ?: ""
                val period = gameObj.get("period")?.asInt ?: 0
                val clock = gameObj.get("time")?.asString ?: ""
                val startTime = gameObj.get("date")?.asString ?: ""

                val homeTeamObj = gameObj.getAsJsonObject("home_team")
                val awayTeamObj = gameObj.getAsJsonObject("visitor_team")

                val homeTeamId = gameObj.get("home_team_id")?.asInt ?: 0
                val awayTeamId = gameObj.get("visitor_team_id")?.asInt ?: 0

                val homeScore = gameObj.get("home_team_score")?.asInt ?: 0
                val awayScore = gameObj.get("visitor_team_score")?.asInt ?: 0

                val homeInfo = teamIdToInfo[homeTeamId] ?: Pair(homeTeamObj?.get("abbreviation")?.asString ?: "", homeTeamObj?.get("name")?.asString ?: "")
                val awayInfo = teamIdToInfo[awayTeamId] ?: Pair(awayTeamObj?.get("abbreviation")?.asString ?: "", awayTeamObj?.get("name")?.asString ?: "")

                // 状态映射：BallDontLie 的 status 字段
                val gameStatus = when {
                    status.equals("Final", ignoreCase = true) || status.contains("Final") -> "finished"
                    status.contains(":") || period > 0 -> "in_progress"
                    else -> "scheduled"
                }

                NBAGame(
                    gameId = gameId,
                    status = gameStatus,
                    period = period,
                    clock = clock,
                    startTime = startTime,
                    detail = status,
                    homeTeam = NBAGame.Team(
                        id = homeTeamId.toString(),
                        name = homeInfo.second,
                        abbreviation = homeInfo.first,
                        shortName = homeTeamObj?.get("name")?.asString ?: homeInfo.second,
                        logo = ""  // BallDontLie 不提供队徽
                    ),
                    homeScore = homeScore,
                    awayTeam = NBAGame.Team(
                        id = awayTeamId.toString(),
                        name = awayInfo.second,
                        abbreviation = awayInfo.first,
                        shortName = awayTeamObj?.get("name")?.asString ?: awayInfo.second,
                        logo = ""
                    ),
                    awayScore = awayScore
                )
            } catch (e: Exception) {
                null
            }
        }

        return NBAScoreboard(date.toString(), games)
    }
}

class BallDontLieStandingsDataSource(private val apiKey: String = "") : StandingsDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson

    private val baseUrl = "https://api.balldontlie.io/v1/standings"

    // 球队 ID 映射
    private val teamIdToInfo = mapOf(
        1 to Triple("ATL", "老鹰", "East"), 2 to Triple("BOS", "凯尔特人", "East"),
        3 to Triple("BKN", "篮网", "East"), 4 to Triple("CHA", "黄蜂", "East"),
        5 to Triple("CHI", "公牛", "East"), 6 to Triple("CLE", "骑士", "East"),
        7 to Triple("DAL", "独行侠", "West"), 8 to Triple("DEN", "掘金", "West"),
        9 to Triple("DET", "活塞", "East"), 10 to Triple("GSW", "勇士", "West"),
        11 to Triple("HOU", "火箭", "West"), 12 to Triple("IND", "步行者", "East"),
        13 to Triple("LAC", "快船", "West"), 14 to Triple("LAL", "湖人", "West"),
        15 to Triple("MEM", "灰熊", "West"), 16 to Triple("MIA", "热火", "East"),
        17 to Triple("MIL", "雄鹿", "East"), 18 to Triple("MIN", "森林狼", "West"),
        19 to Triple("NOP", "鹈鹕", "West"), 20 to Triple("NYK", "尼克斯", "East"),
        21 to Triple("OKC", "雷霆", "West"), 22 to Triple("ORL", "魔术", "East"),
        23 to Triple("PHI", "76人", "East"), 24 to Triple("PHX", "太阳", "West"),
        25 to Triple("POR", "开拓者", "West"), 26 to Triple("SAC", "国王", "West"),
        27 to Triple("SAS", "马刺", "West"), 28 to Triple("TOR", "猛龙", "East"),
        29 to Triple("UTA", "爵士", "West"), 30 to Triple("WAS", "奇才", "East")
    )

    override fun getStandings(): Result<NBAStandings> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Ball Don't Lie API Key 未配置，请在设置中填写"))
        }

        return try {
            // 获取当前赛季排名（默认取上一赛季或最近赛季）
            val currentSeason = getCurrentSeason()
            val url = "$baseUrl?season=$currentSeason"

            val requestBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)

            if (apiKey.isNotBlank()) {
                requestBuilder.header("Authorization", apiKey)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("BallDontLie API请求失败: ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("BallDontLie响应为空"))
                val standings = parseStandings(body)
                Result.success(standings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getCurrentSeason(): Int {
        val year = LocalDate.now().year
        // NBA 赛季横跨两年，如 2024-25 赛季用 2024
        return if (LocalDate.now().monthValue < 10) year - 1 else year
    }

    private fun parseStandings(json: String): NBAStandings {
        val root = gson.fromJson(json, JsonObject::class.java)
        val dataArray = root.getAsJsonArray("data") ?: return createEmptyStandings()

        val easternTeams = mutableListOf<TeamStanding>()
        val westernTeams = mutableListOf<TeamStanding>()

        dataArray.forEach { teamElement ->
            try {
                val teamObj = teamElement.asJsonObject
                val teamId = teamObj.get("team_id")?.asInt ?: 0
                val info = teamIdToInfo[teamId] ?: return@forEach

                val wins = teamObj.get("wins")?.asInt ?: 0
                val losses = teamObj.get("losses")?.asInt ?: 0
                val winPercent = if (wins + losses > 0) wins.toDouble() / (wins + losses) else 0.0
                val gamesBehind = teamObj.get("games_behind")?.asString ?: "-"
                val streak = teamObj.get("streak")?.asString ?: ""
                val conferenceRank = teamObj.get("conference_rank")?.asInt ?: 0
                val homeRecord = "${teamObj.get("home_win")?.asInt ?: 0}-${teamObj.get("home_loss")?.asInt ?: 0}"
                val awayRecord = "${teamObj.get("away_win")?.asInt ?: 0}-${teamObj.get("away_loss")?.asInt ?: 0}"
                val last10 = "${teamObj.get("last_ten_win")?.asInt ?: 0}-${teamObj.get("last_ten_loss")?.asInt ?: 0}"

                val standing = TeamStanding(
                    teamId = teamId.toString(),
                    teamName = info.second,
                    abbreviation = info.first,
                    logo = "",  // BallDontLie 不提供队徽
                    wins = wins,
                    losses = losses,
                    winPercent = winPercent,
                    gamesBehind = gamesBehind,
                    homeRecord = homeRecord,
                    awayRecord = awayRecord,
                    last10 = last10,
                    streak = streak,
                    conferenceRank = conferenceRank,
                    clincher = ""
                )

                if (info.third == "East") easternTeams.add(standing) else westernTeams.add(standing)
            } catch (_: Exception) {
            }
        }

        easternTeams.sortBy { it.conferenceRank }
        westernTeams.sortBy { it.conferenceRank }

        return NBAStandings(
            eastern = ConferenceStandings("East", easternTeams),
            western = ConferenceStandings("West", westernTeams),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }

    private fun createEmptyStandings(): NBAStandings {
        return NBAStandings(
            eastern = ConferenceStandings("East", emptyList()),
            western = ConferenceStandings("West", emptyList()),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }
}

/**
 * BallDontLie 比赛详情数据源
 * 注意：BallDontLie 的 stats 接口提供球员数据，但没有文字转播（play-by-play）
 */
class BallDontLieGameDetailDataSource(private val apiKey: String = "") : GameDetailDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson

    private val gamesUrl = "https://api.balldontlie.io/v1/games"
    private val statsUrl = "https://api.balldontlie.io/v1/stats"

    private val teamIdToInfo = mapOf(
        1 to Pair("ATL", "老鹰"), 2 to Pair("BOS", "凯尔特人"), 3 to Pair("BKN", "篮网"),
        4 to Pair("CHA", "黄蜂"), 5 to Pair("CHI", "公牛"), 6 to Pair("CLE", "骑士"),
        7 to Pair("DAL", "独行侠"), 8 to Pair("DEN", "掘金"), 9 to Pair("DET", "活塞"),
        10 to Pair("GSW", "勇士"), 11 to Pair("HOU", "火箭"), 12 to Pair("IND", "步行者"),
        13 to Pair("LAC", "快船"), 14 to Pair("LAL", "湖人"), 15 to Pair("MEM", "灰熊"),
        16 to Pair("MIA", "热火"), 17 to Pair("MIL", "雄鹿"), 18 to Pair("MIN", "森林狼"),
        19 to Pair("NOP", "鹈鹕"), 20 to Pair("NYK", "尼克斯"), 21 to Pair("OKC", "雷霆"),
        22 to Pair("ORL", "魔术"), 23 to Pair("PHI", "76人"), 24 to Pair("PHX", "太阳"),
        25 to Pair("POR", "开拓者"), 26 to Pair("SAC", "国王"), 27 to Pair("SAS", "马刺"),
        28 to Pair("TOR", "猛龙"), 29 to Pair("UTA", "爵士"), 30 to Pair("WAS", "奇才")
    )

    override fun getGameDetail(gameId: String): Result<GameDetail> {
        if (apiKey.isBlank()) {
            return Result.failure(Exception("Ball Don't Lie API Key 未配置，请在设置中填写"))
        }

        return try {
            // 1. 获取比赛基本信息
            val gameUrl = "$gamesUrl/$gameId"
            val gameRequest = Request.Builder()
                .url(gameUrl)
                .header("Authorization", apiKey)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .build()

            val (gameObj, gameBody) = client.newCall(gameRequest).execute().use { gameResponse ->
                if (!gameResponse.isSuccessful) {
                    return Result.failure(Exception("BallDontLie API请求失败: ${gameResponse.code}"))
                }
                val body = gameResponse.body?.string() ?: return Result.failure(Exception("响应为空"))
                val gameRoot = gson.fromJson(body, JsonObject::class.java)
                val obj = gameRoot.getAsJsonObject("data") ?: return Result.failure(Exception("无比赛数据"))
                obj to body
            }

            // 2. 获取球员统计
            val statsUrlWithId = "$statsUrl?game_ids[]=$gameId&per_page=100"
            val statsRequest = Request.Builder()
                .url(statsUrlWithId)
                .header("Authorization", apiKey)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .build()

            val statsBody = client.newCall(statsRequest).execute().use { statsResponse ->
                if (statsResponse.isSuccessful) statsResponse.body?.string() else null
            }

            val homeTeamId = gameObj.get("home_team_id")?.asInt ?: 0
            val awayTeamId = gameObj.get("visitor_team_id")?.asInt ?: 0
            val homeInfo = teamIdToInfo[homeTeamId] ?: Pair("", "")
            val awayInfo = teamIdToInfo[awayTeamId] ?: Pair("", "")

            val status = gameObj.get("status")?.asString ?: ""
            val gameStatus = when {
                status.equals("Final", ignoreCase = true) || status.contains("Final") -> "finished"
                gameObj.get("period")?.asInt ?: 0 > 0 -> "in_progress"
                else -> "scheduled"
            }

            val homePlayers = mutableListOf<GameDetail.Player>()
            val awayPlayers = mutableListOf<GameDetail.Player>()

            // 解析球员统计
            statsBody?.let {
                val statsRoot = gson.fromJson(it, JsonObject::class.java)
                val dataArray = statsRoot.getAsJsonArray("data")
                dataArray?.forEach { statElement ->
                    try {
                        val statObj = statElement.asJsonObject
                        val playerTeamId = statObj.get("team")?.asJsonObject?.get("id")?.asInt ?: 0

                        val player = GameDetail.Player(
                            id = statObj.get("player")?.asJsonObject?.get("id")?.asString ?: "",
                            name = statObj.get("player")?.asJsonObject?.let { p ->
                                "${p.get("first_name")?.asString ?: ""} ${p.get("last_name")?.asString ?: ""}".trim()
                            } ?: "",
                            jersey = statObj.get("player")?.asJsonObject?.get("jersey_number")?.asString ?: "",
                            position = statObj.get("player")?.asJsonObject?.get("position")?.asString ?: "",
                            minutes = statObj.get("min")?.asString ?: "0",
                            points = statObj.get("pts")?.asInt?.toString() ?: "0",
                            rebounds = statObj.get("reb")?.asInt?.toString() ?: "0",
                            assists = statObj.get("ast")?.asInt?.toString() ?: "0",
                            steals = statObj.get("stl")?.asInt?.toString() ?: "0",
                            blocks = statObj.get("blk")?.asInt?.toString() ?: "0",
                            turnovers = statObj.get("turnover")?.asInt?.toString() ?: "0",
                            fgMade = statObj.get("fgm")?.asInt?.toString() ?: "0",
                            fgAttempts = statObj.get("fga")?.asInt?.toString() ?: "0",
                            threeMade = statObj.get("fg3m")?.asInt?.toString() ?: "0",
                            threeAttempts = statObj.get("fg3a")?.asInt?.toString() ?: "0",
                            ftMade = statObj.get("ftm")?.asInt?.toString() ?: "0",
                            ftAttempts = statObj.get("fta")?.asInt?.toString() ?: "0",
                            plusMinus = "",
                            headshot = "",
                            playerUrl = ""
                        )

                        if (playerTeamId == homeTeamId) homePlayers.add(player) else awayPlayers.add(player)
                    } catch (_: Exception) {
                    }
                }
            }

            // 计算领袖
            val homeLeaders = calculateLeaders(homePlayers)
            val awayLeaders = calculateLeaders(awayPlayers)

            val detail = GameDetail(
                gameId = gameId,
                status = gameStatus,
                clock = gameObj.get("time")?.asString ?: "",
                period = gameObj.get("period")?.asInt ?: 0,
                venue = null,
                homeTeam = GameDetail.TeamDetail(
                    id = homeTeamId.toString(),
                    name = homeInfo.second,
                    abbreviation = homeInfo.first,
                    logo = "",
                    score = gameObj.get("home_team_score")?.asInt ?: 0,
                    statistics = emptyMap(),
                    leaders = homeLeaders
                ),
                awayTeam = GameDetail.TeamDetail(
                    id = awayTeamId.toString(),
                    name = awayInfo.second,
                    abbreviation = awayInfo.first,
                    logo = "",
                    score = gameObj.get("visitor_team_score")?.asInt ?: 0,
                    statistics = emptyMap(),
                    leaders = awayLeaders
                ),
                players = GameDetail.PlayerStats(homePlayers, awayPlayers),
                highlights = emptyList()
            )

            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun calculateLeaders(players: List<GameDetail.Player>): List<GameDetail.TeamLeader> {
        val sortedByPoints = players.sortedByDescending { it.points.toIntOrNull() ?: 0 }
        val sortedByRebounds = players.sortedByDescending { it.rebounds.toIntOrNull() ?: 0 }
        val sortedByAssists = players.sortedByDescending { it.assists.toIntOrNull() ?: 0 }

        return listOfNotNull(
            sortedByPoints.firstOrNull()?.let { GameDetail.TeamLeader("得分", it.name, "", "", it.points) },
            sortedByRebounds.firstOrNull()?.let { GameDetail.TeamLeader("篮板", it.name, "", "", it.rebounds) },
            sortedByAssists.firstOrNull()?.let { GameDetail.TeamLeader("助攻", it.name, "", "", it.assists) }
        )
    }

    override fun getPlayByPlay(gameId: String, homeTeamId: String, awayTeamId: String): Result<PlayByPlay> {
        // BallDontLie 免费版不提供 play-by-play 数据
        return Result.failure(Exception("Ball Don't Lie API 暂不支持文字转播数据"))
    }
}
