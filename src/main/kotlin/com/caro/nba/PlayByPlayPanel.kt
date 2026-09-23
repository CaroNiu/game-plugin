package com.caro.nba

import com.caro.nba.model.PlayByPlay
import com.caro.nba.service.GameDetailService
import com.caro.nba.service.GameRef
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
    private val awayTeamId: String,
    private val gameStatus: String = "scheduled",
    private val gameRef: GameRef? = null  // 中文源 gameId 与 ESPN 不通用时用于反查
) : DialogWrapper(project) {

    private val service = GameDetailService()
    private var contentPanel: JPanel? = null
    private var loadingLabel: JLabel? = null
    private var refreshButton: JButton? = null
    private var lastUpdateLabel: JLabel? = null
    
    private val isGameFinished: Boolean = gameStatus == "finished"
    private val isGameScheduled: Boolean = gameStatus == "scheduled"

    init {
        title = "文字转播: $awayTeamName vs $homeTeamName"
        setOKButtonText("关闭")
        setSize(600, 700)
        init()
    }

    private var mainPanel: JPanel? = null
    private var scrollPane: JBScrollPane? = null

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 10))
        panel.preferredSize = Dimension(550, 650)
        panel.border = JBUI.Borders.empty(10)
        mainPanel = panel

        // 顶部工具栏
        val toolbar = JPanel(BorderLayout(10, 5))
        
        refreshButton = JButton("🔄 刷新").apply {
            addActionListener { loadPlayByPlay() }
        }

        val statusLabel = JLabel(when {
            isGameFinished -> "📋 已结束"
            isGameScheduled -> "⏰ 未开始"
            else -> "🔴 进行中"
        }).apply {
            foreground = when {
                isGameFinished -> JBColor.GRAY
                isGameScheduled -> JBColor(0xFF9900, 0xFFCC33)
                else -> JBColor(0x009900, 0x66CC66)
            }
        }

        lastUpdateLabel = JLabel("").apply {
            font = font.deriveFont(10f)
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.RIGHT
        }

        toolbar.add(refreshButton, BorderLayout.WEST)
        toolbar.add(statusLabel, BorderLayout.CENTER)
        toolbar.add(lastUpdateLabel, BorderLayout.EAST)
        
        panel.add(toolbar, BorderLayout.NORTH)

        // 加载中提示
        loadingLabel = JLabel("正在加载文字转播...", SwingConstants.CENTER).apply {
            font = font.deriveFont(16f)
        }
        panel.add(loadingLabel, BorderLayout.CENTER)

        // 创建空的 scrollPane
        scrollPane = JBScrollPane().apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            isVisible = false
        }
        panel.add(scrollPane, BorderLayout.CENTER)

        // 延迟加载数据
        SwingUtilities.invokeLater {
            loadPlayByPlay()
        }

        return panel
    }

    private fun loadPlayByPlay() {
        // 未开赛的比赛不会有解说流，直接显示提示不发请求
        if (isGameScheduled) {
            showInfo("🕐 比赛尚未开始\n\n开赛前 10 分钟左右这里会提供文字直播，\n届时点击「刷新」即可获取中文解说。")
            return
        }

        SwingUtilities.invokeLater {
            loadingLabel?.isVisible = true
        }

        try {
            val result = service.getPlayByPlay(gameId, homeTeamId, awayTeamId, gameRef)
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
    }

    private fun showPlayByPlay(pbp: PlayByPlay) {
        loadingLabel?.isVisible = false

        // 创建内容面板
        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)

        // 空数据提示：未开赛/数据源无文字直播时给出说明而不是空白
        if (pbp.plays.isEmpty()) {
            content.add(JLabel("<html><div style='padding: 30px; text-align: center;'>"
                    + "该比赛暂无文字转播数据。<br>比赛临近开赛或进行中时才会有中文解说流，<br>可稍后点击刷新重试。</div></html>").apply {
                font = font.deriveFont(13f)
                foreground = JBColor.GRAY
                alignmentX = Component.CENTER_ALIGNMENT
            })
            scrollPane?.viewport?.view = content
            scrollPane?.isVisible = true
            mainPanel?.revalidate()
            mainPanel?.repaint()
            return
        }

        // 按节数分组显示
        val playsByPeriod = pbp.plays.groupBy { it.period }
        val sortedPeriods = playsByPeriod.keys.sortedDescending()

        for (period in sortedPeriods) {
            val periodPlays = playsByPeriod[period] ?: continue
            val periodDisplay = periodPlays.firstOrNull()?.periodDisplay ?: "第${period}节"

            // 节标题
            content.add(JLabel("━━━ $periodDisplay ━━━").apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = JBColor(0x0066CC, 0x4A9EFF)
                alignmentX = Component.CENTER_ALIGNMENT
                border = EmptyBorder(10, 0, 10, 0)
            })

            // 该节的所有事件
            val sortedPlays = periodPlays.sortedByDescending {
                val clockParts = it.clock.split(":")
                val minutes = clockParts.getOrNull(0)?.toIntOrNull() ?: 0
                val seconds = clockParts.getOrNull(1)?.toIntOrNull() ?: 0
                minutes * 60 + seconds
            }

            for (play in sortedPlays) {
                content.add(createPlayPanel(play))
            }
        }

        // 更新 scrollPane 内容
        scrollPane?.viewport?.view = content
        scrollPane?.isVisible = true
        loadingLabel?.isVisible = false
        
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
        val leftPanel = JPanel()
        leftPanel.layout = BoxLayout(leftPanel, BoxLayout.Y_AXIS)
        
        leftPanel.add(JLabel("[${play.clock}]").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = JBColor.GRAY
        })

        if (play.isScoringPlay) {
            leftPanel.add(JLabel("${play.awayScore} - ${play.homeScore}").apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = JBColor(0xFF6600, 0xFF9933)
            })
        }

        // 右侧：事件描述
        val rightPanel = JPanel()
        rightPanel.layout = BoxLayout(rightPanel, BoxLayout.Y_AXIS)
        
        rightPanel.add(JLabel(play.text).apply {
            font = font.deriveFont(12f)
            foreground = if (play.isScoringPlay) JBColor(0x0066CC, 0x4A9EFF) else JBColor.BLACK
        })

        rightPanel.add(JLabel(play.playType).apply {
            font = font.deriveFont(9f)
            foreground = when {
                play.isScoringPlay -> JBColor(0xFF3300, 0xFF6633)
                play.playType.contains("Rebound") -> JBColor(0x009900, 0x66CC66)
                play.playType.contains("Foul") -> JBColor(0xFF9900, 0xFFCC33)
                else -> JBColor.GRAY
            }
        })

        panel.add(leftPanel, BorderLayout.WEST)
        panel.add(rightPanel, BorderLayout.CENTER)

        return panel
    }

    private fun updateLastRefreshTime() {
        val now = java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date())
        lastUpdateLabel?.text = "最后更新: $now"
    }

    /**
     * 显示非错误的说明信息（灰色，居中）
     */
    private fun showInfo(message: String) {
        loadingLabel?.isVisible = false
        scrollPane?.isVisible = false

        mainPanel?.add(JLabel("<html><div style='padding: 20px; text-align: center;'>"
                + message.replace("\n", "<br>") + "</div></html>").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(14f)
            horizontalAlignment = SwingConstants.CENTER
        }, BorderLayout.CENTER)
        mainPanel?.revalidate()
        mainPanel?.repaint()
    }

    private fun showError(message: String) {
        loadingLabel?.isVisible = false
        scrollPane?.isVisible = false

        // 将底层报错翻译为用户可理解的提示
        val friendly = when {
            message.contains("所有数据源均失败") ->
                "暂时无法获取文字转播。<br>比赛进行中时点击「刷新」重试，<br>中文解说流通常在开赛前 10 分钟左右开放。"
            message.contains("没有建立") || message.contains("无文字直播") ->
                "该比赛暂未开放文字直播间。<br>临近开赛或进行中时点击「刷新」重试。"
            else -> message
        }
        mainPanel?.add(JLabel("<html><div style='padding: 20px; text-align: center;'>ℹ️ $friendly</div></html>").apply {
            foreground = JBColor(0xCC8800, 0xFFCC33)  // 橙色提示而非红色错误
            font = font.deriveFont(14f)
            horizontalAlignment = SwingConstants.CENTER
        }, BorderLayout.CENTER)
        mainPanel?.revalidate()
        mainPanel?.repaint()
    }
}
