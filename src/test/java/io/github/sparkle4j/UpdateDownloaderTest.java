package io.github.sparkle4j;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

class UpdateDownloaderTest {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(WireMockConfiguration.wireMockConfig().dynamicPort())
        .build();

    @TempDir
    Path tempDir;

    private String url(String path) {
        return "http://localhost:" + wireMock.getPort() + path;
    }

    @Test @DisplayName("downloads file and fires progress callbacks")
    void downloadsWithProgress() throws Exception {
        var content = "hello world update payload".getBytes();
        wireMock.stubFor(get(urlEqualTo("/update.exe"))
            .willReturn(aResponse().withStatus(200).withBody(content)));

        var progressEvents = new ArrayList<long[]>();
        var downloader = new TestDownloader(tempDir);
        var file = downloader.download(url("/update.exe"), content.length, (received, total) ->
            progressEvents.add(new long[]{received, total}));

        assertTrue(Files.exists(file));
        assertArrayEquals(content, Files.readAllBytes(file));
        assertFalse(progressEvents.isEmpty());
        assertEquals(content.length, progressEvents.get(progressEvents.size() - 1)[0]);
    }

    @Test @DisplayName("resumes partial download when temp file exists")
    void resumesPartialDownload() throws Exception {
        var first = "first-half-".getBytes();
        var second = "second-half".getBytes();
        var full = new byte[first.length + second.length];
        System.arraycopy(first, 0, full, 0, first.length);
        System.arraycopy(second, 0, full, first.length, second.length);

        wireMock.stubFor(get(urlEqualTo("/update.exe"))
            .withHeader("Range", matching("bytes=\\d+-"))
            .willReturn(aResponse().withStatus(206).withBody(second)));

        // Pre-create partial temp file
        Files.write(tempDir.resolve("sparkle4j-update.exe"), first);

        var downloader = new TestDownloader(tempDir);
        var file = downloader.download(url("/update.exe"), full.length, (r, t) -> {});

        assertArrayEquals(full, Files.readAllBytes(file));
        wireMock.verify(getRequestedFor(urlEqualTo("/update.exe"))
            .withHeader("Range", matching("bytes=" + first.length + "-")));
    }

    @Test @DisplayName("starts fresh download when server returns 200 instead of 206")
    void freshDownloadOnFull200() throws Exception {
        var content = "full content".getBytes();
        wireMock.stubFor(get(urlEqualTo("/update.exe"))
            .willReturn(aResponse().withStatus(200).withBody(content)));

        // Pre-create stale partial file
        Files.write(tempDir.resolve("sparkle4j-update.exe"), "stale partial".getBytes());

        var downloader = new TestDownloader(tempDir);
        var file = downloader.download(url("/update.exe"), content.length, (r, t) -> {});

        assertArrayEquals(content, Files.readAllBytes(file));
    }

    @Test @DisplayName("throws IOException on HTTP error")
    void throwsOnHttpError() {
        wireMock.stubFor(get(urlEqualTo("/update.exe"))
            .willReturn(aResponse().withStatus(503)));

        var downloader = new TestDownloader(tempDir);
        assertThrows(IOException.class, () ->
            downloader.download(url("/update.exe"), 100L, (r, t) -> {}));
    }

    @Test @DisplayName("throws InterruptedException when thread is interrupted")
    void throwsOnInterrupt() {
        var content = new byte[1024];
        wireMock.stubFor(get(urlEqualTo("/update.exe"))
            .willReturn(aResponse().withStatus(200).withBody(content)));

        var downloader = new TestDownloader(tempDir);
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, () ->
                downloader.download(url("/update.exe"), content.length, (r, t) -> {}));
        } finally {
            Thread.interrupted(); // clear flag
        }
    }

    /** Thin subclass that redirects temp files into the test temp directory. */
    private static class TestDownloader extends UpdateDownloader {
        private final Path tempDir;
        TestDownloader(Path tempDir) { this.tempDir = tempDir; }
        @Override protected Path tempDir() { return tempDir; }
    }
}
