package io.github.sparkle4j.apply

import io.github.sparkle4j.UpdateItem
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.system.exitProcess

internal class LinuxApplier {

    private val log = Logger.getLogger(LinuxApplier::class.java.name)

    fun apply(item: UpdateItem, tempFile: Path) {
        val ext = tempFile.fileName.toString().substringAfterLast('.').lowercase()
        val command = when (ext) {
            "deb" -> listOf("pkexec", "dpkg", "-i", tempFile.toString())
            "rpm" -> listOf("pkexec", "rpm", "-U", tempFile.toString())
            else  -> null
        }

        if (command != null) {
            log.info("Installing package via pkexec: $command")
            val exitCode = ProcessBuilder(command).inheritIO().start().waitFor()
            if (exitCode != 0) {
                log.warning("Package manager exited with code $exitCode")
            }
            exitProcess(0)
        }

        // Tier 2: AppImage or unknown format — open browser and show the downloaded path
        log.info("Unknown installer format ($ext) — opening browser for $item.url")
        try {
            Desktop.getDesktop().browse(URI.create(item.url))
        } catch (e: Exception) {
            log.warning("Could not open browser: ${e.message}")
        }
    }
}
