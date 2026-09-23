package com.caro.nba.datasource

import com.caro.nba.model.NBAScoreboard
import java.time.LocalDate

/**
 * 比分数据源接口
 */
interface ScoreDataSource {
    /**
     * 获取指定日期的比赛数据
     * @param date 美国东部时间日期
     */
    fun getScores(date: LocalDate): Result<NBAScoreboard>
}
