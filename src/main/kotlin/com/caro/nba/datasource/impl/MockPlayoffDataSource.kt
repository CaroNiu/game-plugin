package com.caro.nba.datasource.impl

import com.caro.nba.datasource.PlayoffDataSource
import com.caro.nba.model.*

/**
 * Mock 季后赛对阵图数据源
 *
 * 用于演示和开发阶段，提供一组静态的对阵图数据。
 * 后续可替换为真实 API 数据源。
 */
class MockPlayoffDataSource : PlayoffDataSource {
    override fun getPlayoffBracket(): Result<PlayoffBracketResponse> {
        return try {
            Result.success(buildMockBracket())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildMockBracket(): PlayoffBracketResponse {
        val westR1 = listOf(
            buildSeries("雷霆", "鹈鹕", "https://a.espncdn.com/i/teamlogos/nba/500/okc.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/no.png", "4", "1", 4),
            buildSeries("掘金", "森林狼", "https://a.espncdn.com/i/teamlogos/nba/500/den.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/min.png", "4", "3", 4),
            buildSeries("快船", "独行侠", "https://a.espncdn.com/i/teamlogos/nba/500/lac.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/dal.png", "2", "4", 4),
            buildSeries("太阳", "湖人", "https://a.espncdn.com/i/teamlogos/nba/500/phx.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/lal.png", "2", "4", 4)
        )

        val westR2 = listOf(
            buildSeries("雷霆", "湖人", "https://a.espncdn.com/i/teamlogos/nba/500/okc.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/lal.png", "4", "2", 4),
            buildSeries("掘金", "独行侠", "https://a.espncdn.com/i/teamlogos/nba/500/den.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/dal.png", "3", "4", 4)
        )

        val westFinal = listOf(
            buildSeries("雷霆", "独行侠", "https://a.espncdn.com/i/teamlogos/nba/500/okc.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/dal.png", "4", "1", 4)
        )

        val eastR1 = listOf(
            buildSeries("凯尔特人", "热火", "https://a.espncdn.com/i/teamlogos/nba/500/bos.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/mia.png", "4", "1", 4),
            buildSeries("雄鹿", "魔术", "https://a.espncdn.com/i/teamlogos/nba/500/mil.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/orl.png", "4", "2", 4),
            buildSeries("骑士", "步行者", "https://a.espncdn.com/i/teamlogos/nba/500/cle.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/ind.png", "4", "3", 4),
            buildSeries("尼克斯", "步行者", "https://a.espncdn.com/i/teamlogos/nba/500/ny.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/ind.png", "3", "4", 4)
        )

        val eastR2 = listOf(
            buildSeries("凯尔特人", "骑士", "https://a.espncdn.com/i/teamlogos/nba/500/bos.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/cle.png", "4", "1", 4),
            buildSeries("雄鹿", "步行者", "https://a.espncdn.com/i/teamlogos/nba/500/mil.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/ind.png", "2", "4", 4)
        )

        val eastFinal = listOf(
            buildSeries("凯尔特人", "步行者", "https://a.espncdn.com/i/teamlogos/nba/500/bos.png",
                "https://a.espncdn.com/i/teamlogos/nba/500/ind.png", "4", "0", 4)
        )

        val finals = buildSeries("雷霆", "凯尔特人", "https://a.espncdn.com/i/teamlogos/nba/500/okc.png",
            "https://a.espncdn.com/i/teamlogos/nba/500/bos.png", "2", "4", 4)

        return PlayoffBracketResponse(
            data = PlayoffBracketData(
                top = listOf(westR1, westR2, westFinal),
                bottom = listOf(eastR1, eastR2, eastFinal),
                finals = finals
            )
        )
    }

    private fun buildSeries(
        team1: String,
        team2: String,
        logo1: String,
        logo2: String,
        score1: String,
        score2: String,
        winThreshold: Int
    ): PlayoffBracketSeries {
        return PlayoffBracketSeries(
            teams = listOf(
                PlayoffTeam(name = team1, img = logo1, rank = "1"),
                PlayoffTeam(name = team2, img = logo2, rank = "2")
            ),
            info1 = score1,
            info2 = score2,
            win_threshold = winThreshold,
            schedule = PlayoffSchedule(
                list = buildMockSchedule(team1, team2, logo1, logo2, score1.toIntOrNull() ?: 0, score2.toIntOrNull() ?: 0)
            )
        )
    }

    private fun buildMockSchedule(
        team1: String,
        team2: String,
        logo1: String,
        logo2: String,
        wins1: Int,
        wins2: Int
    ): List<PlayoffGame> {
        val games = mutableListOf<PlayoffGame>()
        val totalGames = wins1 + wins2
        for (i in 1..7) {
            val isPlayed = i <= totalGames
            val score = if (isPlayed) {
                val winnerIsTeam1 = if (i % 2 == 1) wins1 >= (i + 1) / 2 else wins1 >= i / 2
                if (winnerIsTeam1) "110-105" else "105-110"
            } else {
                "vs"
            }
            games.add(
                PlayoffGame(
                    left_team = team1,
                    right_team = team2,
                    left_logo = logo1,
                    right_logo = logo2,
                    score = score
                )
            )
        }
        return games
    }
}
