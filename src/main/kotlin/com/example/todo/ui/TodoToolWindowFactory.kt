package com.example.todo.ui

import com.example.todo.model.TodoItem
import com.example.todo.service.ReminderService
import com.example.todo.service.TodoService
import com.example.todo.ui.dialog.TodoDialog
import com.example.todo.util.DrinkReminder
import com.example.todo.ui.renderer.TodoRenderer
import com.example.todo.ui.toolbar.TodoToolbar
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class TodoToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        project.service<ReminderService>()

        val service = project.service<TodoService>()

        DrinkReminder.init(project)
        DrinkReminder.restartFromConfig()
        Disposer.register(project, Disposable { DrinkReminder.stopAll() })

        val model = DefaultListModel<TodoItem>()

        val list = JBList(model).apply {
            cellRenderer = TodoRenderer()
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            fixedCellHeight = -1
        }

        fun reload(todos: List<TodoItem> = service.getTodos()) {
            model.clear()
            todos.forEach(model::addElement)
        }

        reload()

        /**
         * Toolbar
         */
        val toolbar = TodoToolbar(
            onAdd = {
                val dialog = TodoDialog()
                if (dialog.showAndGet()) {
                    val todo = service.addTodo(dialog.getTodo())
                    model.addElement(todo)
                }
            },
            onSearch = { keyword ->
                reload(service.search(keyword))
            },
            onFilter = { filter ->
                reload(service.filter(filter))
            }
        )

        /**
         * 右键菜单
         */
        val popupMenu = JPopupMenu()

        val editMenu = JMenuItem("编辑")
        val deleteMenu = JMenuItem("删除")
        val clearDoneMenu = JMenuItem("批量指定清空")

        popupMenu.add(editMenu)
        popupMenu.add(deleteMenu)
        popupMenu.addSeparator()
        popupMenu.add(clearDoneMenu)

        editMenu.addActionListener {
            val index = list.selectedIndex
            if (index < 0) return@addActionListener

            val todo = model[index]

            val dialog = TodoDialog(todo)

            if (dialog.showAndGet()) {
                val updated = dialog.getTodo()

                service.updateTodo(updated)

                model.set(index, updated)
            }
        }

        deleteMenu.addActionListener {
            val index = list.selectedIndex
            if (index < 0) return@addActionListener

            val todo = model[index]

            service.removeTodo(todo.id)

            model.remove(index)
        }

        clearDoneMenu.addActionListener {
            val completed = service.getTodos().filter { it.done }

            completed.forEach {
                service.removeTodo(it.id)
            }

            reload()
        }

        /**
         * 鼠标事件
         */
        list.addMouseListener(object : MouseAdapter() {

            override fun mousePressed(e: MouseEvent) {
                val index = list.locationToIndex(e.point)

                if (index < 0) return

                val bounds = list.getCellBounds(index, index)

                if (!bounds.contains(e.point)) return

                val todo = model[index]

                /**
                 * 右键菜单
                 */
                if (SwingUtilities.isRightMouseButton(e)) {
                    list.selectedIndex = index
                    popupMenu.show(list, e.x, e.y)
                    return
                }

                /**
                 * 左键 Toggle
                 */
                if (SwingUtilities.isLeftMouseButton(e)) {
                    service.toggleTodo(todo.id)

                    model.set(index, todo)
                }
            }
        })

        /**
         * Delete 删除
         */
        list.inputMap.put(
            KeyStroke.getKeyStroke("DELETE"),
            "deleteTodo"
        )

        list.actionMap.put(
            "deleteTodo",
            object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val index = list.selectedIndex
                    if (index < 0) return

                    val todo = model[index]

                    service.removeTodo(todo.id)

                    model.remove(index)
                }
            }
        )

        /**
         * Space Toggle
         */
        list.inputMap.put(
            KeyStroke.getKeyStroke("SPACE"),
            "toggleTodo"
        )

        list.actionMap.put(
            "toggleTodo",
            object : AbstractAction() {
                override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                    val index = list.selectedIndex
                    if (index < 0) return

                    val todo = model[index]

                    service.toggleTodo(todo.id)

                    model.set(index, todo)
                }
            }
        )

        /**
         * 缩进彩虹配置面板
         */
        val rainbowEnabled = service.getIndentRainbowEnabled()
        val rainbowThickness = service.getIndentRainbowThickness()
        val rainbowFileTypes = service.getIndentRainbowFileTypes()

        val rainbowToggle = JCheckBox("启用缩进彩虹", rainbowEnabled).apply {
            font = font.deriveFont(13f)
        }
        val thicknessBox = JComboBox(arrayOf("1 px", "2 px", "3 px", "4 px")).apply {
            selectedIndex = (rainbowThickness - 1).coerceIn(0, 3)
        }
        
        // Common file types for easy selection
        val commonTypes = listOf("Java", "Kotlin", "Python", "YAML", "YML", "JSON", "CSS", "JavaScript", "TypeScript", "XML", "HTML")
        val currentTypes = rainbowFileTypes.split(",").map { it.trim() }.toMutableSet()
        
        // Use CheckListModel for easy toggle
        val typeCheckModel = object : DefaultListModel<String>() {
            val checked = mutableSetOf<String>()
            
            fun isChecked(index: Int): Boolean {
                return checked.contains(getElementAt(index))
            }
            
            fun toggle(index: Int) {
                val item = getElementAt(index)
                if (checked.contains(item)) {
                    checked.remove(item)
                } else {
                    checked.add(item)
                }
            }
        }
        
        commonTypes.forEach { 
            typeCheckModel.addElement(it)
            if (currentTypes.any { ct -> ct.equals(it, ignoreCase = true) }) {
                typeCheckModel.checked.add(it)
            }
        }
        
        val typeList = JList(typeCheckModel).apply {
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
                ): java.awt.Component {
                    val comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                    val text = value?.toString() ?: ""
                    val checked = typeCheckModel.isChecked(index)
                    comp.text = if (checked) "☑ $text" else "☐ $text"
                    return comp
                }
            }
            font = font.deriveFont(12f)
            // Single click to toggle
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val index = locationToIndex(e.point)
                    if (index >= 0) {
                        typeCheckModel.toggle(index)
                        // Update file types
                        val types = mutableListOf<String>()
                        for (i in 0 until typeCheckModel.size) {
                            if (typeCheckModel.isChecked(i)) {
                                types.add(typeCheckModel.getElementAt(i))
                            }
                        }
                        service.setIndentRainbowFileTypes(types.joinToString(","))
                        IndentRainbow.refreshAll()
                        repaint()
                    }
                }
            })
        }
        
        val customTypeField = JTextField("", 10).apply {
            toolTipText = "添加自定义文件类型（按回车）"
        }
        
        val deleteBtn = JButton("删除已选中").apply {
            font = font.deriveFont(11f)
            toolTipText = "删除所有已打勾的类型"
            addActionListener {
                val toRemove = mutableListOf<Int>()
                
                // Collect indices of checked items to remove
                for (i in (typeCheckModel.size - 1) downTo 0) {
                    if (typeCheckModel.isChecked(i)) {
                        toRemove.add(i)
                    }
                }
                
                if (toRemove.isNotEmpty()) {
                    // Remove checked items from model
                    toRemove.forEach { idx ->
                        typeCheckModel.remove(idx)
                    }
                    
                    // Recalculate enabled types from remaining checked items
                    val enabledTypes = mutableListOf<String>()
                    for (i in 0 until typeCheckModel.size) {
                        if (typeCheckModel.isChecked(i)) {
                            enabledTypes.add(typeCheckModel.getElementAt(i))
                        }
                    }
                    service.setIndentRainbowFileTypes(enabledTypes.joinToString(","))
                    IndentRainbow.refreshAll()
                    typeList.repaint()
                }
            }
        }
        
        val rainbowConfigPanel = JPanel().apply {
            layout = BorderLayout(10, 10)
            border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
            
            // Top section: Toggle + Thickness
            val topSection = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                
                // Toggle row
                val toggleRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
                toggleRow.isOpaque = false
                toggleRow.add(rainbowToggle)
                
                // Thickness row
                val thicknessRow = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
                thicknessRow.isOpaque = false
                thicknessRow.add(JLabel("线条粗细:").apply { font = font.deriveFont(12f) })
                thicknessRow.add(thicknessBox)
                
                add(toggleRow)
                add(Box.createVerticalStrut(8))
                add(thicknessRow)
            }
            add(topSection, BorderLayout.NORTH)
            
            // Center section: File types list
            val centerSection = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                
                add(JLabel("文件类型管理:").apply { 
                    font = this.font?.deriveFont(Font.BOLD, 13f) ?: Font("SansSerif", Font.BOLD, 13)
                })
                add(Box.createVerticalStrut(6))
                
                val listWithButtons = JPanel(BorderLayout(0, 6))
                listWithButtons.isOpaque = false
                
                // Scrollable list
                val scrollPane = JScrollPane(typeList).apply {
                    preferredSize = java.awt.Dimension(250, 180)
                    border = BorderFactory.createLineBorder(java.awt.Color(200, 200, 200))
                }
                listWithButtons.add(scrollPane, BorderLayout.CENTER)
                
                // Buttons row
                val btnRow = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 0))
                btnRow.isOpaque = false
                btnRow.add(deleteBtn)
                btnRow.add(JButton("全部启用").apply {
                    font = font.deriveFont(11f)
                    addActionListener {
                        for (i in 0 until typeCheckModel.size) {
                            if (!typeCheckModel.isChecked(i)) {
                                typeCheckModel.toggle(i)
                            }
                        }
                        // Update file types
                        val types = mutableListOf<String>()
                        for (i in 0 until typeCheckModel.size) {
                            if (typeCheckModel.isChecked(i)) {
                                types.add(typeCheckModel.getElementAt(i))
                            }
                        }
                        service.setIndentRainbowFileTypes(types.joinToString(","))
                        IndentRainbow.refreshAll()
                        typeList.repaint()
                    }
                })
                btnRow.add(JButton("全部禁用").apply {
                    font = font.deriveFont(11f)
                    addActionListener {
                        for (i in 0 until typeCheckModel.size) {
                            if (typeCheckModel.isChecked(i)) {
                                typeCheckModel.toggle(i)
                            }
                        }
                        service.setIndentRainbowFileTypes("")
                        IndentRainbow.refreshAll()
                        typeList.repaint()
                    }
                })
                listWithButtons.add(btnRow, BorderLayout.SOUTH)
                
                add(listWithButtons)
                add(Box.createVerticalStrut(8))
                
                // Custom type input
                val customRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
                customRow.isOpaque = false
                customRow.add(JLabel("添加类型:").apply { font = font.deriveFont(11f) })
                customRow.add(customTypeField)
                customRow.add(JButton("+ 添加").apply {
                    font = font.deriveFont(11f)
                    addActionListener {
                        customTypeField.actionListeners.forEach { 
                            it.actionPerformed(java.awt.event.ActionEvent(this, 0, "")) 
                        }
                    }
                })
                add(customRow)
            }
            add(centerSection, BorderLayout.CENTER)
            
            // Bottom hint
            val hintLabel = JLabel("  ☑ 点击列表项切换启用/禁用  ☑ 配置实时生效", SwingConstants.CENTER).apply {
                font = font.deriveFont(11f)
                foreground = java.awt.Color(120, 120, 120)
                border = BorderFactory.createEmptyBorder(10, 0, 0, 0)
            }
            add(hintLabel, BorderLayout.SOUTH)
        }

        rainbowToggle.addActionListener {
            service.setIndentRainbowEnabled(rainbowToggle.isSelected)
            IndentRainbow.refreshAll()
        }
        thicknessBox.addActionListener {
            val t = thicknessBox.selectedIndex + 1
            service.setIndentRainbowThickness(t)
            IndentRainbow.refreshAll()
        }
        customTypeField.addActionListener {
            val text = customTypeField.text.trim()
            if (text.isNotEmpty()) {
                // Add to list if not exists
                var exists = false
                for (i in 0 until typeCheckModel.size) {
                    if (typeCheckModel.getElementAt(i).equals(text, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }
                if (!exists) {
                    typeCheckModel.addElement(text)
                    typeCheckModel.checked.add(text)
                }
                customTypeField.text = ""
                // Update file types
                val types = mutableListOf<String>()
                for (i in 0 until typeCheckModel.size) {
                    if (typeCheckModel.isChecked(i)) {
                        types.add(typeCheckModel.getElementAt(i))
                    }
                }
                service.setIndentRainbowFileTypes(types.joinToString(","))
                IndentRainbow.refreshAll()
                typeList.repaint()
            }
        }

        /**
         * 主面板 — 标签页
         */
        val tabbedPane = JTabbedPane()

        // Tab 1: Git 提交总结 (Left)
        tabbedPane.addTab("Git 提交总结", GitCommitSummaryPanel(project))
        
        // Tab 2: 待办事项 (Center)
        val todoPanel = JPanel(BorderLayout())
        val northPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(toolbar)
        }
        todoPanel.add(northPanel, BorderLayout.NORTH)
        todoPanel.add(JScrollPane(list), BorderLayout.CENTER)
        tabbedPane.addTab("待办事项", todoPanel)

        // Tab 3: 提醒
        tabbedPane.addTab("提醒", ReminderSettingsPanel(project))

        // Tab 4: 缩进彩虹配置
        val rainbowPanel = JPanel(BorderLayout())
        rainbowPanel.add(rainbowConfigPanel, BorderLayout.NORTH)
        rainbowPanel.add(JLabel("  配置将在编辑文件时自动生效", SwingConstants.CENTER).apply {
            font = font.deriveFont(12f)
            foreground = java.awt.Color.GRAY
        }, BorderLayout.CENTER)
        tabbedPane.addTab("缩进彩虹", rainbowPanel)

        val content = ContentFactory.getInstance()
            .createContent(tabbedPane, "", false)

        toolWindow.contentManager.addContent(content)

        // 编辑器缩进彩虹
        IndentRainbow.install(project, project)
    }
}
