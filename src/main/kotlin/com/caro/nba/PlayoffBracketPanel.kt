package com.caro.nba

import com.caro.nba.model.TeamStanding
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * 季后赛对阵图面板 - 真实数据版本
 * 显示东西部季后赛对阵：附加赛 → 首轮 → 半决赛 → 分区决赛 → 总决赛
 */
class PlayoffBracketPanel : JPanel(BorderLayout()) {
    
    private var easternTeams = listOf<TeamStanding>()
    private var westernTeams = listOf<TeamStanding>()
    
    // 存储各轮次的对阵面板引用，用于数据更新
    private var easternFirstRound: JPanel? = null
    private var easternSemiFinal: JPanel? = null
    private var easternFinal: JPanel? = null
    private var westernFirstRound: JPanel? = null
    private var westernSemiFinal: JPanel? = null
    private var westernFinal: JPanel? = null
    private var easternPlayIn: JPanel? = null
    private var westernPlayIn: JPanel? = null
    
    // 总决赛球队
    private var easternChampion: JPanel? = null
    private var westernChampion: JPanel? = null
    private var finalsWinner: JPanel? = null
    
    private val teamColors = mapOf(
        "DET" to Color(0xC8102E), "BOS" to Color(0x007A33), "NY" to Color(0x006BB6), "CLE" to Color(0x860038),
        "TOR" to Color(0xCE1141), "ORL" to Color(0x0077C0), "MIA" to Color(0x98002E), "PHI" to Color(0x006BB6),
        "ATL" to Color(0xE03A3E), "CHA" to Color(0x1D1160), "MIL" to Color(0x00471B), "CHI" to Color(0xCE1141),
        "BKN" to Color(0x000000), "WSH" to Color(0x002B5C), "IND" to Color(0x002D62),
        "OKC" to Color(0x007AC1), "SAS" to Color(0xC4CED4), "MIN" to Color(0x0C2340), "HOU" to Color(0xCE1141),
        "LAL" to Color(0x552583), "DEN" to Color(0x0E2240), "PHX" to Color(0x1D1160), "GS" to Color(0x1D428A),
        "LAC" to Color(0xC8102E), "POR" to Color(0xE03A3E), "MEM" to Color(0x5D76A9),
        "DAL" to Color(0x00538C), "NO" to Color(0x0C2340), "UTAH" to Color(0x002B5C), "SAC" to Color(0x5A2D81)
    )
    
    private val teamNameMap = mapOf(
        "DET" to "活塞", "BOS" to "凯尔特人", "NY" to "尼克斯", "CLE" to "骑士",
        "TOR" to "猛龙", "ORL" to "魔术", "MIA" to "热火", "PHI" to "76人",
        "ATL" to "老鹰", "CHA" to "黄蜂", "MIL" to "雄鹿", "CHI" to "公牛",
        "BKN" to "篮网", "WSH" to "奇才", "IND" to "步行者",
        "OKC" to "雷霆", "SAS" to "马刺", "MIN" to "森林狼", "HOU" to "火箭",
        "LAL" to "湖人", "DEN" to "掘金", "PHX" to "太阳", "GS" to "勇士",
        "LAC" to "快船", "POR" to "开拓者", "MEM" to "灰熊",
        "DAL" to "独行侠", "NO" to "鹈鹕", "UTAH" to "爵士", "SAC" to "国王"
    )
    
    init {
        setupUI()
    }
    
    private fun setupUI() {
        background = JBColor(0xF5F5F5, 0x1E1E1E)
        border = JBUI.Borders.empty(15)
        
        val mainPanel = JPanel(GridLayout(1, 3, 20, 0))
        mainPanel.isOpaque = false
        
        // 东部对阵图（正常顺序）
        mainPanel.add(createConferenceBracket("🏀 东部", reverse = false, isEastern = true))
        
        // 总决赛区域
        mainPanel.add(createFinalsPanel())
        
        // 西部对阵图（反转顺序，使决赛朝向总决赛）
        mainPanel.add(createConferenceBracket("🏀 西部", reverse = true, isEastern = false))
        
        add(mainPanel, BorderLayout.CENTER)
        
        // 顶部标题
        val titleLabel = JPanel(FlowLayout(FlowLayout.CENTER)).apply {
            isOpaque = false
            add(JLabel("NBA 季后赛对阵图").apply {
                font = font.deriveFont(Font.BOLD, 20f)
                foreground = JBColor(0x1a1a1a, 0xE0E0E0)
            })
        }
        add(titleLabel, BorderLayout.NORTH)
        
        preferredSize = Dimension(1100, 550)
    }
    
    /**
     * 创建分区对阵图
     */
    private fun createConferenceBracket(title: String, reverse: Boolean = false, isEastern: Boolean): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        
        // 标题
        val titleLabel = JLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            horizontalAlignment = SwingConstants.CENTER
            border = EmptyBorder(5, 0, 10, 0)
            foreground = JBColor(0x333333, 0xCCCCCC)
        }
        panel.add(titleLabel, BorderLayout.NORTH)
        
        // 对阵区域
        val bracketPanel = JPanel()
        bracketPanel.layout = BoxLayout(bracketPanel, BoxLayout.X_AXIS)
        bracketPanel.isOpaque = false
        
        // 附加赛（7-10名）
        val playIn = createPlayInColumn(if (isEastern) easternTeams else westernTeams)
        if (isEastern) easternPlayIn = playIn else westernPlayIn = playIn
        
        // 首轮
        val firstRound = createFirstRoundColumn(if (isEastern) easternTeams else westernTeams)
        if (isEastern) easternFirstRound = firstRound else westernFirstRound = firstRound
        
        // 半决赛
        val semiFinal = createSemiFinalColumn()
        if (isEastern) easternSemiFinal = semiFinal else westernSemiFinal = semiFinal
        
        // 决赛
        val conferenceFinal = createConferenceFinalColumn()
        if (isEastern) easternFinal = conferenceFinal else westernFinal = conferenceFinal
        
        // 根据是否反转决定列顺序
        if (reverse) {
            bracketPanel.add(conferenceFinal)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(semiFinal)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(firstRound)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(playIn)
        } else {
            bracketPanel.add(playIn)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(firstRound)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(semiFinal)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(conferenceFinal)
        }
        
        panel.add(bracketPanel, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * 创建附加赛列
     */
    private fun createPlayInColumn(teams: List<TeamStanding>): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        
        // 标题
        panel.add(JLabel("附加赛").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = JBColor(0xFF9800, 0xFF9800)
        })
        panel.add(Box.createVerticalStrut(5))
        
        // 附加赛对阵：7vs8，9vs10
        val playInTeams = teams.filter { it.conferenceRank in 7..10 }.sortedBy { it.conferenceRank }
        
        if (playInTeams.size >= 4) {
            // 第7 vs 第8
            panel.add(createMatchupCard(playInTeams[0], playInTeams[1]))
            panel.add(Box.createVerticalStrut(15))
            // 第9 vs 第10
            panel.add(createMatchupCard(playInTeams[2], playInTeams[3]))
        } else if (playInTeams.size >= 2) {
            panel.add(createMatchupCard(playInTeams[0], playInTeams[1]))
        } else {
            // 占位
            panel.add(createPlaceholderCard("待确定"))
        }
        
        return panel
    }
    
    /**
     * 创建首轮列
     */
    private fun createFirstRoundColumn(teams: List<TeamStanding>): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        
        panel.add(JLabel("首轮").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = JBColor(0x666666, 0x999999)
        })
        panel.add(Box.createVerticalStrut(5))
        
        // 获取排名前8的球队
        val playoffTeams = teams.filter { it.conferenceRank in 1..8 }.sortedBy { it.conferenceRank }
        
        if (playoffTeams.size >= 8) {
            // 4组首轮对决
            for (i in 0 until 8 step 2) {
                panel.add(createMatchupCard(playoffTeams[i], playoffTeams[i + 1]))
                if (i < 6) panel.add(Box.createVerticalStrut(8))
            }
        } else {
            // 静态占位
            panel.add(createPlaceholderCard("等待确定"))
        }
        
        return panel
    }
    
    /**
     * 创建半决赛列
     */
    private fun createSemiFinalColumn(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        
        panel.add(JLabel("半决赛").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = JBColor(0x666666, 0x999999)
        })
        panel.add(Box.createVerticalStrut(40))
        
        panel.add(createPlaceholderCard(""))
        panel.add(Box.createVerticalStrut(60))
        panel.add(createPlaceholderCard(""))
        
        return panel
    }
    
    /**
     * 创建分区决赛列
     */
    private fun createConferenceFinalColumn(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        
        panel.add(JLabel("决赛").apply {
            font = font.deriveFont(Font.BOLD, 11f)
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = JBColor(0xFFD700, 0xFFD700)
        })
        panel.add(Box.createVerticalStrut(80))
        
        panel.add(createPlaceholderCard(""))
        
        return panel
    }
    
    /**
     * 创建总决赛区域
     */
    private fun createFinalsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        border = EmptyBorder(30, 10, 10, 10)
        
        // 标题
        val titleLabel = JLabel("🏆 总决赛").apply {
            font = font.deriveFont(Font.BOLD, 16f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = JBColor(0xFFD700, 0xFFD700)
        }
        panel.add(titleLabel, BorderLayout.NORTH)
        
        // 冠军卡片
        val championPanel = JPanel()
        championPanel.layout = BoxLayout(championPanel, BoxLayout.Y_AXIS)
        championPanel.isOpaque = false
        
        // 西部冠军
        val westCard = createChampionCard("西部冠军", null)
        westernChampion = westCard
        championPanel.add(westCard)
        
        championPanel.add(Box.createVerticalStrut(60))
        
        // 总冠军
        val winnerCard = createChampionCard("总冠军", null)
        finalsWinner = winnerCard
        championPanel.add(winnerCard)
        
        championPanel.add(Box.createVerticalStrut(60))
        
        // 东部冠军
        val eastCard = createChampionCard("东部冠军", null)
        easternChampion = eastCard
        championPanel.add(eastCard)
        
        panel.add(championPanel, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * 创建冠军卡片
     */
    private fun createChampionCard(title: String, team: TeamStanding?): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.preferredSize = Dimension(120, 50)
        panel.background = JBColor(0xFFD700, 0x333333)
        panel.border = LineBorder(JBColor(0xDAA520, 0x555555), 2, true)
        
        val displayText = team?.let { "${it.abbreviation} ${it.getRecordDisplay()}" } ?: "待定"
        
        val label = JLabel(displayText).apply {
            horizontalAlignment = SwingConstants.CENTER
            font = font.deriveFont(Font.BOLD, 12f)
            foreground = JBColor(0x333333, 0xFFFFFF)
        }
        panel.add(label, BorderLayout.CENTER)
        
        // 添加种子排名标签
        team?.let {
            val seedLabel = JLabel("#${it.conferenceRank}").apply {
                font = font.deriveFont(Font.BOLD, 9f)
                foreground = JBColor(0x666666, 0xAAAAAA)
                border = EmptyBorder(2, 5, 0, 0)
            }
            panel.add(seedLabel, BorderLayout.NORTH)
        }
        
        return panel
    }
    
    /**
     * 创建占位卡片
     */
    private fun createPlaceholderCard(text: String): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = JBColor(0xFFFFFF, 0x2D2D2D)
        panel.border = LineBorder(JBColor(0xE0E0E0, 0x444444), 1, true)
        panel.preferredSize = Dimension(90, 50)
        panel.maximumSize = Dimension(90, 50)
        
        panel.add(Box.createVerticalStrut(15))
        
        val label = JLabel(text.ifEmpty { "待确定" }).apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = JBColor(0x999999, 0x666666)
        }
        panel.add(label)
        
        return panel
    }
    
    /**
     * 创建对阵卡片 - 使用真实球队数据
     */
    private fun createMatchupCard(team1: TeamStanding?, team2: TeamStanding?): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = JBColor(0xFFFFFF, 0x2D2D2D)
        panel.border = LineBorder(JBColor(0xE0E0E0, 0x444444), 1, true)
        panel.preferredSize = Dimension(90, 56)
        panel.maximumSize = Dimension(90, 56)
        
        // 上位球队
        panel.add(createTeamRow(team1, isHigher = true))
        // 分隔线
        panel.add(JSeparator().apply {
            background = JBColor(0xE0E0E0, 0x444444)
            maximumSize = Dimension(90, 1)
        })
        // 下位球队
        panel.add(createTeamRow(team2, isHigher = false))
        
        return panel
    }
    
    /**
     * 创建球队行 - 使用真实数据
     */
    private fun createTeamRow(team: TeamStanding?, isHigher: Boolean): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.preferredSize = Dimension(90, 27)
        
        // 种子/排名
        val rankText = team?.let { "#${it.conferenceRank}" } ?: ""
        val seedLabel = JLabel(rankText).apply {
            font = font.deriveFont(Font.BOLD, 10f)
            foreground = if (isHigher) JBColor(0x4CAF50, 0x4CAF50) else JBColor(0x666666, 0x999999)
            border = EmptyBorder(0, 5, 0, 0)
        }
        panel.add(seedLabel, BorderLayout.WEST)
        
        // 队名
        val teamName = team?.abbreviation ?: ""
        val record = team?.getRecordDisplay() ?: ""
        val teamLabel = JLabel(if (teamName.isNotEmpty()) "$teamName $record" else "").apply {
            font = font.deriveFont(Font.PLAIN, 9f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = JBColor(0x333333, 0xE0E0E0)
        }
        panel.add(teamLabel, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 更新季后赛数据 - 核心方法
     */
    fun updatePlayoffData(easternTeams: List<TeamStanding>, westernTeams: List<TeamStanding>) {
        this.easternTeams = easternTeams
        this.westernTeams = westernTeams
        
        // 重新创建并更新各列
        removeAll()
        setupUI()
        
        revalidate()
        repaint()
    }
}
