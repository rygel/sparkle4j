package io.github.sparkle4j.ui

import io.github.sparkle4j.Sparkle4jConfig
import io.github.sparkle4j.UpdateDownloader
import io.github.sparkle4j.UpdateItem
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
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

    private lateinit var dialog: JDialog
    private lateinit var buttonPanel: JPanel
    private lateinit var progressPanel: JPanel
    private lateinit var progressBar: JProgressBar
    private lateinit var progressLabel: JLabel

    @Volatile
    private var downloadThread: Thread? = null

    @Volatile
    private var cancelled = false

    fun show() {
        val parentWindow = config.parentComponent
            ?.let { SwingUtilities.getWindowAncestor(it) }

        dialog = JDialog(parentWindow, "Update Available", java.awt.Dialog.ModalityType.APPLICATION_MODAL)
        dialog.defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        dialog.layout = BorderLayout(0, 0)

        dialog.add(createHeaderPanel(), BorderLayout.NORTH)
        dialog.add(createNotesScrollPane(), BorderLayout.CENTER)

        buttonPanel = createButtonPanel()
        dialog.add(buttonPanel, BorderLayout.SOUTH)

        progressPanel = createProgressPanel()

        dialog.pack()
        dialog.setLocationRelativeTo(config.parentComponent)
        dialog.isVisible = true
    }

    private fun createHeaderPanel(): JPanel = JPanel(BorderLayout(0, 4)).apply {
        border = EmptyBorder(16, 16, 8, 16)
        add(
            JLabel("<html><b>${config.appName} ${item.shortVersionString} is available</b></html>").apply {
                font = font.deriveFont(Font.BOLD, font.size + 2f)
            },
            BorderLayout.NORTH,
        )
        add(
            JLabel("<html>You have ${config.currentVersion}. Would you like to update?</html>"),
            BorderLayout.CENTER,
        )
    }

    private fun createNotesScrollPane(): JScrollPane {
        val notesPanel = ReleaseNotesPanel(item.releaseNotesUrl, item.inlineDescription)
        return JScrollPane(notesPanel).apply {
            preferredSize = Dimension(600, 280)
            border = BorderFactory.createCompoundBorder(
                EmptyBorder(0, 16, 0, 16),
                BorderFactory.createEtchedBorder(),
            )
        }
    }

    private fun createButtonPanel(): JPanel {
        val skipButton = JButton("Skip This Version").also { it.isVisible = !item.isCriticalUpdate }
        val remindButton = JButton("Remind Me Later")
        val installButton = JButton("Install").also { dialog.rootPane.defaultButton = it }

        skipButton.addActionListener {
            onSkip(item.version)
            dialog.dispose()
        }
        remindButton.addActionListener { dialog.dispose() }
        installButton.addActionListener { startDownload() }

        return JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            border = EmptyBorder(8, 16, 16, 16)
            add(skipButton)
            add(remindButton)
            add(installButton)
        }
    }

    private fun createProgressPanel(): JPanel {
        progressBar = JProgressBar(0, 100).also { it.isStringPainted = true }
        progressLabel = JLabel("Downloading…")
        val cancelButton = JButton("Cancel")
        cancelButton.addActionListener { cancelDownload() }

        return JPanel(BorderLayout(8, 0)).apply {
            border = EmptyBorder(8, 16, 16, 16)
            add(progressLabel, BorderLayout.NORTH)
            add(progressBar, BorderLayout.CENTER)
            add(cancelButton, BorderLayout.EAST)
        }
    }

    private fun startDownload() {
        dialog.remove(buttonPanel)
        dialog.add(progressPanel, BorderLayout.SOUTH)
        dialog.revalidate()
        dialog.repaint()
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
                // User cancelled — tempFile cleanup done in cancelDownload()
            } catch (e: IOException) {
                if (!cancelled) {
                    log.warning("Download failed: ${e.message}")
                    SwingUtilities.invokeLater {
                        JOptionPane.showMessageDialog(dialog, "Download failed. Try again later.", "Download Error", JOptionPane.ERROR_MESSAGE)
                        dialog.dispose()
                    }
                }
            }
        }.also {
            it.isDaemon = true
            it.name = "sparkle4j-download"
            it.start()
        }
    }

    private fun switchToReady(tempFile: Path) {
        progressPanel.removeAll()
        progressPanel.layout = BorderLayout(8, 0)
        progressPanel.add(
            JLabel("<html>Ready to install. The app will quit and the update will be applied.</html>"),
            BorderLayout.CENTER,
        )
        val installNowButton = JButton("Install Now").apply {
            addActionListener {
                dialog.dispose()
                Thread {
                    onInstall(item, tempFile)
                }.also { t ->
                    t.isDaemon = false
                    t.name = "sparkle4j-install"
                    t.start()
                }
            }
        }
        progressPanel.add(installNowButton, BorderLayout.EAST)
        dialog.revalidate()
        dialog.repaint()
    }

    private fun cancelDownload() {
        cancelled = true
        downloadThread?.interrupt()
        try {
            val fileName = item.url.substringAfterLast('/').substringBefore('?').ifBlank { "sparkle4j-update" }
            val tempFile = Path.of(System.getProperty("java.io.tmpdir"), "sparkle4j-$fileName")
            Files.deleteIfExists(tempFile)
        } catch (_: IOException) {
            // best effort cleanup
        }
        dialog.dispose()
    }
}
