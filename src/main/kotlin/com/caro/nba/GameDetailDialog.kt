package com.caro.nba

import com.caro.nba.model.GameDetail
import com.caro.nba.service.GameDetailService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * 比赛详情对话框 - 展示球员比分和高光时刻
 */
class GameDetailDialog(
    private val project: Project,
    private val gameId: String,
    private val homeTeamName: String,
    private val awayTeamName: String
) : DialogWrapper(project) {
    
    private val service = GameDetailService()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var mainPanel: JPanel? = null
    private var contentPanel: JPanel? = null
    private var loadingLabel: JLabel? = null
    
    init {
        title = "$awayTeamName vs $homeTeamName"
        setOKButtonText("关闭")
        // 移除 Cancel 按钮
        setCancelButtonText(null)
        init()
        loadGameDetail()
    }
    
    override fun createCenterPanel(): JComponent? {
        mainPanel = JPanel(BorderLayout())
        mainPanel?.preferredSize = Dimension(650, 550)
        
        // 加载中提示
        loadingLabel = JLabel("正在加载比赛详情...", SwingConstants.CENTER).apply {
            font = font.deriveFont(16f)
            border = EmptyBorder(50, 0, 50, 0)
        }
        mainPanel?.add(loadingLabel, BorderLayout.CENTER)
        
        return mainPanel
    }
    
    // 隐藏 Cancel 按钮
    override fun createActions(): Array<Action> {
        return arrayOf(okAction)
    }
    
    private fun loadGameDetail() {
        scope.launch {
            val result = service.getGameDetail(gameId)
            
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { detail -> showGameDetail(detail) },
                    onFailure = { error -> showError(error.message ?: "加载失败") }
                )
            }
        }
    }
    
    private fun showGameDetail(detail: GameDetail) {
        mainPanel?.removeAll()
        
        contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }
        
        // 1. 比分区域
        addScoreSection(detail)
        
        // 2. 球员领袖
        addLeadersSection(detail)
        
        // 3. 高光时刻
        if (detail.highlights.isNotEmpty()) {
            addHighlightsSection(detail)
        }
        
        val scrollPane = JBScrollPane(contentPanel).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        
        mainPanel?.add(scrollPane, BorderLayout.CENTER)
        mainPanel?.revalidate()
        mainPanel?.repaint()
    }
    
    private fun addScoreSection(detail: GameDetail) {
        val panel = JPanel(BorderLayout(10, 0)).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                "比分"
            )
            maximumSize = Dimension(Int.MAX_VALUE, 120)
        }
        
        // 比分显示
        val scorePanel = JPanel(GridLayout(2, 3, 20, 10)).apply {
            border = JBUI.Borders.empty(15)
        }
        
        // 客队
        scorePanel.add(JLabel(detail.awayTeam.name).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            horizontalAlignment = SwingConstants.CENTER
        })
        scorePanel.add(JLabel("VS").apply {
            font = font.deriveFont(14f)
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.CENTER
        })
        scorePanel.add(JLabel(detail.homeTeam.name).apply {
            font = font.deriveFont(Font.BOLD, 16f)
            horizontalAlignment = SwingConstants.CENTER
        })
        
        // 比分
        scorePanel.add(JLabel(detail.awayTeam.score.toString()).apply {
            font = font.deriveFont(Font.BOLD, 36f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (detail.awayTeam.score > detail.homeTeam.score) JBColor(0x0066CC, 0x4A9EFF) else JBColor.BLACK
        })
        scorePanel.add(JLabel("-").apply {
            font = font.deriveFont(24f)
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.CENTER
        })
        scorePanel.add(JLabel(detail.homeTeam.score.toString()).apply {
            font = font.deriveFont(Font.BOLD, 36f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (detail.homeTeam.score > detail.awayTeam.score) JBColor(0x0066CC, 0x4A9EFF) else JBColor.BLACK
        })
        
        panel.add(scorePanel, BorderLayout.CENTER)
        
        // 状态
        val statusText = when (detail.status) {
            "scheduled" -> "未开始"
            "in_progress" -> "进行中 ${if (detail.period > 4) "OT${detail.period - 4}" else "第${detail.period}节"}"
            "finished" -> "已结束"
            else -> detail.status
        }
        val statusLabel = JLabel(statusText).apply {
            font = font.deriveFont(14f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(5)
        }
        panel.add(statusLabel, BorderLayout.SOUTH)
        
        contentPanel?.add(panel)
        contentPanel?.add(Box.createVerticalStrut(10))
    }
    
    private fun addLeadersSection(detail: GameDetail) {
        val panel = JPanel(GridLayout(1, 2, 20, 0)).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                "球员领袖"
            )
            maximumSize = Dimension(Int.MAX_VALUE, 200)
        }
        
        // 客队领袖
        val awayPanel = createTeamLeadersPanel(detail.awayTeam)
        // 主队领袖
        val homePanel = createTeamLeadersPanel(detail.homeTeam)
        
        panel.add(awayPanel)
        panel.add(homePanel)
        
        contentPanel?.add(panel)
        contentPanel?.add(Box.createVerticalStrut(10))
    }
    
    private fun createTeamLeadersPanel(team: GameDetail.TeamDetail): JPanel {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }
        
        // 队名
        panel.add(JLabel(team.name).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            alignmentX = Component.CENTER_ALIGNMENT
        })
        panel.add(Box.createVerticalStrut(10))
        
        // 领袖数据
        team.leaders.take(3).forEach { leader ->
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2)).apply {
                isOpaque = false
                maximumSize = Dimension(Int.MAX_VALUE, 30)
            }
            
            row.add(JLabel("${leader.category}:").apply {
                font = font.deriveFont(12f)
                foreground = JBColor.GRAY
                preferredSize = Dimension(60, 20)
            })
            row.add(JLabel("${leader.playerName} (#${leader.playerJersey})").apply {
                font = font.deriveFont(12f)
                preferredSize = Dimension(120, 20)
            })
            row.add(JLabel(leader.value).apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor(0x0066CC, 0x4A9EFF)
            })
            
            panel.add(row)
        }
        
        if (team.leaders.isEmpty()) {
            panel.add(JLabel("暂无数据").apply {
                foreground = JBColor.GRAY
                alignmentX = Component.CENTER_ALIGNMENT
            })
        }
        
        return panel
    }
    
    private fun addHighlightsSection(detail: GameDetail) {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                "高光时刻"
            )
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
        }
        
        detail.highlights.forEach { highlight ->
            val itemPanel = JPanel(BorderLayout(10, 0)).apply {
                border = JBUI.Borders.empty(5)
                maximumSize = Dimension(Int.MAX_VALUE, 80)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                
                // 点击打开链接
                addMouseListener(object : java.awt.event.MouseAdapter() {
                    override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                        if (highlight.videoUrl.isNotEmpty()) {
                            try {
                                Desktop.getDesktop().browse(java.net.URI(highlight.videoUrl))
                            } catch (ex: Exception) {
                                JOptionPane.showMessageDialog(mainPanel, "无法打开链接: ${ex.message}")
                            }
                        }
                    }
                })
            }
            
            // 标题和描述
            val textPanel = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
            }
            
            textPanel.add(JLabel(highlight.title).apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = JBColor(0x0066CC, 0x4A9EFF)
            })
            textPanel.add(JLabel(highlight.description.take(80) + if (highlight.description.length > 80) "..." else "").apply {
                font = font.deriveFont(11f)
                foreground = JBColor.GRAY
            })
            
            itemPanel.add(textPanel, BorderLayout.CENTER)
            itemPanel.add(JLabel("▶ 播放").apply {
                font = font.deriveFont(Font.BOLD, 12f)
                foreground = JBColor(0x0066CC, 0x4A9EFF)
            }, BorderLayout.EAST)
            
            panel.add(itemPanel)
        }
        
        contentPanel?.add(panel)
    }
    
    private fun showError(message: String) {
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
    
    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}