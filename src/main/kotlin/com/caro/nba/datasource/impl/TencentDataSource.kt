package com.caro.nba.datasource.impl

import com.caro.nba.datasource.DataSourceCommon
import com.caro.nba.datasource.ScoreDataSource
import com.caro.nba.datasource.StandingsDataSource
import com.caro.nba.datasource.PlayoffDataSource
import com.caro.nba.model.*
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 腾讯 NBA 数据源（中文数据源）
 *
 * 腾讯体育 NBA 频道的公开数据接口，中文队名，无需 API Key。
 * 主要数据来自 h5sports.match.qq.com 和 other APIs。
 *
 * 主要端点：
 * - 比分/赛程: https://h5sports.match.qq.com/h5/nba/matchlist
 * - 排名:     https://h5sports.match.qq.com/h5/nba/teamrank
 * - 对阵图:   腾讯有专门的季后赛对阵图接口
 *
 * 优势：
 * - 全部中文队名，无需翻译
 * - 季后赛对阵图数据结构完整
 * - 国内访问速度快
 *
 * 注意：腾讯 API 可能有反爬和签名机制，需要正确设置请求头。
 * 接口地址可能变动，作为备用数据源使用。
 */
class TencentScoreDataSource : ScoreDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson

    private val baseUrl = "https://h5sports.match.qq.com/h5/nba/matchlist"

    override fun getScores(date: LocalDate): Result<NBAScoreboard> {
        return try {
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val url = "$baseUrl?date=$dateStr"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15")
                .header("Referer", "https://sports.qq.com/")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("腾讯NBA API请求失败: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("腾讯NBA响应为空"))
                val scoreboard = parseScoreboard(body, date)
                return Result.success(scoreboard)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseScoreboard(json: String, date: LocalDate): NBAScoreboard {
        val root = gson.fromJson(json, JsonObject::class.java)
        val data = root.getAsJsonObject("data") ?: return NBAScoreboard(date.toString(), emptyList())
        val matchList = data.getAsJsonArray("matchList") ?: return NBAScoreboard(date.toString(), emptyList())

        val games = matchList.mapNotNull { matchElem ->
            try {
                val match = matchElem.asJsonObject

                val matchId = match.get("matchId")?.asString ?: ""
                val status = match.get("matchStatus")?.asString ?: "0"
                val homeTeamName = match.get("homeTeamName")?.asString ?: ""
                val awayTeamName = match.get("awayTeamName")?.asString ?: ""
                val homeScore = match.get("homeScore")?.asInt ?: 0
                val awayScore = match.get("awayScore")?.asInt ?: 0
                val homeTeamId = match.get("homeTeamId")?.asString ?: ""
                val awayTeamId = match.get("awayTeamId")?.asString ?: ""
                val quarter = match.get("quarter")?.asInt ?: 0
                val quarterTime = match.get("quarterTime")?.asString ?: ""
                val startTime = match.get("startTime")?.asString ?: ""
                val homeBadge = match.get("homeBadge")?.asString ?: ""
                val awayBadge = match.get("awayBadge")?.asString ?: ""

                // 状态映射：1=未开始, 2=进行中, 3=已结束
                val gameStatus = when (status) {
                    "1" -> "scheduled"
                    "2" -> "in_progress"
                    "3" -> "finished"
                    else -> "scheduled"
                }

                // 队徽 URL 构造
                val homeLogo = if (homeBadge.isNotBlank()) {
                    "https://mat1.gtimg.com/sports/nba/logo/1602/$homeBadge.png"
                } else {
                    "https://sports.gtimg.com/nba/team/logo/big/${homeTeamId}.png"
                }
                val awayLogo = if (awayBadge.isNotBlank()) {
                    "https://mat1.gtimg.com/sports/nba/logo/1602/$awayBadge.png"
                } else {
                    "https://sports.gtimg.com/nba/team/logo/big/${awayTeamId}.png"
                }

                NBAGame(
                    gameId = matchId,
                    status = gameStatus,
                    period = quarter,
                    clock = quarterTime,
                    startTime = startTime,
                    detail = quarterTime,
                    homeTeam = NBAGame.Team(
                        id = homeTeamId,
                        name = homeTeamName,  // 已经是中文
                        abbreviation = "",    // 腾讯 API 不提供缩写
                        shortName = homeTeamName,
                        logo = homeLogo
                    ),
                    homeScore = homeScore,
                    awayTeam = NBAGame.Team(
                        id = awayTeamId,
                        name = awayTeamName,
                        abbreviation = "",
                        shortName = awayTeamName,
                        logo = awayLogo
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

class TencentStandingsDataSource : StandingsDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson

    private val baseUrl = "https://h5sports.match.qq.com/h5/nba/teamrank"

    override fun getStandings(): Result<NBAStandings> {
        return try {
            val url = "$baseUrl?season=current"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15")
                .header("Referer", "https://sports.qq.com/")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("腾讯NBA API请求失败: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("腾讯NBA响应为空"))
                val standings = parseStandings(body)
                return Result.success(standings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStandings(json: String): NBAStandings {
        val root = gson.fromJson(json, JsonObject::class.java)
        val data = root.getAsJsonObject("data") ?: return createEmptyStandings()
        val east = data.getAsJsonArray("east") ?: return createEmptyStandings()
        val west = data.getAsJsonArray("west") ?: return createEmptyStandings()

        val easternTeams = parseConferenceTeams(east)
        val westernTeams = parseConferenceTeams(west)

        return NBAStandings(
            eastern = ConferenceStandings("East", easternTeams),
            western = ConferenceStandings("West", westernTeams),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }

    private fun parseConferenceTeams(teamsArray: com.google.gson.JsonArray): List<TeamStanding> {
        return teamsArray.mapIndexedNotNull { index, teamElem ->
            try {
                val team = teamElem.asJsonObject
                val teamId = team.get("teamId")?.asString ?: ""
                val teamName = team.get("teamName")?.asString ?: ""
                val badge = team.get("badge")?.asString ?: ""
                val wins = team.get("win")?.asInt ?: 0
                val losses = team.get("lose")?.asInt ?: 0
                val winPercent = team.get("winRate")?.asDouble ?: 0.0
                val gamesBehind = team.get("diffWin")?.asString ?: "-"
                val homeRecord = "${team.get("homeWin")?.asInt ?: 0}-${team.get("homeLose")?.asInt ?: 0}"
                val awayRecord = "${team.get("awayWin")?.asInt ?: 0}-${team.get("awayLose")?.asInt ?: 0}"
                val last10 = "${team.get("recentWin")?.asInt ?: 0}-${team.get("recentLose")?.asInt ?: 0}"
                val streak = team.get("streak")?.asString ?: ""
                val conferenceRank = team.get("rank")?.asInt ?: (index + 1)
                val clincher = team.get("clinch")?.asString ?: ""

                val logo = if (badge.isNotBlank()) {
                    "https://mat1.gtimg.com/sports/nba/logo/1602/$badge.png"
                } else {
                    ""
                }

                TeamStanding(
                    teamId = teamId,
                    teamName = teamName,  // 已经是中文
                    abbreviation = "",
                    logo = logo,
                    wins = wins,
                    losses = losses,
                    winPercent = winPercent,
                    gamesBehind = gamesBehind,
                    homeRecord = homeRecord,
                    awayRecord = awayRecord,
                    last10 = last10,
                    streak = streak,
                    conferenceRank = conferenceRank,
                    clincher = clincher
                )
            } catch (e: Exception) {
                null
            }
        }
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
 * 腾讯季后赛对阵图数据源
 *
 * 腾讯体育有完整的季后赛对阵图 API，包括每轮系列赛比分、赛程等。
 * 这是 PlayoffBracketPanel 的理想数据源。
 */
class TencentPlayoffDataSource : PlayoffDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson

    // 腾讯季后赛对阵图接口（地址可能随赛季变化）
    private val playoffUrl = "https://h5sports.match.qq.com/h5/nba/playoff"

    override fun getPlayoffBracket(): Result<PlayoffBracketResponse> {
        return try {
            val currentSeason = getCurrentSeason()
            val url = "$playoffUrl?season=$currentSeason"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 15_0 like Mac OS X) AppleWebKit/605.1.15")
                .header("Referer", "https://sports.qq.com/")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("腾讯NBA季后赛 API请求失败: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("响应为空"))
                val bracket = parseBracket(body)
                return Result.success(bracket)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getCurrentSeason(): Int {
        val now = LocalDate.now()
        return if (now.monthValue < 10) now.year - 1 else now.year
    }

    private fun parseBracket(json: String): PlayoffBracketResponse {
        val root = gson.fromJson(json, JsonObject::class.java)
        val data = root.getAsJsonObject("data") ?: throw Exception("无对阵图数据")

        // 腾讯季后赛数据结构：
        // data.west / data.east，每边有 multi 轮次数组
        // 每轮有 series 数组，每个 series 有 leftTeam/rightTeam/briefLeft/briefRight/gameList 等

        val westRounds = parseConferenceRounds(data, "west")
        val eastRounds = parseConferenceRounds(data, "east")
        val finals = parseFinals(data)

        return PlayoffBracketResponse(
            data = PlayoffBracketData(
                top = westRounds,    // 西部在上
                bottom = eastRounds, // 东部在下
                finals = finals
            )
        )
    }

    private fun parseConferenceRounds(data: JsonObject, conference: String): List<List<PlayoffBracketSeries>> {
        val conf = data.getAsJsonObject(conference) ?: return emptyList()
        val rounds = conf.getAsJsonArray("rounds") ?: return emptyList()

        return rounds.mapIndexed { _, roundElem ->
            val round = roundElem.asJsonObject
            val seriesList = round.getAsJsonArray("series") ?: return@mapIndexed emptyList<PlayoffBracketSeries>()

            seriesList.mapNotNull { seriesElem ->
                try {
                    parseSeries(seriesElem.asJsonObject)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun parseFinals(data: JsonObject): PlayoffBracketSeries? {
        val finals = data.getAsJsonObject("finals") ?: return null
        return try {
            parseSeries(finals)
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSeries(series: JsonObject): PlayoffBracketSeries {
        val leftTeam = series.getAsJsonObject("leftTeam")
        val rightTeam = series.getAsJsonObject("rightTeam")

        val leftName = leftTeam?.get("teamName")?.asString ?: ""
        val rightName = rightTeam?.get("teamName")?.asString ?: ""
        val leftBadge = leftTeam?.get("badge")?.asString ?: ""
        val rightBadge = rightTeam?.get("badge")?.asString ?: ""
        val leftRank = leftTeam?.get("rank")?.asString ?: ""
        val rightRank = rightTeam?.get("rank")?.asString ?: ""

        val leftScore = series.get("leftScore")?.asInt ?: 0
        val rightScore = series.get("rightScore")?.asInt ?: 0
        val winThreshold = series.get("winThreshold")?.asInt ?: 4

        val leftLogo = if (leftBadge.isNotBlank()) {
            "https://mat1.gtimg.com/sports/nba/logo/1602/$leftBadge.png"
        } else ""
        val rightLogo = if (rightBadge.isNotBlank()) {
            "https://mat1.gtimg.com/sports/nba/logo/1602/$rightBadge.png"
        } else ""

        // 解析赛程列表
        val gameList = series.getAsJsonArray("gameList")
        val scheduleGames = gameList?.mapNotNull { gameElem ->
            try {
                val game = gameElem.asJsonObject
                val leftTeamName = game.get("leftTeam")?.asString ?: leftName
                val rightTeamName = game.get("rightTeam")?.asString ?: rightName
                val score = game.get("score")?.asString ?: "vs"

                PlayoffGame(
                    left_team = leftTeamName,
                    right_team = rightTeamName,
                    left_logo = leftLogo,
                    right_logo = rightLogo,
                    score = score
                )
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()

        return PlayoffBracketSeries(
            teams = listOf(
                PlayoffTeam(name = leftName, img = leftLogo, rank = leftRank),
                PlayoffTeam(name = rightName, img = rightLogo, rank = rightRank)
            ),
            info1 = leftScore.toString(),
            info2 = rightScore.toString(),
            win_threshold = winThreshold,
            schedule = if (scheduleGames.isNotEmpty()) PlayoffSchedule(scheduleGames) else null
        )
    }
}
