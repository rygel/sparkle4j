package io.github.rygel.sparkle4j;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Fetches the appcast XML over HTTPS with ETag/Last-Modified caching. Returns null if the feed has
 * not changed since the last fetch (304). Throws {@link IOException} on network errors or non-2xx
 * HTTP responses.
 */
final class AppcastFetcher {

    private static final Logger log = Logger.getLogger(AppcastFetcher.class.getName());

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    private final Map<String, CacheEntry> cache = new HashMap<>();

    private record CacheEntry(String body, @Nullable String etag, @Nullable String lastModified) {}

    /**
     * @return the XML body, or null if not modified (304 with no cached body).
     * @throws IOException on network error or non-2xx/304 HTTP response
     */
    @Nullable String fetch(String url) throws IOException {
        var cached = cache.get(url);
        var builder =
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();

        if (cached != null && cached.etag() != null) {
            builder.header("If-None-Match", cached.etag());
        }
        if (cached != null && cached.lastModified() != null) {
            builder.header("If-Modified-Since", cached.lastModified());
        }

        HttpResponse<String> response;
        try {
            response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.warning("Network error fetching appcast from " + url + ": " + e.getMessage());
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Appcast fetch interrupted for " + url, e);
        }

        return switch (response.statusCode()) {
            case 304 -> {
                log.fine("Appcast not modified (304)");
                if (cached != null) {
                    yield cached.body();
                }
                yield null;
            }
            case 200 -> {
                var body = response.body();
                var etag = response.headers().firstValue("ETag").orElse(null);
                var lastModified = response.headers().firstValue("Last-Modified").orElse(null);
                cache.put(url, new CacheEntry(body, etag, lastModified));
                yield body;
            }
            default ->
                    throw new IOException(
                            "HTTP " + response.statusCode() + " fetching appcast from " + url);
        };
    }
}
