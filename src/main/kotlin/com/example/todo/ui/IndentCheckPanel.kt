package com.example.todo.ui

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.table.JBTable
import com.intellij.openapi.editor.ScrollType
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel
import kotlin.concurrent.thread

class IndentCheckPanel(private val project: Project) : JPanel() {

    private val fileLabel = JLabel("当前文件: -").apply {
        font = font.deriveFont(Font.BOLD, 13f)
    }

    private val refreshBtn = JButton("⟳ 刷新").apply {
        isFocusPainted = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
    }

    private val summaryLabel = JLabel("").apply {
        font = font.deriveFont(12f)
        foreground = JBColor.GRAY
    }

    private val tableModel = object : DefaultTableModel() {
        override fun isCellEditable(row: Int, column: Int) = false
    }

    private val table: JBTable

    private var currentHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    data class IndentLine(
        val line: Int,
        val indentCount: Int,
        val level: Int,
        val content: String,
        val isConsistent: Boolean
    )

    init {
        // Init table model columns before creating the table
        tableModel.setColumnIdentifiers(arrayOf("行号", "缩进", "层级", "状态", "内容"))

        table = JBTable(tableModel).apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            rowHeight = 24
            setShowGrid(false)
            intercellSpacing = Dimension(0, 0)
            autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN

            getColumnModel().getColumn(0).preferredWidth = 50
            getColumnModel().getColumn(0).maxWidth = 60
            getColumnModel().getColumn(1).preferredWidth = 100
            getColumnModel().getColumn(1).maxWidth = 120
            getColumnModel().getColumn(2).preferredWidth = 50
            getColumnModel().getColumn(2).maxWidth = 60
            getColumnModel().getColumn(3).preferredWidth = 45
            getColumnModel().getColumn(3).maxWidth = 50

            setDefaultRenderer(Object::class.java, object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, col: Int
                ): Component {
                    val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col)
                    if (!isSelected) {
                        val status = tableModel.getValueAt(row, 3)
                        comp.background = if (status != "✓") Color(255, 235, 235) else Color.WHITE
                    }
                    return comp
                }
            })
        }

        layout = BorderLayout()

        // === Top bar ===
        val topPanel = JPanel(BorderLayout())
        topPanel.add(fileLabel, BorderLayout.WEST)

        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        rightPanel.add(summaryLabel)
        rightPanel.add(refreshBtn)
        topPanel.add(rightPanel, BorderLayout.EAST)
        add(topPanel, BorderLayout.NORTH)

        // === Table ===
        val scrollPane = JScrollPane(table)
        scrollPane.border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
        add(scrollPane, BorderLayout.CENTER)

        // === Events ===
        refreshBtn.addActionListener { analyze() }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val row = table.rowAtPoint(e.point)
                if (row >= 0 && e.clickCount >= 2) {
                    val line = tableModel.getValueAt(row, 0) as? Int ?: return
                    navigateToLine(line - 1)
                }
            }
        })

        SwingUtilities.invokeLater { analyze() }
    }

    fun analyze() {
        // Clear previous
        clearHighlights()
        tableModel.setRowCount(0)
        summaryLabel.text = "分析中..."

        thread {
            try {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                if (editor == null) {
                    SwingUtilities.invokeLater {
                        fileLabel.text = "当前文件: -"
                        summaryLabel.text = "请打开一个文件"
                    }
                    return@thread
                }

                val file = FileDocumentManager.getInstance().getFile(editor.document)
                val fileName = file?.name ?: "未知"
                val results = analyzeDocument(editor.document)

                SwingUtilities.invokeLater {
                    fileLabel.text = "📄 $fileName"
                    updateTable(results)
                    highlightProblemLines(editor, results)
                    val problemCount = results.count { !it.isConsistent }
                    summaryLabel.text = if (problemCount > 0) {
                        "发现 $problemCount 处缩进异常"
                    } else {
                        "缩进检查通过"
                    }
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    summaryLabel.text = "分析失败: ${e.message}"
                }
            }
        }
    }

    private fun analyzeDocument(doc: Document): List<IndentLine> {
        val lines = mutableListOf<IndentLine>()
        val lineCount = doc.lineCount

        // Collect indent counts for non-empty lines
        val nonEmptyIndents = mutableListOf<Int>()

        for (i in 0 until lineCount) {
            val startOff = doc.getLineStartOffset(i)
            val endOff = doc.getLineEndOffset(i)
            val text = doc.getText(com.intellij.openapi.util.TextRange(startOff, endOff))
            val content = text.trimEnd()
            val leadingWs = text.takeWhile { it == ' ' || it == '\t' }
            val indentCount = if (leadingWs.isEmpty()) 0
            else leadingWs.sumOf { c: Char -> if (c == '\t') 4.toLong() else 1.toLong() }.toInt()

            lines.add(IndentLine(
                line = i + 1,
                indentCount = indentCount,
                level = 0,
                content = content,
                isConsistent = true
            ))

            if (content.isNotBlank()) {
                nonEmptyIndents.add(indentCount)
            }
        }

        // Determine common indent step using GCD of non-zero differences
        val nonzeroIndents = nonEmptyIndents.filter { it > 0 }.distinct().sorted()
        val indentStep = if (nonzeroIndents.size >= 2) {
            val diffs = mutableListOf<Int>()
            for (j in 1 until nonzeroIndents.size) {
                val diff = nonzeroIndents[j] - nonzeroIndents[j - 1]
                if (diff > 0) diffs.add(diff)
            }
            if (diffs.isEmpty()) 4 else gcdOf(diffs)
        } else {
            4 // default indent step
        }

        // Recalculate levels and check consistency
        for (i in lines.indices) {
            val line = lines[i]
            if (line.content.isBlank()) {
                lines[i] = line.copy(level = 0, isConsistent = true)
                continue
            }
            val level = line.indentCount / indentStep
            val isConsistent = line.indentCount % indentStep == 0
            lines[i] = line.copy(level = level, isConsistent = isConsistent)
        }

        return lines
    }

    private fun gcdOf(numbers: List<Int>): Int {
        if (numbers.isEmpty()) return 4
        var result = numbers[0]
        for (i in 1 until numbers.size) {
            result = gcd(result, numbers[i])
        }
        return result
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private fun updateTable(results: List<IndentLine>) {
        tableModel.setRowCount(0)
        for (r in results) {
            val indentDisplay = if (r.indentCount > 0) "·".repeat(r.indentCount) else ""
            val status = if (r.content.isBlank()) "-" else if (r.isConsistent) "✓" else "✗"
            val preview = r.content.take(80)
            tableModel.addRow(arrayOf(r.line, indentDisplay, r.level, status, preview))
        }
    }

    private fun highlightProblemLines(editor: Editor, results: List<IndentLine>) {
        clearHighlights()
        for (r in results) {
            if (!r.isConsistent) {
                val lineStart = editor.document.getLineStartOffset(r.line - 1)
                val lineEnd = editor.document.getLineEndOffset(r.line - 1)
                val attrs = TextAttributes().apply {
                    backgroundColor = JBColor(Color(255, 200, 200), Color(100, 40, 40))
                    effectType = EffectType.SEARCH_MATCH
                }
                val hl = editor.markupModel.addRangeHighlighter(
                    lineStart, lineEnd, HighlighterLayer.WARNING, attrs, com.intellij.openapi.editor.markup.HighlighterTargetArea.EXACT_RANGE
                )
                currentHighlighters.add(hl)
            }
        }
    }

    private fun clearHighlights() {
        currentHighlighters.forEach { it.dispose() }
        currentHighlighters.clear()
    }

    private fun navigateToLine(lineIndex: Int) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        if (lineIndex in 0 until editor.document.lineCount) {
            val offset = editor.document.getLineStartOffset(lineIndex)
            editor.caretModel.moveToOffset(offset)
            editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
        }
    }
}
