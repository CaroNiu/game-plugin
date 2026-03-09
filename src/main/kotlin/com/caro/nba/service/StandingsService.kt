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
 * NBA 排名数据服务
 * 使用 ESPN API 获取东西部排名
 */
class StandingsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    private val standingsUrl = "https://site.api.espn.com/apis/site/v2/sports/basketball/nba/standings"
    
    // 英文队名到中文映射
    private val teamNameMap = mapOf(
        "Hawks" to "老鹰", "Celtics" to "凯尔特人", "Nets" to "篮网", "Hornets" to "黄蜂",
        "Bulls" to "公牛", "Cavaliers" to "骑士", "Mavericks" to "独行侠", "Nuggets" to "掘金",
        "Pistons" to "活塞", "Warriors" to "勇士", "Rockets" to "火箭", "Pacers" to "步行者",
        "Clippers" to "快船", "Lakers" to "湖人", "Grizzlies" to "灰熊", "Heat" to "热火",
        "Bucks" to "雄鹿", "Timberwolves" to "森林狼", "Pelicans" to "鹈鹕", "Knicks" to "尼克斯",
        "Thunder" to "雷霆", "Magic" to "魔术", "76ers" to "76人", "Suns" to "太阳",
        "Trail Blazers" to "开拓者", "Blazers" to "开拓者", "Kings" to "国王", "Spurs" to "马刺", "Raptors" to "猛龙",
        "Jazz" to "爵士", "Wizards" to "奇才"
    )
    
    /**
     * 获取排名数据
     */
    fun getStandings(): Result<NBAStandings> {
        return try {
            val request = Request.Builder()
                .url(standingsUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("API请求失败: ${response.code}"))
            }
            
            val body = response.body?.string() ?: return Result.failure(Exception("响应为空"))
            val standings = parseStandingsResponse(body)
            Result.success(standings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 解析 ESPN API 响应
     */
    private fun parseStandingsResponse(json: String): NBAStandings {
        val root = gson.fromJson(json, JsonObject::class.java)
        
        val easternTeams = mutableListOf<TeamStanding>()
        val westernTeams = mutableListOf<TeamStanding>()
        
        // ESPN standings 结构: standings -> children (按group分组)
        // 每个children包含 entries (球队排名)
        val standingsArray = root.getAsJsonArray("standings") ?: return createEmptyStandings()
        
        standingsArray.forEach { standingsChild ->
            val childObj = standingsChild.asJsonObject
            val entries = childObj.getAsJsonArray("entries") ?: return@forEach
            
            entries.forEach { entry ->
                val entryObj = entry.asJsonObject
                val teamStanding = parseTeamStanding(entryObj)
                
                // 根据conference分类
                val conference = getTeamConference(teamStanding.abbreviation)
                if (conference == "East") {
                    easternTeams.add(teamStanding)
                } else {
                    westernTeams.add(teamStanding)
                }
            }
        }
        
        // 按排名排序
        easternTeams.sortBy { it.conferenceRank }
        westernTeams.sortBy { it.conferenceRank }
        
        return NBAStandings(
            eastern = ConferenceStandings("East", easternTeams),
            western = ConferenceStandings("West", westernTeams),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }
    
    /**
     * 解析单个球队排名数据
     */
    private fun parseTeamStanding(entry: JsonObject): TeamStanding {
        val teamObj = entry.getAsJsonObject("team")
        val stats = entry.getAsJsonArray("stats")
        
        // 基本信息
        val teamId = teamObj.get("id")?.asString ?: ""
        val abbreviation = teamObj.get("abbreviation")?.asString ?: ""
        val shortDisplayName = teamObj.get("shortDisplayName")?.asString ?: ""
        val logo = teamObj.getAsJsonArray("logos")?.get(0)?.asJsonObject?.get("href")?.asString ?: ""
        
        // 解析stats数组
        val statsMap = mutableMapOf<String, String>()
        stats?.forEach { stat ->
            val statObj = stat.asJsonObject
            val name = statObj.get("name")?.asString ?: ""
            val value = statObj.get("value")?.asString ?: ""
            statsMap[name] = value
        }
        
        // 提取关键数据
        val wins = statsMap["wins"]?.toIntOrNull() ?: 0
        val losses = statsMap["losses"]?.toIntOrNull() ?: 0
        val winPercent = statsMap["winPercent"]?.toDoubleOrNull() ?: 0.0
        val gamesBehind = statsMap["gamesBehind"] ?: "-"
        val homeRecord = statsMap["homeRecord"] ?: "0-0"
        val awayRecord = statsMap["awayRecord"] ?: "0-0"
        val last10 = statsMap["last10"] ?: "0-0"
        val streak = statsMap["streak"] ?: ""
        val clincher = statsMap["clinch"] ?: ""
        
        // 排名信息
        val seed = entry.get("seed")?.asJsonObject
        val conferenceRank = seed?.get("playoffSeed")?.asInt 
            ?: statsMap["rank"]?.toIntOrNull() ?: 0
        val divisionRank = seed?.get("divisionSeed")?.asInt ?: 0
        val playoffSeed = seed?.get("playoffSeed")?.asInt ?: 0
        
        return TeamStanding(
            teamId = teamId,
            teamName = teamNameMap[shortDisplayName] ?: shortDisplayName,
            abbreviation = abbreviation,
            logo = logo,
            wins = wins,
            losses = losses,
            winPercent = winPercent,
            gamesBehind = if (gamesBehind == "0" || gamesBehind == "0.0") "-" else gamesBehind,
            homeRecord = homeRecord,
            awayRecord = awayRecord,
            last10 = last10,
            streak = streak,
            conferenceRank = conferenceRank,
            divisionRank = divisionRank,
            playoffSeed = playoffSeed,
            clincher = clincher
        )
    }
    
    /**
     * 根据球队缩写判断所属分区
     */
    private fun getTeamConference(abbreviation: String): String {
        val easternTeams = setOf("ATL", "BOS", "BKN", "CHA", "CHI", "CLE", "DET", "IND", "MIA", "MIL", "NYK", "ORL", "PHI", "TOR", "WAS")
        return if (abbreviation in easternTeams) "East" else "West"
    }
    
    /**
     * 创建空的排名数据
     */
    private fun createEmptyStandings(): NBAStandings {
        return NBAStandings(
            eastern = ConferenceStandings("East", emptyList()),
            western = ConferenceStandings("West", emptyList())
        )
    }
}
