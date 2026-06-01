package com.example.todo.model

import com.intellij.util.xmlb.annotations.Transient
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TodoItem(
    var id: String = UUID.randomUUID().toString(),

    var title: String = "",

    var description: String = "",

    var done: Boolean = false,

    var priority: Priority = Priority.MEDIUM,

    var deadlineStr: String? = null,

    var tags: MutableList<String> = mutableListOf(),

    var createdAtStr: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),

    var updatedAtStr: String = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),

    var linkedLocation: CodeLocation? = null,

    var remind: Boolean = false
) {
    @get:Transient
    var deadline: LocalDateTime?
        get() = deadlineStr?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
        set(value) {
            deadlineStr = value?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }

    @get:Transient
    var createdAt: LocalDateTime
        get() = LocalDateTime.parse(createdAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        set(value) {
            createdAtStr = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }

    @get:Transient
    var updatedAt: LocalDateTime
        get() = LocalDateTime.parse(updatedAtStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        set(value) {
            updatedAtStr = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
}