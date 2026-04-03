package io.github.sparkle4j.apply

import io.github.sparkle4j.UpdateItem
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.logging.Logger
import kotlin.system.exitProcess

internal class WindowsApplier {

    private val log = Logger.getLogger(WindowsApplier::class.java.name)

    fun apply(item: UpdateItem, tempFile: Path) {
        val installerPath = tempFile.parent.resolve("sparkle4j-update.exe")
        Files.move(tempFile, installerPath, StandardCopyOption.REPLACE_EXISTING)

        val args = when (item.installerType.lowercase()) {
            "nsis" -> listOf(installerPath.toString(), "/S")
            else -> listOf(installerPath.toString(), "/PASSIVE", "/NORESTART")
        }

        log.info("Launching installer: $args")
        ProcessBuilder(args).start()
        exitProcess(0)
    }
}
