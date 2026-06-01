package com.example.todo.service

import com.example.todo.model.TodoItem
import java.util.UUID

data class LLMConfig(
    var id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var endpoint: String = "",
    // Kept only to migrate API keys saved by older versions.
    var apiKey: String = "",
    var model: String = ""
)

data class ReminderConfig(
    var id: String = UUID.randomUUID().toString(),
    var title: String = "该喝水啦",
    var message: String = "%d 分钟没喝水啦，快去补充水分吧",
    var enabled: Boolean = false,
    var intervalValue: Int = 30,
    var intervalUnit: String = "分钟",
    var imagePath: String = ""
)

data class TodoState(
    var todos: MutableList<TodoItem> = mutableListOf(),
    var reminderConfigs: MutableList<ReminderConfig> = mutableListOf(
        ReminderConfig()
    ),
    // 旧字段保留以兼容，新代码不再使用
    @Deprecated("使用 reminderConfigs")
    var drinkReminderEnabled: Boolean = false,
    @Deprecated("使用 reminderConfigs")
    var drinkReminderInterval: Int = 30,
    var llmConfigs: MutableList<LLMConfig> = mutableListOf(
        LLMConfig(
            name = "Qwen",
            endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
            apiKey = "",
            model = "qwen-plus"
        ),
        LLMConfig(
            name = "DeepSeek",
            endpoint = "https://api.deepseek.com/chat/completions",
            apiKey = "",
            model = "deepseek-v4-flash"
        )
    ),
    var activeLlmConfigIndex: Int = 0,
    var llmPromptTemplate: String = "",
    var indentRainbowEnabled: Boolean = true,
    var indentRainbowFileTypes: String = "Python,YAML,YML,JSON,CSS,JavaScript,TypeScript,Java,Kotlin",
    var indentRainbowThickness: Int = 2,
    // Theme color (ARGB format, e.g., 0xFF4A90E2 for blue)
    var themeColor: Int = 0xFF4A90E2.toInt()
)
