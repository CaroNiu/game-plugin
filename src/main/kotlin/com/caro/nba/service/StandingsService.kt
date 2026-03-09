package com.caro.nba.service

import com.caro.nba.model.*
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
     * ESPN数据结构包含两个groups: Eastern Conference 和 Western Conference
     */
    private fun parseStandingsFromHtml(html: String): NBAStandings {
        val easternTeams = mutableListOf<TeamStanding>()
        val westernTeams = mutableListOf<TeamStanding>()
        
        // 查找所有groups数组中的standings数据
        // 格式: "groups":[{"name":"Eastern Conference","standings":[...]},{"name":"Western Conference","standings":[...]}]
        val groupsPattern = """"groups":\[(.*?)\]\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val groupsMatch = groupsPattern.find(html)
        
        if (groupsMatch != null) {
            val groupsJson = groupsMatch.groupValues[1]
            
            // 分别提取东部和西部
            val easternPattern = """"name":"Eastern Conference","abbreviation":"East","standings":(\[(.*?)\])""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val westernPattern = """"name":"Western Conference","abbreviation":"West","standings":(\[(.*?)\])""".toRegex(RegexOption.DOT_MATCHES_ALL)
            
            // 解析东部
            easternPattern.findAll(groupsJson).forEach { match ->
                val standingsJson = match.groupValues[1]
                val teams = parseTeamsFromStandings(standingsJson, "East")
                easternTeams.addAll(teams)
            }
            
            // 解析西部
            westernPattern.findAll(groupsJson).forEach { match ->
                val standingsJson = match.groupValues[1]
                val teams = parseTeamsFromStandings(standingsJson, "West")
                westernTeams.addAll(teams)
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
     * 解析standings数组中的球队数据
     */
    private fun parseTeamsFromStandings(standingsJson: String, conference: String): List<TeamStanding> {
        val teams = mutableListOf<TeamStanding>()
        
        // 匹配每个球队的数据块
        val teamPattern = """"team":\{(.*?)\},"stats":\[(.*?)\]""".toRegex(RegexOption.DOT_MATCHES_ALL)
        
        teamPattern.findAll(standingsJson).forEach { match ->
            val teamInfo = match.groupValues[1]
            val statsStr = match.groupValues[2]
            
            // 提取球队基本信息
            val idPattern = """"id":"(\d+)"""".toRegex()
            val abbrPattern = """"abbrev":"([A-Z]+)"""".toRegex()
            val shortNamePattern = """"shortDisplayName":"([^"]+)"""".toRegex()
            val logoPattern = """"logo":"([^"]+)"""".toRegex()
            
            val teamId = idPattern.find(teamInfo)?.groupValues?.get(1) ?: ""
            val abbr = abbrPattern.find(teamInfo)?.groupValues?.get(1) ?: ""
            val shortName = shortNamePattern.find(teamInfo)?.groupValues?.get(1) ?: ""
            val logo = logoPattern.find(teamInfo)?.groupValues?.get(1) ?: ""
            
            // 解析stats数组 - ESPN stats顺序: 0=oppPoints,1=points,2=diff,3=divWinPct,4=gb,5=confWinPct,6=L,7=rank,8=pointDiff,9=pointsDiff,10=ptsAgainst,11=ptsFor,12=streak,13=pct,14=W,15=home/away区分等
            // 解析stats - 去掉首尾的方括号
            val statsContent = statsStr.trim().removeSurrounding("[", "]")
            val stats = statsContent.split(",").map { it.trim().replace("\"", "") }
            
            if (stats.size >= 15) {
                val wins = stats[14].toIntOrNull() ?: 0  // 第14位是胜场
                val losses = stats[6].toIntOrNull() ?: 0   // 第6位是负场
                val winPercent = stats[13].replace(".", "").toDoubleOrNull()?.div(1000) ?: 
                                (if (wins + losses > 0) wins.toDouble() / (wins + losses) else 0.0)
                val gamesBehind = stats[4].ifEmpty { "-" }
                val streak = stats[12].ifEmpty { "" }
                val conferenceRank = stats[7].toIntOrNull() ?: 0
                
                // 主客场战绩在后面
                val homeAway = stats.drop(15).joinToString(",")
                val homeRecord = if (homeAway.contains(",")) {
                    val parts = homeAway.split(",")
                    parts.getOrNull(0)?.replace("\"", "")?.ifEmpty { "0-0" } ?: "0-0"
                } else "0-0"
                val awayRecord = if (homeAway.contains(",")) {
                    val parts = homeAway.split(",")
                    parts.getOrNull(1)?.replace("\"", "")?.ifEmpty { "0-0" } ?: "0-0"
                } else "0-0"
                
                // 最后10场需要从另一个位置获取
                val last10 = extractLast10(stats)
                
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
                
                teams.add(teamStanding)
            }
        }
        
        return teams
    }
    
    /**
     * 从stats中提取最近10场战绩
     */
    private fun extractLast10(stats: List<String>): String {
        // stats中包含last10数据，通常在后面几个位置
        // 格式可能是 "L10" 或者类似格式
        for (stat in stats) {
            if (stat.contains("-") && stat.length <= 5 && stat.matches(Regex(".*\\d+-\\d+.*"))) {
                val parts = stat.split("-")
                if (parts.size == 2) {
                    val first = parts[0].replace(Regex("[^0-9]"), "")
                    val second = parts[1].replace(Regex("[^0-9]"), "")
                    if (first.toIntOrNull() != null && second.toIntOrNull() != null) {
                        return stat.replace("\"", "")
                    }
                }
            }
        }
        return "0-0"
    }
}
