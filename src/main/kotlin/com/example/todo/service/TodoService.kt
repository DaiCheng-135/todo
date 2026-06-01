package com.example.todo.service

import com.example.todo.model.FilterType
import com.example.todo.model.Priority
import com.example.todo.model.TodoItem
import com.intellij.openapi.components.*
import java.time.LocalDateTime
import java.util.UUID

@Service(Service.Level.PROJECT)
@State(
    name = "TodoState",
    storages = [Storage("todo-state.xml")]
)
class TodoService : PersistentStateComponent<TodoState> {

    private var state = TodoState()

    override fun getState(): TodoState = state.copy(
        todos = state.todos.toMutableList(),
        llmConfigs = state.llmConfigs.map { it.copy() }.toMutableList(),
        reminderConfigs = state.reminderConfigs.map { it.copy() }.toMutableList()
    )

    override fun loadState(state: TodoState) {
        this.state = state
        normalizeLlmConfigs()

        // 兼容旧版本：val bug 导致 LLM 配置序列化为空，恢复默认配置
        val hasValidConfig = this.state.llmConfigs.any { it.name.isNotBlank() && it.model.isNotBlank() }
        if (this.state.llmConfigs.isEmpty() || !hasValidConfig) {
            this.state.llmConfigs = TodoState().llmConfigs
            this.state.activeLlmConfigIndex = 0
        }

        // 仅在配置索引无效时重置为 0
        if (this.state.activeLlmConfigIndex !in this.state.llmConfigs.indices) {
            this.state.activeLlmConfigIndex = 0
        }

        // 兼容旧版：将旧 drinkReminderEnabled 设置迁移到新 reminderConfigs
        if (this.state.drinkReminderEnabled &&
            this.state.reminderConfigs.size == 1 &&
            this.state.reminderConfigs[0].enabled == false &&
            this.state.reminderConfigs[0].title == "该喝水啦") {
            this.state.reminderConfigs[0].enabled = true
            this.state.reminderConfigs[0].intervalValue = this.state.drinkReminderInterval
        }
        this.state.drinkReminderEnabled = false

        // 兼容旧状态：新字段在旧版本中不存在，加载后为 JVM 默认值
        if (this.state.indentRainbowThickness <= 0) {
            this.state.indentRainbowThickness = 2
            this.state.indentRainbowEnabled = true
        }
        // 直接检查字段，兼容 null（旧版序列化可能设置 null）
        try {
            if (this.state.indentRainbowFileTypes.isBlank()) {
                this.state.indentRainbowFileTypes = "Python,YAML,YML,JSON,CSS,JavaScript,TypeScript,Java,Kotlin"
            }
        } catch (_: Exception) {
            this.state.indentRainbowFileTypes = "Python,YAML,YML,JSON,CSS,JavaScript,TypeScript,Java,Kotlin"
        }
    }

    private fun normalizeLlmConfigs() {
        state.llmConfigs = state.llmConfigs.map { config ->
            if (config.id.isBlank()) config.copy(id = UUID.randomUUID().toString()) else config
        }.toMutableList()
    }

    fun getTodos(): List<TodoItem> = state.todos.toList()

    fun addTodo(todo: TodoItem): TodoItem {
        state.todos.add(todo)
        return todo
    }

    fun addTodo(title: String): TodoItem {
        val todo = TodoItem(title = title)
        state.todos.add(todo)
        return todo
    }

    fun removeTodo(id: String): Boolean {
        return state.todos.removeIf { it.id == id }
    }

    fun toggleTodo(id: String): TodoItem? {
        val todo = state.todos.find { it.id == id } ?: return null
        todo.done = !todo.done
        todo.updatedAt = LocalDateTime.now()
        return todo
    }

    fun updateTodo(updatedTodo: TodoItem): Boolean {
        val index = state.todos.indexOfFirst { it.id == updatedTodo.id }
        if (index == -1) return false

        updatedTodo.updatedAt = LocalDateTime.now()
        state.todos[index] = updatedTodo
        return true
    }

    fun updateTitle(id: String, title: String): Boolean {
        val todo = state.todos.find { it.id == id } ?: return false
        todo.title = title
        todo.updatedAt = LocalDateTime.now()
        return true
    }

    fun search(keyword: String): List<TodoItem> {
        if (keyword.isBlank()) return getTodos()

        return state.todos.filter {
            it.title.contains(keyword, true) ||
                    it.description.contains(keyword, true) ||
                    it.tags.any { tag -> tag.contains(keyword, true) }
        }
    }

    fun filter(type: FilterType): List<TodoItem> {
        return when (type) {
            FilterType.ALL -> getTodos()
            FilterType.ACTIVE -> state.todos.filter { !it.done }
            FilterType.COMPLETED -> state.todos.filter { it.done }
            FilterType.HIGH_PRIORITY ->
                state.todos.filter {
                    it.priority == Priority.HIGH || it.priority == Priority.CRITICAL
                }

            FilterType.OVERDUE ->
                state.todos.filter {
                    !it.done &&
                            it.deadline != null &&
                            it.deadline!!.isBefore(LocalDateTime.now())
                }
        }
    }

    fun sortTodos() {
        state.todos.sortWith(
            compareBy<TodoItem> { it.done }
                .thenByDescending { it.priority.ordinal }
                .thenBy { it.deadline ?: LocalDateTime.MAX }
        )
    }

    fun getReminderConfigs(): MutableList<ReminderConfig> = state.reminderConfigs

    fun addReminderConfig(config: ReminderConfig) {
        state.reminderConfigs.add(config)
    }

    fun updateReminderConfig(index: Int, config: ReminderConfig) {
        if (index in state.reminderConfigs.indices) {
            state.reminderConfigs[index] = config
        }
    }

    fun removeReminderConfig(index: Int) {
        if (index in state.reminderConfigs.indices) {
            state.reminderConfigs.removeAt(index)
        }
    }

    fun getLlmConfigs(): MutableList<LLMConfig> = state.llmConfigs

    fun setLlmConfigs(configs: MutableList<LLMConfig>) {
        state.llmConfigs = configs
    }

    fun getActiveLlmConfigIndex(): Int = state.activeLlmConfigIndex

    fun setActiveLlmConfigIndex(index: Int) {
        state.activeLlmConfigIndex = index
    }

    fun getActiveLlmConfig(): LLMConfig? {
        val configs = state.llmConfigs
        val index = state.activeLlmConfigIndex
        return if (configs.isNotEmpty() && index in configs.indices) configs[index] else null
    }

    fun addLlmConfig(config: LLMConfig) {
        state.llmConfigs.add(config)
        state.activeLlmConfigIndex = state.llmConfigs.size - 1
    }

    fun updateLlmConfig(index: Int, config: LLMConfig) {
        if (index < 0 || index >= state.llmConfigs.size) return
        state.llmConfigs[index] = config
        state.activeLlmConfigIndex = index
    }

    fun addLlmConfigAt(index: Int, config: LLMConfig) {
        if (index < 0 || index > state.llmConfigs.size) return
        state.llmConfigs.add(index, config)
        // Keep active index valid
        if (state.activeLlmConfigIndex >= index) {
            state.activeLlmConfigIndex++
        }
    }

    fun removeLlmConfig(index: Int) {
        if (index < 0 || index >= state.llmConfigs.size) return
        state.llmConfigs.removeAt(index)
        if (state.activeLlmConfigIndex >= state.llmConfigs.size) {
            state.activeLlmConfigIndex = (state.llmConfigs.size - 1).coerceAtLeast(0)
        }
    }

    fun getLlmPromptTemplate(): String = state.llmPromptTemplate

    fun setLlmPromptTemplate(prompt: String) {
        state.llmPromptTemplate = prompt
    }

    fun getIndentRainbowEnabled(): Boolean = state.indentRainbowEnabled

    fun setIndentRainbowEnabled(enabled: Boolean) {
        state.indentRainbowEnabled = enabled
    }

    fun getIndentRainbowFileTypes(): String = state.indentRainbowFileTypes

    fun setIndentRainbowFileTypes(types: String) {
        state.indentRainbowFileTypes = types
    }

    fun getIndentRainbowThickness(): Int = state.indentRainbowThickness

    fun setIndentRainbowThickness(thickness: Int) {
        state.indentRainbowThickness = thickness
    }

    fun getThemeColor(): java.awt.Color {
        return java.awt.Color(state.themeColor, true)
    }

    fun setThemeColor(color: java.awt.Color) {
        state.themeColor = color.rgb
    }

}
