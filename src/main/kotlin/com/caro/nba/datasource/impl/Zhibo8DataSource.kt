package com.caro.nba.datasource.impl

import com.caro.nba.datasource.DataSourceCommon
import com.caro.nba.datasource.ScoreDataSource
import com.caro.nba.model.NBAGame
import com.caro.nba.model.NBAScoreboard
import com.google.gson.JsonObject
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 直播吧数据源（中文数据源）
 *
 * 数据来自直播吧（zhibo8.com）比分频道的公开接口（bifen.qiumibao.com），
 * 中文队名、国内访问速度快，无需 API Key。
 *
 * 主要端点：
 * - 按日比分: https://bifen.qiumibao.com/json/{yyyy-MM-dd}/list.htm
 *   返回当天全部体育项目的比赛列表（NBA、CBA、足球等），需按 type=basketball + 队名过滤出 NBA
 *
 * 注意：
 * - 接口日期为北京时间；NBA 比赛发生在美国东部晚间，对应北京时间为次日，
 *   因此查询美东日期 D 时需请求 D 和 D+1 两天，再按比赛开始时间精确过滤
 * - 该源仅支持比分/赛程，不支持排名、季后赛对阵图和比赛详情
 */
class Zhibo8ScoreDataSource : ScoreDataSource {
    private val client = DataSourceCommon.client
    private val gson = DataSourceCommon.gson

    private val baseUrl = "https://bifen.qiumibao.com/json"

    /** 是否 NBA 球队（基于公共中文队名映射） */
    private fun isNbaTeam(name: String): Boolean =
        DataSourceCommon.chineseNameToAbbr.containsKey(DataSourceCommon.normalizeChineseTeamName(name))

    override fun getScores(date: LocalDate): Result<NBAScoreboard> {
        return try {
            // 直播吧按北京日期组织比赛：美东日期 D 的 NBA 比赛位于北京日期 D+1。
            // 同时请求 D 与 D+1 两天，先按比赛开始时间（北京->美东）精确过滤；
            // 若无精确匹配，退回请求日期当天的 NBA 比赛（兼容调用方直接传北京日期）
            val nextDay = fetchDay(date.plusDays(1))
            val sameDay = fetchDay(date)

            if (nextDay.isFailure && sameDay.isFailure) {
                val failure: Result<NBAScoreboard> = Result.failure(
                    Exception("直播吧 API 请求失败: ${nextDay.exceptionOrNull()?.message ?: sameDay.exceptionOrNull()?.message}")
                )
                return failure
            }

            val candidates = nextDay.getOrDefault(emptyList()) + sameDay.getOrDefault(emptyList())
            DataSourceCommon.debugLog(
                "直播吧 请求日期=$date: 次日(${date.plusDays(1)})=${nextDay.getOrDefault(emptyList()).size} 场, " +
                        "当日=${sameDay.getOrDefault(emptyList()).size} 场 NBA 比赛"
            )

            // 精确过滤：比赛开始时间（北京时间）换算到美东后落在请求日期
            val etMatched = candidates.filter { game ->
                toEasternDate(game.startTime) == date
            }
            DataSourceCommon.debugLog("直播吧 美东日期精确匹配: ${etMatched.size} 场")

            val games = when {
                etMatched.isNotEmpty() -> etMatched
                sameDay.getOrDefault(emptyList()).isNotEmpty() -> sameDay.getOrDefault(emptyList())
                else -> nextDay.getOrDefault(emptyList())
            }

            Result.success(NBAScoreboard(date.toString(), games))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 请求直播吧某北京日期的比赛列表，过滤出 NBA 比赛
     */
    private fun fetchDay(beijingDate: LocalDate): Result<List<NBAGame>> {
        return try {
            val dateStr = beijingDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val url = "$baseUrl/$dateStr/list.htm"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", DataSourceCommon.DEFAULT_USER_AGENT)
                .header("Referer", "https://www.zhibo8.com/")
                .header("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(request).execute().use { response ->
                DataSourceCommon.debugLog("直播吧 响应: HTTP ${response.code} ($url)")
                if (!response.isSuccessful) {
                    val failure: Result<List<NBAGame>> = Result.failure(Exception("HTTP ${response.code}"))
                    return failure
                }

                val body = response.body?.string()
                    ?: return Result.failure(Exception("直播吧响应为空"))

                val games = parseScoreboard(body)
                DataSourceCommon.debugLog("直播吧 $dateStr 解析出 ${games.size} 场 NBA 比赛")
                Result.success(games)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 解析按日比赛列表 JSON，过滤出 NBA 比赛
     */
    private fun parseScoreboard(json: String): List<NBAGame> {
        return try {
            val root = gson.fromJson(json, JsonObject::class.java)
            val listElement = root.get("list")
            if (listElement == null || listElement.isJsonNull || !listElement.isJsonArray) {
                return emptyList()
            }

            listElement.asJsonArray.mapNotNull { element -> parseGame(element.asJsonObject) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 解析单场比赛，非 NBA 比赛返回 null
     */
    private fun parseGame(game: JsonObject): NBAGame? {
        return try {
            // type=basketball 且 leagueid=924 或任一队为 NBA 球队（容错：季前赛 leagueid 可能不同）
            val type = game.get("type")?.asString ?: return null
            if (type != "basketball") return null

            val homeNameRaw = game.get("home_team")?.asString ?: ""
            val awayNameRaw = game.get("visit_team")?.asString ?: ""
            val homeName = DataSourceCommon.normalizeChineseTeamName(homeNameRaw)
            val awayName = DataSourceCommon.normalizeChineseTeamName(awayNameRaw)

            // 过滤系列赛大比分伪条目（队名带 "(1)" 之类后缀，如 "尼克斯(1) vs 骑士(0)"）
            val seriesPattern = Regex("""\(\d+\)$""")
            if (seriesPattern.containsMatchIn(homeNameRaw) || seriesPattern.containsMatchIn(awayNameRaw)) {
                return null
            }

            val leagueId = game.get("leagueid")?.asString ?: ""
            val isNba = leagueId == "924" || (isNbaTeam(homeName) && homeName.isNotEmpty()) ||
                    (isNbaTeam(awayName) && awayName.isNotEmpty())
            if (!isNba) return null

            // 状态映射：1=未开始, 2=进行中, 3=完赛, 4=延期, 7=待定, 8=取消
            val state = game.get("state")?.asString ?: "1"
            val periodCn = (game.get("period_cn")?.asString ?: "").trim()
            val status = when (state) {
                "2" -> "in_progress"
                "3" -> "finished"
                else -> "scheduled"
            }

            // period_cn 形如 "第4节\n08:04"、"中场"、"完赛"
            var period = 0
            var clock = ""
            val periodMatch = Regex("""第(\d+)节""").find(periodCn)
            when {
                periodMatch != null -> period = periodMatch.groupValues[1].toIntOrNull() ?: 0
                periodCn.contains("加时") -> period = 5
            }
            periodCn.split("\n").getOrNull(1)?.let { clock = it.trim() }

            val homeScore = game.get("home_score")?.asString?.toIntOrNull() ?: 0
            val awayScore = game.get("visit_score")?.asString?.toIntOrNull() ?: 0

            NBAGame(
                gameId = game.get("id")?.asString ?: "",
                status = status,
                period = period,
                clock = clock,
                startTime = game.get("start")?.asString ?: "",
                detail = periodCn.replace("\n", " "),
                homeTeam = buildTeam(game.get("home_id")?.asString ?: "", homeName),
                homeScore = homeScore,
                awayTeam = buildTeam(game.get("visit_id")?.asString ?: "", awayName),
                awayScore = awayScore
            )
        } catch (e: Exception) {
            // 解析失败不再静默丢弃，打日志便于排查
            DataSourceCommon.debugLog("直播吧 单场解析失败(id=${game.get("id")?.asString}): ${e.message}")
            null
        }
    }

    /**
     * 构建球队信息：直播吧不提供队徽，用 ESPN CDN 队徽兜底
     */
    private fun buildTeam(teamId: String, chineseName: String): NBAGame.Team {
        val abbr = DataSourceCommon.chineseNameToAbbr[chineseName] ?: ""
        return NBAGame.Team(
            id = teamId,
            name = chineseName,  // 已经是中文
            abbreviation = abbr,
            shortName = chineseName,
            logo = DataSourceCommon.espnTeamLogo(abbr)
        )
    }

    /**
     * 北京时间字符串 -> 美东日期
     */
    private fun toEasternDate(beijingStart: String): LocalDate? {
        return try {
            val ldt = LocalDateTime.parse(beijingStart, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            ldt.atZone(ZoneId.of("Asia/Shanghai"))
                .withZoneSameInstant(ZoneId.of("America/New_York"))
                .toLocalDate()
        } catch (e: Exception) {
            null
        }
    }
}
