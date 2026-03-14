package com.caro.nba

import com.caro.nba.model.GameDetail
import com.caro.nba.service.GameDetailService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.event.ActionEvent
import java.net.URI
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * 比赛详情对话框 - 展示球员统计数据
 */
class GameDetailDialog(
    private val project: Project,
    private val gameId: String,
    private val homeTeamName: String,
    private val awayTeamName: String,
    private val homeTeamId: String = "",
    private val awayTeamId: String = ""
) : DialogWrapper(project) {

    private val service = GameDetailService()

    private var mainPanel: JPanel? = null
    private var contentPanel: JPanel? = null
    private var loadingLabel: JLabel? = null
    private var currentStatus: String = "scheduled"  // 存储比赛状态

    init {
        title = "$awayTeamName vs $homeTeamName"
        setOKButtonText("关闭")
        init()
    }

    override fun createCenterPanel(): JComponent? {
        mainPanel = JPanel(BorderLayout())
        mainPanel?.preferredSize = Dimension(750, 600)

        // 加载中提示
        loadingLabel = JLabel("正在加载比赛详情...", SwingConstants.CENTER).apply {
            font = font.deriveFont(16f)
            border = EmptyBorder(50, 0, 50, 0)
        }
        mainPanel?.add(loadingLabel, BorderLayout.CENTER)

        // 后台线程加载数据
        Thread {
            try {
                val result = service.getGameDetail(gameId)
                result.fold(
                    onSuccess = { detail ->
                        SwingUtilities.invokeLater { showGameDetail(detail) }
                    },
                    onFailure = { error ->
                        SwingUtilities.invokeLater { showError(error.message ?: "加载失败") }
                    }
                )
            } catch (e: Exception) {
                SwingUtilities.invokeLater { showError(e.message ?: "未知错误") }
            }
        }.start()

        return mainPanel
    }

    // 添加文字转播按钮
    override fun createActions(): Array<Action> {
        val playByPlayAction = object : AbstractAction("📺 文字转播") {
            override fun actionPerformed(e: ActionEvent?) {
                println("DEBUG: 文字转播按钮被点击！gameId=$gameId, status=$currentStatus")
                showPlayByPlay()
            }
        }
        return arrayOf(playByPlayAction, okAction)
    }

    private fun showPlayByPlay() {
        println("DEBUG: showPlayByPlay 开始执行")
        try {
            println("DEBUG: 创建 PlayByPlayPanel, gameId=$gameId")
            val dialog = PlayByPlayPanel(
                project,
                gameId,
                homeTeamName,
                awayTeamName,
                homeTeamId,
                awayTeamId,
                currentStatus
            )
            println("DEBUG: PlayByPlayPanel 创建成功，调用 show()")
            dialog.show()
            println("DEBUG: show() 调用完成")
        } catch (ex: Exception) {
            println("DEBUG: 异常 - ${ex.message}")
            ex.printStackTrace()
            JOptionPane.showMessageDialog(mainPanel, "打开文字转播失败: ${ex.message}", "错误", JOptionPane.ERROR_MESSAGE)
        }
    }

    private fun showGameDetail(detail: GameDetail) {
        currentStatus = detail.status  // 存储当前比赛状态
        
        mainPanel?.removeAll()

        contentPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(10)
        }

        // 1. 比分区域
        addScoreSection(detail)

        // 2. 球员统计数据表格
        if (detail.players != null) {
            addPlayerStatsSection(detail)
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
//            maximumSize = Dimension(Int.MAX_VALUE, 120)
            // 移除最大高度限制，让面板根据内容自适应
            preferredSize = Dimension(700, 160)
        }

        // 比分显示
        val scorePanel = JPanel(GridLayout(2, 3, 20, 10)).apply {
            border = JBUI.Borders.empty(10)
        }

        // 客队
        scorePanel.add(JLabel(detail.awayTeam.name).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            horizontalAlignment = SwingConstants.CENTER
        })
        scorePanel.add(JLabel("VS").apply {
            font = font.deriveFont(12f)
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.CENTER
        })
        scorePanel.add(JLabel(detail.homeTeam.name).apply {
            font = font.deriveFont(Font.BOLD, 14f)
            horizontalAlignment = SwingConstants.CENTER
        })

        // 比分
        scorePanel.add(JLabel(detail.awayTeam.score.toString()).apply {
            font = font.deriveFont(Font.BOLD, 42f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = if (detail.awayTeam.score > detail.homeTeam.score) JBColor(0x0066CC, 0x4A9EFF) else JBColor.BLACK
        })
        scorePanel.add(JLabel("-").apply {
            font = font.deriveFont(28f)
            foreground = JBColor.GRAY
            horizontalAlignment = SwingConstants.CENTER
        })
        scorePanel.add(JLabel(detail.homeTeam.score.toString()).apply {
            font = font.deriveFont(Font.BOLD, 42f)
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
            font = font.deriveFont(13f)
            horizontalAlignment = SwingConstants.CENTER
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(3)
        }
        panel.add(statusLabel, BorderLayout.SOUTH)

        contentPanel?.add(panel)
        contentPanel?.add(Box.createVerticalStrut(10))
    }

    private fun addPlayerStatsSection(detail: GameDetail) {
        val players = detail.players ?: return

        // 客队球员表格
        if (players.awayPlayers.isNotEmpty()) {
            val awayPanel = createTeamPlayersPanel(detail.awayTeam.name, players.awayPlayers)
            contentPanel?.add(awayPanel)
            contentPanel?.add(Box.createVerticalStrut(15))
        }

        // 主队球员表格
        if (players.homePlayers.isNotEmpty()) {
            val homePanel = createTeamPlayersPanel(detail.homeTeam.name, players.homePlayers)
            contentPanel?.add(homePanel)
        }
    }

    private fun createTeamPlayersPanel(teamName: String, players: List<GameDetail.Player>): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(JBColor.border()),
                "$teamName 球员数据"
            )
            maximumSize = Dimension(Int.MAX_VALUE, 300)
        }

        // 表格列：球员、得分、篮板、助攻、+/-、投篮
        val columnNames = arrayOf("球员", "得分", "篮板", "助攻", "+/-", "投篮", "三分", "罚球")
        val model = object : DefaultTableModel(columnNames, 0) {
            override fun isCellEditable(row: Int, column: Int) = false
        }

        players.forEach { player ->
            val shooting = "${player.fgMade}/${player.fgAttempts}"
            val three = "${player.threeMade}/${player.threeAttempts}"
            val ft = "${player.ftMade}/${player.ftAttempts}"

            model.addRow(arrayOf(
                "${player.name} (#${player.jersey})",
                player.points,
                player.rebounds,
                player.assists,
                player.plusMinus,
                shooting,
                three,
                ft
            ))
        }

        val table = JTable(model).apply {
            rowHeight = 25
            font = font.deriveFont(12f)
            setGridColor(JBColor.border())

            // 设置列宽
            columnModel.getColumn(0).preferredWidth = 150  // 球员
            columnModel.getColumn(1).preferredWidth = 50   // 得分
            columnModel.getColumn(2).preferredWidth = 50   // 篮板
            columnModel.getColumn(3).preferredWidth = 50   // 助攻
            columnModel.getColumn(4).preferredWidth = 50   // +/-
            columnModel.getColumn(5).preferredWidth = 60   // 投篮
            columnModel.getColumn(6).preferredWidth = 50   // 三分
            columnModel.getColumn(7).preferredWidth = 50   // 罚球

            // 居中对齐数字列
            val centerRenderer = DefaultTableCellRenderer().apply {
                horizontalAlignment = SwingConstants.CENTER
            }
            for (i in 1 until columnCount) {
                columnModel.getColumn(i).cellRenderer = centerRenderer
            }

            // 球员名称列左对齐，并添加点击事件
            columnModel.getColumn(0).cellRenderer = object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
                ): Component {
                    super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    foreground = JBColor(0x0066CC, 0x4A9EFF)
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    return this
                }
            }

            // 点击球员名称打开详情
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val row = rowAtPoint(e.point)
                    val col = columnAtPoint(e.point)
                    if (col == 0 && row >= 0 && row < players.size) {
                        val player = players[row]
                        openPlayerDetail(player)
                    }
                }
            })
        }

        val scrollPane = JScrollPane(table).apply {
            preferredSize = Dimension(700, (players.size * 25 + 30).coerceAtMost(280))
        }

        panel.add(scrollPane, BorderLayout.CENTER)

        // 点击提示
        val hintLabel = JLabel("💡 点击球员名称查看详情").apply {
            font = font.deriveFont(10f)
            foreground = JBColor.GRAY
            border = JBUI.Borders.empty(5)
            horizontalAlignment = SwingConstants.RIGHT
        }
        panel.add(hintLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun openPlayerDetail(player: GameDetail.Player) {
        val options = arrayOf("打开 ESPN 球员页面", "取消")
        val choice = JOptionPane.showOptionDialog(
            mainPanel,
            "查看 ${player.name} 的详细信息？",
            "球员详情",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        )

        if (choice == 0) {
            try {
                // 使用 ESPN 球员页面 URL
                val url = if (player.playerUrl.isNotEmpty()) {
                    player.playerUrl
                } else {
                    "https://www.espn.com/nba/player/_/id/${player.id}"
                }
                Desktop.getDesktop().browse(URI(url))
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(mainPanel, "无法打开链接: ${e.message}")
            }
        }
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
}