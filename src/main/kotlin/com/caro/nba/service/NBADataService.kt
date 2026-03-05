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
                return Result.failure(Exception("API request failed: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
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
            val state = type?.get("state")?.asString ?: "scheduled"
            val period = type?.get("period")?.asInt ?: 0
            val clock = type?.get("shortDetail")?.asString ?: ""
            
            // 获取比赛竞争信息
            val competitions = eventObj.getAsJsonArray("competitions")
            val competition = competitions?.get(0)?.asJsonObject
            val competitors = competition?.getAsJsonArray("competitors") ?: return@map null
            
            // 找到主队和客队 - homeAway 字段是 "home" 或 "away"
            val home = competitors.find { 
                it.asJsonObject.get("homeAway")?.asString == "home" 
            }?.asJsonObject
            val away = competitors.find { 
                it.asJsonObject.get("homeAway")?.asString == "away" 
            }?.asJsonObject
            
            val homeTeam = home?.getAsJsonObject("team")
            val awayTeam = away?.getAsJsonObject("team")
            
            // score 是字符串，需要转换
            val homeScore = home?.get("score")?.asString?.toIntOrNull() ?: 0
            val awayScore = away?.get("score")?.asString?.toIntOrNull() ?: 0
            
            // logo 直接在 team 对象中
            val homeLogo = homeTeam?.get("logo")?.asString ?: ""
            val awayLogo = awayTeam?.get("logo")?.asString ?: ""
            
            NBAGame(
                gameId = eventObj.get("id")?.asString ?: "",
                status = when (state) {
                    "pre" -> "scheduled"
                    "in" -> "in_progress"
                    "post" -> "finished"
                    else -> state
                },
                period = period,
                clock = clock,
                startTime = eventObj.get("date")?.asString ?: "",
                homeTeam = NBAGame.Team(
                    id = homeTeam?.get("id")?.asString ?: "",
                    name = homeTeam?.get("displayName")?.asString ?: "",
                    abbreviation = homeTeam?.get("abbreviation")?.asString ?: "",
                    logo = homeLogo
                ),
                homeScore = homeScore,
                awayTeam = NBAGame.Team(
                    id = awayTeam?.get("id")?.asString ?: "",
                    name = awayTeam?.get("displayName")?.asString ?: "",
                    abbreviation = awayTeam?.get("abbreviation")?.asString ?: "",
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