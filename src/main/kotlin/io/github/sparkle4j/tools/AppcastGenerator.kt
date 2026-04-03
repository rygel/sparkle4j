package io.github.sparkle4j.tools

import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

/**
 * CLI tool for generating / updating an appcast XML file on each release.
 *
 * Usage:
 *   java -cp sparkle4j.jar io.github.sparkle4j.tools.AppcastGeneratorKt \
 *        <version> <appcast-file>
 *
 * Environment variables:
 *   SPARKLE_PRIVATE_KEY   Base64-encoded PKCS#8 Ed25519 private key
 *   APPCAST_BASE_URL      Base download URL (e.g. https://example.com/releases)
 *   INSTALLER_WINDOWS     Path to the Windows .exe installer
 *   INSTALLER_MACOS       Path to the macOS .zip archive
 *   INSTALLER_LINUX       Path to the Linux .deb or .rpm package
 *   RELEASE_NOTES_URL     URL to the release notes page (optional)
 *   APP_NAME              Application name shown in the appcast title (optional)
 *
 * The tool prepends a new <item> to the existing appcast.xml content.
 * If the appcast file does not exist, a minimal RSS wrapper is created.
 *
 * Key generation (one-time, by the project maintainer):
 *   openssl genpkey -algorithm ed25519 -out private.pem
 *   openssl pkey -in private.pem -pubout -out public.pem
 *   # Export private key as base64 PKCS#8 for CI:
 *   openssl pkcs8 -topk8 -nocrypt -in private.pem -outform DER | base64 -w 0
 *   # Export public key as base64 X.509 SubjectPublicKeyInfo for the app:
 *   openssl pkey -in private.pem -pubout -outform DER | base64 -w 0
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("Usage: AppcastGenerator <version> <appcast-file>")
        System.exit(1)
    }

    val version = args[0]
    val appcastPath = Path.of(args[1])

    val privateKeyB64 = requireEnv("SPARKLE_PRIVATE_KEY")
    val baseUrl = requireEnv("APPCAST_BASE_URL").trimEnd('/')
    val appName = System.getenv("APP_NAME") ?: "Application"
    val releaseNotesUrl = System.getenv("RELEASE_NOTES_URL")
    val installers = collectInstallers()

    if (installers.isEmpty()) {
        System.err.println("No installer paths provided via INSTALLER_* environment variables.")
        System.exit(1)
    }

    val privateKey = loadPrivateKey(privateKeyB64)
    val pubDate = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
    val enclosures = buildEnclosures(installers, baseUrl, privateKey)
    val newItem = buildItem(appName, version, pubDate, releaseNotesUrl, enclosures)

    val existing = if (Files.exists(appcastPath)) Files.readString(appcastPath) else minimalRssSkeleton(appName)
    val updated = insertItem(existing, newItem)
    appcastPath.parent?.let { Files.createDirectories(it) }
    Files.writeString(appcastPath, updated)

    println("Appcast updated: $appcastPath (version $version, ${installers.size} enclosure(s))")
}

private fun collectInstallers(): List<Installer> = buildList {
    System.getenv("INSTALLER_WINDOWS")?.let { add(Installer(Path.of(it), "windows", "exe")) }
    System.getenv("INSTALLER_MACOS")?.let { add(Installer(Path.of(it), "macos", "zip")) }
    System.getenv("INSTALLER_LINUX")?.let { add(Installer(Path.of(it), "linux", detectLinuxExt(it))) }
}

private fun buildEnclosures(installers: List<Installer>, baseUrl: String, privateKey: java.security.PrivateKey): String =
    installers.joinToString("\n") { inst ->
        val bytes = Files.readAllBytes(inst.path)
        val sha256 = sha256Hex(bytes)
        val signature = sign(bytes, privateKey)
        val fileName = inst.path.fileName.toString()
        val length = Files.size(inst.path)
        val url = "$baseUrl/$fileName"
        val mimeType = mimeType(inst.ext)
        """
      <enclosure
        url="$url"
        sparkle:os="${inst.os}"
        length="$length"
        type="$mimeType"
        sparkle:edSignature="$signature"
        sparkle:sha256="$sha256"/>
        """.trimIndent()
    }

private fun buildItem(appName: String, version: String, pubDate: String, releaseNotesUrl: String?, enclosures: String): String {
    val releaseNotesElement = releaseNotesUrl
        ?.let { "\n      <sparkle:releaseNotesLink>$it</sparkle:releaseNotesLink>" } ?: ""
    return """
    <item>
      <title>$appName $version</title>
      <pubDate>$pubDate</pubDate>
      <sparkle:version>$version</sparkle:version>$releaseNotesElement
      $enclosures
    </item>
    """.trimIndent()
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

private data class Installer(val path: Path, val os: String, val ext: String)

private fun requireEnv(name: String): String = System.getenv(name) ?: error("Required environment variable not set: $name")

private fun detectLinuxExt(path: String): String = if (path.endsWith(".rpm")) "rpm" else "deb"

private fun loadPrivateKey(base64: String): java.security.PrivateKey {
    val bytes = Base64.getDecoder().decode(base64)
    val spec = PKCS8EncodedKeySpec(bytes)
    return KeyFactory.getInstance("Ed25519").generatePrivate(spec)
}

private fun sign(data: ByteArray, key: java.security.PrivateKey): String {
    val sig = Signature.getInstance("Ed25519")
    sig.initSign(key)
    sig.update(data)
    return Base64.getEncoder().encodeToString(sig.sign())
}

private fun sha256Hex(data: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(data)
    .joinToString("") { "%02x".format(it) }

private fun mimeType(ext: String): String = when (ext) {
    "exe" -> "application/octet-stream"
    "zip" -> "application/zip"
    "deb" -> "application/vnd.debian.binary-package"
    "rpm" -> "application/x-rpm"
    else -> "application/octet-stream"
}

private fun minimalRssSkeleton(appName: String): String = """<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle"
     xmlns:dc="http://purl.org/dc/elements/1.1/">
  <channel>
    <title>$appName</title>
  </channel>
</rss>
"""

/**
 * Inserts [newItem] as the first `<item>` inside `<channel>`.
 * If no `<item>` exists yet, inserts before `</channel>`.
 */
private fun insertItem(existing: String, newItem: String): String {
    val marker = Regex("""(\s*<item\b)""")
    val match = marker.find(existing)
    return if (match != null) {
        existing.substring(0, match.range.first) +
            "\n    $newItem\n" +
            existing.substring(match.range.first)
    } else {
        existing.replace("</channel>", "\n    $newItem\n  </channel>")
    }
}
