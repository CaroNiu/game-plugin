package com.caro.nba.service

import com.caro.nba.NBASettingsState
import com.caro.nba.datasource.DataSource
import com.caro.nba.datasource.DataSourceManager
import com.caro.nba.model.NBAScoreboard
import java.time.LocalDate

/**
 * NBA 数据服务 - 获取实时比分
 *
 * 委托给 DataSourceManager 进行多数据源管理和自动 fallback。
 * 数据源配置从 NBASettingsState 读取。
 */
class NBADataService {

    /**
     * 获取指定日期的比赛数据
     * @param date 应已为美国东部时间
     * @return 比分数据 + 实际使用的数据源
     */
    fun getGames(date: LocalDate = LocalDate.now()): Result<NBAScoreboard> {
        return getGamesWithSource(date).first
    }

    /**
     * 获取指定日期的比赛数据（返回实际使用的数据源）
     */
    fun getGamesWithSource(date: LocalDate = LocalDate.now()): Pair<Result<NBAScoreboard>, DataSource> {
        return try {
            val settings = NBASettingsState.getInstance()
            val primarySource = DataSource.fromId(settings.dataSource)
            val sportsDbKey = settings.sportsDbApiKey.ifBlank { "3" }
            val manager = DataSourceManager(primarySource, settings.ballDontLieApiKey, sportsDbKey)
            manager.getScores(date)
        } catch (e: Exception) {
            val failure: Result<NBAScoreboard> = Result.failure(e)
            return failure to DataSource.ESPN
        }
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
