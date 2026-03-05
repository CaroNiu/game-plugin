package com.caro.nba

import com.caro.nba.model.NBAGame
import com.caro.nba.model.NBAScoreboard
import com.caro.nba.service.NBADataService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import javax.swing.*

/**
 * NBA 比分面板 - 主 UI
 */
class NBAScorePanel(private val project: Project) : JPanel(BorderLayout()) {
    
    private val service = NBADataService()
    private var refreshJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 当前选择的日期
    private var selectedDate = LocalDate.now()
    
    // UI 组件
    private val dateLabel = JLabel()
    private val prevDayButton = JButton("◀")
    private val nextDayButton = JButton("▶")
    private val todayButton = JButton("今天")
    private val datePickerButton = JButton("📅")
    private val refreshButton = JButton("刷新")
    private val autoRefreshCheckBox = JCheckBox("自动刷新", false)
    private val gamesPanel = JPanel()
    private val statusLabel = JLabel("加载中...")
    
    init {
        setupUI()
        loadGames()
        setupAutoRefresh()
    }
    
    private fun setupUI() {
        // 顶部工具栏 - 日期选择
        val datePanel = JPanel(FlowLayout(FlowLayout.CENTER, 5, 2))
        prevDayButton.preferredSize = Dimension(40, 25)
        nextDayButton.preferredSize = Dimension(40, 25)
        todayButton.preferredSize = Dimension(50, 25)
        datePickerButton.preferredSize = Dimension(35, 25)
        
        datePanel.add(prevDayButton)
        datePanel.add(todayButton)
        datePanel.add(dateLabel)
        datePanel.add(datePickerButton)
        datePanel.add(nextDayButton)
        
        // 操作栏
        val actionPanel = JPanel(FlowLayout(FlowLayout.LEFT, 10, 2))
        actionPanel.add(refreshButton)
        actionPanel.add(autoRefreshCheckBox)
        actionPanel.add(Box.createHorizontalStrut(20))
        actionPanel.add(statusLabel)
        
        // 顶部容器
        val topPanel = JPanel(BorderLayout())
        topPanel.add(datePanel, BorderLayout.NORTH)
        topPanel.add(actionPanel, BorderLayout.SOUTH)
        
        // 设置按钮事件
        prevDayButton.addActionListener { changeDate(-1) }
        nextDayButton.addActionListener { changeDate(1) }
        todayButton.addActionListener { 
            selectedDate = LocalDate.now()
            updateDateLabel()
            loadGames()
        }
        datePickerButton.addActionListener { showDatePicker() }
        refreshButton.addActionListener { loadGames() }
        autoRefreshCheckBox.addActionListener { setupAutoRefresh() }
        
        // 更新日期
        updateDateLabel()
        
        // 比赛列表
        gamesPanel.layout = BoxLayout(gamesPanel, BoxLayout.Y_AXIS)
        gamesPanel.border = JBUI.Borders.empty(10)
        
        val scrollPane = JBScrollPane(gamesPanel)
        scrollPane.preferredSize = Dimension(350, 400)
        
        add(topPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        
        preferredSize = Dimension(370, 480)
    }
    
    /**
     * 更新日期标签显示
     */
    private fun updateDateLabel() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("MM月dd日 EEEE", Locale.CHINA)
        val dateStr = selectedDate.format(formatter)
        
        dateLabel.text = if (selectedDate == today) {
            "今天 ($dateStr)"
        } else if (selectedDate == today.minusDays(1) ) {
            "昨天 ($dateStr)"
        } else if (selectedDate == today.plusDays(1)) {
            "明天 ($dateStr)"
        } else {
            dateStr
        }
    }
    
    /**
     * 切换日期
     */
    private fun changeDate(delta: Int) {
        selectedDate = selectedDate.plusDays(delta.toLong())
        updateDateLabel()
        loadGames()
    }
    
    /**
     * 显示日期选择器
     */
    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        calendar.set(selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth)
        
        // 使用简单的输入对话框
        val input = JOptionPane.showInputDialog(
            this,
            "请输入日期 (格式: yyyy-MM-dd):",
            "选择日期",
            JOptionPane.PLAIN_MESSAGE,
            null,
            null,
            selectedDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        ) as? String
        
        if (input != null) {
            try {
                val newDate = LocalDate.parse(input, DateTimeFormatter.ISO_LOCAL_DATE)
                selectedDate = newDate
                updateDateLabel()
                loadGames()
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(this, "日期格式错误，请使用 yyyy-MM-dd 格式", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }
    
    /**
     * 加载比赛数据
     */
    private fun loadGames() {
        statusLabel.text = "加载中..."
        refreshButton.isEnabled = false
        
        scope.launch {
            val result = service.getGames(selectedDate)
            
            ApplicationManager.getApplication().invokeLater {
                refreshButton.isEnabled = true
                result.fold(
                    onSuccess = { scoreboard ->
                        updateGamesPanel(scoreboard)
                        statusLabel.text = "✅ 共 ${scoreboard.games.size} 场"
                    },
                    onFailure = { error ->
                        showError(error.message ?: "加载失败")
                        statusLabel.text = "❌ 加载失败"
                    }
                )
            }
        }
    }
    
    /**
     * 更新比赛列表显示
     */
    private fun updateGamesPanel(scoreboard: NBAScoreboard) {
        gamesPanel.removeAll()
        
        if (scoreboard.games.isEmpty()) {
            val emptyPanel = JPanel()
            emptyPanel.layout = BoxLayout(emptyPanel, BoxLayout.Y_AXIS)
            val emptyLabel = JLabel("该日期没有比赛安排 🏀")
            emptyLabel.alignmentX = Component.CENTER_ALIGNMENT
            emptyPanel.add(Box.createVerticalStrut(50))
            emptyPanel.add(emptyLabel)
            gamesPanel.add(emptyPanel)
        } else {
            for (game in scoreboard.games) {
                gamesPanel.add(createGameCard(game))
                gamesPanel.add(Box.createVerticalStrut(8))
            }
        }
        
        gamesPanel.revalidate()
        gamesPanel.repaint()
    }
    
    /**
     * 创建比赛卡片
     */
    private fun createGameCard(game: NBAGame): JPanel {
        val card = JPanel()
        card.layout = BorderLayout(5, 5)
        card.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor.border(), 1),
            JBUI.Borders.empty(8)
        )
        card.background = JBColor.PanelBackground
        card.maximumSize = Dimension(Int.MAX_VALUE, 100)
        
        // 左侧：比赛状态
        val statusPanel = JPanel()
        statusPanel.layout = BoxLayout(statusPanel, BoxLayout.Y_AXIS)
        statusPanel.isOpaque = false
        
        val statusLabel = JLabel(game.getStatusDisplay()).apply {
            font = font.deriveFont(Font.BOLD, 13f)
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = when (game.status) {
                "in_progress" -> JBColor(0x0066CC, 0x4A9EFF)
                "finished" -> JBColor.GRAY
                else -> JBColor(0x333333, 0xCCCCCC)
            }
        }
        
        val timeLabel = JLabel(game.getClockDisplay()).apply {
            font = font.deriveFont(11f)
            foreground = JBColor.GRAY
            alignmentX = Component.CENTER_ALIGNMENT
        }
        
        statusPanel.add(statusLabel)
        statusPanel.add(Box.createVerticalStrut(2))
        statusPanel.add(timeLabel)
        
        // 右侧：队伍和比分
        val scorePanel = JPanel(GridBagLayout())
        scorePanel.isOpaque = false
        val gbc = GridBagConstraints()
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.insets = Insets(2, 5, 2, 5)
        
        // 客队名称
        gbc.gridx = 0; gbc.gridy = 0
        gbc.weightx = 1.0
        scorePanel.add(JLabel(game.awayTeam.name).apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }, gbc)
        
        // VS
        gbc.gridx = 1
        gbc.weightx = 0.0
        scorePanel.add(JLabel("vs").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(10f)
        }, gbc)
        
        // 主队名称
        gbc.gridx = 2
        gbc.weightx = 1.0
        scorePanel.add(JLabel(game.homeTeam.name).apply {
            font = font.deriveFont(Font.BOLD, 12f)
        }, gbc)
        
        // 客队比分
        gbc.gridx = 0; gbc.gridy = 1
        val awayScoreLabel = JLabel(if (game.status == "scheduled") "-" else game.awayScore.toString()).apply {
            font = font.deriveFont(Font.BOLD, 22f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (game.awayScore > game.homeScore && game.status != "scheduled") {
                JBColor(0x0066CC, 0x4A9EFF)
            } else {
                JBColor.BLACK
            }
        }
        scorePanel.add(awayScoreLabel, gbc)
        
        // 中间空白
        gbc.gridx = 1
        scorePanel.add(Box.createHorizontalStrut(20), gbc)
        
        // 主队比分
        gbc.gridx = 2
        val homeScoreLabel = JLabel(if (game.status == "scheduled") "-" else game.homeScore.toString()).apply {
            font = font.deriveFont(Font.BOLD, 22f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (game.homeScore > game.awayScore && game.status != "scheduled") {
                JBColor(0x0066CC, 0x4A9EFF)
            } else {
                JBColor.BLACK
            }
        }
        scorePanel.add(homeScoreLabel, gbc)
        
        card.add(statusPanel, BorderLayout.WEST)
        card.add(scorePanel, BorderLayout.CENTER)
        
        return card
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        gamesPanel.removeAll()
        val errorLabel = JLabel("❌ $message")
        errorLabel.foreground = JBColor.RED
        errorLabel.alignmentX = Component.CENTER_ALIGNMENT
        gamesPanel.add(errorLabel)
        gamesPanel.revalidate()
        gamesPanel.repaint()
    }
    
    /**
     * 设置自动刷新
     */
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