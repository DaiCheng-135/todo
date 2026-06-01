package com.example.todo.service

import com.example.todo.util.NotificationUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.time.LocalDateTime
import java.util.*

@Service(Service.Level.PROJECT)
class ReminderService(private val project: Project) : Disposable {
    private val timer = Timer("Todo-Reminder", true)
    private val notifiedIds = Collections.synchronizedSet(mutableSetOf<String>())

    init {
        timer.schedule(object : TimerTask() {
            override fun run() {
                val todoService = project.service<TodoService>()
                val now = LocalDateTime.now()
                for (todo in todoService.getTodos()) {
                    if (todo.remind && !todo.done && todo.deadline != null && !todo.deadline!!.isAfter(now)) {
                        val key = "${todo.id}_${todo.deadline}"
                        if (notifiedIds.add(key)) {
                            ApplicationManager.getApplication().invokeLater {
                                if (!project.isDisposed) {
                                    NotificationUtils.warning(project, "待办到期: ${todo.title}")
                                }
                            }
                        }
                    }
                }
            }
        }, 30_000, 60_000)
    }

    override fun dispose() {
        timer.cancel()
    }
}
