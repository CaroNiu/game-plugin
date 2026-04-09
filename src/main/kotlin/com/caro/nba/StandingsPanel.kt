package com.caro.nba

import com.caro.nba.model.NBAStandings
import com.caro.nba.model.RankStatus
import com.caro.nba.model.TeamStanding
import com.caro.nba.service.StandingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * NBA 排名面板 - 东西部排名展示 + 季后赛对阵图
 */
class StandingsPanel(
    private val project: Project,
    private val onPlayoffClick: (() -> Unit)? = null
) : JPanel(BorderLayout()) {
    
    private val service = StandingsService()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // UI 组件
    private val refreshButton = JButton("刷新")
    private val standingsButton = JButton("排名")
    private val playoffButton = JButton("季后赛")
    private val statusLabel = JLabel("加载中...")
    
    private val contentPanel = JPanel(CardLayout())
    private val standingsPanel = JPanel()
    private val playoffPanel = PlayoffBracketPanel()
    
    private var currentStandings: NBAStandings? = null
    
    init {
        setupUI()
        loadStandings()
    }
    
    private fun setupUI() {
        // 顶部工具栏
        val toolBar = JPanel(FlowLayout(FlowLayout.LEFT, 10, 5))
        toolBar.add(refreshButton)
        toolBar.add(Box.createHorizontalStrut(20))
        toolBar.add(standingsButton)
        toolBar.add(playoffButton)
        toolBar.add(Box.createHorizontalStrut(20))
        toolBar.add(statusLabel)
        
        refreshButton.addActionListener { loadStandings() }
        standingsButton.addActionListener { showStandings() }
        playoffButton.addActionListener { showPlayoff() }
        
        // 排名内容区域
        standingsPanel.layout = BoxLayout(standingsPanel, BoxLayout.Y_AXIS)
        standingsPanel.border = JBUI.Borders.empty(10)
        
        val scrollPane = JBScrollPane(standingsPanel)
        scrollPane.preferredSize = Dimension(700, 500)
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        
        // 内容面板 - 卡片布局
        contentPanel.add(scrollPane, "standings")
        contentPanel.add(playoffPanel, "playoff")
        
        add(toolBar, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)
        
        preferredSize = Dimension(720, 550)
        
        // 默认显示排名
        standingsButton.isEnabled = false
    }
    
    /**
     * 显示排名视图
     */
    private fun showStandings() {
        (contentPanel.layout as CardLayout).show(contentPanel, "standings")
        standingsButton.isEnabled = false
        playoffButton.isEnabled = true
    }
    
    /**
     * 显示季后赛对阵图
     */
    private fun showPlayoff() {
        // 如果有回调，调用回调切换标签页
        onPlayoffClick?.invoke()
        
        // 更新季后赛数据
        currentStandings?.let { standings ->
            playoffPanel.updatePlayoffData(standings.eastern.teams, standings.western.teams)
        }
        
        // 如果没有回调，使用原来的 CardLayout 切换
        if (onPlayoffClick == null) {
            (contentPanel.layout as CardLayout).show(contentPanel, "playoff")
            playoffButton.isEnabled = false
            standingsButton.isEnabled = true
        }
    }
    
    /**
     * 加载排名数据
     */
    private fun loadStandings() {
        statusLabel.text = "加载中..."
        refreshButton.isEnabled = false
        
        scope.launch {
            val result = service.getStandings()
            
            ApplicationManager.getApplication().invokeLater {
                refreshButton.isEnabled = true
                result.fold(
                    onSuccess = { standings ->
                        currentStandings = standings
                        updateStandingsPanel(standings)
                        statusLabel.text = "✅ 更新于 ${standings.lastUpdated}"
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
     * 更新排名面板
     */
    private fun updateStandingsPanel(standings: NBAStandings) {
        standingsPanel.removeAll()
        
        // 图例说明
        addLegend()
        
        // 检查是否有数据
        if (standings.eastern.teams.isEmpty() && standings.western.teams.isEmpty()) {
            val noDataLabel = JLabel("⚠️ 暂无数据，请检查网络连接").apply {
                alignmentX = Component.CENTER_ALIGNMENT
                font = font.deriveFont(14f)
            }
            standingsPanel.add(noDataLabel)
            standingsPanel.revalidate()
            standingsPanel.repaint()
            return
        }
        
        // 东西部并排显示
        val contentPanel = JPanel(GridLayout(1, 2, 20, 0))
        contentPanel.isOpaque = false
        
        // 东部排名
        if (standings.eastern.teams.isNotEmpty()) {
            contentPanel.add(createConferencePanel("东部排名", standings.eastern.teams))
        } else {
            contentPanel.add(createEmptyPanel("东部排名"))
        }
        
        // 西部排名
        if (standings.western.teams.isNotEmpty()) {
            contentPanel.add(createConferencePanel("西部排名", standings.western.teams))
        } else {
            contentPanel.add(createEmptyPanel("西部排名"))
        }
        
        standingsPanel.add(contentPanel)
        standingsPanel.revalidate()
        standingsPanel.repaint()
    }
    
    /**
     * 创建空面板（无数据时显示）
     */
    private fun createEmptyPanel(title: String): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            title
        )
        val label = JLabel("暂无数据").apply {
            horizontalAlignment = SwingConstants.CENTER
        }
        panel.add(label, BorderLayout.CENTER)
        panel.preferredSize = Dimension(340, 200)
        return panel
    }
    
    /**
     * 添加图例说明
     */
    private fun addLegend() {
        val legendPanel = JPanel(FlowLayout(FlowLayout.CENTER, 15, 5))
        legendPanel.border = EmptyBorder(5, 0, 10, 0)
        legendPanel.isOpaque = false
        
        legendPanel.add(createLegendItem("季后赛区", JBColor(0x4CAF50, 0x4CAF50)))
        legendPanel.add(createLegendItem("附加赛区", JBColor(0xFF9800, 0xFF9800)))
        legendPanel.add(createLegendItem("已淘汰", JBColor.GRAY))
        
        standingsPanel.add(legendPanel)
    }
    
    private fun createLegendItem(text: String, color: Color): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0))
        panel.isOpaque = false
        val dot = JLabel("●").apply {
            foreground = color
            font = font.deriveFont(Font.BOLD, 14f)
        }
        val label = JLabel(text).apply {
            font = font.deriveFont(11f)
        }
        panel.add(dot)
        panel.add(label)
        return panel
    }
    
    /**
     * 创建分区排名面板
     */
    private fun createConferencePanel(title: String, teams: List<TeamStanding>): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(JBColor.border()),
            title
        )
        
        // 表格列：排名、球队、胜、负、胜率、落后、近10场、连胜
        val columnNames = arrayOf("排名", "球队", "胜", "负", "胜率", "落后", "近10场", "连胜")
        val model = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }
        
        teams.forEach { team ->
            model.addRow(arrayOf(
                team.conferenceRank,
                team.teamName,
                team.wins,
                team.losses,
                team.getWinPercentDisplay(),
                team.gamesBehind,
                team.last10,
                team.streak
            ))
        }
        
        val table = JTable(model).apply {
            rowHeight = 28
            font = font.deriveFont(12f)
            setGridColor(JBColor.border())
            tableHeader.reorderingAllowed = false
            tableHeader.resizingAllowed = true
            
            // 设置列宽
            columnModel.getColumn(0).preferredWidth = 35   // 排名
            columnModel.getColumn(1).preferredWidth = 80   // 球队
            columnModel.getColumn(2).preferredWidth = 40   // 胜
            columnModel.getColumn(3).preferredWidth = 40   // 负
            columnModel.getColumn(4).preferredWidth = 50   // 胜率
            columnModel.getColumn(5).preferredWidth = 45   // 落后
            columnModel.getColumn(6).preferredWidth = 55   // 近10场
            columnModel.getColumn(7).preferredWidth = 50   // 连胜
            
            // 自定义行渲染器（颜色标识）
            val centerRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    horizontalAlignment = SwingConstants.CENTER
                    
                    // 根据排名状态设置背景色
                    val team = teams.getOrNull(row)
                    if (team != null && !isSelected) {
                        background = getRowBackgroundColor(team)
                    }
                    
                    return comp
                }
            }
            
            // 球队名称左对齐
            val teamRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    val team = teams.getOrNull(row)
                    
                    // 根据季后赛锁定状态显示标记
                    val displayText = if (team != null) {
                        val status = team.getRankStatus()
                        val marker = if (status == RankStatus.PLAYOFF_CLINCHED) " ✓" else ""
                        "${team.teamName}$marker"
                    } else {
                        value?.toString() ?: ""
                    }
                    
                    val comp = super.getTableCellRendererComponent(table, displayText, isSelected, hasFocus, row, column)
                    horizontalAlignment = SwingConstants.LEFT
                    
                    // 根据排名状态设置背景色
                    if (team != null && !isSelected) {
                        background = getRowBackgroundColor(team)
                    }
                    
                    // 季后赛已锁定的球队加粗
                    if (team != null) {
                        font = if (team.getRankStatus() == RankStatus.PLAYOFF_CLINCHED) {
                            font.deriveFont(Font.BOLD, 12f)
                        } else {
                            font.deriveFont(Font.PLAIN, 12f)
                        }
                    }
                    
                    return comp
                }
            }
            
            columnModel.getColumn(0).cellRenderer = centerRenderer
            columnModel.getColumn(1).cellRenderer = teamRenderer
            for (i in 2 until columnCount) {
                columnModel.getColumn(i).cellRenderer = centerRenderer
            }
        }
        
        val scrollPane = JScrollPane(table).apply {
            preferredSize = Dimension(340, teams.size * 28 + 30)
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        panel.add(scrollPane, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 根据排名状态获取行背景色
     */
    private fun getRowBackgroundColor(team: TeamStanding): Color {
        return when (team.getRankStatus()) {
            RankStatus.PLAYOFF_CLINCHED -> JBColor(0xC8E6C9, 0x1B3D1B) // 已锁定季后赛
            RankStatus.PLAYOFF_SPOT -> JBColor(0xE8F5E9, 0x1B3D1B)     // 季后赛区
            RankStatus.PLAY_IN -> JBColor(0xFFF3E0, 0x3D2D1B)          // 附加赛区
            RankStatus.OUT -> JBColor(0xF5F5F5, 0x2D2D2D)              // 已淘汰
        }
    }
    
    /**
     * 显示错误信息
     */
    private fun showError(message: String) {
        standingsPanel.removeAll()
        val errorLabel = JLabel("❌ $message").apply {
            foreground = JBColor.RED
            alignmentX = Component.CENTER_ALIGNMENT
        }
        standingsPanel.add(errorLabel)
        standingsPanel.revalidate()
        standingsPanel.repaint()
    }
    
    fun refresh() {
        loadStandings()
    }
    
    fun dispose() {
        scope.cancel()
    }
}
