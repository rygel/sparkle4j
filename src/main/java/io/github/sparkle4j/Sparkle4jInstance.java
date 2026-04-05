package io.github.sparkle4j;

import org.jspecify.annotations.Nullable;

/** The handle returned by {@link Sparkle4jBuilder#build()}. */
public interface Sparkle4jInstance {

    /** Fire-and-forget background check. Shows dialog if an update is found. */
    void checkInBackground();

    /** Blocking check. Returns the newest available update, or null if up to date. */
    @Nullable UpdateItem checkNow();

    /** Download and apply the given item immediately (skips the check dialog). */
    void applyUpdate(UpdateItem item);

    /** Suppress future checks for this version (persisted across launches). */
    void skipVersion(String version);
}
