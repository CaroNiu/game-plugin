package com.caro.nba.service

import com.caro.nba.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * NBA 排名数据服务 - 使用 ESPN API
 */
class StandingsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // ESPN 排名 API
    private val standingsUrl = "https://site.api.espn.com/apis/v2/sports/basketball/nba/standings"
    
    // 英文队名到中文映射
    private val teamNameMap = mapOf(
        "Pistons" to "活塞", "Celtics" to "凯尔特人", "Knicks" to "尼克斯", "Cavaliers" to "骑士",
        "Raptors" to "猛龙", "Magic" to "魔术", "Heat" to "热火", "76ers" to "76人",
        "Hawks" to "老鹰", "Hornets" to "黄蜂", "Bucks" to "雄鹿", "Bulls" to "公牛",
        "Nets" to "篮网", "Wizards" to "奇才", "Pacers" to "步行者",
        "Thunder" to "雷霆", "Spurs" to "马刺", "Timberwolves" to "森林狼", "Rockets" to "火箭",
        "Lakers" to "湖人", "Nuggets" to "掘金", "Suns" to "太阳", "Warriors" to "勇士",
        "Clippers" to "快船", "Trail Blazers" to "开拓者", "Grizzlies" to "灰熊",
        "Mavericks" to "独行侠", "Pelicans" to "鹈鹕", "Jazz" to "爵士", "Kings" to "国王"
    )
    
    /**
     * 获取排名数据
     */
    fun getStandings(): Result<NBAStandings> {
        return try {
            val request = Request.Builder()
                .url(standingsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val standings = parseStandings(body)
            Result.success(standings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 解析 ESPN API 响应
     */
    private fun parseStandings(json: String): NBAStandings {
        val root = gson.fromJson(json, JsonObject::class.java)
        val children = root.getAsJsonArray("children") ?: return createEmptyStandings()
        
        var easternTeams = listOf<TeamStanding>()
        var westernTeams = listOf<TeamStanding>()
        
        children.forEach { child ->
            val conf = child.asJsonObject
            val confName = conf.get("name")?.asString ?: ""
            val entries = conf.getAsJsonObject("standings")?.getAsJsonArray("entries") ?: return@forEach
            
            val teams = entries.mapIndexed { index, entry ->
                parseTeamStanding(entry.asJsonObject, index + 1)  // 强制使用顺序作为排名
            }.sortedBy { it.conferenceRank }  // 按排名排序
            
            when (confName) {
                "Eastern Conference" -> easternTeams = teams
                "Western Conference" -> westernTeams = teams
            }
        }
        
        return NBAStandings(
            eastern = ConferenceStandings("East", easternTeams),
            western = ConferenceStandings("West", westernTeams),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }
    
    /**
     * 解析单个球队排名数据
     */
    private fun parseTeamStanding(entry: JsonObject, defaultRank: Int): TeamStanding {
        val team = entry.getAsJsonObject("team")
        val stats = entry.getAsJsonArray("stats")
        
        val teamId = team.get("id")?.asString ?: ""
        val abbreviation = team.get("abbreviation")?.asString ?: ""
        val shortName = team.get("shortDisplayName")?.asString ?: ""
        val displayName = team.get("displayName")?.asString ?: ""
        val logo = team.getAsJsonArray("logos")?.get(0)?.asJsonObject?.get("href")?.asString ?: ""
        
        // 解析 stats
        val statsMap = mutableMapOf<String, String>()
        stats?.forEach { stat ->
            val statObj = stat.asJsonObject
            val name = statObj.get("name")?.asString ?: ""
            val displayValue = statObj.get("displayValue")?.asString ?: ""
            statsMap[name] = displayValue
        }
        
        val wins = statsMap["wins"]?.toIntOrNull() ?: 0
        val losses = statsMap["losses"]?.toIntOrNull() ?: 0
        val winPercent = statsMap["winPercent"]?.toDoubleOrNull() ?: 0.0
        val gamesBehind = statsMap["gamesBehind"]?.ifEmpty { "-" } ?: "-"
        val streak = statsMap["streak"] ?: ""
        val clincher = statsMap["clincher"] ?: ""
        val playoffSeed = statsMap["playoffSeed"]?.toIntOrNull() ?: defaultRank
        
        // 获取 Overall 记录 (55-21 格式)
        val overallRecord = stats?.find { 
            it.asJsonObject.get("name")?.asString == "overall" 
        }?.asJsonObject?.get("summary")?.asString ?: "$wins-$losses"
        
        // 获取主场客场记录
        val homeRecord = stats?.find { 
            it.asJsonObject.get("name")?.asString == "home" 
        }?.asJsonObject?.get("summary")?.asString ?: "0-0"
        
        val awayRecord = stats?.find { 
            it.asJsonObject.get("name")?.asString == "road" 
        }?.asJsonObject?.get("summary")?.asString ?: "0-0"
        
        val last10 = stats?.find { 
            it.asJsonObject.get("name")?.asString == "lasttengames" 
        }?.asJsonObject?.get("summary")?.asString ?: "0-0"
        
        return TeamStanding(
            teamId = teamId,
            teamName = teamNameMap[shortName] ?: shortName,
            abbreviation = abbreviation,
            logo = logo,
            wins = wins,
            losses = losses,
            winPercent = winPercent,
            gamesBehind = gamesBehind,
            homeRecord = homeRecord,
            awayRecord = awayRecord,
            last10 = last10,
            streak = streak,
            conferenceRank = playoffSeed,
            clincher = clincher
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
