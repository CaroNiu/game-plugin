package com.caro.nba

import com.caro.nba.model.NBAStandings
import com.caro.nba.model.TeamStanding
import com.caro.nba.service.StandingsService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.awt.*
import java.util.concurrent.TimeUnit
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * AI 数据问答助手面板
 */
class AIAssistantPanel : JPanel(BorderLayout()) {
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // UI 组件
    private val inputField = JTextField().apply {
        font = font.deriveFont(14f)
    }
    private val sendButton = JButton("提问")
    private val clearButton = JButton("清空")
    private val settingsButton = JButton("⚙️ 设置")
    private val outputArea = JTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = font.deriveFont(13f)
        margin = Insets(10, 10, 10, 10)
    }
    private val statusLabel = JLabel("准备就绪")
    
    // 限流：1分钟最多5次
    private val callTimestamps = mutableListOf<Long>()
    private val maxCallsPerMinute = 5
    
    // 当前排名数据（用于构造 context）
    private var currentStandings: NBAStandings? = null
    
    init {
        setupUI()
        loadStandingsData()
    }
    
    private fun setupUI() {
        background = JBColor(0xF5F5F5, 0x1E1E1E)
        border = JBUI.Borders.empty(15)
        
        // 顶部：预设问题按钮
        val presetPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 10, 0)
            
            add(JLabel("快捷问题：").apply { font = font.deriveFont(Font.BOLD, 12f) })
            add(createPresetButton("今天有哪些比赛？"))
            add(createPresetButton("雷霆战绩如何？"))
            add(createPresetButton("东西部排名"))
            add(createPresetButton("季后赛对阵"))
        }
        
        // 输入区域
        val inputPanel = JPanel(BorderLayout(5, 0)).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 10, 0)
            
            add(JLabel("💬 ").apply { font = font.deriveFont(16f) }, BorderLayout.WEST)
            add(inputField, BorderLayout.CENTER)
            
            val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 5, 0)).apply {
                isOpaque = false
                add(sendButton)
                add(clearButton)
                add(settingsButton)
            }
            add(buttonPanel, BorderLayout.EAST)
        }
        
        // 输出区域
        val scrollPane = JScrollPane(outputArea).apply {
            preferredSize = Dimension(600, 350)
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        }
        
        // 底部状态栏
        val statusPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(10, 0, 0, 0)
            add(statusLabel, BorderLayout.WEST)
            add(JLabel("限流：每分钟最多5次调用").apply { 
                font = font.deriveFont(Font.ITALIC, 11f)
                foreground = JBColor.GRAY
            }, BorderLayout.EAST)
        }
        
        // 布局
        val mainPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(presetPanel, BorderLayout.NORTH)
            add(inputPanel, BorderLayout.SOUTH)
        }
        
        add(mainPanel, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        add(statusPanel, BorderLayout.SOUTH)
        
        // 事件监听
        sendButton.addActionListener { sendQuestion() }
        clearButton.addActionListener { 
            outputArea.text = ""
            statusLabel.text = "已清空"
        }
        settingsButton.addActionListener {
            ShowSettingsUtil.getInstance().showSettingsDialog(null, NBASettingsConfigurable::class.java)
        }
        
        inputField.addActionListener { sendQuestion() }
    }
    
    private fun createPresetButton(text: String): JButton {
        return JButton(text).apply {
            font = font.deriveFont(11f)
            addActionListener {
                inputField.text = text
                sendQuestion()
            }
        }
    }
    
    private fun loadStandingsData() {
        scope.launch {
            val service = StandingsService()
            val result = service.getStandings()
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { standings ->
                        currentStandings = standings
                    },
                    onFailure = { }
                )
            }
        }
    }
    
    private fun sendQuestion() {
        val question = inputField.text.trim()
        if (question.isEmpty()) {
            statusLabel.text = "请输入问题"
            return
        }
        
        // 检查限流
        if (!checkRateLimit()) {
            statusLabel.text = "⚠️ 调用过于频繁，请稍后再试"
            return
        }
        
        sendButton.isEnabled = false
        statusLabel.text = "🤔 思考中..."
        
        scope.launch {
            val result = callAI(question)
            ApplicationManager.getApplication().invokeLater {
                sendButton.isEnabled = true
                result.fold(
                    onSuccess = { response ->
                        appendOutput("👤 你：$question\n\n🤖 AI：$response\n\n${"─".repeat(40)}\n\n")
                        statusLabel.text = "✅ 回答完成"
                        inputField.text = ""
                    },
                    onFailure = { error ->
                        appendOutput("❌ 错误：${error.message}\n\n")
                        statusLabel.text = "❌ 请求失败"
                    }
                )
            }
        }
    }
    
    private fun checkRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60_000
        
        // 移除1分钟前的记录
        callTimestamps.removeAll { it < oneMinuteAgo }
        
        // 检查是否超过限制
        return if (callTimestamps.size < maxCallsPerMinute) {
            callTimestamps.add(now)
            true
        } else {
            false
        }
    }
    
    private fun callAI(question: String): Result<String> {
        return try {
            val settings = NBASettingsState.getInstance()
            val apiUrl = settings.apiUrl.ifEmpty { DEFAULT_API_URL }
            val apiKey = settings.apiKey.ifEmpty { DEFAULT_API_KEY }
            val model = settings.model.ifEmpty { DEFAULT_MODEL }
            
            // 构造系统提示词 + NBA 数据上下文
            val systemPrompt = buildSystemPrompt()
            
            val requestBody = JsonObject().apply {
                addProperty("model", model)
                add("messages", gson.toJsonTree(listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to question)
                )))
                addProperty("temperature", 0.7)
                addProperty("max_tokens", 1000)
            }
            
            val request = Request.Builder()
                .url("$apiUrl/chat/completions")
                .header("Authorization", "Bearer $apiKey")
                .header("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()
            
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                return Result.failure(Exception("HTTP ${response.code}: ${response.message}"))
            }
            
            val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
            val json = gson.fromJson(body, JsonObject::class.java)
            
            val content = json.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?: return Result.failure(Exception("Invalid response format"))
            
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun buildSystemPrompt(): String {
        val sb = StringBuilder()
        sb.append("你是一个NBA数据分析助手，可以回答用户关于NBA的问题。请用中文回答，简洁专业。\n\n")
        
        // 添加当前排名数据作为上下文
        currentStandings?.let { standings ->
            sb.append("【当前NBA排名数据】\n\n")
            
            sb.append("东部排名（前8）：\n")
            standings.eastern.teams.take(8).forEach { team ->
                sb.append("${team.conferenceRank}. ${team.teamName} ${team.wins}胜${team.losses}负 胜率${team.getWinPercentDisplay()}\n")
            }
            
            sb.append("\n西部排名（前8）：\n")
            standings.western.teams.take(8).forEach { team ->
                sb.append("${team.conferenceRank}. ${team.teamName} ${team.wins}胜${team.losses}负 胜率${team.getWinPercentDisplay()}\n")
            }
            
            sb.append("\n数据更新时间：${standings.lastUpdated}\n")
        }
        
        return sb.toString()
    }
    
    private fun appendOutput(text: String) {
        outputArea.append(text)
        outputArea.caretPosition = outputArea.document.length
    }
    
    fun dispose() {
        scope.cancel()
    }
    
    companion object {
        // 默认使用免费的 API（用户可以自行配置）
        const val DEFAULT_API_URL = "https://api.deepseek.com/v1"
        const val DEFAULT_API_KEY = "" // 用户需要配置自己的 key
        const val DEFAULT_MODEL = "deepseek-chat"
    }
}

/**
 * 设置状态持久化
 */
@Service(Service.Level.APP)
@State(name = "NBASettings", storages = [Storage("nba_settings.xml")])
class NBASettingsState : com.intellij.openapi.components.PersistentStateComponent<NBASettingsState> {
    var apiUrl: String = ""
    var apiKey: String = ""
    var model: String = ""
    
    companion object {
        fun getInstance(): NBASettingsState {
            return ApplicationManager.getApplication().getService(NBASettingsState::class.java)
        }
    }
    
    override fun getState(): NBASettingsState? = this
    
    override fun loadState(state: NBASettingsState) {
        this.apiUrl = state.apiUrl
        this.apiKey = state.apiKey
        this.model = state.model
    }
}

/**
 * 设置页面
 */
class NBASettingsConfigurable : com.intellij.openapi.options.Configurable {
    private var apiurlField: JTextField? = null
    private var apikeyField: JPasswordField? = null
    private var modelField: JTextField? = null
    
    override fun getDisplayName(): String = "NBA AI 助手"
    
    override fun createComponent(): JComponent {
        val settings = NBASettingsState.getInstance()
        
        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(10)
        }
        
        val gbc = GridBagConstraints().apply {
            insets = Insets(5, 5, 5, 5)
            anchor = GridBagConstraints.WEST
        }
        
        // API URL
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("API URL:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        apiurlField = JTextField(settings.apiUrl, 40).also {
            it.toolTipText = "默认：https://api.deepseek.com/v1"
            panel.add(it, gbc)
        }
        
        // API Key
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("API Key:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        apikeyField = JPasswordField(settings.apiKey, 40).also {
            panel.add(it, gbc)
        }
        
        // Model
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("模型:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        modelField = JTextField(settings.model, 40).also {
            it.toolTipText = "默认：deepseek-chat"
            panel.add(it, gbc)
        }
        
        // 说明
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2; gbc.insets = Insets(15, 5, 5, 5)
        panel.add(JLabel("""
            <html><body style='width:400px'>
            <b>配置说明：</b><br>
            • 支持 OpenAI 兼容的 API（DeepSeek、通义千问、本地模型等）<br>
            • API Key 会在本地保存，请勿泄露<br>
            • 留空则使用默认值
            </body></html>
        """.trimIndent()), gbc)
        
        return panel
    }
    
    override fun isModified(): Boolean {
        val settings = NBASettingsState.getInstance()
        return apiurlField?.text != settings.apiUrl ||
               String(apikeyField?.password ?: charArrayOf()) != settings.apiKey ||
               modelField?.text != settings.model
    }
    
    override fun apply() {
        val settings = NBASettingsState.getInstance()
        settings.apiUrl = apiurlField?.text ?: ""
        settings.apiKey = String(apikeyField?.password ?: charArrayOf())
        settings.model = modelField?.text ?: ""
    }
    
    override fun reset() {
        val settings = NBASettingsState.getInstance()
        apiurlField?.text = settings.apiUrl
        apikeyField?.text = settings.apiKey
        modelField?.text = settings.model
    }
}
