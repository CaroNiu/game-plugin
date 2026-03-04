package com.caro.nba

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 刷新比分动作
 */
class RefreshAction : AnAction {
    
    constructor() : super("刷新NBA比分")
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow("NBA Score")
        
        toolWindow?.activate {
            // 窗口激活后会自动加载
        }
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = true
    }
}