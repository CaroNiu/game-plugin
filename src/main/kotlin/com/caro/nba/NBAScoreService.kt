package com.caro.nba

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

/**
 * NBA 比分插件服务管理
 */
class NBAScoreService(private val project: Project) {
    
    fun refreshScores() {
        ToolWindowManager.getInstance(project)
            .getToolWindow("NBA Score")
            ?.activate { }
    }
}