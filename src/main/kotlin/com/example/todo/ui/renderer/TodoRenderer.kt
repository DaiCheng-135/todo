package com.example.todo.ui.renderer

import com.example.todo.model.Priority
import com.example.todo.model.TodoItem
import com.intellij.ui.JBColor
import java.awt.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.swing.*

class TodoRenderer : ListCellRenderer<TodoItem> {

    override fun getListCellRendererComponent(
        list: JList<out TodoItem>,
        value: TodoItem,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {

        val root = JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
            background =
                if (isSelected) list.selectionBackground
                else list.background
        }

        /**
         * 第一行：Checkbox + 标题 + 优先级 + 截止时间
         */
        val topPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
        }

        val titleBox = JCheckBox(value.title, value.done).apply {
            isOpaque = false
            font = font.deriveFont(Font.PLAIN, 14f)
        }

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
        }

        // Deadline badge
        value.deadline?.let {
            val isOverdue = !value.done && it.isBefore(LocalDateTime.now())
            val deadlineLabel = JLabel("截止：${it.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}").apply {
                foreground = if (isOverdue) JBColor.RED else JBColor.GRAY
                font = font.deriveFont(Font.PLAIN, 11f)
                border = BorderFactory.createEmptyBorder(0, 4, 0, 0)
            }
            rightPanel.add(deadlineLabel)
        }

        // Priority badge
        val priorityLabel = JLabel(priorityText(value.priority)).apply {
            foreground = priorityColor(value.priority)
            font = font.deriveFont(Font.BOLD, 12f)
        }
        rightPanel.add(priorityLabel)

        topPanel.add(titleBox, BorderLayout.CENTER)
        topPanel.add(rightPanel, BorderLayout.EAST)

        /**
         * 第二行：其他 Meta 信息（标签、位置）
         */
        val metaParts = mutableListOf<String>()

        if (value.tags.isNotEmpty()) {
            metaParts += "标签：${value.tags.joinToString(" / ")}"
        }

        value.linkedLocation?.let {
            metaParts += "位置：${it.filePath.substringAfterLast("/")}:${it.lineNumber}"
        }

        val metaLabel = JLabel(metaParts.joinToString("    ")).apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(Font.PLAIN, 11f)
            border = BorderFactory.createEmptyBorder(4, 24, 0, 0)
        }

        val contentPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            add(topPanel, BorderLayout.NORTH)

            if (metaParts.isNotEmpty()) {
                add(metaLabel, BorderLayout.SOUTH)
            }
        }

        root.add(contentPanel, BorderLayout.CENTER)

        return root
    }

    private fun priorityText(priority: Priority): String {
        return when (priority) {
            Priority.LOW -> "低"
            Priority.MEDIUM -> "中"
            Priority.HIGH -> "高"
            Priority.CRITICAL -> "紧急"
        }
    }

    private fun priorityColor(priority: Priority): Color {
        return when (priority) {
            Priority.LOW -> JBColor.GRAY
            Priority.MEDIUM -> JBColor(0xC79200, 0xE6B422)
            Priority.HIGH -> JBColor(0xD35400, 0xFF8C42)
            Priority.CRITICAL -> JBColor.RED
        }
    }
}