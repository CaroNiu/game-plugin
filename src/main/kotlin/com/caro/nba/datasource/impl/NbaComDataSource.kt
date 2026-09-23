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
 * NBA 官方 stats.nba.com 数据源
 *
 * NBA 官方统计数据接口，数据最权威。
 * 无需 API Key，但需要正确设置请求头（Referer, User-Agent）。
 *
 * 主要端点：
 * - 比分/赛程: https://stats.nba.com/stats/scoreboardv2
 * - 排名:     https://stats.nba.com/stats/leaguestandingsv3
 * - 比赛详情: https://stats.nba.com/stats/boxscoresummaryv2
 *
 * 注意：此 API 有速率限制和反爬机制，频繁请求可能被封禁。
 * 请求头必须包含 Referer: https://www.nba.com/ 和正确的 User-Agent。
 */
class NbaComScoreDataSource : ScoreDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson
    private val teamNameMap = DataSourceCommon.teamNameMap

    private val baseUrl = "https://stats.nba.com/stats/scoreboardv2"

    // 球队 ID 到缩写/中文名映射（NBA 官方 14 支球队？不，是 30 支）
    // stats.nba.com 的 teamId 体系与其他 API 不同
    private val teamIdToInfo = mapOf(
        1610612737 to Pair("ATL", "老鹰"),
        1610612738 to Pair("BOS", "凯尔特人"),
        1610612751 to Pair("BKN", "篮网"),
        1610612766 to Pair("CHA", "黄蜂"),
        1610612741 to Pair("CHI", "公牛"),
        1610612739 to Pair("CLE", "骑士"),
        1610612742 to Pair("DAL", "独行侠"),
        1610612743 to Pair("DEN", "掘金"),
        1610612765 to Pair("DET", "活塞"),
        1610612744 to Pair("GSW", "勇士"),
        1610612745 to Pair("HOU", "火箭"),
        1610612754 to Pair("IND", "步行者"),
        1610612746 to Pair("LAC", "快船"),
        1610612747 to Pair("LAL", "湖人"),
        1610612763 to Pair("MEM", "灰熊"),
        1610612748 to Pair("MIA", "热火"),
        1610612749 to Pair("MIL", "雄鹿"),
        1610612750 to Pair("MIN", "森林狼"),
        1610612740 to Pair("NOP", "鹈鹕"),
        1610612752 to Pair("NYK", "尼克斯"),
        1610612760 to Pair("OKC", "雷霆"),
        1610612753 to Pair("ORL", "魔术"),
        1610612755 to Pair("PHI", "76人"),
        1610612756 to Pair("PHX", "太阳"),
        1610612757 to Pair("POR", "开拓者"),
        1610612758 to Pair("SAC", "国王"),
        1610612759 to Pair("SAS", "马刺"),
        1610612761 to Pair("TOR", "猛龙"),
        1610612762 to Pair("UTA", "爵士"),
        1610612764 to Pair("WAS", "奇才")
    )

    override fun getScores(date: LocalDate): Result<NBAScoreboard> {
        return try {
            val dateStr = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy"))
            val url = "$baseUrl?GameDate=$dateStr&LeagueID=00&DayOffset=0"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .header("Referer", "https://www.nba.com/")
                .header("Origin", "https://www.nba.com")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("NBA.com API请求失败: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("NBA.com响应为空"))
                val scoreboard = parseScoreboard(body, date)
                return Result.success(scoreboard)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseScoreboard(json: String, date: LocalDate): NBAScoreboard {
        val root = gson.fromJson(json, JsonObject::class.java)
        val resultSets = root.getAsJsonArray("resultSets") ?: return NBAScoreboard(date.toString(), emptyList())

        // 找到 GameHeader 结果集（包含比赛基本信息和比分）
        var gameHeaderRows: List<List<String>> = emptyList()
        var lineScoreRows: List<List<String>> = emptyList()

        resultSets.forEach { rs ->
            val rsObj = rs.asJsonObject
            val name = rsObj.get("name")?.asString ?: ""
            val rowSet = rsObj.getAsJsonArray("rowSet") ?: return@forEach

            val rows = rowSet.map { row ->
                row.asJsonArray.map { it?.asString ?: "" }
            }

            when (name) {
                "GameHeader" -> gameHeaderRows = rows
                "LineScore" -> lineScoreRows = rows
            }
        }

        if (gameHeaderRows.isEmpty()) {
            return NBAScoreboard(date.toString(), emptyList())
        }

        // GameHeader 列索引: 0=GAME_DATE_EST, 1=GAME_SEQUENCE, 2=GAME_ID, 3=GAME_STATUS_ID, 4=GAME_STATUS_TEXT, ...
        // LineScore 列索引: 0=GAME_ID, 1=TEAM_ID, 3=TEAM_ABBREVIATION, 4=TEAM_CITY_NAME, 5=TEAM_NAME, 6=TEAM_NICKNAME, 7=TEAM_WINS_LOSSES, ... PTS 在较后面
        // 实际需要根据列名 headers 来定位

        // 从 headers 获取列索引更可靠
        var gameIdIdx = 2
        var statusIdx = 3
        var statusTextIdx = 4
        var periodIdx = 7
        var homeTeamIdIdx = 6

        val lineScoreHeaders = getHeaders(resultSets, "LineScore")
        val lsGameIdIdx = lineScoreHeaders.indexOf("GAME_ID")
        val lsTeamIdIdx = lineScoreHeaders.indexOf("TEAM_ID")
        val lsTeamAbbrIdx = lineScoreHeaders.indexOf("TEAM_ABBREVIATION")
        val lsTeamNameIdx = lineScoreHeaders.indexOf("TEAM_NICKNAME")
        val lsPtsIdx = lineScoreHeaders.indexOf("PTS")
        val lsHomeTeamIdIdx = lineScoreHeaders.indexOf("HOME_TEAM_ID")
        val lsVisitorTeamIdIdx = lineScoreHeaders.indexOf("VISITOR_TEAM_ID")

        // 按 GAME_ID 分组 LineScore 数据
        val lineScoreByGame = lineScoreRows.groupBy { it.getOrElse(lsGameIdIdx) { "" } }

        val games = gameHeaderRows.mapNotNull { header ->
            try {
                val gameId = header.getOrElse(gameIdIdx) { "" }
                val gameStatusId = header.getOrElse(statusIdx) { "1" }
                val gameStatusText = header.getOrElse(statusTextIdx) { "" }

                val lineScores = lineScoreByGame[gameId] ?: return@mapNotNull null
                if (lineScores.size < 2) return@mapNotNull null

                // 找到主队和客队（通常第一个是客队，第二个是主队，或者通过 HOME_TEAM_ID 来判断）
                val visitorTeam = lineScores[0]
                val homeTeam = lineScores[1]

                val awayTeamId = visitorTeam.getOrElse(lsTeamIdIdx) { "" }
                val homeTeamId = homeTeam.getOrElse(lsTeamIdIdx) { "" }
                val awayAbbr = visitorTeam.getOrElse(lsTeamAbbrIdx) { "" }
                val homeAbbr = homeTeam.getOrElse(lsTeamAbbrIdx) { "" }
                val awayName = visitorTeam.getOrElse(lsTeamNameIdx) { "" }
                val homeName = homeTeam.getOrElse(lsTeamNameIdx) { "" }
                val awayScore = visitorTeam.getOrElse(lsPtsIdx) { "0" }.toIntOrNull() ?: 0
                val homeScore = homeTeam.getOrElse(lsPtsIdx) { "0" }.toIntOrNull() ?: 0

                // 状态映射：1=未开始, 2=进行中, 3=已结束
                val status = when (gameStatusId) {
                    "1" -> "scheduled"
                    "2" -> "in_progress"
                    "3" -> "finished"
                    else -> "scheduled"
                }

                // 从 gameStatusText 中解析节数和时间
                var period = 0
                var clock = ""
                if (status == "in_progress") {
                    // 格式如 "Q4 - 05:32" 或 "End of 3rd Period"
                    val parts = gameStatusText.split(" - ")
                    if (parts.isNotEmpty()) {
                        val periodStr = parts[0]
                        period = when {
                            periodStr.contains("Q1") -> 1
                            periodStr.contains("Q2") -> 2
                            periodStr.contains("Q3") -> 3
                            periodStr.contains("Q4") -> 4
                            periodStr.contains("OT") -> 5
                            else -> 0
                        }
                        clock = parts.getOrNull(1) ?: ""
                    }
                }

                val awayInfo = teamIdToInfo[awayTeamId.toIntOrNull() ?: 0] ?: Pair(awayAbbr, teamNameMap[awayName] ?: awayName)
                val homeInfo = teamIdToInfo[homeTeamId.toIntOrNull() ?: 0] ?: Pair(homeAbbr, teamNameMap[homeName] ?: homeName)

                NBAGame(
                    gameId = gameId,
                    status = status,
                    period = period,
                    clock = clock,
                    startTime = gameStatusText,
                    detail = gameStatusText,
                    homeTeam = NBAGame.Team(
                        id = homeTeamId,
                        name = homeInfo.second,
                        abbreviation = homeInfo.first,
                        shortName = homeName,
                        logo = "https://cdn.nba.com/logos/nba/$homeTeamId/primary/L/logo.svg"
                    ),
                    homeScore = homeScore,
                    awayTeam = NBAGame.Team(
                        id = awayTeamId,
                        name = awayInfo.second,
                        abbreviation = awayInfo.first,
                        shortName = awayName,
                        logo = "https://cdn.nba.com/logos/nba/$awayTeamId/primary/L/logo.svg"
                    ),
                    awayScore = awayScore
                )
            } catch (e: Exception) {
                null
            }
        }

        return NBAScoreboard(date.toString(), games)
    }

    private fun getHeaders(resultSets: com.google.gson.JsonArray, setName: String): List<String> {
        resultSets.forEach { rs ->
            val rsObj = rs.asJsonObject
            if (rsObj.get("name")?.asString == setName) {
                val headers = rsObj.getAsJsonArray("headers") ?: return emptyList()
                return headers.map { it?.asString ?: "" }
            }
        }
        return emptyList()
    }
}

class NbaComStandingsDataSource : StandingsDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson
    private val teamNameMap = DataSourceCommon.teamNameMap

    private val baseUrl = "https://stats.nba.com/stats/leaguestandingsv3"

    private val teamIdToInfo = mapOf(
        1610612737 to Triple("ATL", "老鹰", "East"),
        1610612738 to Triple("BOS", "凯尔特人", "East"),
        1610612751 to Triple("BKN", "篮网", "East"),
        1610612766 to Triple("CHA", "黄蜂", "East"),
        1610612741 to Triple("CHI", "公牛", "East"),
        1610612739 to Triple("CLE", "骑士", "East"),
        1610612742 to Triple("DAL", "独行侠", "West"),
        1610612743 to Triple("DEN", "掘金", "West"),
        1610612765 to Triple("DET", "活塞", "East"),
        1610612744 to Triple("GSW", "勇士", "West"),
        1610612745 to Triple("HOU", "火箭", "West"),
        1610612754 to Triple("IND", "步行者", "East"),
        1610612746 to Triple("LAC", "快船", "West"),
        1610612747 to Triple("LAL", "湖人", "West"),
        1610612763 to Triple("MEM", "灰熊", "West"),
        1610612748 to Triple("MIA", "热火", "East"),
        1610612749 to Triple("MIL", "雄鹿", "East"),
        1610612750 to Triple("MIN", "森林狼", "West"),
        1610612740 to Triple("NOP", "鹈鹕", "West"),
        1610612752 to Triple("NYK", "尼克斯", "East"),
        1610612760 to Triple("OKC", "雷霆", "West"),
        1610612753 to Triple("ORL", "魔术", "East"),
        1610612755 to Triple("PHI", "76人", "East"),
        1610612756 to Triple("PHX", "太阳", "West"),
        1610612757 to Triple("POR", "开拓者", "West"),
        1610612758 to Triple("SAC", "国王", "West"),
        1610612759 to Triple("SAS", "马刺", "West"),
        1610612761 to Triple("TOR", "猛龙", "East"),
        1610612762 to Triple("UTA", "爵士", "West"),
        1610612764 to Triple("WAS", "奇才", "East")
    )

    override fun getStandings(): Result<NBAStandings> {
        return try {
            val currentSeason = getCurrentSeason()
            val seasonStr = "${currentSeason}-${((currentSeason + 1) % 100).toString().padStart(2, '0')}"
            val url = "$baseUrl?LeagueID=00&Season=$seasonStr&SeasonType=Regular+Season"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .header("Referer", "https://www.nba.com/")
                .header("Origin", "https://www.nba.com")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(Exception("NBA.com API请求失败: HTTP ${response.code}"))
                }

                val body = response.body?.string() ?: return Result.failure(Exception("NBA.com响应为空"))
                val standings = parseStandings(body)
                return Result.success(standings)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getCurrentSeason(): Int {
        val now = LocalDate.now()
        return if (now.monthValue < 10) now.year - 1 else now.year
    }

    private fun parseStandings(json: String): NBAStandings {
        val root = gson.fromJson(json, JsonObject::class.java)
        val resultSets = root.getAsJsonArray("resultSets") ?: return createEmptyStandings()

        var rows: List<List<String>> = emptyList()
        var headers = emptyList<String>()

        resultSets.forEach { rs ->
            val rsObj = rs.asJsonObject
            val name = rsObj.get("name")?.asString ?: ""
            if (name == "Standings") {
                val headerArr = rsObj.getAsJsonArray("headers")
                headers = headerArr?.map { it?.asString ?: "" } ?: emptyList()

                val rowSet = rsObj.getAsJsonArray("rowSet") ?: return@forEach
                rows = rowSet.map { row ->
                    row.asJsonArray.map { it?.asString ?: "" }
                }
                return@forEach
            }
        }

        if (rows.isEmpty()) return createEmptyStandings()

        // 列索引
        val teamIdIdx = headers.indexOf("TeamID")
        val teamCityIdx = headers.indexOf("TeamCity")
        val teamNameIdx = headers.indexOf("Nickname")
        val winsIdx = headers.indexOf("WINS")
        val lossesIdx = headers.indexOf("LOSSES")
        val winPctIdx = headers.indexOf("WinPCT")
        val gamesBehindIdx = headers.indexOf("GamesBehind")
        val confRankIdx = headers.indexOf("ConferenceRank")
        val homeWinsIdx = headers.indexOf("HOME")
        val roadWinsIdx = headers.indexOf("ROAD")
        val last10Idx = headers.indexOf("L10")
        val streakIdx = headers.indexOf("CurrentStreak")
        val clincherIdx = headers.indexOf("ClinchIndicator")
        val confIdx = headers.indexOf("Conference")

        val easternTeams = mutableListOf<TeamStanding>()
        val westernTeams = mutableListOf<TeamStanding>()

        rows.forEach { row ->
            try {
                val teamId = row.getOrElse(teamIdIdx) { "0" }
                val teamIdInt = teamId.toIntOrNull() ?: 0
                val info = teamIdToInfo[teamIdInt]
                val conference = info?.third ?: row.getOrElse(confIdx) { "" }
                val teamCity = row.getOrElse(teamCityIdx) { "" }
                val teamNick = row.getOrElse(teamNameIdx) { "" }
                val fullName = "$teamCity $teamNick"

                val wins = row.getOrElse(winsIdx) { "0" }.toIntOrNull() ?: 0
                val losses = row.getOrElse(lossesIdx) { "0" }.toIntOrNull() ?: 0
                val winPct = row.getOrElse(winPctIdx) { "0" }.toDoubleOrNull() ?: 0.0
                val gamesBehind = row.getOrElse(gamesBehindIdx) { "-" }
                val confRank = row.getOrElse(confRankIdx) { "0" }.toIntOrNull() ?: 0
                val homeRecord = row.getOrElse(homeWinsIdx) { "0-0" }
                val roadRecord = row.getOrElse(roadWinsIdx) { "0-0" }
                val last10 = row.getOrElse(last10Idx) { "0-0" }
                val streakRaw = row.getOrElse(streakIdx) { "" }
                val clincher = row.getOrElse(clincherIdx) { "" }

                val streak = if (streakRaw.isNotEmpty()) {
                    // 格式: W3 或 L5
                    if (streakRaw.startsWith("W")) "🔥${streakRaw.drop(1)}连胜"
                    else if (streakRaw.startsWith("L")) "${streakRaw.drop(1)}连败"
                    else streakRaw
                } else ""

                val teamName = info?.second ?: teamNameMap[teamNick] ?: teamNick

                val standing = TeamStanding(
                    teamId = teamId,
                    teamName = teamName,
                    abbreviation = info?.first ?: "",
                    logo = "https://cdn.nba.com/logos/nba/$teamId/primary/L/logo.svg",
                    wins = wins,
                    losses = losses,
                    winPercent = winPct,
                    gamesBehind = gamesBehind,
                    homeRecord = homeRecord,
                    awayRecord = roadRecord,
                    last10 = last10,
                    streak = streak,
                    conferenceRank = confRank,
                    clincher = clincher
                )

                if (conference.equals("East", ignoreCase = true) || conference == "东部") {
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

    private fun createEmptyStandings(): NBAStandings {
        return NBAStandings(
            eastern = ConferenceStandings("East", emptyList()),
            western = ConferenceStandings("West", emptyList()),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }
}
