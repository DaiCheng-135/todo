package com.example.todo.model

data class CodeLocation(
    var filePath: String = "",
    var lineNumber: Int = 0,
    var column: Int? = null
)