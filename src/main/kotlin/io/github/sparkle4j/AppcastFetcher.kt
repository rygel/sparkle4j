package io.github.sparkle4j

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.logging.Logger

/**
 * Fetches the appcast XML over HTTPS with ETag/Last-Modified caching.
 * Returns null if the feed has not changed since the last fetch (304).
 * Fails silently on network errors — callers receive null.
 */
internal class AppcastFetcher {

    private val log = Logger.getLogger(AppcastFetcher::class.java.name)

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // In-memory cache across calls within the same process lifetime.
    private val cache = mutableMapOf<String, CacheEntry>()

    private data class CacheEntry(val body: String, val etag: String?, val lastModified: String?)

    /**
     * @return the XML body, or null if not modified / on error.
     */
    fun fetch(url: String): String? {
        val cached = cache[url]
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .GET()

        cached?.etag?.let { builder.header("If-None-Match", it) }
        cached?.lastModified?.let { builder.header("If-Modified-Since", it) }

        val response = try {
            client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        } catch (e: java.io.IOException) {
            log.warning("Network error fetching appcast from $url: ${e.message}")
            return null
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            log.warning("Appcast fetch interrupted for $url")
            return null
        }

        return when (response.statusCode()) {
            304 -> {
                log.fine("Appcast not modified (304)")
                cached?.body
            }
            200 -> {
                val body = response.body()
                val etag = response.headers().firstValue("ETag").orElse(null)
                val lastModified = response.headers().firstValue("Last-Modified").orElse(null)
                cache[url] = CacheEntry(body, etag, lastModified)
                body
            }
            else -> {
                log.warning("Unexpected HTTP ${response.statusCode()} fetching appcast from $url")
                null
            }
        }
    }
}
