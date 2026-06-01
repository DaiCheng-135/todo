package com.example.todo.action

import com.example.todo.model.CodeLocation
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

object JumpToCodeAction {

    fun jump(project: Project, location: CodeLocation) {
        val file = LocalFileSystem.getInstance()
            .findFileByPath(location.filePath)
            ?: return

        OpenFileDescriptor(
            project,
            file,
            location.lineNumber - 1,
            location.column ?: 0
        ).navigate(true)
    }
}