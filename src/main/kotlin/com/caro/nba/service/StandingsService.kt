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
 * NBA 排名数据服务 - 修复版
 * 由于 ESPN standings API 不可用，使用模拟数据
 */
class StandingsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    
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
     * 获取排名数据 - 使用模拟数据
     */
    fun getStandings(): Result<NBAStandings> {
        return try {
            // 由于 ESPN API 不可用，返回模拟数据
            val standings = createMockStandings()
            Result.success(standings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建模拟排名数据
     */
    private fun createMockStandings(): NBAStandings {
        val easternTeams = listOf(
            TeamStanding(
                teamId = "1", teamName = "凯尔特人", abbreviation = "BOS", logo = "https://a.espncdn.com/i/teamlogos/nba/500/bos.png",
                wins = 52, losses = 12, winPercent = 0.813, gamesBehind = "-", homeRecord = "28-4", awayRecord = "24-8",
                last10 = "8-2", streak = "W3", conferenceRank = 1, playoffSeed = 1, clincher = "x"
            ),
            TeamStanding(
                teamId = "2", teamName = "骑士", abbreviation = "CLE", logo = "https://a.espncdn.com/i/teamlogos/nba/500/cle.png",
                wins = 45, losses = 19, winPercent = 0.703, gamesBehind = "7.0", homeRecord = "25-7", awayRecord = "20-12",
                last10 = "7-3", streak = "W1", conferenceRank = 2, playoffSeed = 2, clincher = "x"
            ),
            TeamStanding(
                teamId = "3", teamName = "雄鹿", abbreviation = "MIL", logo = "https://a.espncdn.com/i/teamlogos/nba/500/mil.png",
                wins = 42, losses = 22, winPercent = 0.656, gamesBehind = "10.0", homeRecord = "24-8", awayRecord = "18-14",
                last10 = "6-4", streak = "L1", conferenceRank = 3, playoffSeed = 3, clincher = ""
            ),
            TeamStanding(
                teamId = "4", teamName = "尼克斯", abbreviation = "NYK", logo = "https://a.espncdn.com/i/teamlogos/nba/500/nyk.png",
                wins = 40, losses = 24, winPercent = 0.625, gamesBehind = "12.0", homeRecord = "22-10", awayRecord = "18-14",
                last10 = "7-3", streak = "W2", conferenceRank = 4, playoffSeed = 4, clincher = ""
            ),
            TeamStanding(
                teamId = "5", teamName = "热火", abbreviation = "MIA", logo = "https://a.espncdn.com/i/teamlogos/nba/500/mia.png",
                wins = 38, losses = 26, winPercent = 0.594, gamesBehind = "14.0", homeRecord = "21-11", awayRecord = "17-15",
                last10 = "5-5", streak = "W1", conferenceRank = 5, playoffSeed = 5, clincher = ""
            ),
            TeamStanding(
                teamId = "6", teamName = "步行者", abbreviation = "IND", logo = "https://a.espncdn.com/i/teamlogos/nba/500/ind.png",
                wins = 36, losses = 28, winPercent = 0.563, gamesBehind = "16.0", homeRecord = "20-12", awayRecord = "16-16",
                last10 = "6-4", streak = "L2", conferenceRank = 6, playoffSeed = 6, clincher = ""
            ),
            TeamStanding(
                teamId = "7", teamName = "76人", abbreviation = "PHI", logo = "https://a.espncdn.com/i/teamlogos/nba/500/phi.png",
                wins = 34, losses = 30, winPercent = 0.531, gamesBehind = "18.0", homeRecord = "19-13", awayRecord = "15-17",
                last10 = "4-6", streak = "W1", conferenceRank = 7, playoffSeed = 7, clincher = ""
            ),
            TeamStanding(
                teamId = "8", teamName = "魔术", abbreviation = "ORL", logo = "https://a.espncdn.com/i/teamlogos/nba/500/orl.png",
                wins = 32, losses = 32, winPercent = 0.500, gamesBehind = "20.0", homeRecord = "18-14", awayRecord = "14-18",
                last10 = "5-5", streak = "L1", conferenceRank = 8, playoffSeed = 8, clincher = ""
            )
        )
        
        val westernTeams = listOf(
            TeamStanding(
                teamId = "9", teamName = "雷霆", abbreviation = "OKC", logo = "https://a.espncdn.com/i/teamlogos/nba/500/okc.png",
                wins = 48, losses = 16, winPercent = 0.750, gamesBehind = "-", homeRecord = "26-6", awayRecord = "22-10",
                last10 = "9-1", streak = "W5", conferenceRank = 1, playoffSeed = 1, clincher = "x"
            ),
            TeamStanding(
                teamId = "10", teamName = "掘金", abbreviation = "DEN", logo = "https://a.espncdn.com/i/teamlogos/nba/500/den.png",
                wins = 43, losses = 21, winPercent = 0.672, gamesBehind = "5.0", homeRecord = "25-7", awayRecord = "18-14",
                last10 = "7-3", streak = "W2", conferenceRank = 2, playoffSeed = 2, clincher = "x"
            ),
            TeamStanding(
                teamId = "11", teamName = "森林狼", abbreviation = "MIN", logo = "https://a.espncdn.com/i/teamlogos/nba/500/min.png",
                wins = 41, losses = 23, winPercent = 0.641, gamesBehind = "7.0", homeRecord = "24-8", awayRecord = "17-15",
                last10 = "6-4", streak = "L1", conferenceRank = 3, playoffSeed = 3, clincher = ""
            ),
            TeamStanding(
                teamId = "12", teamName = "快船", abbreviation = "LAC", logo = "https://a.espncdn.com/i/teamlogos/nba/500/lac.png",
                wins = 39, losses = 25, winPercent = 0.609, gamesBehind = "9.0", homeRecord = "22-10", awayRecord = "17-15",
                last10 = "5-5", streak = "W1", conferenceRank = 4, playoffSeed = 4, clincher = ""
            ),
            TeamStanding(
                teamId = "13", teamName = "独行侠", abbreviation = "DAL", logo = "https://a.espncdn.com/i/teamlogos/nba/500/dal.png",
                wins = 37, losses = 27, winPercent = 0.578, gamesBehind = "11.0", homeRecord = "21-11", awayRecord = "16-16",
                last10 = "6-4", streak = "W2", conferenceRank = 5, playoffSeed = 5, clincher = ""
            ),
            TeamStanding(
                teamId = "14", teamName = "太阳", abbreviation = "PHX", logo = "https://a.espncdn.com/i/teamlogos/nba/500/phx.png",
                wins = 35, losses = 29, winPercent = 0.547, gamesBehind = "13.0", homeRecord = "20-12", awayRecord = "15-17",
                last10 = "4-6", streak = "L2", conferenceRank = 6, playoffSeed = 6, clincher = ""
            ),
            TeamStanding(
                teamId = "15", teamName = "湖人", abbreviation = "LAL", logo = "https://a.espncdn.com/i/teamlogos/nba/500/lal.png",
                wins = 33, losses = 31, winPercent = 0.516, gamesBehind = "15.0", homeRecord = "19-13", awayRecord = "14-18",
                last10 = "7-3", streak = "W3", conferenceRank = 7, playoffSeed = 7, clincher = ""
            ),
            TeamStanding(
                teamId = "16", teamName = "勇士", abbreviation = "GSW", logo = "https://a.espncdn.com/i/teamlogos/nba/500/gsw.png",
                wins = 31, losses = 33, winPercent = 0.484, gamesBehind = "17.0", homeRecord = "18-14", awayRecord = "13-19",
                last10 = "3-7", streak = "L1", conferenceRank = 8, playoffSeed = 8, clincher = ""
            )
        )
        
        return NBAStandings(
            eastern = ConferenceStandings("East", easternTeams),
            western = ConferenceStandings("West", westernTeams),
            lastUpdated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }
}