package io.github.rygel.sparkle4j;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

class Sparkle4jBuilderTest {

    @Test
    @DisplayName("build() throws on blank appcastUrl")
    void throwsOnBlankAppcastUrl() {
        var builder = Sparkle4j.builder().currentVersion("1.0.0");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    @DisplayName("build() throws on blank currentVersion")
    void throwsOnBlankCurrentVersion() {
        var builder = Sparkle4j.builder().appcastUrl("https://example.com/appcast.xml");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    @DisplayName("build() succeeds with required fields only")
    void buildsWithRequiredFields() {
        try (var instance =
                Sparkle4j.builder()
                        .appcastUrl("https://example.com/appcast.xml")
                        .currentVersion("1.0.0")
                        .build()) {
            assertNotNull(instance);
        }
    }

    @Test
    @DisplayName("builder() returns a new Sparkle4jBuilder")
    void builderReturnsBuilder() {
        assertNotNull(Sparkle4j.builder());
    }

    @Test
    @DisplayName("all fluent setters return the same builder instance")
    void fluentSettersReturnThis() {
        var builder = Sparkle4j.builder();
        assertSame(builder, builder.appcastUrl("https://example.com/appcast.xml"));
        assertSame(builder, builder.currentVersion("1.0.0"));
        assertSame(builder, builder.publicKey("key"));
        assertSame(builder, builder.checkIntervalHours(12));
        assertSame(builder, builder.parentComponent(new javax.swing.JPanel()));
        assertSame(builder, builder.appName("TestApp"));
        assertSame(builder, builder.onUpdateFound(item -> true));
        assertSame(builder, builder.macosAppPath(Path.of("/tmp")));
    }

    @Test
    @DisplayName("build() produces a working Sparkle4jInstance")
    void buildProducesInstance() {
        try (var instance =
                Sparkle4j.builder()
                        .appcastUrl("https://example.com/appcast.xml")
                        .currentVersion("1.0.0")
                        .appName("TestApp")
                        .checkIntervalHours(0)
                        .build()) {
            assertInstanceOf(Sparkle4jInstance.class, instance);
        }
    }

    @Test
    @DisplayName("build() throws on empty string appcastUrl")
    void throwsOnEmptyAppcastUrl() {
        var builder = Sparkle4j.builder().appcastUrl("").currentVersion("1.0.0");
        assertThrows(IllegalArgumentException.class, builder::build);
    }

    @Test
    @DisplayName("build() throws on whitespace-only currentVersion")
    void throwsOnWhitespaceCurrentVersion() {
        var builder =
                Sparkle4j.builder()
                        .appcastUrl("https://example.com/appcast.xml")
                        .currentVersion("   ");
        assertThrows(IllegalArgumentException.class, builder::build);
    }
}
