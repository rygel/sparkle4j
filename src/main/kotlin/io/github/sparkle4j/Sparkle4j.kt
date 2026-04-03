package io.github.sparkle4j

import io.github.sparkle4j.apply.UpdateApplier
import io.github.sparkle4j.ui.UpdateDialog
import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.concurrent.Executors
import java.util.logging.Logger
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Immutable configuration for a sparkle4j instance.
 */
data class Sparkle4jConfig(
    /** Appcast XML feed URL. Must be HTTPS. */
    val appcastUrl: String,
    /** Running version of the host app. Semver string. */
    val currentVersion: String,
    /** Base64-encoded Ed25519 public key. Null disables signature verification. */
    val publicKey: String? = null,
    /** Minimum hours between checks. 0 = check every launch. Default: 24. */
    val checkIntervalHours: Int = 24,
    /** Swing parent for dialogs. Null = dialog centered on screen. */
    val parentComponent: Component? = null,
    /** App name shown in dialogs. Defaults to JAR manifest Implementation-Title. */
    val appName: String = resolveAppName(),
    /**
     * Called on the EDT when an update is found, before the built-in dialog.
     * Return false to suppress the built-in dialog and handle UI yourself.
     */
    val onUpdateFound: ((UpdateItem) -> Boolean)? = null,
    /** macOS-only: path to the current .app bundle. Auto-resolved from ProcessHandle if null. */
    val macosAppPath: Path? = null,
)

/**
 * The handle returned by [Sparkle4j.configure] / [Sparkle4jBuilder.build].
 */
interface Sparkle4jInstance {
    /** Fire-and-forget background check. Shows dialog if an update is found. */
    fun checkInBackground()

    /** Blocking check. Returns the newest available update, or null if up to date. */
    fun checkNow(): UpdateItem?

    /** Download and apply [item] immediately (skips the check dialog). */
    fun applyUpdate(item: UpdateItem)

    /** Suppress future checks for [version] (persisted across launches). */
    fun skipVersion(version: String)
}

// ---------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------

/** Kotlin DSL entry point. */
object Sparkle4j {

    /** Kotlin DSL. */
    fun configure(block: Sparkle4jBuilder.() -> Unit): Sparkle4jInstance = Sparkle4jBuilder().apply(block).build()

    /** Java fluent-builder entry point. */
    @JvmStatic
    fun builder(): Sparkle4jBuilder = Sparkle4jBuilder()
}

// ---------------------------------------------------------------------------
// Builder (supports both Kotlin property-style and Java fluent-style usage)
// ---------------------------------------------------------------------------

class Sparkle4jBuilder {
    var appcastUrl: String = ""
    var currentVersion: String = ""
    var publicKey: String? = null
    var checkIntervalHours: Int = 24
    var parentComponent: Component? = null
    var appName: String = resolveAppName()
    var onUpdateFound: ((UpdateItem) -> Boolean)? = null
    var macosAppPath: Path? = null

    // Java fluent setters
    fun appcastUrl(url: String) = apply { appcastUrl = url }
    fun currentVersion(version: String) = apply { currentVersion = version }
    fun publicKey(key: String?) = apply { publicKey = key }
    fun checkIntervalHours(hours: Int) = apply { checkIntervalHours = hours }
    fun parentComponent(component: Component?) = apply { parentComponent = component }
    fun appName(name: String) = apply { appName = name }
    fun onUpdateFound(handler: (UpdateItem) -> Boolean) = apply { onUpdateFound = handler }
    fun macosAppPath(path: Path?) = apply { macosAppPath = path }

    fun build(): Sparkle4jInstance {
        require(appcastUrl.isNotBlank()) { "appcastUrl is required" }
        require(currentVersion.isNotBlank()) { "currentVersion is required" }
        return Sparkle4jInstanceImpl(
            Sparkle4jConfig(
                appcastUrl = appcastUrl,
                currentVersion = currentVersion,
                publicKey = publicKey,
                checkIntervalHours = checkIntervalHours,
                parentComponent = parentComponent,
                appName = appName,
                onUpdateFound = onUpdateFound,
                macosAppPath = macosAppPath,
            ),
        )
    }
}

// ---------------------------------------------------------------------------
// Implementation
// ---------------------------------------------------------------------------

private class Sparkle4jInstanceImpl(private val config: Sparkle4jConfig) : Sparkle4jInstance {

    private val log = Logger.getLogger(Sparkle4jInstanceImpl::class.java.name)

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sparkle4j-checker").also { it.isDaemon = true }
    }
    private val prefs = UpdatePreferences(config.appName)
    private val fetcher = AppcastFetcher()
    private val parser = AppcastParser()
    private val verifier = SignatureVerifier(config.publicKey)
    private val applier = UpdateApplier(config)

    // Top-level safety net: the library must never crash the host app (design doc §Error Handling)
    @Suppress("TooGenericExceptionCaught")
    override fun checkInBackground() {
        executor.submit {
            try {
                val item = checkNow() ?: return@submit
                SwingUtilities.invokeLater { presentUpdate(item) }
            } catch (e: RuntimeException) {
                log.warning("Unexpected error during background update check: ${e.message}")
            }
        }
    }

    override fun checkNow(): UpdateItem? {
        if (!isCheckDue()) return null
        val best = fetchAndParseBestUpdate() ?: return null
        if (prefs.isSkipped(best.version)) return null
        if (!VersionComparator.isNewer(best.version, config.currentVersion)) return null
        return best
    }

    private fun isCheckDue(): Boolean {
        if (config.checkIntervalHours > 0) {
            val last = prefs.lastCheckTimestamp
            if (last != null && Duration.between(last, Instant.now()).toHours() < config.checkIntervalHours) {
                return false
            }
        }
        if (!config.appcastUrl.startsWith("https://")) {
            log.severe("Appcast URL must be HTTPS: ${config.appcastUrl}")
            return false
        }
        return true
    }

    // Broad catches intentional: XML parsing and HTTP can throw various unchecked exceptions,
    // and the library must never propagate errors to the host app (design doc §Error Handling)
    @Suppress("TooGenericExceptionCaught")
    private fun fetchAndParseBestUpdate(): UpdateItem? {
        val xml = try {
            fetcher.fetch(config.appcastUrl)
        } catch (e: RuntimeException) {
            log.warning("Failed to fetch appcast: ${e.message}")
            return null
        } ?: return null

        prefs.lastCheckTimestamp = Instant.now()

        val items = try {
            parser.parse(xml)
        } catch (e: RuntimeException) {
            log.severe("Failed to parse appcast: ${e.message}")
            return null
        } catch (e: org.xml.sax.SAXException) {
            log.severe("Failed to parse appcast: ${e.message}")
            return null
        }

        return items.firstOrNull()
    }

    override fun applyUpdate(item: UpdateItem) {
        val downloader = UpdateDownloader()
        val tempFile = try {
            downloader.download(item.url, item.length) { _, _ -> }
        } catch (e: java.io.IOException) {
            log.severe("Download failed in applyUpdate: ${e.message}")
            showErrorDialog("Download failed. Try again later.", "Download Error")
            return
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warning("Download interrupted in applyUpdate")
            return
        }
        installUpdate(item, tempFile)
    }

    override fun skipVersion(version: String) {
        prefs.skipVersion(version)
    }

    /** Called by the dialog after the download is complete. */
    internal fun installUpdate(item: UpdateItem, tempFile: Path) {
        if (item.edSignature != null && !verifier.verify(tempFile, item.edSignature)) {
            log.severe("Signature verification failed for ${item.version} — aborting update")
            Files.deleteIfExists(tempFile)
            showErrorDialog("The update could not be verified. It has been discarded for your safety.", "Verification Failed")
            return
        }
        applier.apply(item, tempFile)
    }

    private fun presentUpdate(item: UpdateItem) {
        val hook = config.onUpdateFound
        if (hook != null && !hook(item)) return // caller suppressed built-in dialog

        UpdateDialog(
            config = config,
            item = item,
            onSkip = { version -> skipVersion(version) },
            onInstall = { updateItem, tempFile -> installUpdate(updateItem, tempFile) },
        ).show()
    }

    private fun showErrorDialog(message: String, title: String) {
        SwingUtilities.invokeLater {
            JOptionPane.showMessageDialog(config.parentComponent, message, title, JOptionPane.ERROR_MESSAGE)
        }
    }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

internal fun resolveAppName(): String = Sparkle4jConfig::class.java.`package`?.implementationTitle ?: "Application"
