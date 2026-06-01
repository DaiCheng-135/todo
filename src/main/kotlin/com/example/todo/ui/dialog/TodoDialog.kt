package com.example.todo.ui.dialog

import com.example.todo.model.Priority
import com.example.todo.model.TodoItem
import com.intellij.openapi.ui.DialogWrapper
import java.awt.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.swing.*
import javax.swing.border.LineBorder

class TodoDialog(
    private val existing: TodoItem? = null
) : DialogWrapper(true) {

    private val titleField = JTextField(existing?.title ?: "")
    private val descField = JTextArea(existing?.description ?: "")
    private val priorityBox = JComboBox(Priority.values())

    private val dateField = JTextField(
        existing?.deadline?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: ""
    ).apply {
        preferredSize = Dimension(130, 25)
    }

    private val hourBox = JComboBox(
        (0..23).map { it.toString().padStart(2, '0') }.toTypedArray()
    ).apply {
        existing?.deadline?.let { selectedItem = it.format(DateTimeFormatter.ofPattern("HH")) }
        preferredSize = Dimension(60, 25)
    }

    private val minuteBox = JComboBox(
        (0..59).map { it.toString().padStart(2, '0') }.toTypedArray()
    ).apply {
        existing?.deadline?.let { selectedItem = it.format(DateTimeFormatter.ofPattern("mm")) }
        preferredSize = Dimension(60, 25)
    }

    private val calendarButton = JButton("📅").apply {
        preferredSize = Dimension(30, 25)
        addActionListener { showCalendarPopup() }
    }

    private val tagsField = JTextField(
        existing?.tags?.joinToString(",") ?: ""
    )

    private val remindCheckBox = JCheckBox("到点提醒（弹出通知）", existing?.remind ?: false)

    init {
        title = if (existing == null) "新增待办" else "编辑待办"

        existing?.let {
            priorityBox.selectedItem = it.priority
        }

        init()
    }

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

        // 月份导航
        val navPanel = JPanel(BorderLayout())
        val prevBtn = JButton("<")
        val nextBtn = JButton(">")
        prevBtn.font = prevBtn.font.deriveFont(Font.BOLD, 14.0f)
        nextBtn.font = nextBtn.font.deriveFont(Font.BOLD, 14.0f)

        val monthLabel = JLabel(yearMonthRef[0].format(
            DateTimeFormatter.ofPattern("yyyy年MM月")
        ), SwingConstants.CENTER).apply {
            font = font.deriveFont(Font.BOLD, 13f)
        }

        navPanel.add(prevBtn, BorderLayout.WEST)
        navPanel.add(monthLabel, BorderLayout.CENTER)
        navPanel.add(nextBtn, BorderLayout.EAST)

        // 日期网格
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
            val startOffset = (firstDay.dayOfWeek.value + 6) % 7 // Monday=0
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
                        val month = yearMonthRef[0].monthValue.toString().padStart(2, '0')
                        val d = day.toString().padStart(2, '0')
                        dateField.text = "${yearMonthRef[0].year}-$month-$d"
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

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val constraints = GridBagConstraints()
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.insets = Insets(5, 5, 5, 5)
        constraints.weightx = 1.0

        var row = 0

        // 标题
        constraints.gridx = 0
        constraints.gridy = row
        constraints.gridwidth = 1
        panel.add(JLabel("标题:"), constraints)

        constraints.gridx = 1
        constraints.weightx = 1.0
        titleField.preferredSize = java.awt.Dimension(300, 25)
        panel.add(titleField, constraints)

        // 描述
        row++
        constraints.gridx = 0
        constraints.gridy = row
        constraints.weightx = 0.0
        panel.add(JLabel("描述:"), constraints)

        constraints.gridx = 1
        constraints.weightx = 1.0
        constraints.fill = GridBagConstraints.BOTH
        descField.rows = 4
        descField.lineWrap = true
        descField.wrapStyleWord = true
        val descScroll = JScrollPane(descField)
        descScroll.preferredSize = java.awt.Dimension(300, 80)
        panel.add(descScroll, constraints)

        // 优先级
        row++
        constraints.gridx = 0
        constraints.gridy = row
        constraints.fill = GridBagConstraints.HORIZONTAL
        constraints.weightx = 0.0
        panel.add(JLabel("优先级:"), constraints)

        constraints.gridx = 1
        constraints.weightx = 1.0
        priorityBox.preferredSize = java.awt.Dimension(300, 25)
        panel.add(priorityBox, constraints)

        // 截止时间
        row++
        constraints.gridx = 0
        constraints.gridy = row
        constraints.weightx = 0.0
        constraints.fill = GridBagConstraints.HORIZONTAL
        panel.add(JLabel("截止时间:"), constraints)

        constraints.gridx = 1
        constraints.weightx = 1.0
        constraints.fill = GridBagConstraints.HORIZONTAL
        val deadlinePanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(dateField)
            add(Box.createHorizontalStrut(2))
            add(calendarButton)
            add(Box.createHorizontalStrut(8))
            add(JLabel("时间:"))
            add(Box.createHorizontalStrut(2))
            add(hourBox)
            add(JLabel(":"))
            add(minuteBox)
            add(Box.createHorizontalGlue())
        }
        panel.add(deadlinePanel, constraints)

        // 标签
        row++
        constraints.gridx = 0
        constraints.gridy = row
        constraints.weightx = 0.0
        panel.add(JLabel("标签:"), constraints)

        constraints.gridx = 1
        constraints.weightx = 1.0
        tagsField.preferredSize = java.awt.Dimension(300, 25)
        panel.add(tagsField, constraints)

        // 到点提醒
        row++
        constraints.gridx = 0
        constraints.gridy = row
        constraints.weightx = 0.0
        constraints.gridwidth = 2
        panel.add(remindCheckBox, constraints)

        return panel
    }

    fun getTodo(): TodoItem {
        val deadline = try {
            val dateStr = dateField.text.trim()
            if (dateStr.isNotBlank()) {
                val h = hourBox.selectedItem
                val m = minuteBox.selectedItem
                LocalDateTime.parse("$dateStr ${h}:${m}", DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            } else null
        } catch (_: Exception) {
            null
        }

        val tags =
            tagsField.text.split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toMutableList()

        return existing?.apply {
            title = titleField.text.trim()
            description = descField.text.trim()
            priority = priorityBox.selectedItem as Priority
            this.deadline = deadline
            this.tags = tags
            updatedAt = LocalDateTime.now()
            remind = remindCheckBox.isSelected
        } ?: TodoItem(
            title = titleField.text.trim(),
            description = descField.text.trim(),
            priority = priorityBox.selectedItem as Priority,
            tags = tags,
            remind = remindCheckBox.isSelected
        ).apply {
            this.deadline = deadline
        }
    }
}