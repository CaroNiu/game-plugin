package com.caro.nba

import com.caro.nba.model.NBAGame
import com.caro.nba.model.NBAScoreboard
import com.caro.nba.service.NBADataService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.swing.*

/**
 * NBA 比分面板 - 主 UI
 */
class NBAScorePanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = NBADataService()
    private var refreshJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // UI 组件
    private val dateLabel = JLabel()
    private val refreshButton = JButton("🔄 刷新")
    private val autoRefreshCheckBox = JCheckBox("自动刷新(60秒)", false)
    private val gamesPanel = JPanel()
    private val statusLabel = JLabel("加载中...")
    
    init {
        setupUI()
        loadGames()
        setupAutoRefresh()
    }
    
    private fun setupUI() {
        // 顶部工具栏
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT))
        toolbar.add(dateLabel)
        toolbar.add(Box.createHorizontalStrut(20))
        toolbar.add(refreshButton)
        toolbar.add(autoRefreshCheckBox)
        toolbar.add(Box.createHorizontalStrut(20))
        toolbar.add(statusLabel)
        
        // 设置按钮事件
        refreshButton.addActionListener { loadGames() }
        autoRefreshCheckBox.addActionListener { setupAutoRefresh() }
        
        // 更新日期
        updateDateLabel()
        
        // 比赛列表
        gamesPanel.layout = BoxLayout(gamesPanel, BoxLayout.Y_AXIS)
        gamesPanel.border = JBUI.Borders.empty(10)
        
        val scrollPane = JBScrollPane(gamesPanel)
        scrollPane.preferredSize = Dimension(300, 400)
        
        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        
        preferredSize = Dimension(320, 450)
    }
    
    private fun updateDateLabel() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd EEEE")
        dateLabel.text = "📅 ${today.format(formatter)}"
    }
    
    private fun loadGames() {
        statusLabel.text = "加载中..."
        
        scope.launch {
            val result = service.getTodayGames()
            
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { scoreboard ->
                        updateGamesPanel(scoreboard)
                        statusLabel.text = "✅ 已更新"
                    },
                    onFailure = { error ->
                        showError(error.message ?: "加载失败")
                        statusLabel.text = "❌ 加载失败"
                    }
                )
            }
        }
    }
    
    private fun updateGamesPanel(scoreboard: NBAScoreboard) {
        gamesPanel.removeAll()
        
        if (scoreboard.games.isEmpty()) {
            val emptyLabel = JLabel("今天没有比赛 😢")
            emptyLabel.alignmentX = Component.CENTER_ALIGNMENT
            gamesPanel.add(emptyLabel)
        } else {
            for (game in scoreboard.games) {
                gamesPanel.add(createGameCard(game))
                gamesPanel.add(Box.createVerticalStrut(10))
            }
        }
        
        gamesPanel.revalidate()
        gamesPanel.repaint()
    }
    
    private fun createGameCard(game: NBAGame): JPanel {
        val card = JPanel()
        card.layout = BorderLayout(10, 0)
        card.border = JBUI.Borders.empty(10)
        card.background = JBColor.PanelBackground
        
        // 比赛状态
        val statusPanel = JPanel(GridLayout(2, 1))
        val statusText = JLabel(game.getStatusDisplay()).apply {
            font = font.deriveFont(Font.BOLD, 12f)
            horizontalAlignment = SwingConstants.CENTER
        }
        val timeText = JLabel(game.startTime.take(16).replace("T", " ")).apply {
            font = font.deriveFont(10f)
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.CENTER
        }
        statusPanel.add(statusText)
        statusPanel.add(timeText)
        
        // 比分
        val scorePanel = JPanel(GridLayout(2, 3, 10, 5))
        
        // 客队
        scorePanel.add(JLabel(game.awayTeam.abbreviation).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            horizontalAlignment = SwingConstants.CENTER
        })
        scorePanel.add(JLabel("").apply { horizontalAlignment = SwingConstants.CENTER })
        
        // 主队
        scorePanel.add(JLabel(game.homeTeam.abbreviation).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            horizontalAlignment = SwingConstants.CENTER
        })
        
        // 比分
        val awayScoreLabel = JLabel(game.awayScore.toString()).apply {
            font = font.deriveFont(Font.BOLD, 20f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (game.awayScore > game.homeScore) JBColor.BLUE else JBColor.BLACK
        }
        val vsLabel = JLabel("vs").apply {
            horizontalAlignment = SwingConstants.CENTER
            foreground = JBColor.GRAY
        }
        val homeScoreLabel = JLabel(game.homeScore.toString()).apply {
            font = font.deriveFont(Font.BOLD, 20f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (game.homeScore > game.awayScore) JBColor.BLUE else JBColor.BLACK
        }
        
        scorePanel.add(awayScoreLabel)
        scorePanel.add(vsLabel)
        scorePanel.add(homeScoreLabel)
        
        card.add(statusPanel, BorderLayout.NORTH)
        card.add(scorePanel, BorderLayout.CENTER)
        
        // 添加边框
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border(), 1),
            JBUI.Borders.empty(5)
        )
        
        return card
    }
    
    private fun showError(message: String) {
        gamesPanel.removeAll()
        val errorLabel = JLabel("❌ $message")
        errorLabel.foreground = JBColor.RED
        errorLabel.alignmentX = Component.CENTER_ALIGNMENT
        gamesPanel.add(errorLabel)
        gamesPanel.revalidate()
        gamesPanel.repaint()
    }
    
    private fun setupAutoRefresh() {
        refreshJob?.cancel()
        
        if (autoRefreshCheckBox.isSelected) {
            refreshJob = scope.launch {
                while (isActive) {
                    delay(60_000) // 60秒刷新一次
                    loadGames()
                }
            }
        }
    }
    
    fun dispose() {
        refreshJob?.cancel()
        scope.cancel()
    }
}