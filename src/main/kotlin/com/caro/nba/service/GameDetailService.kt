package com.caro.nba.service

import com.caro.nba.model.GameDetail
import com.caro.nba.model.PlayByPlay
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 比赛详情服务
 */
class GameDetailService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private val summaryUrl = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary"

    // 英文队名到中文映射
    private val teamNameMap = mapOf(
        "Hawks" to "老鹰", "Celtics" to "凯尔特人", "Nets" to "篮网", "Hornets" to "黄蜂",
        "Bulls" to "公牛", "Cavaliers" to "骑士", "Mavericks" to "独行侠", "Nuggets" to "掘金",
        "Pistons" to "活塞", "Warriors" to "勇士", "Rockets" to "火箭", "Pacers" to "步行者",
        "Clippers" to "快船", "Lakers" to "湖人", "Grizzlies" to "灰熊", "Heat" to "热火",
        "Bucks" to "雄鹿", "Timberwolves" to "森林狼", "Pelicans" to "鹈鹕", "Knicks" to "尼克斯",
        "Thunder" to "雷霆", "Magic" to "魔术", "76ers" to "76人", "Suns" to "太阳",
        "Trail Blazers" to "开拓者", "Kings" to "国王", "Spurs" to "马刺", "Raptors" to "猛龙",
        "Jazz" to "爵士", "Wizards" to "奇才"
    )

    /**
     * 获取比赛详情
     */
    fun getGameDetail(gameId: String): Result<GameDetail> {
        return try {
            val url = "$summaryUrl?event=$gameId"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(Exception("API请求失败: ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("响应为空"))
            val detail = parseGameDetail(body, gameId)
            Result.success(detail)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseGameDetail(json: String, gameId: String): GameDetail {
        val root = gson.fromJson(json, JsonObject::class.java)

        // 解析场馆
        val gameInfo = root.getAsJsonObject("gameInfo")
        val venueObj = gameInfo?.getAsJsonObject("venue")
        val venue = venueObj?.let {
            GameDetail.Venue(
                name = it.get("fullName")?.asString ?: "",
                city = it.getAsJsonObject("address")?.get("city")?.asString ?: ""
            )
        }

        // 从 header.competitions[0].competitors 获取比分（这是正确的数据源）
        val header = root.getAsJsonObject("header")
        val competitions = header?.getAsJsonArray("competitions")
        val competition = competitions?.get(0)?.asJsonObject
        val competitors = competition?.getAsJsonArray("competitors")

        var homeTeam: GameDetail.TeamDetail? = null
        var awayTeam: GameDetail.TeamDetail? = null
        var status = "scheduled"
        var period = 0
        var statusDetail = ""

        // 解析状态
        val statusObj = competition?.getAsJsonObject("status")
        val type = statusObj?.getAsJsonObject("type")
        val state = type?.get("state")?.asString ?: "pre"
        statusDetail = type?.get("detail")?.asString ?: ""
        period = type?.get("period")?.asInt ?: 0

        status = when (state) {
            "pre" -> "scheduled"
            "in" -> "in_progress"
            "post" -> "finished"
            else -> state
        }

        // 解析球队和比分
        competitors?.forEach { compElement ->
            val compObj = compElement.asJsonObject
            val isHome = compObj.get("homeAway")?.asString == "home"
            val team = compObj.getAsJsonObject("team")
            val shortDisplayName = team.get("shortDisplayName")?.asString ?: ""
            val score = compObj.get("score")?.asString?.toIntOrNull() ?: 0

            val teamDetail = GameDetail.TeamDetail(
                id = team.get("id")?.asString ?: "",
                name = teamNameMap[shortDisplayName] ?: shortDisplayName,
                abbreviation = team.get("abbreviation")?.asString ?: "",
                logo = team.getAsJsonArray("logos")?.get(0)?.asJsonObject?.get("href")?.asString ?: "",
                score = score,
                statistics = emptyMap(),
                leaders = emptyList()
            )

            if (isHome) homeTeam = teamDetail else awayTeam = teamDetail
        }

        // 解析球员领袖
        val leadersArray = root.getAsJsonArray("leaders") ?: emptyList()
        val homeLeaders = mutableListOf<GameDetail.TeamLeader>()
        val awayLeaders = mutableListOf<GameDetail.TeamLeader>()

        leadersArray.forEach { teamLeadersElement ->
            val teamLeadersObj = teamLeadersElement.asJsonObject
            val teamId = teamLeadersObj.getAsJsonObject("team")?.get("id")?.asString
            val isHomeTeam = teamId == homeTeam?.id

            teamLeadersObj.getAsJsonArray("leaders")?.forEach { categoryElement ->
                val categoryObj = categoryElement.asJsonObject
                val categoryName = categoryObj.get("displayName")?.asString ?: ""

                categoryObj.getAsJsonArray("leaders")?.firstOrNull()?.let { playerElement ->
                    val playerObj = playerElement.asJsonObject
                    val athlete = playerObj.getAsJsonObject("athlete")

                    val leader = GameDetail.TeamLeader(
                        category = categoryName,
                        playerName = athlete?.get("fullName")?.asString ?: "",
                        playerJersey = athlete?.get("jersey")?.asString ?: "",
                        playerHeadshot = athlete?.getAsJsonObject("headshot")?.get("href")?.asString ?: "",
                        value = playerObj.get("displayValue")?.asString ?: ""
                    )

                    if (isHomeTeam) homeLeaders.add(leader) else awayLeaders.add(leader)
                }
            }
        }

        homeTeam = homeTeam?.copy(leaders = homeLeaders)
        awayTeam = awayTeam?.copy(leaders = awayLeaders)

        // 解析球员统计数据
        val boxscore = root.getAsJsonObject("boxscore")
        val playersArray = boxscore?.getAsJsonArray("players") ?: emptyList()

        val homePlayers = mutableListOf<GameDetail.Player>()
        val awayPlayers = mutableListOf<GameDetail.Player>()

        playersArray.forEach { teamElement ->
            val teamObj = teamElement.asJsonObject
            val teamId = teamObj.getAsJsonObject("team")?.get("id")?.asString
            val isHomeTeam = teamId == homeTeam?.id

            val statistics = teamObj.getAsJsonArray("statistics")
            statistics?.forEach { statBlock ->
                val block = statBlock.asJsonObject
                val labels = block.getAsJsonArray("labels")?.map { it.asString } ?: emptyList()
                val athletes = block.getAsJsonArray("athletes") ?: emptyList()

                athletes.forEach { athleteElement ->
                    val athleteObj = athleteElement.asJsonObject
                    val athleteInfo = athleteObj.getAsJsonObject("athlete")
                    val stats = athleteObj.getAsJsonArray("stats")?.map { it.asString } ?: emptyList()

                    // 根据 labels 获取对应值
                    fun getStat(label: String): String {
                        val index = labels.indexOf(label)
                        return if (index >= 0 && index < stats.size) stats[index] else "0"
                    }

                    val player = GameDetail.Player(
                        id = athleteInfo?.get("id")?.asString ?: "",
                        name = athleteInfo?.get("displayName")?.asString ?: "",
                        jersey = athleteInfo?.get("jersey")?.asString ?: "",
                        position = athleteInfo?.getAsJsonObject("position")?.get("abbreviation")?.asString ?: "",
                        minutes = getStat("MIN"),
                        points = getStat("PTS"),
                        rebounds = getStat("REB"),
                        assists = getStat("AST"),
                        steals = getStat("STL"),
                        blocks = getStat("BLK"),
                        turnovers = getStat("TO"),
                        fgMade = getStat("FG").split("-").getOrNull(0) ?: "0",
                        fgAttempts = getStat("FG").split("-").getOrNull(1) ?: "0",
                        threeMade = getStat("3PT").split("-").getOrNull(0) ?: "0",
                        threeAttempts = getStat("3PT").split("-").getOrNull(1) ?: "0",
                        ftMade = getStat("FT").split("-").getOrNull(0) ?: "0",
                        ftAttempts = getStat("FT").split("-").getOrNull(1) ?: "0",
                        plusMinus = getStat("+/-"),
                        headshot = athleteInfo?.getAsJsonObject("headshot")?.get("href")?.asString ?: "",
                        playerUrl = athleteInfo?.getAsJsonArray("links")?.get(0)?.asJsonObject?.get("href")?.asString ?: ""
                    )

                    if (isHomeTeam) homePlayers.add(player) else awayPlayers.add(player)
                }
            }
        }

        return GameDetail(
            gameId = gameId,
            status = status,
            clock = "",
            period = period,
            venue = venue,
            homeTeam = homeTeam ?: GameDetail.TeamDetail("", "", "", ""),
            awayTeam = awayTeam ?: GameDetail.TeamDetail("", "", "", ""),
            players = GameDetail.PlayerStats(homePlayers, awayPlayers),
            highlights = emptyList()
        )
    }

    // ESPN 球队 ID 到球队名称和缩写的映射
    private val teamIdMap = mapOf(
        "1" to Pair("Warriors", "GSW"),
        "2" to Pair("Lakers", "LAL"),
        "3" to Pair("Heat", "MIA"),
        "4" to Pair("Suns", "PHX"),
        "5" to Pair("Spurs", "SAS"),
        "6" to Pair("Bulls", "CHI"),
        "7" to Pair("Cavaliers", "CLE"),
        "8" to Pair("Mavericks", "DAL"),
        "9" to Pair("Nets", "BKN"),
        "10" to Pair("Knicks", "NYK"),
        "11" to Pair("Magic", "ORL"),
        "12" to Pair("76ers", "PHI"),
        "14" to Pair("Kings", "SAC"),
        "15" to Pair("Hornets", "CHA"),
        "16" to Pair("Celtics", "BOS"),
        "17" to Pair("Clippers", "LAC"),
        "18" to Pair("Raptors", "TOR"),
        "19" to Pair("Rockets", "HOU"),
        "20" to Pair("Nuggets", "DEN"),
        "21" to Pair("Timberwolves", "MIN"),
        "22" to Pair("Grizzlies", "MEM"),
        "23" to Pair("Pelicans", "NOP"),
        "24" to Pair("Thunder", "OKC"),
        "25" to Pair("Pacers", "IND"),
        "27" to Pair("Bucks", "MIL"),
        "28" to Pair("Hawks", "ATL"),
        "29" to Pair("Wizards", "WAS"),
        "30" to Pair("Jazz", "UTA"),
        "38" to Pair("Trail Blazers", "POR")
    )

    /**
     * 获取比赛文字转播数据
     */
    fun getPlayByPlay(gameId: String, homeTeamId: String, awayTeamId: String): Result<PlayByPlay> {
        return try {
            val url = "$summaryUrl?event=$gameId"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return Result.failure(Exception("API请求失败: ${response.code}"))
            }

            val body = response.body?.string() ?: return Result.failure(Exception("响应为空"))
            val plays = parsePlayByPlay(body, gameId, homeTeamId, awayTeamId)
            Result.success(plays)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parsePlayByPlay(json: String, gameId: String, homeTeamId: String, awayTeamId: String): PlayByPlay {
        val root = gson.fromJson(json, JsonObject::class.java)
        val playsArray = root.getAsJsonArray("plays") ?: JsonArray()

        val plays = playsArray.mapNotNull { playElement ->
            try {
                val play = playElement.asJsonObject

                // 获取球队信息
                val teamObj = play.getAsJsonObject("team")
                val teamId = teamObj?.get("id")?.asString ?: ""
                val teamInfo = teamIdMap[teamId] ?: Pair("", "")

                // 获取参与者信息
                val participantsArray = play.getAsJsonArray("participants")
                val participantNames = participantsArray?.mapNotNull { p ->
                    p.asJsonObject.getAsJsonObject("athlete")?.get("displayName")?.asString
                } ?: emptyList()

                // 判断节数
                val periodObj = play.getAsJsonObject("period")
                val periodNumber = periodObj?.get("number")?.asInt ?: 1

                // 获取时间
                val clockObj = play.getAsJsonObject("clock")
                val clockDisplay = clockObj?.get("displayValue")?.asString ?: ""

                // 获取事件类型
                val typeObj = play.getAsJsonObject("type")
                val playType = typeObj?.get("text")?.asString ?: ""

                PlayByPlay.Play(
                    id = play.get("id")?.asString ?: "",
                    sequenceNumber = play.get("sequenceNumber")?.asString ?: "",
                    text = play.get("text")?.asString ?: "",
                    shortText = play.get("shortDescription")?.asString ?: "",
                    clock = clockDisplay,
                    period = periodNumber,
                    periodDisplay = periodObj?.get("displayValue")?.asString ?: "",
                    teamId = teamId,
                    teamName = teamInfo.first,
                    awayScore = play.get("awayScore")?.asInt ?: 0,
                    homeScore = play.get("homeScore")?.asInt ?: 0,
                    isScoringPlay = play.get("scoringPlay")?.asBoolean ?: false,
                    scoreValue = play.get("scoreValue")?.asInt ?: 0,
                    isShootingPlay = play.get("shootingPlay")?.asBoolean ?: false,
                    playType = playType,
                    participants = participantNames
                )
            } catch (e: Exception) {
                null
            }
        }

        return PlayByPlay(gameId, plays)
    }
}