package com.caro.nba.service

import com.caro.nba.model.*
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * NBA 排名数据服务 - 真实数据版本
 * 通过抓取 ESPN 网页获取实时排名数据
 */
class StandingsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
    // ESPN 排名页面URL
    private val standingsUrl = "https://www.espn.com/nba/standings"
    
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
    
    // 东部球队缩写
    private val easternAbbrs = setOf("DET", "BOS", "NY", "CLE", "TOR", "ORL", "MIA", "PHI", "ATL", "CHA", "MIL", "CHI", "BKN", "WSH", "IND")
    
    /**
     * 获取排名数据
     */
    fun getStandings(): Result<NBAStandings> {
        return try {
            val request = Request.Builder()
                .url(standingsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}"))
            }
            
            val html = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            
            // 从HTML中提取standings JSON数据
            val standings = parseStandingsFromHtml(html)
            Result.success(standings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 从HTML中解析排名数据
     */
    private fun parseStandingsFromHtml(html: String): NBAStandings {
        // 查找 __NEXT_DATA__ 或 window['__INITIAL_STATE__'] 中的数据
        // ESPN使用 Next.js，数据在 <script id="__NEXT_DATA__"> 中
        
        val easternTeams = mutableListOf<TeamStanding>()
        val westernTeams = mutableListOf<TeamStanding>()
        
        // 提取standings数组
        val standingsPattern = """"standings":\[(.*?)\],"notes"""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = standingsPattern.find(html)
        
        if (match != null) {
            val standingsJson = match.groupValues[1]
            
            // 解析每个球队
            val teamPattern = """"team":\{(.*?)"links":"(.*?)"\},"stats":\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
            teamPattern.findAll(standingsJson).forEach { teamMatch ->
                val teamInfo = teamMatch.groupValues[1]
                val statsStr = teamMatch.groupValues[3]
                
                // 提取球队信息
                val idPattern = """"id":"(\d+)"""".toRegex()
                val abbrPattern = """"abbrev":"([A-Z]+)"""".toRegex()
                val namePattern = """"displayName":"([^"]+)"""".toRegex()
                val shortNamePattern = """"shortDisplayName":"([^"]+)"""".toRegex()
                val logoPattern = """"logo":"([^"]+)"""".toRegex()
                
                val teamId = idPattern.find(teamInfo)?.groupValues?.get(1) ?: ""
                val abbr = abbrPattern.find(teamInfo)?.groupValues?.get(1) ?: ""
                val displayName = namePattern.find(teamInfo)?.groupValues?.get(1) ?: ""
                val shortName = shortNamePattern.find(teamInfo)?.groupValues?.get(1) ?: displayName
                val logo = logoPattern.find(teamInfo)?.groupValues?.get(1) ?: ""
                
                // 解析stats数组
                val stats = statsStr.split(",").map { it.trim().replace("\"", "") }
                
                if (stats.size >= 22) {
                    // stats索引：6=负场，7=排名，12=连胜，14=胜场
                    val wins = stats[14].toIntOrNull() ?: 0
                    val losses = stats[6].toIntOrNull() ?: 0
                    val winPercent = if (wins + losses > 0) wins.toDouble() / (wins + losses) else 0.0
                    val gamesBehind = stats[4].ifEmpty { "-" }
                    val homeRecord = stats[17].ifEmpty { "0-0" }
                    val awayRecord = stats[18].ifEmpty { "0-0" }
                    val last10 = stats[21].ifEmpty { "0-0" }
                    val streak = stats[12].ifEmpty { "" }
                    val conferenceRank = stats[7].toIntOrNull() ?: 0
                    
                    val teamStanding = TeamStanding(
                        teamId = teamId,
                        teamName = teamNameMap[shortName] ?: shortName,
                        abbreviation = abbr,
                        logo = logo,
                        wins = wins,
                        losses = losses,
                        winPercent = winPercent,
                        gamesBehind = gamesBehind,
                        homeRecord = homeRecord,
                        awayRecord = awayRecord,
                        last10 = last10,
                        streak = streak,
                        conferenceRank = conferenceRank
                    )
                    
                    if (abbr in easternAbbrs) {
                        easternTeams.add(teamStanding)
                    } else {
                        westernTeams.add(teamStanding)
                    }
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
}
