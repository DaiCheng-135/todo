package com.example.todo.ui.toolbar

import com.example.todo.model.FilterType
import java.awt.*
import javax.swing.*

class TodoToolbar(
    onAdd: () -> Unit,
    onSearch: (String) -> Unit,
    onFilter: (FilterType) -> Unit
) : JPanel() {

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)

        val row1 = JPanel(BorderLayout())
        val addButton = JButton("新增")
        val searchField = JTextField().apply { toolTipText = "输入关键字搜索待办" }
        val filterBox = JComboBox(FilterType.values())

        addButton.addActionListener { onAdd() }
        searchField.addActionListener { onSearch(searchField.text) }
        filterBox.addActionListener { onFilter(filterBox.selectedItem as FilterType) }

        row1.add(addButton, BorderLayout.WEST)
        row1.add(searchField, BorderLayout.CENTER)
        row1.add(filterBox, BorderLayout.EAST)
        row1.maximumSize = Dimension(Int.MAX_VALUE, 30)

        add(row1)
    }
}
