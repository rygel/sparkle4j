package io.github.sparkle4j

import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration
import java.util.logging.Logger

/**
 * Downloads an update installer to a temp file, with:
 * - Progress callback
 * - Resume support via HTTP Range header (retries partial download)
 */
open class UpdateDownloader {

    private val log = Logger.getLogger(UpdateDownloader::class.java.name)

    protected open fun tempDir(): Path = Path.of(System.getProperty("java.io.tmpdir"))

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    /**
     * Downloads [url] to a temp file named after the URL's last path segment.
     * Resumes a partial download if the temp file already exists.
     *
     * @param onProgress called periodically with (bytesReceived, totalExpected)
     * @return path to the completed temp file
     * @throws IOException on HTTP error or interrupted download
     */
    fun download(
        url: String,
        totalBytes: Long,
        onProgress: (bytesReceived: Long, totalBytes: Long) -> Unit,
    ): Path {
        val fileName = url.substringAfterLast('/').substringBefore('?').ifBlank { "sparkle4j-update" }
        val tempFile = tempDir().resolve("sparkle4j-$fileName")

        val existingBytes = if (Files.exists(tempFile)) Files.size(tempFile) else 0L

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
            log.fine("Resuming download of $fileName from byte $existingBytes")
        }

        val response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())

        val resumeAt: Long = when (response.statusCode()) {
            206 -> existingBytes
            200 -> {
                Files.deleteIfExists(tempFile)
                0L
            }
            else -> throw IOException("HTTP ${response.statusCode()} downloading $url")
        }

        val openOptions = if (resumeAt > 0) {
            arrayOf(StandardOpenOption.APPEND)
        } else {
            arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
        }

        response.body().use { input ->
            Files.newOutputStream(tempFile, *openOptions).use { output ->
                val buf = ByteArray(65536)
                var received = resumeAt
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException("Download cancelled")
                    }
                    output.write(buf, 0, n)
                    received += n
                    onProgress(received, totalBytes)
                }
            }
        }

        return tempFile
    }
}
