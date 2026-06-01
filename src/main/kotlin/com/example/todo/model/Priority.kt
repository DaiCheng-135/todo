package com.example.todo.model

enum class Priority(
    val displayName: String
) {
    LOW("低"),
    MEDIUM("中"),
    HIGH("高"),
    CRITICAL("紧急");

    override fun toString(): String = displayName
}