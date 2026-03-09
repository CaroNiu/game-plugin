package com.caro.nba.service

import com.caro.nba.model.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class StandingsService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val standingsUrl = "https://www.espn.com/nba/standings"

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

    fun getStandings(): Result<NBAStandings> {
        return try {
            val request = Request.Builder()
                .url(standingsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return Result.failure(Exception("HTTP ${response.code}"))

            val html = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            Result.success(parseStandingsFromHtml(html))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseStandingsFromHtml(html: String): NBAStandings {
        val easternTeams = mutableListOf<TeamStanding>()
        val westernTeams = mutableListOf<TeamStanding>()

        val pattern = """"team":\{[^}]+\},"stats":\[[^\]]+\]""".toRegex()

        pattern.findAll(html).forEach { match ->
            val fullMatch = match.value

// 提取字段
            val abbr = """"abbrev":"([A-Z]+)"""".toRegex().find(fullMatch)?.groupValues?.get(1) ?: return@forEach
            val shortName = """"shortDisplayName":"([^"]+)"""".toRegex().find(fullMatch)?.groupValues?.get(1) ?: ""
            val logo = """"logo":"([^"]+)"""".toRegex().find(fullMatch)?.groupValues?.get(1) ?: ""
            val id = """"id":"(\d+)"""".toRegex().find(fullMatch)?.groupValues?.get(1) ?: ""

// 提取stats
            val statsMatch = """"stats":\[([^\]]+)\]""".toRegex().find(fullMatch)
            val statsStr = statsMatch?.groupValues?.get(1) ?: return@forEach
            val stats = statsStr.split(",").map { it.trim().replace("\"", "") }

            if (stats.size >= 22) {
                val team = TeamStanding(
                    teamId = id,
                    teamName = teamNameMap[shortName] ?: shortName,
                    abbreviation = abbr,
                    logo = logo,
                    wins = stats[14].toIntOrNull() ?: 0,
                    losses = stats[6].toIntOrNull() ?: 0,
                    winPercent = stats[13].toDoubleOrNull() ?: 0.0,
                    gamesBehind = stats[4].ifEmpty { "-" },
                    homeRecord = stats[17].ifEmpty { "0-0" },
                    awayRecord = stats[18].ifEmpty { "0-0" },
                    last10 = stats[21].ifEmpty { "0-0" },
                    streak = stats[12].ifEmpty { "" },
                    conferenceRank = stats[7].toIntOrNull() ?: 0
                )

                if (isEasternTeam(abbr)) easternTeams.add(team)
                else westernTeams.add(team)
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

    private fun isEasternTeam(abbr: String): Boolean {
        return abbr in setOf(
            "ATL",
            "BOS",
            "BKN",
            "CHA",
            "CHI",
            "CLE",
            "DET",
            "IND",
            "MIA",
            "MIL",
            "NY",
            "ORL",
            "PHI",
            "TOR",
            "WSH"
        )
    }
}