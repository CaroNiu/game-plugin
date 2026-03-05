package com.caro.nba.service

import com.caro.nba.model.NBAGame
import com.caro.nba.model.NBAScoreboard
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * NBA 数据服务 - 获取实时比分
 * 使用 ESPN API
 */
class NBADataService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    private val espnUrl = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard"
    
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
     * 获取指定日期的比赛数据
     */
    fun getGames(date: LocalDate = LocalDate.now()): Result<NBAScoreboard> {
        return try {
            val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val url = "$espnUrl?dates=$dateStr"
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("API请求失败: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return Result.failure(Exception("响应为空"))
            val scoreboard = parseESPNResponse(body, date)
            Result.success(scoreboard)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 解析 ESPN API 响应
     */
    private fun parseESPNResponse(json: String, date: LocalDate): NBAScoreboard {
        val root = gson.fromJson(json, JsonObject::class.java)
        val events = root.getAsJsonArray("events") ?: return NBAScoreboard(date.toString(), emptyList())
        
        val games = events.map { event ->
            val eventObj = event.asJsonObject
            
            // 获取比赛状态
            val status = eventObj.getAsJsonObject("status")
            val type = status?.getAsJsonObject("type")
            val state = type?.get("state")?.asString ?: "pre"
            val period = type?.get("period")?.asInt ?: 0
            val displayClock = status?.get("displayClock")?.asString ?: ""
            val shortDetail = type?.get("shortDetail")?.asString ?: ""
            val detail = type?.get("detail")?.asString ?: ""
            
            // 获取比赛竞争信息
            val competitions = eventObj.getAsJsonArray("competitions")
            val competition = competitions?.get(0)?.asJsonObject
            val competitors = competition?.getAsJsonArray("competitors") ?: return@map null
            
            // 找到主队和客队 - homeAway 字段是 "home" 或 "away"
            val homeCompetitor = competitors.find { 
                it.asJsonObject.get("homeAway")?.asString == "home" 
            }?.asJsonObject
            val awayCompetitor = competitors.find { 
                it.asJsonObject.get("homeAway")?.asString == "away" 
            }?.asJsonObject
            
            val homeTeam = homeCompetitor?.getAsJsonObject("team")
            val awayTeam = awayCompetitor?.getAsJsonObject("team")
            
            // score 是字符串，需要转换
            val homeScore = homeCompetitor?.get("score")?.asString?.toIntOrNull() ?: 0
            val awayScore = awayCompetitor?.get("score")?.asString?.toIntOrNull() ?: 0
            
            // logo 直接在 team 对象中
            val homeLogo = homeTeam?.get("logo")?.asString ?: ""
            val awayLogo = awayTeam?.get("logo")?.asString ?: ""
            
            // 获取队名信息
            val homeShortName = homeTeam?.get("shortDisplayName")?.asString ?: ""
            val awayShortName = awayTeam?.get("shortDisplayName")?.asString ?: ""
            val homeAbbr = homeTeam?.get("abbreviation")?.asString ?: ""
            val awayAbbr = awayTeam?.get("abbreviation")?.asString ?: ""
            
            NBAGame(
                gameId = eventObj.get("id")?.asString ?: "",
                status = when (state) {
                    "pre" -> "scheduled"
                    "in" -> "in_progress"
                    "post" -> "finished"
                    else -> state
                },
                period = period,
                clock = displayClock,
                startTime = eventObj.get("date")?.asString ?: "",
                detail = detail,
                homeTeam = NBAGame.Team(
                    id = homeTeam?.get("id")?.asString ?: "",
                    name = teamNameMap[homeShortName] ?: homeShortName,
                    abbreviation = homeAbbr,
                    shortName = homeShortName,
                    logo = homeLogo
                ),
                homeScore = homeScore,
                awayTeam = NBAGame.Team(
                    id = awayTeam?.get("id")?.asString ?: "",
                    name = teamNameMap[awayShortName] ?: awayShortName,
                    abbreviation = awayAbbr,
                    shortName = awayShortName,
                    logo = awayLogo
                ),
                awayScore = awayScore
            )
        }.filterNotNull()
        
        return NBAScoreboard(date.toString(), games)
    }
    
    /**
     * 获取今天的比赛
     */
    fun getTodayGames(): Result<NBAScoreboard> = getGames(LocalDate.now())
    
    /**
     * 获取指定日期范围的比赛
     */
    fun getGamesInRange(startDate: LocalDate, endDate: LocalDate): List<Result<NBAScoreboard>> {
        return generateSequence(startDate) { it.plusDays(1) }
            .takeWhile { !it.isAfter(endDate) }
            .map { getGames(it) }
            .toList()
    }
}