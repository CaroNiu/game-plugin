package com.caro.nba

import com.caro.nba.model.TeamStanding
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * 季后赛对阵图面板 - 现代风格
 * 显示东西部季后赛对阵：首轮 → 半决赛 → 分区决赛 → 总决赛
 */
class PlayoffBracketPanel : JPanel(BorderLayout()) {
    
    private var easternTeams = listOf<TeamStanding>()
    private var westernTeams = listOf<TeamStanding>()
    
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
        mainPanel.add(createConferenceBracket("🏀 东部", reverse = false))
        
        // 总决赛区域
        mainPanel.add(createFinalsPanel())
        
        // 西部对阵图（反转顺序，使决赛朝向总决赛）
        mainPanel.add(createConferenceBracket("🏀 西部", reverse = true))
        
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
        
        preferredSize = Dimension(900, 450)
    }
    
    /**
     * 创建分区对阵图
     * @param reverse 是否反转列顺序（西部需要反转，使决赛朝向总决赛中心）
     */
    private fun createConferenceBracket(title: String, reverse: Boolean = false): JPanel {
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
        
        // 创建各列
        val firstRound = createRoundColumn("首轮", listOf("1", "8", "4", "5", "3", "6", "2", "7"))
        val semiFinal = createRoundColumn("半决赛", listOf("", ""))
        val conferenceFinal = createRoundColumn("决赛", listOf(""))
        
        // 根据是否反转决定列顺序
        if (reverse) {
            // 西部：决赛 → 半决赛 → 首轮（决赛靠近总决赛）
            bracketPanel.add(conferenceFinal)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(semiFinal)
            bracketPanel.add(Box.createHorizontalStrut(5))
            bracketPanel.add(firstRound)
        } else {
            // 东部：首轮 → 半决赛 → 决赛（决赛靠近总决赛）
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
        
        championPanel.add(Box.createVerticalStrut(50))
        
        val card = createChampionCard()
        championPanel.add(card)
        
        panel.add(championPanel, BorderLayout.CENTER)
        return panel
    }
    
    /**
     * 创建冠军卡片
     */
    private fun createChampionCard(): JPanel {
        val panel = JPanel()
        panel.layout = BorderLayout()
        panel.preferredSize = Dimension(120, 80)
        panel.background = JBColor(0xFFD700, 0x333333)
        panel.border = LineBorder(JBColor(0xDAA520, 0x555555), 2, true)
        
        val label = JLabel("待定").apply {
            horizontalAlignment = SwingConstants.CENTER
            font = font.deriveFont(Font.BOLD, 14f)
            foreground = JBColor(0x333333, 0xFFFFFF)
        }
        panel.add(label, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 创建单列对阵
     */
    private fun createRoundColumn(title: String, seeds: List<String>): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.isOpaque = false
        
        // 标题
        panel.add(JLabel(title).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            alignmentX = Component.CENTER_ALIGNMENT
            foreground = JBColor(0x666666, 0x999999)
        })
        panel.add(Box.createVerticalStrut(5))
        
        // 根据种子数量创建对阵
        when (seeds.size) {
            8 -> {
                // 首轮：4组对决
                for (i in 0 until 8 step 2) {
                    panel.add(createMatchupCard(seeds[i], seeds[i + 1]))
                    if (i < 6) panel.add(Box.createVerticalStrut(8))
                }
            }
            2 -> {
                // 半决赛：2组对决
                panel.add(Box.createVerticalStrut(40))
                panel.add(createMatchupCard("", ""))
                panel.add(Box.createVerticalStrut(60))
                panel.add(createMatchupCard("", ""))
            }
            1 -> {
                // 决赛：1组对决
                panel.add(Box.createVerticalStrut(80))
                panel.add(createMatchupCard("", ""))
            }
        }
        
        return panel
    }
    
    /**
     * 创建对阵卡片
     */
    private fun createMatchupCard(seed1: String, seed2: String): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.background = JBColor(0xFFFFFF, 0x2D2D2D)
        panel.border = LineBorder(JBColor(0xE0E0E0, 0x444444), 1, true)
        panel.preferredSize = Dimension(90, 56)
        panel.maximumSize = Dimension(90, 56)
        
        // 上位球队
        panel.add(createTeamRow(seed1, isHigher = true))
        // 分隔线
        panel.add(JSeparator().apply {
            background = JBColor(0xE0E0E0, 0x444444)
            maximumSize = Dimension(90, 1)
        })
        // 下位球队
        panel.add(createTeamRow(seed2, isHigher = false))
        
        return panel
    }
    
    /**
     * 创建球队行
     */
    private fun createTeamRow(seed: String, isHigher: Boolean): JPanel {
        val panel = JPanel(BorderLayout())
        panel.isOpaque = false
        panel.preferredSize = Dimension(90, 27)
        
        // 种子/排名
        val seedLabel = JLabel(if (seed.isNotEmpty()) "#$seed" else "").apply {
            font = font.deriveFont(Font.BOLD, 10f)
            foreground = if (isHigher) JBColor(0x4CAF50, 0x4CAF50) else JBColor(0x666666, 0x999999)
            border = EmptyBorder(0, 5, 0, 0)
        }
        panel.add(seedLabel, BorderLayout.WEST)
        
        // 队名
        val teamLabel = JLabel(teamNameMap[seed] ?: "").apply {
            font = font.deriveFont(Font.PLAIN, 11f)
            horizontalAlignment = SwingConstants.CENTER
        }
        panel.add(teamLabel, BorderLayout.CENTER)
        
        return panel
    }
    
    /**
     * 更新季后赛数据
     */
    fun updatePlayoffData(easternTeams: List<TeamStanding>, westernTeams: List<TeamStanding>) {
        this.easternTeams = easternTeams.filter { it.conferenceRank in 1..8 }.sortedBy { it.conferenceRank }
        this.westernTeams = westernTeams.filter { it.conferenceRank in 1..8 }.sortedBy { it.conferenceRank }
        
        // 刷新界面
        revalidate()
        repaint()
    }
}
