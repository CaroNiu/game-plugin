package com.caro.nba

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
    
    init {
        setupUI()
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
            
            // 强制要求用户配置 API Key
            if (settings.apiKey.isBlank()) {
                return Result.failure(Exception("请先配置 API Key：点击「设置」按钮进行配置"))
            }
            
            val apiUrl = settings.apiUrl.ifEmpty { DEFAULT_API_URL }
            val apiKey = settings.apiKey
            val model = settings.model.ifEmpty { DEFAULT_MODEL }
            val maxTokens = settings.maxTokens.toIntOrNull() ?: 65536
            val temperature = settings.temperature.toDoubleOrNull() ?: 1.0
            val stream = settings.stream
            
            // 构造请求体
            val systemPrompt = buildSystemPrompt()
            
            val requestBody = JsonObject().apply {
                addProperty("model", model)
                add("messages", gson.toJsonTree(listOf(
                    mapOf("role" to "user", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to question)
                )))
                add("thinking", gson.toJsonTree(mapOf("type" to "enabled")))
                addProperty("stream", stream)
                addProperty("max_tokens", maxTokens)
                addProperty("temperature", temperature)
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
            
            if (stream) {
                // 流式输出处理
                handleStreamResponse(response)
            } else {
                // 非流式输出处理
                handleNonStreamResponse(response)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 处理流式响应
     */
    private fun handleStreamResponse(response: okhttp3.Response): Result<String> {
        val body = response.body ?: return Result.failure(Exception("Empty response"))
        val reader = body.charStream()
        val buffer = CharArray(1024)
        val resultBuilder = StringBuilder()
        
        return try {
            while (true) {
                val read = reader.read(buffer)
                if (read == -1) break
                
                val chunk = String(buffer, 0, read)
                // 解析 SSE 格式: data: {...}
                chunk.lines().filter { it.startsWith("data: ") }.forEach { line ->
                    val jsonStr = line.removePrefix("data: ").trim()
                    if (jsonStr == "[DONE]") return@forEach
                    
                    try {
                        val json = gson.fromJson(jsonStr, JsonObject::class.java)
                        val delta = json.getAsJsonArray("choices")
                            ?.get(0)?.asJsonObject
                            ?.getAsJsonObject("delta")
                            ?.get("content")?.asString
                        if (delta != null) {
                            resultBuilder.append(delta)
                            // 实时更新 UI
                            ApplicationManager.getApplication().invokeLater {
                                appendOutput(delta)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略解析错误
                    }
                }
            }
            
            reader.close()
            
            if (resultBuilder.isEmpty()) {
                return Result.failure(Exception("No content received"))
            }
            
            Result.success(resultBuilder.toString())
        } catch (e: Exception) {
            reader.close()
            return Result.failure(e)
        }
    }
    
    /**
     * 处理非流式响应
     */
    private fun handleNonStreamResponse(response: okhttp3.Response): Result<String> {
        val body = response.body?.string() ?: return Result.failure(Exception("Empty response"))
        
        return try {
            val json = gson.fromJson(body, JsonObject::class.java)
            
            val content = json.getAsJsonArray("choices")
                ?.get(0)?.asJsonObject
                ?.getAsJsonObject("message")
                ?.get("content")?.asString
                ?: return Result.failure(Exception("Invalid response format"))
            
            return Result.success(content)
        } catch (e: Exception) {
            return Result.failure(Exception("Failed to parse response: ${e.message}"))
        }
    }
    
    private fun buildSystemPrompt(): String {
        return """你是一个NBA数据分析助手，可以回答用户关于NBA的问题。
请用中文回答，简洁专业。

你可以基于你的知识回答以下类型的问题：
- NBA历史数据和记录
- 球员和球队统计数据
- 比赛分析和预测
- 季后赛对阵和晋级情况
- NBA规则和赛制说明

请注意：你的知识有时间截止点，无法获取实时数据。"""
    }
    
    private fun appendOutput(text: String) {
        outputArea.append(text)
        outputArea.caretPosition = outputArea.document.length
    }
    
    fun dispose() {
        scope.cancel()
    }
    
    companion object {
        // 智谱 GLM-4.7-Flash 默认配置
        const val DEFAULT_API_URL = "https://open.bigmodel.cn/api/paas/v4"
        const val DEFAULT_MODEL = "glm-4.7-flash"
        
        // 配置参考：https://docs.bigmodel.cn/cn/guide/models/free/glm-4.7-flash
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
    var maxTokens: String = ""
    var temperature: String = ""
    var stream: Boolean = true  // 流式输出开关
    
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
        this.maxTokens = state.maxTokens
        this.temperature = state.temperature
        this.stream = state.stream
    }
}

/**
 * 设置页面
 */
class NBASettingsConfigurable : com.intellij.openapi.options.Configurable {
    private var apiurlField: JTextField? = null
    private var apikeyField: JPasswordField? = null
    private var modelField: JTextField? = null
    private var maxTokensField: JTextField? = null
    private var temperatureField: JTextField? = null
    private var streamCheckBox: JCheckBox? = null
    
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
            it.toolTipText = "智谱：https://open.bigmodel.cn/api/paas/v4"
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
            it.toolTipText = "智谱：glm-4.7-flash | DeepSeek：deepseek-chat"
            panel.add(it, gbc)
        }
        
        // Max Tokens
        gbc.gridx = 0; gbc.gridy = 3; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("Max Tokens:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        maxTokensField = JTextField(settings.maxTokens.ifEmpty { "65536" }, 40).also {
            it.toolTipText = "最大输出 token 数，默认 65536"
            panel.add(it, gbc)
        }
        
        // Temperature
        gbc.gridx = 0; gbc.gridy = 4; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("Temperature:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        temperatureField = JTextField(settings.temperature.ifEmpty { "1.0" }, 40).also {
            it.toolTipText = "温度参数 0-2，默认 1.0"
            panel.add(it, gbc)
        }
        
        // Stream
        gbc.gridx = 0; gbc.gridy = 5; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("流式输出:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        streamCheckBox = JCheckBox("启用流式输出（实时显示回复）", settings.stream).also {
            it.toolTipText = "部分模型不支持流式输出，请根据实际情况选择"
            panel.add(it, gbc)
        }
        
        // 说明
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.insets = Insets(15, 5, 5, 5)
        panel.add(JLabel("""
            <html><body style='width:450px'>
            <b>配置说明：</b><br>
            • 默认使用智谱 GLM-4.7-Flash（免费模型）<br>
            • 获取 API Key：<a href="https://docs.bigmodel.cn/cn/guide/models/free/glm-4.7-flash">查看文档</a><br>
            • API Key 会在本地保存，请勿泄露<br>
            • 支持其他 OpenAI 兼容 API（需修改 URL 和模型名称）
            </body></html>
        """.trimIndent()), gbc)
        
        return panel
    }
    
    override fun isModified(): Boolean {
        val settings = NBASettingsState.getInstance()
        return apiurlField?.text != settings.apiUrl ||
               String(apikeyField?.password ?: charArrayOf()) != settings.apiKey ||
               modelField?.text != settings.model ||
               maxTokensField?.text != settings.maxTokens ||
               temperatureField?.text != settings.temperature ||
               streamCheckBox?.isSelected != settings.stream
    }
    
    override fun apply() {
        val settings = NBASettingsState.getInstance()
        settings.apiUrl = apiurlField?.text ?: ""
        settings.apiKey = String(apikeyField?.password ?: charArrayOf())
        settings.model = modelField?.text ?: ""
        settings.maxTokens = maxTokensField?.text ?: ""
        settings.temperature = temperatureField?.text ?: ""
        settings.stream = streamCheckBox?.isSelected ?: true
    }
    
    override fun reset() {
        val settings = NBASettingsState.getInstance()
        apiurlField?.text = settings.apiUrl
        apikeyField?.text = settings.apiKey
        modelField?.text = settings.model
        maxTokensField?.text = settings.maxTokens.ifEmpty { "65536" }
        temperatureField?.text = settings.temperature.ifEmpty { "1.0" }
        streamCheckBox?.isSelected = settings.stream
    }
}
