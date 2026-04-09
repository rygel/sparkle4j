package io.github.rygel.sparkle4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.Optional;

/**
 * The handle returned by {@link Sparkle4jBuilder#build()}.
 *
 * <p>Implements {@link Closeable} so host applications can cleanly shut down the background checker
 * thread during a graceful shutdown. Use with try-with-resources or call {@link #close()} in a
 * shutdown hook.
 */
public interface Sparkle4jInstance extends Closeable {

    /** Fire-and-forget background check. Shows dialog if an update is found. */
    void checkInBackground();

    /**
     * Blocking check. Returns the newest available update, or empty if up to date, throttled, or
     * skipped.
     *
     * @return the newest available update, or empty if up to date, throttled, or skipped
     * @throws IOException if the appcast cannot be fetched due to a network or HTTP error
     */
    Optional<UpdateItem> checkNow() throws IOException;

    /**
     * Download and apply the given item immediately (skips the check dialog).
     *
     * @param item the update to download and install
     */
    void applyUpdate(UpdateItem item);

    /**
     * Suppress future checks for this version (persisted across launches).
     *
     * @param version the version string to skip
     */
    void skipVersion(String version);

    /** Shuts down the background checker thread. Safe to call multiple times. */
    @Override
    void close();
}
