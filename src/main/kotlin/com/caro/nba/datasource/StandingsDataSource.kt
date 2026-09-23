package com.caro.nba.datasource

import com.caro.nba.model.NBAStandings

/**
 * 排名数据源接口
 */
interface StandingsDataSource {
    /**
     * 获取联盟排名数据
     */
    fun getStandings(): Result<NBAStandings>
}
