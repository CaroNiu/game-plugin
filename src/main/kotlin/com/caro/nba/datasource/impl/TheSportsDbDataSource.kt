package com.caro.nba.datasource.impl

import com.caro.nba.datasource.DataSourceCommon
import com.caro.nba.datasource.ScoreDataSource
import com.caro.nba.datasource.StandingsDataSource
import com.caro.nba.model.*
import com.google.gson.JsonObject
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * TheSportsDB 数据源
 *
 * 免费开源体育数据 API，无需 API Key（也支持注册 Key 获取更高配额）。
 * 覆盖多种体育运动，NBA 数据包含比分、排名、球队信息等。
 *
 * 官网：https://www.thesportsdb.com/
 *
 * 主要端点：
 * - 比分/赛程: https://www.thesportsdb.com/api/v1/json/3/eventsday.php?d=YYYY-MM-DD&s=NBA
 * - 排名:     https://www.thesportsdb.com/api/v1/json/3/lookuptable.php?l=4387&s=2024-2025
 * - 球队信息: https://www.thesportsdb.com/api/v1/json/3/searchteams.php?t=teamname
 *
 * 注意：免费版有速率限制（100次/天），数据更新频率较低。
 * 免费公共 Key: 3（有限制），注册可获得更高配额。
 */
class TheSportsDbScoreDataSource(private val apiKey: String = "3") : ScoreDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson
    private val teamNameMap = DataSourceCommon.teamNameMap

    private val baseUrl = "https://www.thesportsdb.com/api/v1/json/$apiKey"

    // TheSportsDB 队名到中英文映射
    private val teamNameToChinese = mapOf(
        "Atlanta Hawks" to "老鹰",
        "Boston Celtics" to "凯尔特人",
        "Brooklyn Nets" to "篮网",
        "Charlotte Hornets" to "黄蜂",
        "Chicago Bulls" to "公牛",
        "Cleveland Cavaliers" to "骑士",
        "Dallas Mavericks" to "独行侠",
        "Denver Nuggets" to "掘金",
        "Detroit Pistons" to "活塞",
        "Golden State Warriors" to "勇士",
        "Houston Rockets" to "火箭",
        "Indiana Pacers" to "步行者",
        "LA Clippers" to "快船",
        "Los Angeles Clippers" to "快船",
        "Los Angeles Lakers" to "湖人",
        "Memphis Grizzlies" to "灰熊",
        "Miami Heat" to "热火",
        "Milwaukee Bucks" to "雄鹿",
        "Minnesota Timberwolves" to "森林狼",
        "New Orleans Pelicans" to "鹈鹕",
        "New York Knicks" to "尼克斯",
        "Oklahoma City Thunder" to "雷霆",
        "Orlando Magic" to "魔术",
        "Philadelphia 76ers" to "76人",
        "Phoenix Suns" to "太阳",
        "Portland Trail Blazers" to "开拓者",
        "Sacramento Kings" to "国王",
        "San Antonio Spurs" to "马刺",
        "Toronto Raptors" to "猛龙",
        "Utah Jazz" to "爵士",
        "Washington Wizards" to "奇才"
    )

    override fun getScores(date: LocalDate): Result<NBAScoreboard> {
        return try {
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val url = "$baseUrl/eventsday.php?d=$dateStr&s=NBA"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("TheSportsDB API请求失败: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("TheSportsDB响应为空"))
                val scoreboard = parseScoreboard(body, date)
                return Result.success(scoreboard)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseScoreboard(json: String, date: LocalDate): NBAScoreboard {
        val root = gson.fromJson(json, JsonObject::class.java)
        val eventsElement = root.get("events")
        if (eventsElement == null || eventsElement.isJsonNull || !eventsElement.isJsonArray) {
            return NBAScoreboard(date.toString(), emptyList())
        }
        val events = eventsElement.asJsonArray

        val games = events.mapNotNull { eventElem ->
            try {
                val event = eventElem.asJsonObject

                val eventId = event.get("idEvent")?.asString ?: ""
                val strStatus = event.get("strStatus")?.asString ?: ""
                val intHomeScore = event.get("intHomeScore")?.asInt
                val intAwayScore = event.get("intAwayScore")?.asInt
                val strHomeTeam = event.get("strHomeTeam")?.asString ?: ""
                val strAwayTeam = event.get("strAwayTeam")?.asString ?: ""
                val idHomeTeam = event.get("idHomeTeam")?.asString ?: ""
                val idAwayTeam = event.get("idAwayTeam")?.asString ?: ""
                val strTimestamp = event.get("strTimestamp")?.asString ?: ""
                val strTime = event.get("strTime")?.asString ?: ""
                val intRound = event.get("intRound")?.asInt ?: 0

                // 状态解析
                val status = when {
                    strStatus.equals("Finished", ignoreCase = true) ||
                    strStatus.contains("FT", ignoreCase = true) -> "finished"
                    strStatus.contains("QTR", ignoreCase = true) ||
                    strStatus.contains("Half", ignoreCase = true) ||
                    strStatus.contains("Live", ignoreCase = true) -> "in_progress"
                    else -> "scheduled"
                }

                // 解析节数（从 strStatus 中提取，如 "QTR 4" -> 4）
                var period = 0
                var clock = strStatus
                val qtrMatch = Regex("""QTR\s*(\d+)""").find(strStatus)
                if (qtrMatch != null) {
                    period = qtrMatch.groupValues[1].toIntOrNull() ?: 0
                }

                val homeTeamName = teamNameToChinese[strHomeTeam]
                    ?: teamNameMap[strHomeTeam.split(" ").last()]
                    ?: strHomeTeam
                val awayTeamName = teamNameToChinese[strAwayTeam]
                    ?: teamNameMap[strAwayTeam.split(" ").last()]
                    ?: strAwayTeam

                // 队徽 URL
                val homeBadge = event.get("strHomeTeamBadge")?.asString ?: ""
                val awayBadge = event.get("strAwayTeamBadge")?.asString ?: ""

                NBAGame(
                    gameId = eventId,
                    status = status,
                    period = period,
                    clock = clock,
                    startTime = strTimestamp,
                    detail = strStatus,
                    homeTeam = NBAGame.Team(
                        id = idHomeTeam,
                        name = homeTeamName,
                        abbreviation = event.get("strHomeTeamShort")?.asString ?: "",
                        shortName = strHomeTeam.split(" ").last(),
                        logo = homeBadge
                    ),
                    homeScore = intHomeScore ?: 0,
                    awayTeam = NBAGame.Team(
                        id = idAwayTeam,
                        name = awayTeamName,
                        abbreviation = event.get("strAwayTeamShort")?.asString ?: "",
                        shortName = strAwayTeam.split(" ").last(),
                        logo = awayBadge
                    ),
                    awayScore = intAwayScore ?: 0
                )
            } catch (e: Exception) {
                null
            }
        }

        return NBAScoreboard(date.toString(), games)
    }
}

class TheSportsDbStandingsDataSource(private val apiKey: String = "3") : StandingsDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson
    private val teamNameMap = DataSourceCommon.teamNameMap

    private val baseUrl = "https://www.thesportsdb.com/api/v1/json/$apiKey"

    // NBA league ID: 4387
    private val LEAGUE_ID = "4387"

    private val teamNameToChinese = mapOf(
        "Atlanta Hawks" to "老鹰",
        "Boston Celtics" to "凯尔特人",
        "Brooklyn Nets" to "篮网",
        "Charlotte Hornets" to "黄蜂",
        "Chicago Bulls" to "公牛",
        "Cleveland Cavaliers" to "骑士",
        "Dallas Mavericks" to "独行侠",
        "Denver Nuggets" to "掘金",
        "Detroit Pistons" to "活塞",
        "Golden State Warriors" to "勇士",
        "Houston Rockets" to "火箭",
        "Indiana Pacers" to "步行者",
        "Los Angeles Clippers" to "快船",
        "Los Angeles Lakers" to "湖人",
        "Memphis Grizzlies" to "灰熊",
        "Miami Heat" to "热火",
        "Milwaukee Bucks" to "雄鹿",
        "Minnesota Timberwolves" to "森林狼",
        "New Orleans Pelicans" to "鹈鹕",
        "New York Knicks" to "尼克斯",
        "Oklahoma City Thunder" to "雷霆",
        "Orlando Magic" to "魔术",
        "Philadelphia 76ers" to "76人",
        "Phoenix Suns" to "太阳",
        "Portland Trail Blazers" to "开拓者",
        "Sacramento Kings" to "国王",
        "San Antonio Spurs" to "马刺",
        "Toronto Raptors" to "猛龙",
        "Utah Jazz" to "爵士",
        "Washington Wizards" to "奇才"
    )

    override fun getStandings(): Result<NBAStandings> {
        return try {
            val season = getSeasonString()
            val url = "$baseUrl/lookuptable.php?l=$LEAGUE_ID&s=$season"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("TheSportsDB API请求失败: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("TheSportsDB响应为空"))
                val standings = parseStandings(body)
                return Result.success(standings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getSeasonString(): String {
        val now = LocalDate.now()
        val startYear = if (now.monthValue < 10) now.year - 1 else now.year
        return "$startYear-${startYear + 1}"
    }

    private fun parseStandings(json: String): NBAStandings {
        val root = gson.fromJson(json, JsonObject::class.java)
        val tableElement = root.get("table")
        if (tableElement == null || tableElement.isJsonNull || !tableElement.isJsonArray) {
            return createEmptyStandings()
        }
        val table = tableElement.asJsonArray

        val easternTeams = mutableListOf<TeamStanding>()
        val westernTeams = mutableListOf<TeamStanding>()

        table.forEach { teamElem ->
            try {
                val team = teamElem.asJsonObject

                val teamId = team.get("idTeam")?.asString ?: ""
                val teamName = team.get("strTeam")?.asString ?: ""
                val teamBadge = team.get("strTeamBadge")?.asString ?: ""
                val teamShort = team.get("strTeamShort")?.asString ?: ""

                val wins = team.get("intWin")?.asInt ?: 0
                val losses = team.get("intLoss")?.asInt ?: 0
                val winPct = team.get("strWinPct")?.asDouble
                    ?: if (wins + losses > 0) wins.toDouble() / (wins + losses) else 0.0
                val gamesBehind = team.get("strGamesBack")?.asString ?: "-"
                val rank = team.get("intRank")?.asInt ?: 0
                val homeRecord = team.get("strHome")?.asString ?: "0-0"
                val awayRecord = team.get("strAway")?.asString ?: "0-0"
                val last10 = team.get("strLast10")?.asString ?: "0-0"
                val streak = team.get("strStreak")?.asString ?: ""

                // 分区：根据队名判断
                val conference = getConference(teamName)

                val chineseName = teamNameToChinese[teamName]
                    ?: teamNameMap[teamName.split(" ").last()]
                    ?: teamName

                val standing = TeamStanding(
                    teamId = teamId,
                    teamName = chineseName,
                    abbreviation = teamShort,
                    logo = teamBadge,
                    wins = wins,
                    losses = losses,
                    winPercent = winPct,
                    gamesBehind = gamesBehind,
                    homeRecord = homeRecord,
                    awayRecord = awayRecord,
                    last10 = last10,
                    streak = streak,
                    conferenceRank = rank,
                    clincher = ""
                )

                if (conference == "East") {
                    easternTeams.add(standing)
                } else {
                    westernTeams.add(standing)
                }
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

    private fun getConference(teamName: String): String {
        val eastTeams = listOf(
            "Celtics", "Nets", "Knicks", "76ers", "Raptors",
            "Bulls", "Cavaliers", "Pistons", "Pacers", "Bucks",
            "Hawks", "Hornets", "Heat", "Magic", "Wizards"
        )
        val shortName = teamName.split(" ").last()
        return if (eastTeams.contains(shortName)) "East" else "West"
    }

    private fun createEmptyStandings(): NBAStandings {
        return NBAStandings(
            eastern = ConferenceStandings("East", emptyList()),
            western = ConferenceStandings("West", emptyList()),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }
}
