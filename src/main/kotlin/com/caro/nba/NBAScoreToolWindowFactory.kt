package com.caro.nba

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBTabbedPane
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * NBA 比分工具窗口工厂
 */
class NBAScoreToolWindowFactory : ToolWindowFactory {
    
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // 创建主面板（带标签页）
        val mainPanel = NBAScoreMainPanel(project)
        val content = ContentFactory.getInstance().createContent(mainPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }
    
    override fun init(toolWindow: ToolWindow) {
        toolWindow.setTitle("NBA Score")
    }
}

/**
 * 主面板 - 包含多个标签页（懒加载以提升性能）
 */
class NBAScoreMainPanel(project: Project) : JBTabbedPane() {

    // 懒加载面板
    private val scorePanel by lazy { NBAScorePanel(project) }
    private val standingsPanel by lazy {
        StandingsPanel(project) {
            // 点击季后赛按钮时切换到季后赛标签页
            selectedIndex = 2
        }
    }
    private val playoffPanel by lazy { PlayoffBracketPanel() }
    private val aiPanel by lazy { AIAssistantPanel() }

    // 跟踪哪些标签页已初始化
    private val initializedTabs = BooleanArray(4)

    init {
        tabPlacement = TOP

        // 只添加占位标签，不立即初始化面板
        addTab("🏀 比分", JPanel())
        addTab("📊 排名", JPanel())
        addTab("🏆 季后赛", JPanel())
        addTab("🤖 AI助手", JPanel())

        // 设置标签提示
        setToolTipTextAt(0, "查看今日比赛比分")
        setToolTipTextAt(1, "查看东西部排名")
        setToolTipTextAt(2, "季后赛对阵图")
        setToolTipTextAt(3, "AI 数据问答助手")

        // 添加监听器，仅在首次点击标签时初始化内容
        addChangeListener { e ->
            val source = e.source as? JBTabbedPane ?: return@addChangeListener
            val selectedIndex = source.selectedIndex
            initializeTab(selectedIndex)
        }

        // 延迟初始化第一个标签页
        SwingUtilities.invokeLater {
            initializeTab(0)
        }
    }

    /**
     * 初始化指定标签页的内容（仅一次）
     */
    private fun initializeTab(index: Int) {
        if (index < 0 || index >= initializedTabs.size || initializedTabs[index]) {
            return
        }

        initializedTabs[index] = true
        when (index) {
            0 -> setComponentAt(0, scorePanel)
            1 -> {
                setComponentAt(1, standingsPanel)
                standingsPanel.loadDataIfNeeded()
            }
            2 -> setComponentAt(2, playoffPanel)
            3 -> setComponentAt(3, aiPanel)
        }
    }

    fun refresh() {
        if (initializedTabs[1]) {
            standingsPanel.refresh()
        }
    }

    fun dispose() {
        if (initializedTabs[1]) {
            standingsPanel.dispose()
        }
        if (initializedTabs[3]) {
            aiPanel.dispose()
        }
    }
}
