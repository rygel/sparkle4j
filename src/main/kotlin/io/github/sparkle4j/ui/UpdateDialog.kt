package io.github.sparkle4j.ui

import io.github.sparkle4j.Sparkle4jConfig
import io.github.sparkle4j.UpdateDownloader
import io.github.sparkle4j.UpdateItem
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.nio.file.Path
import java.util.logging.Logger
import javax.swing.*
import javax.swing.border.EmptyBorder

/**
 * Modal Swing dialog that drives the update flow:
 *
 * 1. Announcement state: release notes + Skip / Remind Me Later / Install buttons
 * 2. Download state:     progress bar + Cancel
 * 3. Ready state:        confirmation message + Install Now
 */
internal class UpdateDialog(
    private val config: Sparkle4jConfig,
    private val item: UpdateItem,
    private val onSkip: (String) -> Unit,
    private val onInstall: (UpdateItem, Path) -> Unit,
) {
    private val log = Logger.getLogger(UpdateDialog::class.java.name)

    fun show() {
        val parentWindow = config.parentComponent
            ?.let { SwingUtilities.getWindowAncestor(it) }

        val dialog = JDialog(parentWindow, "Update Available", java.awt.Dialog.ModalityType.APPLICATION_MODAL)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.layout = BorderLayout(0, 0)

        // --- Header ---
        val displayVersion = item.shortVersionString
        val headerPanel = JPanel(BorderLayout(0, 4)).apply {
            border = EmptyBorder(16, 16, 8, 16)
            add(
                JLabel("<html><b>${config.appName} $displayVersion is available</b></html>").apply {
                    font = font.deriveFont(Font.BOLD, font.size + 2f)
                },
                BorderLayout.NORTH,
            )
            add(
                JLabel("<html>You have ${config.currentVersion}. Would you like to update?</html>"),
                BorderLayout.CENTER,
            )
        }
        dialog.add(headerPanel, BorderLayout.NORTH)

        // --- Release notes ---
        val notesPanel = ReleaseNotesPanel(item.releaseNotesUrl, item.inlineDescription)
        val notesScroll = JScrollPane(notesPanel).apply {
            preferredSize = Dimension(600, 280)
            border = BorderFactory.createCompoundBorder(
                EmptyBorder(0, 16, 0, 16),
                BorderFactory.createEtchedBorder(),
            )
        }
        dialog.add(notesScroll, BorderLayout.CENTER)

        // --- Button bar (announcement state) ---
        val skipButton    = JButton("Skip This Version").also { it.isVisible = !item.isCriticalUpdate }
        val remindButton  = JButton("Remind Me Later")
        val installButton = JButton("Install").also { dialog.rootPane.defaultButton = it }

        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            border = EmptyBorder(8, 16, 16, 16)
            add(skipButton)
            add(remindButton)
            add(installButton)
        }
        dialog.add(buttonPanel, BorderLayout.SOUTH)

        // --- Progress panel (download state) ---
        val progressBar   = JProgressBar(0, 100).also { it.isStringPainted = true }
        val progressLabel = JLabel("Downloading…")
        val cancelButton  = JButton("Cancel")
        val progressPanel = JPanel(BorderLayout(8, 0)).apply {
            border = EmptyBorder(8, 16, 16, 16)
            add(progressLabel, BorderLayout.NORTH)
            add(progressBar, BorderLayout.CENTER)
            add(cancelButton, BorderLayout.EAST)
        }

        var downloadThread: Thread? = null
        var cancelled = false

        fun switchToProgress() {
            dialog.remove(buttonPanel)
            dialog.add(progressPanel, BorderLayout.SOUTH)
            dialog.revalidate()
            dialog.repaint()
        }

        fun switchToReady(tempFile: Path) {
            progressPanel.removeAll()
            progressPanel.layout = BorderLayout(8, 0)
            progressPanel.add(
                JLabel("<html>Ready to install. The app will quit and the update will be applied.</html>"),
                BorderLayout.CENTER,
            )
            val installNowButton = JButton("Install Now").apply {
                addActionListener {
                    dialog.dispose()
                    Thread { onInstall(item, tempFile) }.also {
                        it.isDaemon = false
                        it.name = "sparkle4j-install"
                        it.start()
                    }
                }
            }
            progressPanel.add(installNowButton, BorderLayout.EAST)
            dialog.revalidate()
            dialog.repaint()
        }

        // --- Wire up announcement buttons ---
        skipButton.addActionListener {
            onSkip(item.version)
            dialog.dispose()
        }

        remindButton.addActionListener { dialog.dispose() }

        installButton.addActionListener {
            switchToProgress()
            cancelled = false

            downloadThread = Thread {
                try {
                    val tempFile = UpdateDownloader().download(item.url, item.length) { received, total ->
                        if (Thread.currentThread().isInterrupted) throw InterruptedException("Cancelled")
                        SwingUtilities.invokeLater {
                            val pct = if (total > 0) (received * 100 / total).toInt() else 0
                            progressBar.value = pct
                            progressLabel.text = "Downloading… $pct%"
                        }
                    }
                    if (!cancelled) {
                        SwingUtilities.invokeLater { switchToReady(tempFile) }
                    } else {
                        tempFile.toFile().delete()
                    }
                } catch (_: InterruptedException) {
                    // User cancelled — tempFile cleanup done in cancelButton handler
                } catch (e: Exception) {
                    if (!cancelled) {
                        log.warning("Download failed: ${e.message}")
                        SwingUtilities.invokeLater {
                            JOptionPane.showMessageDialog(
                                dialog,
                                "Download failed. Try again later.",
                                "Download Error",
                                JOptionPane.ERROR_MESSAGE,
                            )
                            dialog.dispose()
                        }
                    }
                }
            }.also { it.isDaemon = true; it.name = "sparkle4j-download"; it.start() }
        }

        cancelButton.addActionListener {
            cancelled = true
            downloadThread?.interrupt()
            // Clean up any partial temp file from the download
            try {
                val fileName = item.url.substringAfterLast('/').substringBefore('?').ifBlank { "sparkle4j-update" }
                val tempFile = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "sparkle4j-$fileName")
                java.nio.file.Files.deleteIfExists(tempFile)
            } catch (_: Exception) { /* best effort */ }
            dialog.dispose()
        }

        dialog.pack()
        dialog.setLocationRelativeTo(config.parentComponent)
        dialog.isVisible = true
    }
}
