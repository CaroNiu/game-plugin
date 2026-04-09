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
 * 主面板 - 包含多个标签页
 */
class NBAScoreMainPanel(project: Project) : JBTabbedPane() {
    
    private val scorePanel = NBAScorePanel(project)
    private val standingsPanel = StandingsPanel(project)
    private val aiPanel = AIAssistantPanel()
    
    init {
        tabPlacement = TOP
        
        addTab("🏀 比分", scorePanel)
        addTab("📊 排名", standingsPanel)
        addTab("🏆 季后赛", JPanel().apply { 
            add(JLabel("季后赛对阵图（点击排名页面的「季后赛」按钮查看）")) 
        })
        addTab("🤖 AI助手", aiPanel)
        
        // 设置标签提示
        setToolTipTextAt(0, "查看今日比赛比分")
        setToolTipTextAt(1, "查看东西部排名")
        setToolTipTextAt(2, "季后赛对阵图")
        setToolTipTextAt(3, "AI 数据问答助手")
    }
    
    fun refresh() {
        standingsPanel.refresh()
    }
    
    fun dispose() {
        standingsPanel.dispose()
        aiPanel.dispose()
    }
}
