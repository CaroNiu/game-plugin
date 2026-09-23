package com.caro.nba.datasource

import com.caro.nba.model.PlayoffBracketResponse

/**
 * 季后赛对阵图数据源接口
 */
interface PlayoffDataSource {
    /**
     * 获取季后赛对阵图数据
     */
    fun getPlayoffBracket(): Result<PlayoffBracketResponse>
}
