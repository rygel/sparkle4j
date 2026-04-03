package io.github.sparkle4j.apply

import io.github.sparkle4j.UpdateItem
import java.awt.Desktop
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.system.exitProcess

internal class MacosApplier {

    private val log = Logger.getLogger(MacosApplier::class.java.name)

    fun apply(item: UpdateItem, tempFile: Path, currentAppPath: Path) {
        val helperScript = currentAppPath.resolve("Contents/MacOS/sparkle4j-updater")

        if (!Files.exists(helperScript)) {
            log.severe("Helper script not found at $helperScript — falling back to browser download")
            openBrowser(item.url)
            return
        }

        helperScript.toFile().setExecutable(true)

        ProcessBuilder(
            helperScript.toString(),
            currentAppPath.toString(),
            tempFile.toString(),
            ProcessHandle.current().pid().toString(),
        ).start()  // fire and forget

        exitProcess(0)
    }

    private fun openBrowser(url: String) {
        try {
            Desktop.getDesktop().browse(URI.create(url))
        } catch (e: Exception) {
            log.warning("Could not open browser: ${e.message}")
        }
    }
}
