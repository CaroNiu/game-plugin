package com.caro.nba.service

import com.caro.nba.NBASettingsState
import com.caro.nba.datasource.DataSource
import com.caro.nba.datasource.DataSourceManager
import com.caro.nba.model.PlayoffBracketResponse

/**
 * NBA 季后赛对阵图数据服务
 *
 * 委托给 DataSourceManager 进行多数据源管理和自动 fallback。
 * 数据源配置从 NBASettingsState 读取。
 * 保持原有接口签名不变，便于上层面板无缝迁移。
 */
class PlayoffService {

    /**
     * 获取季后赛对阵图
     */
    fun getPlayoffBracket(): Result<PlayoffBracketResponse> {
        return getPlayoffBracketWithSource().first
    }

    /**
     * 获取季后赛对阵图（返回实际使用的数据源）
     */
    fun getPlayoffBracketWithSource(): Pair<Result<PlayoffBracketResponse>, DataSource> {
        return try {
            val settings = NBASettingsState.getInstance()
            val primarySource = DataSource.fromId(settings.dataSource)
            val sportsDbKey = settings.sportsDbApiKey.ifBlank { "3" }
            val manager = DataSourceManager(primarySource, settings.ballDontLieApiKey, sportsDbKey)
            manager.getPlayoffBracket()
        } catch (e: Exception) {
            val failure: Result<PlayoffBracketResponse> = Result.failure(e)
            return failure to DataSource.ESPN
        }
    }
}
