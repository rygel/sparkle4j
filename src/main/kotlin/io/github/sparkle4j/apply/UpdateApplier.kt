package io.github.sparkle4j.apply

import io.github.sparkle4j.AppcastParser
import io.github.sparkle4j.Sparkle4jConfig
import io.github.sparkle4j.UpdateItem
import java.nio.file.Path
import java.util.logging.Logger

/**
 * Dispatches to the correct platform applier and resolves the current .app path on macOS.
 */
internal class UpdateApplier(private val config: Sparkle4jConfig) {

    private val log = Logger.getLogger(UpdateApplier::class.java.name)

    fun apply(item: UpdateItem, tempFile: Path) {
        when (AppcastParser.currentOs()) {
            "windows" -> WindowsApplier().apply(item, tempFile)
            "macos" -> MacosApplier().apply(item, tempFile, resolveCurrentAppPath())
            else -> LinuxApplier().apply(item, tempFile)
        }
    }

    private fun resolveCurrentAppPath(): Path {
        config.macosAppPath?.let { return it }

        val jvmExe = ProcessHandle.current().info().command()
            .map { Path.of(it) }
            .orElse(null)

        val appBundle = jvmExe?.let { walkUpToAppBundle(it) }
        if (appBundle == null) {
            log.warning("Could not locate .app bundle — update may fail")
        }
        return appBundle ?: Path.of(".")
    }

    private fun walkUpToAppBundle(start: Path): Path? {
        var path: Path? = start
        while (path != null) {
            if (path.fileName?.toString()?.endsWith(".app") == true) return path
            path = path.parent
        }
        return null
    }
}
