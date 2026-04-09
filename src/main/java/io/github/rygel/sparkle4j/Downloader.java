package io.github.rygel.sparkle4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Callback that downloads an update to a local file.
 *
 * <p>The default implementation is {@link UpdateDownloader}. Implement this interface to substitute
 * a custom download strategy (custom HTTP headers, proxy support, authentication, etc.).
 *
 * @see Sparkle4jBuilder#downloader(java.util.function.Function)
 */
@FunctionalInterface
public interface Downloader {

    /**
     * Downloads the update to a temp file and returns its path.
     *
     * @param onProgress called periodically with (bytesReceived, totalExpected)
     * @return the path to the downloaded file
     * @throws IOException on HTTP or I/O error
     * @throws InterruptedException if the download is cancelled
     */
    Path download(BiConsumer<Long, Long> onProgress) throws IOException, InterruptedException;
}
