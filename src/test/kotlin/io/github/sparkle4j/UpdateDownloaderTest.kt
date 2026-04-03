package io.github.sparkle4j

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UpdateDownloaderTest {

    @RegisterExtension
    val wireMock: WireMockExtension = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build()

    @TempDir
    lateinit var tempDir: Path

    private fun url(path: String) = "http://localhost:${wireMock.port}$path"

    @Test
    fun `downloads file and fires progress callbacks`() {
        val content = "hello world update payload".toByteArray()
        wireMock.stubFor(
            get(urlEqualTo("/update.exe"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(content),
                ),
        )

        val progressEvents = mutableListOf<Pair<Long, Long>>()
        val downloader = UpdateDownloaderUnderTest(tempDir)

        val file = downloader.download(url("/update.exe"), content.size.toLong()) { received, total ->
            progressEvents.add(received to total)
        }

        assertTrue(Files.exists(file))
        assertEquals(content.toList(), Files.readAllBytes(file).toList())
        assertTrue(progressEvents.isNotEmpty(), "Expected progress callbacks")
        assertEquals(content.size.toLong(), progressEvents.last().first)
    }

    @Test
    fun `resumes partial download when temp file exists`() {
        val first = "first-half-".toByteArray()
        val second = "second-half".toByteArray()
        val full = first + second

        // Stub for partial content response when Range header is present
        wireMock.stubFor(
            get(urlEqualTo("/update.exe"))
                .withHeader("Range", matching("bytes=\\d+-"))
                .willReturn(
                    aResponse()
                        .withStatus(206)
                        .withBody(second),
                ),
        )

        // Pre-create a partial temp file with the first half already downloaded
        val tempFile = tempDir.resolve("sparkle4j-update.exe")
        Files.write(tempFile, first)

        val downloader = UpdateDownloaderUnderTest(tempDir)
        val file = downloader.download(url("/update.exe"), full.size.toLong()) { _, _ -> }

        assertEquals(full.toList(), Files.readAllBytes(file).toList())
        wireMock.verify(
            getRequestedFor(urlEqualTo("/update.exe"))
                .withHeader("Range", matching("bytes=${first.size}-")),
        )
    }

    @Test
    fun `starts fresh download when server returns 200 instead of 206`() {
        val content = "full content".toByteArray()
        wireMock.stubFor(
            get(urlEqualTo("/update.exe"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(content),
                ),
        )

        // Pre-create a stale partial file
        val tempFile = tempDir.resolve("sparkle4j-update.exe")
        Files.write(tempFile, "stale partial".toByteArray())

        val downloader = UpdateDownloaderUnderTest(tempDir)
        val file = downloader.download(url("/update.exe"), content.size.toLong()) { _, _ -> }

        assertEquals(content.toList(), Files.readAllBytes(file).toList())
    }

    @Test
    fun `throws IOException on HTTP error`() {
        wireMock.stubFor(
            get(urlEqualTo("/update.exe"))
                .willReturn(aResponse().withStatus(503)),
        )

        val downloader = UpdateDownloaderUnderTest(tempDir)
        assertFailsWith<java.io.IOException> {
            downloader.download(url("/update.exe"), 100L) { _, _ -> }
        }
    }

    @Test
    fun `throws InterruptedException when thread is interrupted during download`() {
        val content = ByteArray(1024) { it.toByte() }
        wireMock.stubFor(
            get(urlEqualTo("/update.exe"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withBody(content),
                ),
        )

        val downloader = UpdateDownloaderUnderTest(tempDir)
        Thread.currentThread().interrupt()
        try {
            assertFailsWith<InterruptedException> {
                downloader.download(url("/update.exe"), content.size.toLong()) { _, _ -> }
            }
        } finally {
            Thread.interrupted() // clear flag
        }
    }
}

/**
 * Thin subclass that redirects temp files into [tempDir] so tests don't pollute the system temp directory.
 */
private class UpdateDownloaderUnderTest(private val tempDir: Path) : UpdateDownloader() {
    override fun tempDir(): Path = tempDir
}
