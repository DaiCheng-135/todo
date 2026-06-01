package com.example.todo.ui

import com.example.todo.service.TodoService
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.CustomHighlighterRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.SwingUtilities
import javax.swing.UIManager

class IndentRainbow(private val editor: Editor, private val project: Project) : Disposable {

    private val highlighters = mutableListOf<RangeHighlighter>()

    // 6-cycle palette — each indent level gets one color
    private val lightColors = listOf(
        Color(70, 130, 220, 180),  // Blue
        Color(60, 170, 80, 180),   // Green
        Color(190, 150, 50, 180),  // Yellow
        Color(160, 80, 190, 180),  // Purple
        Color(200, 70, 70, 180),   // Red
        Color(40, 160, 170, 180),  // Cyan
    )
    private val darkColors = listOf(
        Color(100, 160, 240, 200), // Blue
        Color(80, 200, 100, 200),  // Green
        Color(220, 180, 60, 200),  // Yellow
        Color(180, 100, 220, 200), // Purple
        Color(230, 80, 80, 200),   // Red
        Color(60, 180, 190, 200),  // Cyan
    )

    private val palette: List<Color>
        get() = if (UIManager.getBoolean("Theme.isDark")) darkColors else lightColors

    init {
        SwingUtilities.invokeLater { applyHighlighters() }
    }

    fun applyHighlighters() {
        clear()

        val service = project.service<TodoService>()

        // check if rainbow is enabled for this file type
        if (!shouldApply(service)) return

        val doc = editor.document
        val chars = doc.immutableCharSequence
        val lineCount = doc.lineCount
        val indentStep = detectIndentStep(doc)
        if (indentStep <= 0) return

        val colors = palette
        val strokeWidth = service.getIndentRainbowThickness().toFloat()

        for (line in 0 until lineCount) {
            val lineStart = doc.getLineStartOffset(line)
            val lineEnd = doc.getLineEndOffset(line)
            if (lineStart >= chars.length || lineEnd > chars.length) continue

            // count leading spaces/tabs
            var indentLen = 0
            for (i in lineStart until lineEnd) {
                val c = chars[i]
                if (c == ' ' || c == '\t') indentLen++ else break
            }
            if (indentLen == 0) continue

            // Draw vertical lines at each indent level
            val maxLevel = indentLen / indentStep
            for (level in 0 until maxLevel) {
                val lineOffset = lineStart + level * indentStep
                if (lineOffset >= lineStart + indentLen) break

                val color = colors[level % colors.size]
                
                // Create a highlighter with custom renderer to draw the line
                val hl = editor.markupModel.addRangeHighlighter(
                    lineOffset, lineOffset + 1, HighlighterLayer.SYNTAX, null,
                    HighlighterTargetArea.EXACT_RANGE
                )
                
                hl.customRenderer = object : CustomHighlighterRenderer {
                    override fun paint(editor: Editor, highlighter: RangeHighlighter, g: Graphics) {
                        val g2d = g as Graphics2D
                        val startOffset = highlighter.startOffset
                        val p = editor.offsetToXY(startOffset)
                        
                        // Draw vertical line
                        g2d.color = color
                        g2d.stroke = BasicStroke(strokeWidth)
                        g2d.drawLine(p.x, p.y, p.x, p.y + editor.lineHeight)
                    }
                }
                highlighters.add(hl)
            }
        }
    }

    private fun shouldApply(service: TodoService): Boolean {
        if (!service.getIndentRainbowEnabled()) return false
        val vFile = FileDocumentManager.getInstance().getFile(editor.document) ?: return false
        val enabledTypes = service.getIndentRainbowFileTypes().split(",").map { it.trim() }
        // match file type name or extension, case-insensitive
        val name = vFile.fileType.name
        val ext = vFile.extension ?: ""
        return enabledTypes.any { it.equals(name, ignoreCase = true) || it.equals(ext, ignoreCase = true) }
    }

    fun clear() {
        highlighters.forEach { it.dispose() }
        highlighters.clear()
    }

    override fun dispose() {
        clear()
    }

    private fun detectIndentStep(doc: com.intellij.openapi.editor.Document): Int {
        val chars = doc.immutableCharSequence
        val indents = mutableListOf<Int>()
        for (line in 0 until doc.lineCount) {
            val start = doc.getLineStartOffset(line)
            val end = doc.getLineEndOffset(line)
            var cnt = 0
            for (i in start until end) {
                val c = chars[i]
                if (c == ' ') cnt++
                else if (c == '\t') {
                    cnt += 4; break
                } else break
            }
            if (cnt > 0) indents.add(cnt)
        }
        val distinct = indents.filter { it > 0 }.distinct().sorted()
        if (distinct.size < 2) return 4
        val diffs = mutableListOf<Int>()
        for (i in 1 until distinct.size) {
            val d = distinct[i] - distinct[i - 1]
            if (d > 0) diffs.add(d)
        }
        return if (diffs.isEmpty()) 4 else gcdList(diffs)
    }

    private fun gcdList(nums: List<Int>): Int {
        var g = nums[0]
        for (i in 1 until nums.size) g = gcd(g, nums[i])
        return g
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    companion object {
        private val instancesByProject = mutableMapOf<Project, MutableMap<Editor, IndentRainbow>>()

        fun refreshAll(project: Project? = null) {
            val instances = if (project != null) {
                instancesByProject[project]?.values.orEmpty()
            } else {
                instancesByProject.values.flatMap { it.values }
            }
            instances.forEach { it.applyHighlighters() }
        }

        fun install(project: Project, parentDisposable: Disposable) {
            val projectInstances = instancesByProject.getOrPut(project) { mutableMapOf() }
            EditorFactory.getInstance().addEditorFactoryListener(
                object : EditorFactoryListener {
                    override fun editorCreated(event: EditorFactoryEvent) {
                        val editor = event.editor
                        if (editor.project == project) {
                            projectInstances[editor] = IndentRainbow(editor, project)
                        }
                    }

                    override fun editorReleased(event: EditorFactoryEvent) {
                        projectInstances.remove(event.editor)?.dispose()
                    }
                }, parentDisposable
            )
            for (editor in EditorFactory.getInstance().allEditors) {
                if (editor.project == project && !projectInstances.containsKey(editor)) {
                    projectInstances[editor] = IndentRainbow(editor, project)
                }
            }
            Disposer.register(parentDisposable, Disposable {
                instancesByProject.remove(project)?.values?.forEach { it.dispose() }
            })
        }
    }
}
