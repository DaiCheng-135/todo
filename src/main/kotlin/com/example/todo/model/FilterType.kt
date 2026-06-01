package com.example.todo.model

enum class FilterType(
    val displayName: String
) {
    ALL("全部"),
    ACTIVE("未完成"),
    COMPLETED("已完成"),
    HIGH_PRIORITY("高优先级"),
    OVERDUE("已超期");

    override fun toString(): String = displayName
}