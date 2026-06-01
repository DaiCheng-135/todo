package com.example.todo.ui

import com.example.todo.service.ReminderConfig
import com.example.todo.service.TodoService
import com.example.todo.util.DrinkReminder
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.awt.*
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.filechooser.FileNameExtensionFilter

class ReminderSettingsPanel(private val project: Project) : JPanel() {

    private val todoService = project.service<TodoService>()
    private val configListModel = DefaultListModel<ReminderConfig>()
    private val configList = JList(configListModel)
    private var currentPreviewLabel = JLabel("", SwingConstants.CENTER)
    private val countdownTimer = Timer(1000) { configList.repaint() }

    // 编辑表单
    private val titleField = JTextField(20)
    private val messageField = JTextField(30)
    private val intervalSpinner = JSpinner(SpinnerNumberModel(30, 1, 999, 1))
    private val unitComboBox = JComboBox(arrayOf("分钟", "小时"))
    private val imagePathField = JTextField(18).apply { isEditable = false }
    private val enabledCheck = JCheckBox("启用此提醒")
    private var editingIndex: Int = -1

    // 操作按钮
    private val saveBtn = JButton("保存")
    private val cancelBtn = JButton("取消")
    private val addBtn = JButton("＋ 新建提醒")
    private val deleteBtn = JButton("删除")

    private val imagePreviewPanel = JPanel().apply {
        preferredSize = Dimension(200, 120)
        minimumSize = preferredSize
        border = BorderFactory.createLineBorder(Color(200, 200, 200))
        layout = BorderLayout()
        add(currentPreviewLabel, BorderLayout.CENTER)
    }

    init {
        layout = BorderLayout(10, 0)
        border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // === 左侧：提醒列表 ===
        val leftPanel = JPanel(BorderLayout())
        leftPanel.border = TitledBorder("提醒列表")
        leftPanel.preferredSize = Dimension(260, 400)

        configList.cellRenderer = ReminderListRenderer()
        configList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        configList.addListSelectionListener {
            if (!configList.valueIsAdjusting) {
                val idx = configList.selectedIndex
                if (idx >= 0) loadConfigToForm(idx)
            }
        }

        val listBtnRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        listBtnRow.add(addBtn)
        listBtnRow.add(deleteBtn)
        val resetSelBtn = JButton("↻ 重置选中").apply {
            font = font.deriveFont(11f)
            addActionListener {
                val idx = configList.selectedIndex
                if (idx >= 0) {
                    val config = configListModel[idx]
                    if (config.enabled) {
                        DrinkReminder.start(config)
                    }
                }
            }
        }
        val resetAllBtn = JButton("🔄 重置全部").apply {
            font = font.deriveFont(11f)
            addActionListener {
                DrinkReminder.stopAll()
                DrinkReminder.restartFromConfig()
                refreshList()
                cancelEdit()
            }
        }
        listBtnRow.add(resetSelBtn)
        listBtnRow.add(resetAllBtn)

        leftPanel.add(JScrollPane(configList), BorderLayout.CENTER)
        leftPanel.add(listBtnRow, BorderLayout.SOUTH)

        // === 右侧：编辑表单 ===
        val rightPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = TitledBorder("编辑提醒")
        }

        // 标题
        rightPanel.add(formRow("提醒标题:", titleField))
        rightPanel.add(Box.createVerticalStrut(6))

        // 消息
        rightPanel.add(formRow("提醒内容:", messageField))
        rightPanel.add(JLabel("  %d 会被替换为间隔数值", SwingConstants.LEFT).apply {
            font = font.deriveFont(11f)
            foreground = Color.GRAY
            alignmentX = Component.LEFT_ALIGNMENT
        })
        rightPanel.add(Box.createVerticalStrut(6))

        // 间隔
        val intervalRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel("提醒间隔:"))
            intervalSpinner.preferredSize = Dimension(60, 25)
            add(intervalSpinner)
            unitComboBox.preferredSize = Dimension(70, 25)
            add(unitComboBox)
        }
        rightPanel.add(intervalRow)
        rightPanel.add(Box.createVerticalStrut(6))

        // 启用开关
        enabledCheck.font = font.deriveFont(Font.BOLD, 12f)
        enabledCheck.alignmentX = Component.LEFT_ALIGNMENT
        enabledCheck.addActionListener {
            val idx = configList.selectedIndex
            if (idx >= 0) {
                // 先把表单最新值写入 model，再启动/停止
                syncFormToModel(idx)
                val config = configListModel[idx]
                if (enabledCheck.isSelected) {
                    DrinkReminder.start(config)
                } else {
                    DrinkReminder.stop(config.id)
                }
                configList.repaint()
            }
        }
        rightPanel.add(enabledCheck)
        rightPanel.add(Box.createVerticalStrut(8))

        // 图片
        val imageRow = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            val pickerRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                isOpaque = false
                add(JLabel("提醒图片:"))
                add(imagePathField)
                add(JButton("选择").apply {
                    font = font.deriveFont(11f)
                    addActionListener { chooseImage() }
                })
                add(JButton("清除").apply {
                    font = font.deriveFont(11f)
                    addActionListener {
                        imagePathField.text = ""
                        refreshImagePreview("")
                    }
                })
            }
            add(pickerRow)
            add(Box.createVerticalStrut(4))
            val previewBorder = JPanel(BorderLayout()).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                border = TitledBorder("图片预览")
                add(imagePreviewPanel, BorderLayout.CENTER)
            }
            add(previewBorder)
        }
        rightPanel.add(imageRow)
        rightPanel.add(Box.createVerticalStrut(4))
        rightPanel.add(Box.createVerticalStrut(6))

        // 说明
        rightPanel.add(JLabel("推荐 120×100 像素 | PNG/JPG/GIF/BMP", SwingConstants.LEFT).apply {
            font = font.deriveFont(11f)
            foreground = Color(150, 155, 165)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        rightPanel.add(Box.createVerticalStrut(10))

        // 保存/取消按钮
        val actionRow = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(saveBtn)
            add(cancelBtn)
        }
        rightPanel.add(actionRow)

        // === 事件绑定 ===
        addBtn.addActionListener { startNewReminder() }
        deleteBtn.addActionListener { deleteSelected() }
        saveBtn.addActionListener { saveCurrent() }
        cancelBtn.addActionListener { cancelEdit() }

        // 分割面板
        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel)
        split.dividerLocation = 280
        add(split, BorderLayout.CENTER)

        // 加载数据
        refreshList()
        countdownTimer.start()
    }

    private fun formRow(label: String, comp: JComponent): JPanel {
        return JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel(label))
            add(comp)
        }
    }

    private fun refreshList() {
        configListModel.clear()
        todoService.getReminderConfigs().forEach { configListModel.addElement(it) }
    }

    private fun loadConfigToForm(index: Int) {
        if (index < 0 || index >= configListModel.size) return
        editingIndex = index
        val config = configListModel[index]
        titleField.text = config.title
        messageField.text = config.message
        intervalSpinner.value = config.intervalValue
        unitComboBox.selectedItem = config.intervalUnit
        imagePathField.text = config.imagePath
        enabledCheck.isSelected = config.enabled
        refreshImagePreview(config.imagePath)
        saveBtn.text = "保存修改"
    }

    private fun syncFormToModel(index: Int) {
        if (index < 0 || index >= configListModel.size) return
        val config = configListModel[index]
        config.title = titleField.text.trim()
        config.message = messageField.text.trim()
        config.intervalValue = intervalSpinner.value as Int
        config.intervalUnit = unitComboBox.selectedItem as String
        config.imagePath = imagePathField.text.trim()
        config.enabled = enabledCheck.isSelected
        todoService.updateReminderConfig(index, config)
    }

    private fun startNewReminder() {
        editingIndex = -1
        titleField.text = ""
        messageField.text = "%d 分钟没休息了，快去活动一下吧"
        intervalSpinner.value = 30
        unitComboBox.selectedItem = "分钟"
        imagePathField.text = ""
        refreshImagePreview("")
        saveBtn.text = "创建提醒"
        configList.clearSelection()
    }

    private fun saveCurrent() {
        val title = titleField.text.trim()
        if (title.isBlank()) {
            JOptionPane.showMessageDialog(this, "请输入提醒标题")
            return
        }
        val config = ReminderConfig(
            title = title,
            message = messageField.text.trim().ifBlank { "%d 分钟到了" },
            enabled = enabledCheck.isSelected,
            intervalValue = intervalSpinner.value as Int,
            intervalUnit = unitComboBox.selectedItem as String,
            imagePath = imagePathField.text.trim()
        )

        if (editingIndex >= 0 && editingIndex < configListModel.size) {
            val existing = configListModel[editingIndex]
            config.id = existing.id
            todoService.updateReminderConfig(editingIndex, config)
        } else {
            todoService.addReminderConfig(config)
        }
        // 根据启用状态启动/停止定时器
        if (config.enabled) DrinkReminder.start(config)
        else DrinkReminder.stop(config.id)
        refreshList()
        cancelEdit()
    }

    private fun cancelEdit() {
        editingIndex = -1
        titleField.text = ""
        messageField.text = ""
        intervalSpinner.value = 30
        unitComboBox.selectedItem = "分钟"
        imagePathField.text = ""
        enabledCheck.isSelected = false
        refreshImagePreview("")
        saveBtn.text = "保存修改"
        configList.clearSelection()
    }

    private fun deleteSelected() {
        val idx = configList.selectedIndex
        if (idx < 0) return
        val config = configListModel[idx]
        val result = JOptionPane.showConfirmDialog(
            this, "确定删除提醒「${config.title}」？",
            "删除提醒", JOptionPane.YES_NO_OPTION
        )
        if (result != JOptionPane.YES_OPTION) return
        DrinkReminder.stop(config.id)
        todoService.removeReminderConfig(idx)
        refreshList()
        cancelEdit()
    }

    private fun chooseImage() {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter(
                "图片文件 (PNG, JPG, GIF, BMP)", "png", "jpg", "jpeg", "gif", "bmp"
            )
            dialogTitle = "选择提醒图片"
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            imagePathField.text = chooser.selectedFile.absolutePath
            refreshImagePreview(chooser.selectedFile.absolutePath)
        }
    }

    private fun refreshImagePreview(path: String) {
        imagePreviewPanel.removeAll()
        if (path.isBlank() || !File(path).exists()) {
            currentPreviewLabel = JLabel("💧\n未设置图片", SwingConstants.CENTER).apply {
                foreground = Color(180, 190, 200)
            }
        } else {
            try {
                val img = ImageIO.read(File(path))
                if (img != null) {
                    val maxW = 180; val maxH = 100
                    val ratio = minOf(maxW.toDouble() / img.width, maxH.toDouble() / img.height, 1.0)
                    val thumb = img.getScaledInstance(
                        (img.width * ratio).toInt(), (img.height * ratio).toInt(), Image.SCALE_SMOOTH
                    )
                    currentPreviewLabel = JLabel("${img.width}×${img.height}", ImageIcon(thumb), SwingConstants.CENTER).apply {
                        foreground = Color(100, 110, 130)
                        horizontalTextPosition = SwingConstants.CENTER
                        verticalTextPosition = SwingConstants.BOTTOM
                    }
                } else {
                    currentPreviewLabel = JLabel("无法读取", SwingConstants.CENTER).apply { foreground = Color.RED }
                }
            } catch (_: Exception) {
                currentPreviewLabel = JLabel("格式不支持", SwingConstants.CENTER).apply { foreground = Color.RED }
            }
        }
        imagePreviewPanel.add(currentPreviewLabel, BorderLayout.CENTER)
        imagePreviewPanel.revalidate()
        imagePreviewPanel.repaint()
    }

    private inner class ReminderListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
        ): Component {
            val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            if (value is ReminderConfig) {
                val status = if (value.enabled) "●" else "○"
                val unit = if (value.intervalUnit == "小时") "h" else "min"
                val countdown = if (value.enabled) {
                    val sec = DrinkReminder.getRemainingSeconds(value.id)
                    if (sec >= 0) "  剩余 %02d:%02d".format(sec / 60, sec % 60) else "  启动中..."
                } else ""
                comp.text = "$status  ${value.title}  (每 ${value.intervalValue}$unit)$countdown"
                comp.font = comp.font.deriveFont(13f)
                if (!value.enabled) comp.foreground = Color.GRAY
            }
            return comp
        }
    }
}
