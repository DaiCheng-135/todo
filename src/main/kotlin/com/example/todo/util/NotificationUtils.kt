package com.example.todo.util

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object NotificationUtils {

    fun info(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Todo Notifications")
            .createNotification(content, NotificationType.INFORMATION)
            .notify(project)
    }

    fun warning(project: Project, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Todo Notifications")
            .createNotification(content, NotificationType.WARNING)
            .notify(project)
    }
}