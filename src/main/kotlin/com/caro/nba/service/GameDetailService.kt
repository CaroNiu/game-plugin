package com.caro.nba.service

import com.caro.nba.model.GameDetail
import com.google.gson.Gson
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
}