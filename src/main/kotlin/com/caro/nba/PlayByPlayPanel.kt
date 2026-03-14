package com.caro.nba

import com.caro.nba.model.PlayByPlay
import com.caro.nba.service.GameDetailService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 文字转播面板 - 展示比赛实时文字转播
 */
class PlayByPlayPanel(
    private val project: Project,
    private val gameId: String,
    private val homeTeamName: String,
    private val awayTeamName: String,
    private val homeTeamId: String,
    private val awayTeamId: String
) : DialogWrapper(project) {

    private val service = GameDetailService()
    private var mainPanel: JPanel? = null
    private var contentPanel: JPanel? = null
    private var loadingLabel: JLabel? = null
    private var refreshButton: JButton? = null
    private var lastUpdateLabel: JLabel? = null
    private var autoRefreshCheck: JCheckBox? = null
    private var autoRefreshTimer: Timer? = null

    init {
        title = "文字转播: $awayTeamName vs $homeTeamName"
        setOKButtonText("关闭")
        setSize(600, 700)
        init()
    }

    override fun createCenterPanel(): JComponent {
        mainPanel = JPanel(BorderLayout())
        mainPanel?.preferredSize = Dimension(550, 650)

        // 顶部工具栏
        val toolbarPanel = createToolbar()
        mainPanel?.add(toolbarPanel, BorderLayout.NORTH)

        // 加载中提示
        loadingLabel = JLabel("正在加载文字转播...", SwingConstants.CENTER).apply {
            font = font.deriveFont(16f)
            border = EmptyBorder(50, 0, 50, 0)
        }
        mainPanel?.add(loadingLabel, BorderLayout.CENTER)

        // 底部状态栏
        lastUpdateLabel = JLabel("").apply {
            font = font.deriveFont(10f)
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.RIGHT
        }

        // 后台线程加载数据
        loadPlayByPlay()

        return mainPanel!!
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel(BorderLayout(10, 5)).apply {
            border = JBUI.Borders.empty(8)
        }

        // 刷新按钮
        refreshButton = JButton("🔄 刷新").apply {
            addActionListener { loadPlayByPlay() }
        }

        // 自动刷新复选框
        autoRefreshCheck = JCheckBox("自动刷新 (10秒)").apply {
            isSelected = true
            addActionListener {
                if (isSelected) {
                    startAutoRefresh()
                } else {
                    stopAutoRefresh()
                }
            }
        }

        val leftPanel = JPanel().apply {
            add(refreshButton)
            add(Box.createHorizontalStrut(10))
            add(autoRefreshCheck)
        }

        toolbar.add(leftPanel, BorderLayout.WEST)
        toolbar.add(lastUpdateLabel, BorderLayout.EAST)

        return toolbar
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        autoRefreshTimer = Timer(10000) { // 10秒刷新一次
            SwingUtilities.invokeLater {
                loadPlayByPlay()
            }
        }
        autoRefreshTimer?.start()
    }

    private fun stopAutoRefresh() {
        autoRefreshTimer?.stop()
        autoRefreshTimer = null
    }

    private fun loadPlayByPlay() {
        loadingLabel?.isVisible = true

        Thread {
            try {
                val result = service.getPlayByPlay(gameId, homeTeamId, awayTeamId)
                result.fold(
                    onSuccess = { pbp ->
                        SwingUtilities.invokeLater {
                            showPlayByPlay(pbp)
                            updateLastRefreshTime()
                        }
                    },
                    onFailure = { error ->
                        SwingUtilities.invokeLater {
                            showError(error.message ?: "加载失败")
                        }
                    }
                )
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    showError(e.message ?: "未知错误")
                }
            }
        }.start()
    }

    private fun showPlayByPlay(pbp: PlayByPlay) {
        loadingLabel?.isVisible = false
        mainPanel?.remove(contentPanel)

        contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }

        // 按节数分组显示
        val playsByPeriod = pbp.plays.groupBy { it.period }

        // 从最新到最旧显示
        val sortedPeriods = playsByPeriod.keys.sortedDescending()

        for (period in sortedPeriods) {
            val periodPlays = playsByPeriod[period] ?: continue
            val periodDisplay = periodPlays.firstOrNull()?.periodDisplay ?: "第${period}节"

            // 节标题
            val periodLabel = JLabel("━━━ $periodDisplay ━━━").apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = JBColor(0x0066CC, 0x4A9EFF)
                alignmentX = Component.CENTER_ALIGNMENT
                border = EmptyBorder(10, 0, 10, 0)
            }
            contentPanel?.add(periodLabel)

            // 该节的所有事件（从新到旧）
            val sortedPlays = periodPlays.sortedByDescending {
                // 按时间排序：先把分钟转换成秒
                val clockParts = it.clock.split(":")
                val minutes = clockParts.getOrNull(0)?.toIntOrNull() ?: 0
                val seconds = clockParts.getOrNull(1)?.toIntOrNull() ?: 0
                minutes * 60 + seconds
            }

            for (play in sortedPlays) {
                val playPanel = createPlayPanel(play)
                contentPanel?.add(playPanel)
            }
        }

        val scrollPane = JBScrollPane(contentPanel).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }

        // 移除加载提示，添加内容
        mainPanel?.removeAll()
        val toolbarPanel = createToolbar()
        mainPanel?.add(toolbarPanel, BorderLayout.NORTH)
        mainPanel?.add(scrollPane, BorderLayout.CENTER)

        // 恢复自动刷新
        if (autoRefreshCheck?.isSelected == true) {
            startAutoRefresh()
        }

        mainPanel?.revalidate()
        mainPanel?.repaint()
    }

    private fun createPlayPanel(play: PlayByPlay.Play): JPanel {
        val panel = JPanel(BorderLayout(8, 3)).apply {
            border = BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor(0xEEEEEE, 0x2D2D2D))
            maximumSize = Dimension(Int.MAX_VALUE, 60)
            preferredSize = Dimension(500, 55)
        }

        // 左侧：时间和比分
        val leftPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // 时间
        val timeLabel = JLabel("[${play.clock}]").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = JBColor.GRAY
        }
        leftPanel.add(timeLabel)

        // 比分变化（如果有得分）
        if (play.isScoringPlay) {
            val scoreText = "${play.awayScore} - ${play.homeScore}"
            val scoreLabel = JLabel(scoreText).apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = JBColor(0xFF6600, 0xFF9933)  // 橙色突出得分
            }
            leftPanel.add(scoreLabel)
        }

        // 右侧：事件描述
        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
        }

        // 事件描述
        val descLabel = JLabel(play.text).apply {
            font = font.deriveFont(12f)
            foreground = if (play.isScoringPlay) JBColor(0x0066CC, 0x4A9EFF) else JBColor.BLACK
        }
        rightPanel.add(descLabel)

        // 事件类型标签
        val typeLabel = JLabel(play.playType).apply {
            font = font.deriveFont(9f)
            foreground = when {
                play.isScoringPlay -> JBColor(0xFF3300, 0xFF6633)
                play.playType.contains("Rebound") -> JBColor(0x009900, 0x66CC66)
                play.playType.contains("Foul") -> JBColor(0xFF9900, 0xFFCC33)
                play.playType.contains("Turnover") -> JBColor(0xCC0000, 0xFF6666)
                else -> JBColor.GRAY
            }
        }
        rightPanel.add(typeLabel)

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(rightPanel, BorderLayout.CENTER)

        return panel
    }

    private fun updateLastRefreshTime() {
        val now = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
        lastUpdateLabel?.text = "最后更新: $now"
    }

    private fun showError(message: String) {
        loadingLabel?.isVisible = false
        mainPanel?.removeAll()

        val errorLabel = JLabel("❌ $message").apply {
            foreground = JBColor.RED
            font = font.deriveFont(16f)
            horizontalAlignment = SwingConstants.CENTER
        }
        mainPanel?.add(errorLabel, BorderLayout.CENTER)
        mainPanel?.revalidate()
        mainPanel?.repaint()
    }

    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }

    override fun dispose() {
        stopAutoRefresh()
        super.dispose()
    }
}
