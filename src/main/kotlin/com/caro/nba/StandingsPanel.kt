package com.caro.nba

import com.caro.nba.model.NBAStandings
import com.caro.nba.model.RankStatus
import com.caro.nba.model.TeamStanding
import com.caro.nba.service.StandingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.*
import java.awt.*
import java.net.URL
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * NBA 排名面板 - 重新设计的整洁版本
 */
class StandingsPanel(
    private val project: Project,
    private val onPlayoffClick: (() -> Unit)? = null
) : JPanel(BorderLayout()) {

    private val service = StandingsService()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // UI 组件
    private val refreshButton = JButton("刷新")
    private val playoffButton = JButton("季后赛")
    private val statusLabel = JLabel("准备就绪")

    // 标签页切换
    private val tabbedPane = JTabbedPane(JTabbedPane.TOP)

    // Logo 缓存
    private val logoCache = mutableMapOf<String, ImageIcon>()

    var currentStandings: NBAStandings? = null
        private set
    private var dataLoaded = false

    init {
        setupUI()
    }

    /**
     * 当面板首次显示时加载数据
     */
    fun loadDataIfNeeded() {
        if (!dataLoaded) {
            dataLoaded = true
            loadStandings()
        }
    }

    private fun setupUI() {
        background = JBColor.background()

        // 顶部工具栏 - 使用 BorderLayout 替代 FlowLayout 以获得更好的间距控制
        val toolBar = JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = EmptyBorder(10, 12, 10, 12)
            preferredSize = Dimension(Int.MAX_VALUE, 55)
        }

        // 左侧按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 12, 0)).apply {
            background = JBColor.background()
            isOpaque = false

            // 设置按钮尺寸
            refreshButton.preferredSize = Dimension(100, 32)
            playoffButton.preferredSize = Dimension(100, 32)

            add(refreshButton)
            add(Box.createHorizontalStrut(15))
            add(playoffButton)
        }

        // 右侧状态标签
        val statusPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0)).apply {
            background = JBColor.background()
            isOpaque = false
            add(statusLabel)
        }

        toolBar.add(buttonPanel, BorderLayout.WEST)
        toolBar.add(statusPanel, BorderLayout.EAST)

        refreshButton.addActionListener { loadStandings() }
        playoffButton.addActionListener { onPlayoffClick?.invoke() }

        // 标签页 - 东西部分开
        tabbedPane.apply {
            font = font.deriveFont(Font.BOLD, 14f)
            background = JBColor.background()
            border = EmptyBorder(0, 0, 0, 0)
        }

        add(toolBar, BorderLayout.NORTH)
        add(tabbedPane, BorderLayout.CENTER)

        preferredSize = Dimension(800, 700)
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
                        updateStandingsUI(standings)
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
     * 更新排名UI - 创建东西部分区标签页
     */
    private fun updateStandingsUI(standings: NBAStandings) {
        currentStandings = standings
        tabbedPane.removeAll()

        // 西部排名标签页
        val westPanel = createConferenceStandingsPanel("西部", standings.western.teams)
        tabbedPane.addTab("西部", westPanel)

        // 东部排名标签页
        val eastPanel = createConferenceStandingsPanel("东部", standings.eastern.teams)
        tabbedPane.addTab("东部", eastPanel)
    }

    /**
     * 创建单个分区的排名面板
     */
    private fun createConferenceStandingsPanel(
        conferenceName: String,
        teams: List<TeamStanding>
    ): JComponent {
        val panel = JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = EmptyBorder(15, 15, 15, 15)
        }

        // 主内容面板 - 使用 BoxLayout 垂直排列球队
        val contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = JBColor.background()
        }

        // 季后赛区标签（前6名）
        if (teams.any { it.conferenceRank in 1..6 }) {
            contentPanel.add(createSectionLabel("季后赛区"))
            val playoffTeams = teams.filter { it.conferenceRank in 1..6 }.sortedBy { it.conferenceRank }
            playoffTeams.forEachIndexed { index, team ->
                contentPanel.add(createTeamRow(team, showBackground = index % 2 == 0))
            }
            contentPanel.add(Box.createVerticalStrut(10))
        }

        // 附加赛区标签（7-10名）
        if (teams.any { it.conferenceRank in 7..10 }) {
            contentPanel.add(createSectionLabel("附加赛区"))
            val playInTeams = teams.filter { it.conferenceRank in 7..10 }.sortedBy { it.conferenceRank }
            playInTeams.forEachIndexed { index, team ->
                contentPanel.add(createTeamRow(team, showBackground = index % 2 == 0))
            }
            contentPanel.add(Box.createVerticalStrut(10))
        }

        // 淘汰区标签（11名以后）
        val eliminatedTeams = teams.filter { it.conferenceRank >= 11 }.sortedBy { it.conferenceRank }
        if (eliminatedTeams.isNotEmpty()) {
            contentPanel.add(createSectionLabel(""))
            eliminatedTeams.forEachIndexed { index, team ->
                contentPanel.add(createTeamRow(team, showBackground = index % 2 == 0, isEliminated = true))
            }
        }

        // 添加滚动
        val scrollPane = JBScrollPane(contentPanel).apply {
            border = null
            viewport.background = JBColor.background()
            verticalScrollBar.unitIncrement = 16
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }

    /**
     * 创建分区标签（季后赛区/附加赛区）
     */
    private fun createSectionLabel(text: String): JComponent {
        return JPanel(BorderLayout()).apply {
            background = JBColor.background()
            border = EmptyBorder(12, 0, 6, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 42)

            if (text.isNotEmpty()) {
                val label = JLabel(text).apply {
                    font = font.deriveFont(Font.BOLD, 13f)
                    foreground = when (text) {
                        "季后赛区" -> JBColor(0x007A33, 0x2D8F44)
                        "附加赛区" -> JBColor(0xC90C2E, 0xE03A3E)
                        else -> JBColor.GRAY
                    }
                    border = EmptyBorder(4, 12, 4, 0)
                }
                add(label, BorderLayout.WEST)

                // 背景条
                background = when (text) {
                    "季后赛区" -> JBColor(0xE8F5E9, 0x1B3D1B)
                    "附加赛区" -> JBColor(0xFFEBEE, 0x3D1B1B)
                    else -> JBColor.background()
                }
            }
        }
    }

    /**
     * 创建球队行
     */
    private fun createTeamRow(
        team: TeamStanding,
        showBackground: Boolean,
        isEliminated: Boolean = false
    ): JComponent {
        val rowPanel = JPanel(BorderLayout()).apply {
            maximumSize = Dimension(Int.MAX_VALUE, 56)
            preferredSize = Dimension(700, 56)
            border = EmptyBorder(8, 12, 8, 12)

            background = when {
                showBackground -> JBColor(0xF8F9FA, 0x2D2D2D)
                else -> JBColor.background()
            }
        }

        // 左边：排名、Logo、队名
        val leftPanel = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
        }

        // 排名徽章
        val rankBadge = createRankBadge(team.conferenceRank, team.getRankStatus())
        leftPanel.add(rankBadge, BorderLayout.WEST)

        // 球队信息（Logo + 名称）
        val teamInfoPanel = JPanel(BorderLayout(8, 0)).apply {
            isOpaque = false
        }

        // Logo 标签
        val logoLabel = JLabel().apply {
            preferredSize = Dimension(32, 32)
            horizontalAlignment = SwingConstants.CENTER
            // 异步加载 logo
            loadTeamLogo(team.logo, this)
        }
        teamInfoPanel.add(logoLabel, BorderLayout.WEST)

        // 队名
        val teamNameLabel = JLabel().apply {
            val clincherMark = if (team.clincher.isNotEmpty()) " *" else ""
            text = "${team.teamName}$clincherMark"
            font = font.deriveFont(Font.BOLD, 13f)
            foreground = if (isEliminated) JBColor.GRAY else JBColor.foreground()
        }
        teamInfoPanel.add(teamNameLabel, BorderLayout.CENTER)

        leftPanel.add(teamInfoPanel, BorderLayout.CENTER)

        // 右边：战绩和胜率
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 15, 0)).apply {
            isOpaque = false
        }

        // 胜负记录
        val recordLabel = JLabel("${team.wins}/${team.losses}").apply {
            font = font.deriveFont(Font.BOLD, 13f)
            foreground = if (isEliminated) JBColor.GRAY else JBColor.foreground()
        }

        // 胜率
        val winPercentLabel = JLabel(team.getWinPercentDisplay().removePrefix("0.")).apply {
            font = font.deriveFont(13f)
            foreground = JBColor.GRAY
        }

        rightPanel.add(recordLabel)
        rightPanel.add(winPercentLabel)

        rowPanel.add(leftPanel, BorderLayout.WEST)
        rowPanel.add(rightPanel, BorderLayout.EAST)

        return rowPanel
    }

    /**
     * 创建排名徽章
     */
    private fun createRankBadge(rank: Int, status: RankStatus): JComponent {
        val badgePanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(36, 36)
            isOpaque = false
        }

        val badgeLabel = JLabel(rank.toString()).apply {
            preferredSize = Dimension(36, 36)
            minimumSize = Dimension(36, 36)
            horizontalAlignment = SwingConstants.CENTER
            verticalAlignment = SwingConstants.CENTER
            font = font.deriveFont(Font.BOLD, 14f)

            // 设置背景和前景色
            foreground = Color.WHITE
            background = when {
                rank == 1 -> JBColor(0xE03A3E, 0xC90C2E)    // 红色 - 第1名
                rank == 2 -> JBColor(0xF39C12, 0xE67E22)    // 橙色 - 第2名
                rank == 3 -> JBColor(0xF1C40F, 0xF39C12)    // 黄色 - 第3名
                status == RankStatus.PLAYOFF_CLINCHED -> JBColor(0x007A33, 0x2D8F44)
                status == RankStatus.PLAY_IN -> JBColor(0xE67E22, 0xD35400)
                else -> JBColor.GRAY
            }
            isOpaque = true
            border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
        }

        badgePanel.add(badgeLabel, BorderLayout.CENTER)
        return badgePanel
    }

    /**
     * 异步加载球队Logo
     */
    private fun loadTeamLogo(logoUrl: String, label: JLabel) {
        if (logoUrl.isBlank()) {
            // 显示默认占位符
            label.text = teamAbbreviationToEmoji(label.parent?.parent?.name ?: "")
            return
        }

        // 检查缓存
        logoCache[logoUrl]?.let {
            label.icon = it
            label.text = ""
            return
        }

        // 异步加载
        scope.launch(Dispatchers.IO) {
            try {
                val url = URL(logoUrl)
                val image = ImageIO.read(url)
                if (image != null) {
                    val scaled = image.getScaledInstance(28, 28, Image.SCALE_SMOOTH)
                    val icon = ImageIcon(scaled)
                    logoCache[logoUrl] = icon

                    ApplicationManager.getApplication().invokeLater {
                        label.icon = icon
                        label.text = ""
                    }
                }
            } catch (e: Exception) {
                // 加载失败时不做处理
            }
        }
    }

    /**
     * 球队缩写转Emoji占位符
     */
    private fun teamAbbreviationToEmoji(abbrev: String): String {
        return "🏀"
    }

    /**
     * 显示错误
     */
    private fun showError(message: String) {
        tabbedPane.removeAll()
        val errorLabel = JLabel("❌ $message").apply {
            horizontalAlignment = SwingConstants.CENTER
            font = font.deriveFont(14f)
            border = EmptyBorder(50, 0, 0, 0)
        }
        tabbedPane.addTab("错误", JPanel(BorderLayout()).apply { add(errorLabel, BorderLayout.CENTER) })
    }

    fun refresh() {
        loadStandings()
    }

    fun dispose() {
        scope.cancel()
    }
}
