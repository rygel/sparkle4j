/**
 * sparkle4j — in-app update checking and silent update installation for Java desktop apps.
 *
 * <p>Compatible with the <a href="https://sparkle-project.org">Sparkle</a> appcast RSS format. The
 * same XML feed works with macOS Sparkle, WinSparkle, and sparkle4j.
 *
 * <h2>Quick start</h2>
 *
 * <pre>{@code
 * try (Sparkle4jInstance updater = Sparkle4j.builder()
 *         .appcastUrl("https://example.com/appcast.xml")
 *         .currentVersion("1.0.0")
 *         .publicKey("BASE64_ED25519_PUBLIC_KEY")
 *         .parentComponent(mainWindow)
 *         .build()) {
 *     updater.checkInBackground();
 * }
 * }</pre>
 *
 * <h2>Entry points</h2>
 *
 * <ul>
 *   <li>{@link io.github.rygel.sparkle4j.Sparkle4j} — static factory
 *   <li>{@link io.github.rygel.sparkle4j.Sparkle4jBuilder} — fluent builder
 *   <li>{@link io.github.rygel.sparkle4j.Sparkle4jInstance} — runtime handle (implements {@link
 *       java.io.Closeable})
 *   <li>{@link io.github.rygel.sparkle4j.UpdateItem} — a single parsed update entry
 *   <li>{@link io.github.rygel.sparkle4j.Downloader} — customise download behaviour
 * </ul>
 */
@NullMarked
package io.github.rygel.sparkle4j;

import org.jspecify.annotations.NullMarked;
