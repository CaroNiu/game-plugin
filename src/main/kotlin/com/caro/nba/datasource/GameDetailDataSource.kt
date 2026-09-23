package com.caro.nba.datasource

import com.caro.nba.model.GameDetail
import com.caro.nba.model.PlayByPlay

/**
 * 比赛详情数据源接口
 */
interface GameDetailDataSource {
    /**
     * 获取比赛详情
     */
    fun getGameDetail(gameId: String): Result<GameDetail>

    /**
     * 获取比赛文字转播
     */
    fun getPlayByPlay(gameId: String, homeTeamId: String, awayTeamId: String): Result<PlayByPlay>
}
