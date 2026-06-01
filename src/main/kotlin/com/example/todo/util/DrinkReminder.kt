package com.example.todo.util

import com.example.todo.service.ReminderConfig
import com.example.todo.service.TodoService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.*
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.*
import javax.swing.Timer as SwingTimer

object DrinkReminder {
    private var project: Project? = null
    private val activeTimers = mutableMapOf<String, java.util.Timer>()
    private val nextFireTimes = mutableMapOf<String, Long>()
    private var currentDialog: JDialog? = null

    fun init(proj: Project) {
        project = proj
    }

    fun start(config: ReminderConfig) {
        stop(config.id)
        val intervalMs = config.intervalValue.toLong() *
                if (config.intervalUnit == "小时") 3600_000L else 60_000L
        val configId = config.id
        nextFireTimes[configId] = System.currentTimeMillis() + intervalMs
        val timer = java.util.Timer("Reminder-$configId", true)
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                nextFireTimes[configId] = System.currentTimeMillis() + intervalMs
                SwingUtilities.invokeLater { showNotification(configId) }
            }
        }, intervalMs, intervalMs)
        activeTimers[configId] = timer
    }

    fun stop(configId: String) {
        activeTimers[configId]?.cancel()
        activeTimers.remove(configId)
        nextFireTimes.remove(configId)
    }

    fun stopAll() {
        activeTimers.values.forEach { it.cancel() }
        activeTimers.clear()
        nextFireTimes.clear()
        dismissDialog()
    }

    fun isEnabled(configId: String): Boolean = activeTimers.containsKey(configId)

    fun getActiveCount(): Int = activeTimers.size

    fun getRemainingSeconds(configId: String): Int {
        val next = nextFireTimes[configId] ?: return -1
        return ((next - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
    }

    fun getAnyRemainingSeconds(): Int {
        if (nextFireTimes.isEmpty()) return -1
        val minNext = nextFireTimes.values.minOrNull() ?: return -1
        return ((minNext - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
    }

    fun restartFromConfig() {
        val proj = project ?: return
        stopAll()
        val configs = proj.service<TodoService>().getReminderConfigs()
        configs.filter { it.enabled }.forEach { start(it) }
    }

    private fun showNotification(configId: String) {
        dismissDialog()
        val proj = project ?: return
        val config = proj.service<TodoService>().getReminderConfigs()
            .find { it.id == configId } ?: return

        val owner = WindowManager.getInstance().getFrame(proj)

        val dialog = JDialog(owner).apply {
            isUndecorated = true
            isAlwaysOnTop = true
            isResizable = false
            background = Color(0, 0, 0, 0)
            rootPane.background = Color(0, 0, 0, 0)
            rootPane.setOpaque(false)
            contentPane = ReminderPanel(config) {
                dismissDialog()
                stop(configId)
            }
            pack()
            // 保证最小宽度
            if (width < 280) setSize(280, height)
            setLocationRelativeTo(owner)
        }

        currentDialog = dialog
        dialog.isVisible = true

        SwingTimer(8000) {
            if (dialog.isVisible) {
                val fade = SwingTimer(30, null)
                fade.addActionListener {
                    dialog.opacity = (dialog.opacity - 0.08f).coerceAtLeast(0f)
                    if (dialog.opacity <= 0f) {
                        fade.stop()
                        dialog.dispose()
                        if (currentDialog == dialog) currentDialog = null
                    }
                }
                fade.start()
            }
        }.apply { isRepeats = false; start() }
    }

    private fun dismissDialog() {
        currentDialog?.dispose()
        currentDialog = null
    }

    private class ReminderPanel(
        private val config: ReminderConfig,
        private val onStop: () -> Unit
    ) : JPanel() {
        private val arc = 20.0
        private val imageMaxWidth = 120
        private val imageMaxHeight = 100
        private var cachedImage: BufferedImage? = null
        private var imageLoadAttempted = false

        init {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val g2d = g.create() as Graphics2D
            try {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g2d.color = Color(0, 0, 0, 30)
                g2d.fillRoundRect(3, 5, width - 6, height - 6, arc.toInt() + 6, arc.toInt() + 6)
                g2d.color = Color(0, 0, 0, 15)
                g2d.fillRoundRect(1, 2, width - 2, height - 2, arc.toInt() + 2, arc.toInt() + 2)
                g2d.color = Color(255, 255, 255)
                g2d.fillRoundRect(0, 0, width, height, arc.toInt(), arc.toInt())
            } finally {
                g2d.dispose()
            }
        }

        override fun addNotify() {
            super.addNotify()
            buildContent()
        }

        private fun loadImage(): BufferedImage? {
            if (imageLoadAttempted) return cachedImage
            imageLoadAttempted = true
            val path = config.imagePath
            if (path.isBlank()) return null
            val file = File(path)
            if (!file.exists()) return null
            return try { ImageIO.read(file) } catch (_: Exception) { null }
        }

        private fun scaleImage(original: BufferedImage): BufferedImage {
            val w = original.width; val h = original.height
            if (w <= imageMaxWidth && h <= imageMaxHeight) return original
            val ratio = minOf(imageMaxWidth.toDouble() / w, imageMaxHeight.toDouble() / h)
            val scaled = BufferedImage((w * ratio).toInt(), (h * ratio).toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = scaled.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(original, 0, 0, scaled.width, scaled.height, null)
            g.dispose()
            return scaled
        }

        private fun showFullImage() {
            val img = cachedImage ?: return
            JDialog(SwingUtilities.getWindowAncestor(this)).apply {
                title = "查看图片 (${img.width} × ${img.height})"
                val imagePanel = object : JPanel() {
                    override fun paintComponent(g: Graphics) {
                        super.paintComponent(g)
                        val g2d = g.create() as Graphics2D
                        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        val maxW = (Toolkit.getDefaultToolkit().screenSize.width * 0.75).toInt()
                        val maxH = (Toolkit.getDefaultToolkit().screenSize.height * 0.75).toInt()
                        val ratio = minOf(maxW.toDouble() / img.width, maxH.toDouble() / img.height, 1.0)
                        val dw = (img.width * ratio).toInt(); val dh = (img.height * ratio).toInt()
                        g2d.drawImage(img, (width - dw) / 2, (height - dh) / 2, dw, dh, null)
                        g2d.dispose()
                    }
                }
                val screen = Toolkit.getDefaultToolkit().screenSize
                imagePanel.preferredSize = Dimension(
                    minOf(img.width, (screen.width * 0.75).toInt()),
                    minOf(img.height, (screen.height * 0.75).toInt())
                )
                contentPane = imagePanel
                pack()
                setLocationRelativeTo(null)
                isVisible = true
            }
        }

        private fun buildContent() {
            removeAll()
            border = BorderFactory.createEmptyBorder(16, 24, 14, 24)

            cachedImage = loadImage()
            if (cachedImage != null) {
                val scaled = scaleImage(cachedImage!!)
                val imgPanel = object : JPanel() {
                    override fun paintComponent(g: Graphics) {
                        super.paintComponent(g)
                        val g2d = g.create() as Graphics2D
                        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                        g2d.drawImage(scaled, (width - scaled.width) / 2, (height - scaled.height) / 2, null)
                        g2d.dispose()
                    }
                }.apply {
                    preferredSize = Dimension(imageMaxWidth, imageMaxHeight)
                    minimumSize = preferredSize; maximumSize = preferredSize
                    isOpaque = false
                    cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    toolTipText = "点击查看大图"
                    addMouseListener(object : java.awt.event.MouseAdapter() {
                        override fun mouseClicked(e: java.awt.event.MouseEvent?) { showFullImage() }
                    })
                }
                add(imgPanel)
                add(Box.createVerticalStrut(4))
            }

            val titleLabel = JLabel(config.title, SwingConstants.CENTER).apply {
                font = font.deriveFont(Font.BOLD, 18f)
                foreground = Color(40, 50, 70)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            add(titleLabel)
            add(Box.createVerticalStrut(6))

            val msgLabel = JLabel(config.message.replace("%d", "${config.intervalValue}"), SwingConstants.CENTER).apply {
                font = font.deriveFont(12f)
                foreground = Color(130, 140, 155)
                alignmentX = Component.CENTER_ALIGNMENT
            }
            add(msgLabel)
            add(Box.createVerticalStrut(16))

            val btnRow = JPanel(FlowLayout(FlowLayout.CENTER, 12, 0)).apply { isOpaque = false }
            val okBtn = JButton("知道了").apply {
                font = font.deriveFont(Font.BOLD, 13f)
                foreground = Color.WHITE; background = Color(80, 160, 240)
                isFocusPainted = false; isBorderPainted = false; setOpaque(true)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = BorderFactory.createEmptyBorder(6, 20, 6, 20)
                addActionListener { SwingUtilities.getWindowAncestor(this)?.dispose() }
            }
            val stopBtn = JButton("关闭提醒").apply {
                font = font.deriveFont(12f)
                foreground = Color(150, 155, 165)
                isFocusPainted = false; isBorderPainted = false; setContentAreaFilled(false)
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                border = BorderFactory.createEmptyBorder(6, 12, 6, 12)
                addActionListener {
                    onStop()
                    SwingUtilities.getWindowAncestor(this)?.dispose()
                }
            }
            btnRow.add(okBtn); btnRow.add(stopBtn)
            add(btnRow)
            add(Box.createVerticalGlue())
        }
    }
}
