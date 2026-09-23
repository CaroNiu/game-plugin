package com.caro.nba.service

import com.caro.nba.NBASettingsState
import com.caro.nba.datasource.DataSource
import com.caro.nba.datasource.DataSourceCommon
import com.caro.nba.datasource.DataSourceManager
import com.caro.nba.model.GameDetail
import com.caro.nba.model.PlayByPlay
import com.google.gson.JsonObject
import okhttp3.Request
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 比赛引用信息：用于跨数据源反查。
 *
 * 中文数据源（新浪/直播吧）的 gameId 与 ESPN 的 eventId 不通用，
 * 直接用其查 ESPN 详情必然失败；此时按「美东日期 + 两队缩写」
 * 调 ESPN scoreboard 反查出真实的 eventId 后再查详情/文字转播。
 */
data class GameRef(
    val easternDate: LocalDate,
    val homeAbbr: String,
    val awayAbbr: String
)

/**
 * 比赛详情服务
 *
 * 委托给 DataSourceManager 进行多数据源管理和自动 fallback。
 * 数据源配置从 NBASettingsState 读取。
 */
class GameDetailService {

    /**
     * 获取比赛详情
     * @param ref 比赛引用信息（中文源的 gameId 与 ESPN 不通用时用于反查），可为 null
     */
    fun getGameDetail(gameId: String, ref: GameRef? = null): Result<GameDetail> {
        val direct = getGameDetailWithSource(gameId).first
        if (direct.isSuccess || ref == null) return direct

        // 直接查失败：可能是中文源 gameId，按日期+队名反查 ESPN eventId 后重试
        val espnEventId = findEspnEventId(ref) ?: return direct
        DataSourceCommon.debugLog("详情反查: $gameId -> ESPN event $espnEventId")
        return getGameDetailWithSource(espnEventId).first
    }

    /**
     * 获取比赛详情（返回实际使用的数据源）
     */
    fun getGameDetailWithSource(gameId: String): Pair<Result<GameDetail>, DataSource> {
        return try {
            val manager = buildManager()
            manager.getGameDetail(gameId)
        } catch (e: Exception) {
            val failure: Result<GameDetail> = Result.failure(e)
            return failure to DataSource.ESPN
        }
    }

    /**
     * 获取比赛文字转播数据
     * @param ref 比赛引用信息（中文源的 gameId 与 ESPN 不通用时用于反查），可为 null
     */
    fun getPlayByPlay(gameId: String, homeTeamId: String, awayTeamId: String, ref: GameRef? = null): Result<PlayByPlay> {
        val direct = getPlayByPlayWithSource(gameId, homeTeamId, awayTeamId).first
        if (direct.isSuccess || ref == null) return direct

        val espnEventId = findEspnEventId(ref) ?: return direct
        DataSourceCommon.debugLog("文字转播反查: $gameId -> ESPN event $espnEventId")
        return getPlayByPlayWithSource(espnEventId, homeTeamId, awayTeamId).first
    }

    /**
     * 获取比赛文字转播数据（返回实际使用的数据源）
     */
    fun getPlayByPlayWithSource(
        gameId: String,
        homeTeamId: String,
        awayTeamId: String
    ): Pair<Result<PlayByPlay>, DataSource> {
        return try {
            val manager = buildManager()
            manager.getPlayByPlay(gameId, homeTeamId, awayTeamId)
        } catch (e: Exception) {
            val failure: Result<PlayByPlay> = Result.failure(e)
            return failure to DataSource.ESPN
        }
    }

    private fun buildManager(): DataSourceManager {
        val settings = NBASettingsState.getInstance()
        val primarySource = DataSource.fromId(settings.dataSource)
        val sportsDbKey = settings.sportsDbApiKey.ifBlank { "3" }
        return DataSourceManager(primarySource, settings.ballDontLieApiKey, sportsDbKey)
    }

    /**
     * 按日期+队名缩写反查 ESPN eventId
     * @return 匹配比赛的 ESPN eventId，未找到或网络失败返回 null
     */
    private fun findEspnEventId(ref: GameRef): String? {
        return try {
            val home = ref.homeAbbr.uppercase()
            val away = ref.awayAbbr.uppercase()
            if (home.isBlank() || away.isBlank()) return null

            val dateStr = ref.easternDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            // site.web.api.espn.com 可达性更好（site.api 对部分网络 403）
            val url = "https://site.web.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard?dates=$dateStr"
            DataSourceCommon.debugLog("详情反查 ESPN scoreboard: $url")

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://www.espn.com")
                .header("Referer", "https://www.espn.com/")
                .build()

            DataSourceCommon.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    DataSourceCommon.debugLog("详情反查 ESPN scoreboard 失败: HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val root = DataSourceCommon.gson.fromJson(body, JsonObject::class.java)
                val events = root.getAsJsonArray("events") ?: return null

                for (event in events) {
                    val competitors = event.asJsonObject
                        .getAsJsonArray("competitions")?.get(0)?.asJsonObject
                        ?.getAsJsonArray("competitors") ?: continue
                    val abbrs = competitors.mapNotNull { c ->
                        c.asJsonObject.getAsJsonObject("team")?.get("abbreviation")?.asString?.uppercase()
                    }.toSet()
                    if (abbrs == setOf(home, away)) {
                        return event.asJsonObject.get("id")?.asString
                    }
                }
                null
            }
        } catch (e: Exception) {
            DataSourceCommon.debugLog("详情反查异常: ${e.message}")
            null
        }
    }
}
