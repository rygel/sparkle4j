package io.github.sparkle4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

/**
 * Downloads an update installer to a temp file, with:
 *
 * <ul>
 *   <li>Progress callback
 *   <li>Resume support via HTTP Range header (retries partial download)
 * </ul>
 */
public class UpdateDownloader {

    private static final Logger log = Logger.getLogger(UpdateDownloader.class.getName());

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

    protected Path tempDir() {
        return Path.of(System.getProperty("java.io.tmpdir"));
    }

    /**
     * Downloads the given URL to a temp file named after the URL's last path segment. Resumes a
     * partial download if the temp file already exists.
     *
     * @param url the URL to download
     * @param totalBytes expected file size (for progress reporting)
     * @param onProgress called periodically with (bytesReceived, totalExpected)
     * @return path to the completed temp file
     * @throws IOException on HTTP error
     * @throws InterruptedException if the download is cancelled
     */
    public Path download(String url, long totalBytes, BiConsumer<Long, Long> onProgress)
            throws IOException, InterruptedException {
        var lastSlash = url.lastIndexOf('/');
        var raw = lastSlash >= 0 ? url.substring(lastSlash + 1) : url;
        var qmark = raw.indexOf('?');
        var fileName = qmark >= 0 ? raw.substring(0, qmark) : raw;
        if (fileName.isBlank()) fileName = "sparkle4j-update";
        var tempFile = tempDir().resolve("sparkle4j-" + fileName);

        long existingBytes = Files.exists(tempFile) ? Files.size(tempFile) : 0L;

        var requestBuilder =
                HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(30)).GET();

        if (existingBytes > 0) {
            requestBuilder.header("Range", "bytes=" + existingBytes + "-");
            log.fine("Resuming download of " + fileName + " from byte " + existingBytes);
        }

        var response =
                client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

        long resumeAt =
                switch (response.statusCode()) {
                    case 206 -> existingBytes;
                    case 200 -> {
                        Files.deleteIfExists(tempFile);
                        yield 0L;
                    }
                    default ->
                            throw new IOException(
                                    "HTTP " + response.statusCode() + " downloading " + url);
                };

        streamToFile(response.body(), tempFile, resumeAt, totalBytes, onProgress);
        return tempFile;
    }

    private void streamToFile(
            InputStream input,
            Path tempFile,
            long resumeAt,
            long totalBytes,
            BiConsumer<Long, Long> onProgress)
            throws IOException, InterruptedException {
        var openOptions =
                resumeAt > 0
                        ? new StandardOpenOption[] {StandardOpenOption.APPEND}
                        : new StandardOpenOption[] {
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING
                        };

        try (input;
                OutputStream output = Files.newOutputStream(tempFile, openOptions)) {
            var buf = new byte[65_536];
            long received = resumeAt;
            int n;
            while ((n = input.read(buf)) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException("Download cancelled");
                }
                output.write(buf, 0, n);
                received += n;
                onProgress.accept(received, totalBytes);
            }
        }
    }
}
