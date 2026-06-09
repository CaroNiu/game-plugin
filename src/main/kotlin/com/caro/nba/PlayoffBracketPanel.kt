package com.caro.nba

import com.caro.nba.model.PlayoffBracketResponse
import com.caro.nba.model.PlayoffBracketSeries
import com.caro.nba.service.PlayoffService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.JBColor
import kotlinx.coroutines.*
import java.awt.*
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 季后赛对阵图 - 晋级树版本
 */
class PlayoffBracketPanel : JPanel(BorderLayout()) {

    private val service = PlayoffService()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val bracketPanel = BracketDrawPanel()

    private var bracketData: PlayoffBracketResponse? = null
    private var errorMessage: String? = null
    private val logoCache = mutableMapOf<String, Image>()

    init {
        setupUI()
        loadData()
    }

    private fun setupUI() {
        background = JBColor.background()
        border = EmptyBorder(16, 16, 16, 16)

        val titlePanel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            background = JBColor.background()
            add(JLabel("🏆 NBA 季后赛对阵图").apply {
                font = font.deriveFont(Font.BOLD, 22f)
                foreground = JBColor(0xC90C2E, 0xE03A3E)
            })
        }

        val scrollPane = JScrollPane(bracketPanel).apply {
            border = null
            viewport.background = JBColor.background()
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBar.unitIncrement = 16
            verticalScrollBar.unitIncrement = 16
        }

        add(titlePanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        preferredSize = Dimension(1120, 760)
    }

    private fun loadData() {
        scope.launch {
            val result = service.getPlayoffBracket()
            result.getOrNull()?.let { preloadLogos(it) }
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { data ->
                        bracketData = data
                        errorMessage = null
                        bracketPanel.revalidate()
                        bracketPanel.repaint()
                    },
                    onFailure = { error ->
                        errorMessage = error.message ?: "加载失败"
                        bracketPanel.repaint()
                    }
                )
            }
        }
    }

    private fun preloadLogos(data: PlayoffBracketResponse) {
        val urls = mutableSetOf<String>()
        val allRounds = data.data.top + data.data.bottom + listOfNotNull(listOfNotNull(data.data.finals))
        allRounds.flatten().forEach { series ->
            series.teams.orEmpty().mapNotNullTo(urls) { it.img }
            series.schedule?.list.orEmpty().forEach { game ->
                game.left_logo?.let(urls::add)
                game.right_logo?.let(urls::add)
            }
        }
        urls.forEach { url ->
            if (!logoCache.containsKey(url)) {
                try {
                    ImageIO.read(URI(url).toURL())?.let { image ->
                        logoCache[url] = image.getScaledInstance(24, 24, Image.SCALE_SMOOTH)
                    }
                } catch (_: Exception) {
                    // Logo 加载失败不影响对阵图展示
                }
            }
        }
    }

    private inner class BracketDrawPanel : JPanel() {

        init {
            background = JBColor.background()
        }

        override fun getPreferredSize(): Dimension = Dimension(1120, 760)

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g as Graphics2D
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val data = bracketData?.data
            if (data == null) {
                drawCenteredText(g2d, errorMessage ?: "加载中...")
                return
            }

            val centerX = width / 2
            drawWatermark(g2d, centerX)

            // top = 西部，bottom = 东部（接口结构和参考图一致）
            drawConference(g2d, "西部", data.top, isTop = true)
            drawConference(g2d, "东部", data.bottom, isTop = false)
            drawFinals(g2d, data.finals, data.top.lastOrNull()?.firstOrNull(), data.bottom.lastOrNull()?.firstOrNull(), centerX)
        }

        private fun drawCenteredText(g2d: Graphics2D, text: String) {
            g2d.color = JBColor.GRAY
            g2d.font = g2d.font.deriveFont(Font.ITALIC, 16f)
            val textWidth = g2d.fontMetrics.stringWidth(text)
            g2d.drawString(text, (width - textWidth) / 2, height / 2)
        }

        private fun drawWatermark(g2d: Graphics2D, centerX: Int) {
            g2d.color = JBColor(0xF2F2F2, 0x303030)
            g2d.font = g2d.font.deriveFont(Font.BOLD, 64f)
            val trophy = "🏆"
            val trophyWidth = g2d.fontMetrics.stringWidth(trophy)
            g2d.drawString(trophy, centerX - trophyWidth / 2, height / 2 + 20)
        }

        private fun drawConference(
            g2d: Graphics2D,
            title: String,
            rounds: List<List<PlayoffBracketSeries>>,
            isTop: Boolean
        ) {
            if (rounds.isEmpty()) return

            val centerX = width / 2
            val titleY = if (isTop) 42 else height - 24
            g2d.font = g2d.font.deriveFont(Font.BOLD, 15f)
            g2d.color = if (title == "西部") JBColor(0x007A33, 0x2D8F44) else JBColor(0xC90C2E, 0xE03A3E)
            val titleText = "$title 联盟"
            g2d.drawString(titleText, centerX - g2d.fontMetrics.stringWidth(titleText) / 2, titleY)

            val firstRound = rounds.getOrNull(0).orEmpty()
            val secondRound = rounds.getOrNull(1).orEmpty()
            val conferenceFinal = rounds.getOrNull(2).orEmpty()

            val y0 = if (isTop) 78 else height - 118
            val direction = if (isTop) 1 else -1
            val roundGapY = 92
            val cardW = 150
            val cardH = 76

            val firstXs = listOf(90, 330, width - 480, width - 240)
            val firstY = y0
            firstRound.take(4).forEachIndexed { index, series ->
                drawSeriesCard(g2d, series, firstXs[index], firstY, cardW, cardH)
            }

            val secondXs = listOf(210, width - 210 - cardW)
            val secondY = y0 + direction * roundGapY
            secondRound.take(2).forEachIndexed { index, series ->
                drawSeriesCard(g2d, series, secondXs[index], secondY, cardW, cardH)
            }

            val finalX = centerX - cardW / 2
            val finalY = y0 + direction * roundGapY * 2
            conferenceFinal.firstOrNull()?.let { drawSeriesCard(g2d, it, finalX, finalY, cardW, cardH) }

            // 连接线：首轮 -> 次轮
            drawConnector(g2d, firstXs[0] + cardW / 2, firstY, secondXs[0] + cardW / 2, secondY, isTop)
            drawConnector(g2d, firstXs[1] + cardW / 2, firstY, secondXs[0] + cardW / 2, secondY, isTop)
            drawConnector(g2d, firstXs[2] + cardW / 2, firstY, secondXs[1] + cardW / 2, secondY, isTop)
            drawConnector(g2d, firstXs[3] + cardW / 2, firstY, secondXs[1] + cardW / 2, secondY, isTop)

            // 次轮 -> 分区决赛
            drawConnector(g2d, secondXs[0] + cardW / 2, secondY, finalX + cardW / 2, finalY, isTop)
            drawConnector(g2d, secondXs[1] + cardW / 2, secondY, finalX + cardW / 2, finalY, isTop)

            // 分区决赛 -> 总决赛
            val finalsY = height / 2 - 32
            drawConnector(g2d, finalX + cardW / 2, finalY, centerX, finalsY, isTop)
        }

        private fun drawConnector(g2d: Graphics2D, fromX: Int, fromY: Int, toX: Int, toY: Int, isTop: Boolean) {
            val cardHeight = 76
            val fromEdgeY = if (isTop) fromY + cardHeight else fromY
            val toEdgeY = if (isTop) toY else toY + cardHeight
            val midY = (fromEdgeY + toEdgeY) / 2

            g2d.color = JBColor(0x2D8CFF, 0x4A9EFF)
            g2d.stroke = BasicStroke(2f)
            g2d.drawLine(fromX, fromEdgeY, fromX, midY)
            g2d.drawLine(fromX, midY, toX, midY)
            g2d.drawLine(toX, midY, toX, toEdgeY)
        }

        private fun drawFinals(
            g2d: Graphics2D,
            finals: PlayoffBracketSeries?,
            westFinal: PlayoffBracketSeries?,
            eastFinal: PlayoffBracketSeries?,
            centerX: Int
        ) {
            val cardW = 820
            val cardH = 72
            val x = centerX - cardW / 2
            val y = height / 2 - cardH / 2

            g2d.color = JBColor(0xEFFFF3, 0x253528)
            g2d.fillRoundRect(x, y, cardW, cardH, 4, 4)
            g2d.color = JBColor(0x18A84A, 0x28C45A)
            g2d.stroke = BasicStroke(3f)
            g2d.drawRoundRect(x, y, cardW, cardH, 4, 4)

            g2d.font = g2d.font.deriveFont(Font.BOLD, 16f)
            g2d.color = JBColor.foreground()
            val label = "决赛"
            g2d.drawString(label, centerX - g2d.fontMetrics.stringWidth(label) / 2, y + cardH / 2 + 6)

            if (finals != null) {
                val westChampion = getSeriesWinnerName(westFinal)
                val eastChampion = getSeriesWinnerName(eastFinal)
                drawFinalsTeams(g2d, finals, westChampion, eastChampion, x, y, cardW, cardH)
            }
        }

        private fun drawFinalsTeams(
            g2d: Graphics2D,
            finals: PlayoffBracketSeries,
            westChampion: String?,
            eastChampion: String?,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) {
            val leftName = westChampion ?: resolveTeamName(finals.teams.orEmpty().getOrNull(0)?.name, finals, preferTop = true) ?: "待定"
            val rightName = eastChampion ?: resolveTeamName(finals.teams.orEmpty().getOrNull(1)?.name, finals, preferTop = false) ?: "待定"
            val score = calculateSeriesScoreForNames(finals, leftName, rightName)
            val baseline = y + height / 2 + 6

            g2d.font = g2d.font.deriveFont(Font.BOLD, 15f)
            g2d.color = JBColor.foreground()
            g2d.drawString(leftName, x + 70, baseline)
            g2d.drawString(rightName, x + width - 170, baseline)

            g2d.font = g2d.font.deriveFont(Font.BOLD, 20f)
            val leftScore = score.first?.toString() ?: "0"
            val rightScore = score.second?.toString() ?: "0"
            g2d.drawString(leftScore, x + 170, baseline)
            g2d.drawString(rightScore, x + width - 240, baseline)
        }

        private fun drawSeriesCard(
            g2d: Graphics2D,
            series: PlayoffBracketSeries,
            x: Int,
            y: Int,
            width: Int,
            height: Int
        ) {
            val team1 = series.teams.orEmpty().getOrNull(0)
            val team2 = series.teams.orEmpty().getOrNull(1)
            val seriesScore = calculateSeriesScore(series)
            val score1 = seriesScore.first
            val score2 = seriesScore.second
            val team1Winner = score1 != null && score2 != null && score1 > score2 && score1 >= series.win_threshold
            val team2Winner = score1 != null && score2 != null && score2 > score1 && score2 >= series.win_threshold

            val team1Name = resolveTeamName(team1?.name, series, preferTop = true)
            val team2Name = resolveTeamName(team2?.name, series, preferTop = false)
            val leftX = x
            val rightX = x + width - 56
            val lineY = y + height - 14
            val midX = x + width / 2

            drawBracketTeam(g2d, team1Name, resolveLogo(team1?.img, team1Name, series), score1, team1Winner, leftX, y)
            drawBracketTeam(g2d, team2Name, resolveLogo(team2?.img, team2Name, series), score2, team2Winner, rightX, y)

            g2d.color = JBColor(0x2D8CFF, 0x4A9EFF)
            g2d.stroke = BasicStroke(2f)
            g2d.drawLine(leftX + 22, lineY, rightX + 22, lineY)
            g2d.drawLine(midX, lineY, midX, lineY + 10)
        }

        private fun resolveLogo(logo: String?, teamName: String?, series: PlayoffBracketSeries): String? {
            if (!logo.isNullOrBlank()) return logo
            return series.schedule?.list.orEmpty().firstNotNullOfOrNull { game ->
                when (teamName) {
                    game.left_team -> game.left_logo
                    game.right_team -> game.right_logo
                    else -> null
                }
            }
        }

        private fun getLogo(logoUrl: String?): Image? {
            if (logoUrl.isNullOrBlank()) return null
            return logoCache[logoUrl]
        }

        private fun drawBracketTeam(
            g2d: Graphics2D,
            name: String?,
            logoUrl: String?,
            score: Int?,
            isWinner: Boolean,
            x: Int,
            y: Int
        ) {
            val teamName = name?.takeIf { it.isNotBlank() } ?: "?"
            getLogo(logoUrl)?.let { logo ->
                g2d.drawImage(logo, x + 12, y, 24, 24, null)
            }

            g2d.font = g2d.font.deriveFont(Font.PLAIN, 10f)
            g2d.color = if (isWinner) JBColor(0x007A33, 0x2D8F44) else JBColor.foreground()
            val nameWidth = g2d.fontMetrics.stringWidth(teamName)
            g2d.drawString(teamName, x + 24 - nameWidth / 2, y + 38)

            g2d.font = g2d.font.deriveFont(Font.BOLD, 13f)
            val scoreText = score?.toString() ?: ""
            val scoreWidth = g2d.fontMetrics.stringWidth(scoreText)
            g2d.drawString(scoreText, x + 24 - scoreWidth / 2, y + 54)
        }

        private fun drawTeamLine(
            g2d: Graphics2D,
            rank: String?,
            name: String?,
            score: Int?,
            isWinner: Boolean,
            x: Int,
            baseline: Int,
            width: Int
        ) {
            val teamName = name?.takeIf { it.isNotBlank() } ?: "?"
            val seed = rank?.takeIf { it.isNotBlank() }?.let { "#$it" } ?: ""

            g2d.font = g2d.font.deriveFont(Font.BOLD, 11f)
            g2d.color = if (isWinner) JBColor(0x007A33, 0x2D8F44) else JBColor.GRAY
            g2d.drawString(seed, x + 8, baseline)

            g2d.color = if (isWinner) JBColor(0x007A33, 0x2D8F44) else JBColor.foreground()
            g2d.drawString(teamName, x + 34, baseline)

            val scoreText = score?.toString() ?: ""
            if (scoreText.isNotEmpty()) {
                g2d.font = g2d.font.deriveFont(Font.BOLD, 13f)
                val scoreWidth = g2d.fontMetrics.stringWidth(scoreText)
                g2d.drawString(scoreText, x + width - scoreWidth - 10, baseline)
            }
        }

        private fun getSeriesWinnerName(series: PlayoffBracketSeries?): String? {
            if (series == null) return null
            val team1 = resolveTeamName(series.teams.orEmpty().getOrNull(0)?.name, series, preferTop = true)
            val team2 = resolveTeamName(series.teams.orEmpty().getOrNull(1)?.name, series, preferTop = false)
            val score = calculateSeriesScore(series)
            val score1 = score.first
            val score2 = score.second
            return when {
                score1 != null && score2 != null && score1 > score2 -> team1
                score1 != null && score2 != null && score2 > score1 -> team2
                else -> null
            }
        }

        private fun calculateSeriesScoreForNames(
            series: PlayoffBracketSeries,
            firstName: String,
            secondName: String
        ): Pair<Int?, Int?> {
            var firstWins = 0
            var secondWins = 0
            series.schedule?.list.orEmpty().forEach { game ->
                val gameScore = parseGameScore(game.score) ?: return@forEach
                val winner = if (gameScore.first > gameScore.second) game.left_team else game.right_team
                when (winner) {
                    firstName -> firstWins++
                    secondName -> secondWins++
                }
            }
            if (firstWins == 0 && secondWins == 0) return null to null
            return firstWins to secondWins
        }

        private fun calculateSeriesScore(series: PlayoffBracketSeries): Pair<Int?, Int?> {
            val directScore1 = parseSeriesScore(series.info1)
            val directScore2 = parseSeriesScore(series.info2)
            if (directScore1 != null || directScore2 != null) {
                return directScore1 to directScore2
            }

            val topName = resolveTeamName(series.teams.orEmpty().getOrNull(0)?.name, series, preferTop = true)
            val bottomName = resolveTeamName(series.teams.orEmpty().getOrNull(1)?.name, series, preferTop = false)
            if (topName.isNullOrBlank() || bottomName.isNullOrBlank()) {
                return null to null
            }

            var topWins = 0
            var bottomWins = 0
            series.schedule?.list.orEmpty().forEach { game ->
                val gameScore = parseGameScore(game.score) ?: return@forEach
                val winner = if (gameScore.first > gameScore.second) game.left_team else game.right_team
                when (winner) {
                    topName -> topWins++
                    bottomName -> bottomWins++
                }
            }

            if (topWins == 0 && bottomWins == 0) return null to null
            return topWins to bottomWins
        }

        private fun resolveTeamName(name: String?, series: PlayoffBracketSeries, preferTop: Boolean): String? {
            if (!name.isNullOrBlank() && name != "?") return name

            val calculated = linkedMapOf<String, Int>()
            series.schedule?.list.orEmpty().forEach { game ->
                game.left_team?.takeIf { it.isNotBlank() }?.let { calculated.putIfAbsent(it, 0) }
                game.right_team?.takeIf { it.isNotBlank() }?.let { calculated.putIfAbsent(it, 0) }

                val gameScore = parseGameScore(game.score) ?: return@forEach
                val winner = if (gameScore.first > gameScore.second) game.left_team else game.right_team
                if (!winner.isNullOrBlank()) {
                    calculated[winner] = (calculated[winner] ?: 0) + 1
                }
            }

            val sorted = calculated.entries.sortedByDescending { it.value }
            return if (preferTop) sorted.getOrNull(0)?.key else sorted.getOrNull(1)?.key
        }

        private fun parseGameScore(value: String?): Pair<Int, Int>? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed.equals("vs", ignoreCase = true)) return null
            val scoreParts = trimmed.split("-")
            if (scoreParts.size != 2) return null
            val leftScore = scoreParts[0].toIntOrNull() ?: return null
            val rightScore = scoreParts[1].toIntOrNull() ?: return null
            return leftScore to rightScore
        }

        private fun parseSeriesScore(value: String?): Int? {
            val trimmed = value?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed == "-" || trimmed == "?") return null
            return trimmed.toIntOrNull()
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
