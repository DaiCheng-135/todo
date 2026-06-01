package com.example.todo.ui

import com.example.todo.service.BranchCommits
import com.example.todo.service.GitCommitService
import com.example.todo.service.LLMCredentialStore
import com.example.todo.service.LLMConfig
import com.example.todo.service.LLMService
import com.example.todo.service.TodoService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.LineBorder
import kotlin.concurrent.thread

class GitCommitSummaryPanel(private val project: Project) : JPanel() {

    private val todoService = project.service<TodoService>()
    private val gitCommitService = GitCommitService(project)

    // region 周期与日期控件
    private val periodBox = JComboBox(arrayOf("日报", "周报", "月报")).apply {
        preferredSize = Dimension(80, 25)
    }

    private val dateField = JTextField(
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    ).apply { preferredSize = Dimension(120, 25) }

    private val calendarButton = JButton("📅").apply {
        preferredSize = Dimension(30, 25)
        addActionListener { showCalendarPopup() }
    }

    private val authorField = JTextField(gitCommitService.getCurrentUserEmail() ?: "").apply {
        preferredSize = Dimension(250, 25)
        toolTipText = "Git 作者邮箱，用于过滤自己的提交"
    }

    private val collapsibleArrow = JLabel("▼ ").apply {
        font = font.deriveFont(Font.BOLD, 14f)
        cursor = Cursor(Cursor.HAND_CURSOR)
    }
    private val currentConfigLabel = JLabel("").apply {
        font = font.deriveFont(12f)
        foreground = Color.GRAY
    }
    // endregion

    // region LLM 配置控件
    private val configComboBox = JComboBox<String>().apply {
        preferredSize = Dimension(250, 25)
        addActionListener { onConfigSelected() }
    }

    private val configNameField = JTextField().apply {
        preferredSize = Dimension(200, 25)
        toolTipText = "配置名称"
    }
    private val configEndpointField = JTextField().apply {
        preferredSize = Dimension(300, 25)
        toolTipText = "API 地址"
    }
    private val configApiKeyField = JPasswordField().apply {
        preferredSize = Dimension(200, 25)
        toolTipText = "API Key"
    }
    private val configModelField = JTextField().apply {
        preferredSize = Dimension(150, 25)
        toolTipText = "模型名称"
    }

    // Track editing state and suppress combo listener during programmatic refresh
    private var editingConfigIndex: Int? = null
    private var suppressComboListener = false

    private val configListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
    }

    private val promptTextArea = JTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
        rows = 8
    }
    // endregion

    // region 操作按钮与文本区域
    private val fetchBtn = JButton("获取提交")
    private val summarizeBtn = JButton("AI 总结")
    private val copyBtn = JButton("复制总结")
    private val streamCheckBox = JCheckBox("流式输出").apply {
        font = font.deriveFont(11f)
    }

    private val commitArea = JTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 12)
    }

    private val summaryArea = JTextArea().apply {
        font = Font("Monospaced", Font.PLAIN, 13)
        lineWrap = true
        wrapStyleWord = true
    }

    private var currentBranchCommits: List<BranchCommits> = emptyList()
    // endregion

    init {
        layout = BorderLayout()

        // === 顶部配置面板 ===
        val configPanel = JPanel()
        configPanel.layout = BoxLayout(configPanel, BoxLayout.Y_AXIS)

        // Row 1: 周期 + 日期 + 操作按钮
        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        row1.add(JLabel("周期:"))
        row1.add(periodBox)
        row1.add(Box.createHorizontalStrut(4))
        row1.add(JLabel("截止:"))
        row1.add(dateField)
        row1.add(calendarButton)
        row1.add(Box.createHorizontalStrut(8))
        row1.add(fetchBtn)
        row1.add(summarizeBtn)
        row1.add(copyBtn)
        row1.add(streamCheckBox)
        configPanel.add(row1)

        // Row 2: 作者 + LLM 配置折叠开关（与第一行 FlowLayout 对齐）
        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        row2.add(JLabel("作者:"))
        row2.add(authorField)
        row2.add(Box.createHorizontalStrut(30))
        row2.add(collapsibleArrow)
        row2.add(JLabel("LLM 配置").apply { font = font.deriveFont(Font.BOLD, 13f) })
        row2.add(currentConfigLabel)
        collapsibleArrow.cursor = Cursor(Cursor.HAND_CURSOR)
        configPanel.add(row2)

        // 可折叠内容区域 - 包装在滚动面板中
        val llmContentPanel = createLlmContentPanel()
        val scrollPane = JScrollPane(llmContentPanel).apply {
            border = null
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            // Allow the panel to shrink when collapsed
            minimumSize = Dimension(0, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 600)
        }
        configPanel.add(scrollPane)

        // 折叠切换
        collapsibleArrow.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                llmContentPanel.isVisible = !llmContentPanel.isVisible
                scrollPane.isVisible = llmContentPanel.isVisible
                collapsibleArrow.text = if (llmContentPanel.isVisible) "▼ " else "▶ "
                
                // Only update the panel layout, don't affect IDE window
                configPanel.revalidate()
                configPanel.repaint()
            }
        })

        add(configPanel, BorderLayout.NORTH)

        // === 中间：提交记录 + 总结 ===
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        splitPane.topComponent = JScrollPane(commitArea).apply {
            border = BorderFactory.createTitledBorder("提交记录(可编辑)")
        }
        splitPane.bottomComponent = JScrollPane(summaryArea).apply {
            border = BorderFactory.createTitledBorder("总结(可编辑)")
        }
        splitPane.resizeWeight = 0.5
        splitPane.dividerLocation = 200
        add(splitPane, BorderLayout.CENTER)

        // === 事件绑定 ===
        fetchBtn.addActionListener { fetchCommits() }
        summarizeBtn.addActionListener { summarize() }
        copyBtn.addActionListener { copySummary() }

        // 初始化数据
        migrateLegacyApiKeys()
        refreshConfigComboBox()
        refreshConfigList()
        loadPrompt()
        updateCurrentConfigLabel()
    }

    // =================================================================
    //  可折叠 LLM 配置区域
    // =================================================================

    private fun createLlmContentPanel(): JPanel {
        val contentPanel = JPanel()
        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color(220, 220, 220)),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        )

        // ── 配置选择行 ──
        val selectorRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        selectorRow.add(JLabel("使用配置:"))
        selectorRow.add(configComboBox)
        contentPanel.add(selectorRow)
        contentPanel.add(Box.createVerticalStrut(6))

        // ── 添加新配置 ──
        val addSection = JPanel(GridBagLayout())
        addSection.border = BorderFactory.createTitledBorder("添加新配置")
        val gc = GridBagConstraints()
        gc.fill = GridBagConstraints.HORIZONTAL
        gc.insets = Insets(2, 4, 2, 4)

        fun addField(row: Int, label: String, comp: JComponent) {
            gc.gridx = 0; gc.gridy = row; gc.weightx = 0.0
            addSection.add(JLabel(label), gc)
            gc.gridx = 1; gc.weightx = 1.0
            addSection.add(comp, gc)
        }
        addField(0, "名称:", configNameField)
        addField(1, "Endpoint:", configEndpointField)
        addField(2, "API Key:", configApiKeyField)
        addField(3, "Model:", configModelField)

        gc.gridx = 0; gc.gridy = 4; gc.weightx = 0.0; gc.gridwidth = 2
        val addBtn = JButton("+ 添加配置")
        addBtn.name = "addConfigButton" // Mark for identification
        addBtn.addActionListener { 
            if (editingConfigIndex != null) {
                updateConfig(editingConfigIndex!!)
            } else {
                addConfig() 
            }
        }
        addSection.add(addBtn, gc)

        contentPanel.add(addSection)
        contentPanel.add(Box.createVerticalStrut(6))

        // ── 已保存配置列表 ─
        configListPanel.layout = BoxLayout(configListPanel, BoxLayout.Y_AXIS)
        configListPanel.border = BorderFactory.createTitledBorder("已保存配置")
        configListPanel.minimumSize = Dimension(0, 40)  // Minimum height for empty state
        contentPanel.add(configListPanel)
        contentPanel.add(Box.createVerticalStrut(6))

        // ── 自定义提示词 ──
        val promptSection = JPanel(BorderLayout())
        promptSection.border = BorderFactory.createTitledBorder("自定义提示词")
        promptSection.add(JScrollPane(promptTextArea), BorderLayout.CENTER)

        val promptBtnRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 2))
        val restoreBtn = JButton("恢复默认提示词")
        restoreBtn.addActionListener { restoreDefaultPrompt() }
        promptBtnRow.add(restoreBtn)
        promptSection.add(promptBtnRow, BorderLayout.SOUTH)
        contentPanel.add(promptSection)

        return contentPanel
    }

    // =================================================================
    //  配置管理
    // =================================================================

    private fun onConfigSelected() {
        if (suppressComboListener) return
        val idx = configComboBox.selectedIndex
        if (idx >= 0) {
            todoService.setActiveLlmConfigIndex(idx)
            populateConfigForm(idx)
            refreshConfigList()
            updateCurrentConfigLabel()
        }
    }

    private fun populateConfigForm(index: Int) {
        val configs = todoService.getLlmConfigs()
        if (index < 0 || index >= configs.size) return
        val config = configs[index]
        configNameField.text = config.name
        configEndpointField.text = config.endpoint
        configApiKeyField.text = getApiKey(config)
        configModelField.text = config.model
        editingConfigIndex = index
        val addSection = findAddSection()
        val addBtn = findAddButton(addSection)
        addBtn?.text = "💾 保存修改"
        addSection?.border = BorderFactory.createTitledBorder("编辑配置: ${config.name}")
    }

    private fun addConfig() {
        val name = configNameField.text.trim()
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "请输入配置名称")
            return
        }
        val config = LLMConfig(
            name = name,
            endpoint = configEndpointField.text.trim(),
            model = configModelField.text.trim()
        )
        LLMCredentialStore.setApiKey(config.id, String(configApiKeyField.password))
        todoService.addLlmConfig(config)
        clearConfigForm()
        refreshConfigComboBox()
        refreshConfigList()
        updateCurrentConfigLabel()
    }

    private fun updateConfig(index: Int) {
        val configs = todoService.getLlmConfigs()
        if (index < 0 || index >= configs.size) return
        
        val name = configNameField.text.trim()
        if (name.isBlank()) {
            JOptionPane.showMessageDialog(this, "请输入配置名称")
            return
        }
        
        val updatedConfig = LLMConfig(
            id = configs[index].id,
            name = name,
            endpoint = configEndpointField.text.trim(),
            model = configModelField.text.trim()
        )
        LLMCredentialStore.setApiKey(updatedConfig.id, String(configApiKeyField.password))
        
        todoService.updateLlmConfig(index, updatedConfig)
        
        clearConfigForm()
        editingConfigIndex = null
        
        refreshConfigComboBox()
        refreshConfigList()
        updateCurrentConfigLabel()
        
        JOptionPane.showMessageDialog(this, "配置已更新", "成功", JOptionPane.INFORMATION_MESSAGE)
    }

    private fun clearConfigForm() {
        configNameField.text = ""
        configEndpointField.text = ""
        configApiKeyField.text = ""
        configModelField.text = ""
        
        // Update button text back to add mode
        val addSection = findAddSection()
        val addBtn = findAddButton(addSection)
        addBtn?.text = "+ 添加配置"
        addSection?.border = BorderFactory.createTitledBorder("添加新配置")
    }

    private fun deleteConfig(index: Int) {
        val configs = todoService.getLlmConfigs()
        if (index < 0 || index >= configs.size) return
        
        val result = JOptionPane.showConfirmDialog(
            this, "确定删除配置「${configs[index].name}」？", "删除配置",
            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
        )
        if (result != JOptionPane.YES_OPTION) return
        
        LLMCredentialStore.removeApiKey(configs[index].id)
        todoService.removeLlmConfig(index)
        refreshConfigComboBox()
        refreshConfigList()
        updateCurrentConfigLabel()
    }

    private fun refreshConfigComboBox() {
        suppressComboListener = true
        configComboBox.removeAllItems()
        val configs = todoService.getLlmConfigs()
        configs.forEach { configComboBox.addItem(it.name) }
        val activeIdx = todoService.getActiveLlmConfigIndex()
        if (configComboBox.itemCount > 0 && activeIdx in 0 until configComboBox.itemCount) {
            configComboBox.selectedIndex = activeIdx
        }
        suppressComboListener = false
    }

    private fun updateCurrentConfigLabel() {
        val config = todoService.getActiveLlmConfig()
        currentConfigLabel.text = if (config != null) "  当前: ${config.name} (${config.model})" else ""
    }

    private fun refreshConfigList() {
        configListPanel.removeAll()
        val configs = todoService.getLlmConfigs()
        if (configs.isEmpty()) {
            // Center the empty message with vertical glue
            configListPanel.add(Box.createVerticalGlue())
            
            val emptyPanel = JPanel(BorderLayout())
            val emptyLabel = JLabel("暂无保存的配置", SwingConstants.CENTER)
            emptyLabel.foreground = Color.GRAY
            emptyLabel.border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
            emptyPanel.add(emptyLabel, BorderLayout.CENTER)
            
            configListPanel.add(emptyPanel)
            configListPanel.add(Box.createVerticalGlue())
        } else {
            configs.forEachIndexed { index, config ->
                val row = JPanel(BorderLayout())
                val activeIdx = todoService.getActiveLlmConfigIndex()
                val prefix = if (index == activeIdx) "● " else "  "
                val label = JLabel("$prefix${config.name}  (${config.model})")
                
                val btnPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0))
                
                // Edit button
                val editBtn = JButton("编辑").apply {
                    font = font.deriveFont(11f)
                    addActionListener { editConfig(index) }
                }
                btnPanel.add(editBtn)
                
                // Delete button
                val delBtn = JButton("删除").apply {
                    font = font.deriveFont(11f)
                    addActionListener { deleteConfig(index) }
                }
                btnPanel.add(delBtn)
                
                row.add(label, BorderLayout.CENTER)
                row.add(btnPanel, BorderLayout.EAST)
                configListPanel.add(row)
            }
        }
        configListPanel.revalidate()
        configListPanel.repaint()
    }

    private fun editConfig(index: Int) {
        val configs = todoService.getLlmConfigs()
        if (index < 0 || index >= configs.size) return
        
        val config = configs[index]
        
        // Fill form with existing config
        configNameField.text = config.name
        configEndpointField.text = config.endpoint
        configApiKeyField.text = getApiKey(config)
        configModelField.text = config.model
        
        // Set editing mode
        editingConfigIndex = index
        
        // Update button text to indicate edit mode
        val addSection = findAddSection()
        val addBtn = findAddButton(addSection)
        addBtn?.text = "💾 保存修改"
        addSection?.border = BorderFactory.createTitledBorder("编辑配置: ${config.name}")
    }

    private fun findAddSection(): JPanel? {
        // Find the panel with "添加新配置" border
        val contentPanel = configListPanel.parent as? JPanel ?: return null
        for (comp in contentPanel.components) {
            if (comp is JPanel && comp.border is javax.swing.border.TitledBorder) {
                val title = (comp.border as javax.swing.border.TitledBorder).title
                if (title.contains("添加") || title.contains("编辑")) {
                    return comp
                }
            }
        }
        return null
    }

    private fun findAddButton(panel: JPanel?): JButton? {
        if (panel == null) return null
        for (comp in panel.components) {
            if (comp is JButton && comp.name == "addConfigButton") {
                return comp
            }
        }
        return null
    }

    // =================================================================
    //  提示词管理
    // =================================================================

    private fun loadPrompt() {
        val saved = todoService.getLlmPromptTemplate()
        promptTextArea.text = if (saved.isNotBlank()) saved else LLMService.DEFAULT_PROMPT
    }

    private fun savePrompt() {
        val text = promptTextArea.text.trim()
        todoService.setLlmPromptTemplate(text)
    }

    private fun restoreDefaultPrompt() {
        promptTextArea.text = LLMService.DEFAULT_PROMPT
        todoService.setLlmPromptTemplate("")
        JOptionPane.showMessageDialog(this, "已恢复默认提示词")
    }

    // =================================================================
    //  API 调用
    // =================================================================

    private fun saveApiConfig() {
        // 在新系统中只保存提示词
        savePrompt()
    }

    private fun getApiKey(config: LLMConfig): String {
        return LLMCredentialStore.getApiKey(config.id).ifBlank { config.apiKey }
    }

    private fun migrateLegacyApiKeys() {
        val configs = todoService.getLlmConfigs()
        configs.forEachIndexed { index, config ->
            if (config.apiKey.isNotBlank() && LLMCredentialStore.getApiKey(config.id).isBlank()) {
                LLMCredentialStore.setApiKey(config.id, config.apiKey)
                todoService.updateLlmConfig(index, config.copy(apiKey = ""))
            }
        }
    }

    private fun getDateRange(): Triple<LocalDate, LocalDate, String> {
        val end = LocalDate.parse(dateField.text.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val (start, periodName) = when (periodBox.selectedIndex) {
            0 -> end to "今天"
            1 -> end.minusDays(6) to "本周"
            2 -> end.withDayOfMonth(1) to "本月"
            else -> end to "今天"
        }
        return Triple(start, end, periodName)
    }

    private fun fetchCommits() {
        saveApiConfig()
        fetchBtn.isEnabled = false
        commitArea.text = "正在获取提交记录..."

        thread {
            try {
                val (start, end, _) = getDateRange()
                val author = authorField.text.trim().ifBlank { null }
                val branchCommits = gitCommitService.getCommitsForRange(start, end, author)

                SwingUtilities.invokeLater {
                    currentBranchCommits = branchCommits
                    val titleRange = if (start == end) start.toString() else "$start ~ $end"
                    val sb = StringBuilder()
                    sb.appendLine("日期范围: $titleRange")
                    sb.appendLine("作    者: ${author ?: "全部"}")
                    sb.appendLine()
                    for (bc in branchCommits) {
                        sb.appendLine("=== ${bc.branch} ===")
                        for (commit in bc.commits) {
                            sb.appendLine("  [${commit.hash.take(7)}] ${commit.message}")
                            sb.appendLine("  ${commit.author}  ${commit.date}")
                            sb.appendLine()
                        }
                    }
                    commitArea.text = if (sb.isEmpty()) "该时间段内没有提交记录" else sb.toString()
                    commitArea.caretPosition = 0
                    fetchBtn.isEnabled = true
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    commitArea.text = "获取失败: ${e.message}"
                    fetchBtn.isEnabled = true
                }
            }
        }
    }

    private fun summarize() {
        saveApiConfig()
        if (currentBranchCommits.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请先获取提交记录")
            return
        }

        val activeConfig = todoService.getActiveLlmConfig()
        if (activeConfig == null) {
            JOptionPane.showMessageDialog(this, "请先添加 LLM 配置")
            return
        }

        summarizeBtn.isEnabled = false
        summaryArea.text = ""

        val sb = StringBuilder()
        for (bc in currentBranchCommits) {
            sb.appendLine("=== ${bc.branch} ===")
            for (commit in bc.commits) {
                sb.appendLine("${commit.message} (${commit.author})")
            }
            sb.appendLine()
        }

        val (_, _, periodName) = getDateRange()
        val llm = LLMService(
            endpoint = activeConfig.endpoint,
            apiKey = getApiKey(activeConfig),
            model = activeConfig.model
        )
        val customPrompt = todoService.getLlmPromptTemplate()

        if (streamCheckBox.isSelected) {
            summarizeStream(llm, sb.toString(), periodName, customPrompt)
        } else {
            summarizeBlocking(llm, sb.toString(), periodName, customPrompt)
        }
    }

    private fun summarizeBlocking(llm: LLMService, commitText: String, periodName: String, customPrompt: String?) {
        summarizeBtn.text = "AI 总结中..."
        summarizeBtn.isEnabled = false
        summaryArea.text = ""
        thread {
            try {
                val result = llm.summarize(
                    commitText = commitText,
                    periodName = periodName,
                    customPrompt = customPrompt?.ifBlank { null }
                )
                SwingUtilities.invokeLater {
                    if (result.success) {
                        summaryArea.text = result.content
                    } else {
                        summaryArea.text = "总结失败: ${result.error}"
                    }
                    summarizeBtn.text = "AI 总结"
                    summarizeBtn.isEnabled = true
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    summaryArea.text = "总结失败: ${e.message}"
                    summarizeBtn.text = "AI 总结"
                    summarizeBtn.isEnabled = true
                }
            }
        }
    }

    private fun summarizeStream(llm: LLMService, commitText: String, periodName: String, customPrompt: String?) {
        summarizeBtn.text = "AI 总结中..."
        summarizeBtn.isEnabled = false
        summaryArea.text = ""
        thread {
            llm.summarizeStream(
                commitText = commitText,
                periodName = periodName,
                customPrompt = customPrompt?.ifBlank { null },
                onChunk = { chunk ->
                    SwingUtilities.invokeLater {
                        summaryArea.append(chunk)
                        summaryArea.caretPosition = summaryArea.document.length
                    }
                },
                onDone = { result ->
                    SwingUtilities.invokeLater {
                        if (!result.success) {
                            summaryArea.text = "总结失败: ${result.error}"
                        }
                        summarizeBtn.text = "AI 总结"
                        summarizeBtn.isEnabled = true
                    }
                }
            )
        }
    }

    private fun copySummary() {
        val text = summaryArea.text.trim()
        if (text.isNotBlank()) {
            val sel = Toolkit.getDefaultToolkit().systemClipboard
            sel.setContents(StringSelection(text), null)
            JOptionPane.showMessageDialog(this, "已复制到剪贴板")
        }
    }

    // =================================================================
    //  日历弹窗
    // =================================================================

    private fun showCalendarPopup() {
        val popup = JPopupMenu()
        popup.border = BorderFactory.createCompoundBorder(
            LineBorder(Color.GRAY),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        )

        val currentDate = try {
            LocalDate.parse(dateField.text, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        } catch (_: Exception) {
            LocalDate.now()
        }

        val yearMonthRef = arrayOf(YearMonth.from(currentDate))
        val content = JPanel(BorderLayout())

        val navPanel = JPanel(BorderLayout())
        val prevBtn = JButton("<")
        val nextBtn = JButton(">")
        prevBtn.font = prevBtn.font.deriveFont(Font.BOLD, 14.0f)
        nextBtn.font = nextBtn.font.deriveFont(Font.BOLD, 14.0f)
        val monthLabel = JLabel(
            yearMonthRef[0].format(DateTimeFormatter.ofPattern("yyyy年MM月")),
            SwingConstants.CENTER
        ).apply { font = font.deriveFont(Font.BOLD, 13f) }

        navPanel.add(prevBtn, BorderLayout.WEST)
        navPanel.add(monthLabel, BorderLayout.CENTER)
        navPanel.add(nextBtn, BorderLayout.EAST)

        val dayGrid = JPanel(GridLayout(0, 7, 2, 2))

        fun refreshGrid() {
            dayGrid.removeAll()
            val dayNames = arrayOf("一", "二", "三", "四", "五", "六", "日")
            dayNames.forEach { name ->
                dayGrid.add(JLabel(name, SwingConstants.CENTER).apply {
                    font = font.deriveFont(Font.BOLD, 11f)
                    foreground = if (name == "六" || name == "日") Color(0x999999) else Color.DARK_GRAY
                })
            }

            val firstDay = yearMonthRef[0].atDay(1)
            val startOffset = (firstDay.dayOfWeek.value + 6) % 7
            val daysInMonth = yearMonthRef[0].lengthOfMonth()

            repeat(startOffset) { dayGrid.add(JLabel("")) }

            (1..daysInMonth).forEach { day ->
                val isToday = yearMonthRef[0] == YearMonth.now() && day == LocalDate.now().dayOfMonth
                val isSelected = yearMonthRef[0] == YearMonth.from(currentDate) && day == currentDate.dayOfMonth

                val btn = JButton(day.toString()).apply {
                    font = font.deriveFont(12f)
                    isFocusPainted = false
                    margin = Insets(2, 4, 2, 4)
                    if (isSelected) {
                        background = Color(53, 114, 179)
                        foreground = Color.WHITE
                        isOpaque = true
                        setBorderPainted(false)
                    } else if (isToday) {
                        border = BorderFactory.createLineBorder(Color(53, 114, 179))
                    }
                    addActionListener {
                        val m = yearMonthRef[0].monthValue.toString().padStart(2, '0')
                        val d = day.toString().padStart(2, '0')
                        dateField.text = "${yearMonthRef[0].year}-$m-$d"
                        popup.isVisible = false
                    }
                }
                dayGrid.add(btn)
            }

            dayGrid.revalidate()
            dayGrid.repaint()
        }

        prevBtn.addActionListener {
            yearMonthRef[0] = yearMonthRef[0].minusMonths(1)
            monthLabel.text = yearMonthRef[0].format(DateTimeFormatter.ofPattern("yyyy年MM月"))
            refreshGrid()
            content.revalidate()
            popup.setPopupSize(content.preferredSize)
        }

        nextBtn.addActionListener {
            yearMonthRef[0] = yearMonthRef[0].plusMonths(1)
            monthLabel.text = yearMonthRef[0].format(DateTimeFormatter.ofPattern("yyyy年MM月"))
            refreshGrid()
            content.revalidate()
            popup.setPopupSize(content.preferredSize)
        }

        content.add(navPanel, BorderLayout.NORTH)
        content.add(dayGrid, BorderLayout.CENTER)
        refreshGrid()
        popup.add(content)
        popup.show(calendarButton, 0, calendarButton.height)
    }
}
