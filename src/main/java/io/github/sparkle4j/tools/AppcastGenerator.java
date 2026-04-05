package io.github.sparkle4j.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CLI tool for generating / updating an appcast XML file on each release.
 *
 * <p>Usage: {@code java -cp sparkle4j.jar io.github.sparkle4j.tools.AppcastGenerator <version>
 * <appcast-file>}
 *
 * <p>Environment variables:
 *
 * <ul>
 *   <li>{@code SPARKLE_PRIVATE_KEY} — Base64-encoded PKCS#8 Ed25519 private key
 *   <li>{@code APPCAST_BASE_URL} — Base download URL
 *   <li>{@code INSTALLER_WINDOWS} — Path to the Windows .exe installer
 *   <li>{@code INSTALLER_MACOS} — Path to the macOS .zip archive
 *   <li>{@code INSTALLER_LINUX} — Path to the Linux .deb or .rpm package
 *   <li>{@code RELEASE_NOTES_URL} — URL to the release notes page (optional)
 *   <li>{@code APP_NAME} — Application name (optional)
 * </ul>
 */
public final class AppcastGenerator {

    private static final Logger log = Logger.getLogger(AppcastGenerator.class.getName());

    private AppcastGenerator() {}

    private record Installer(Path path, String os, String ext) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            log.severe("Usage: AppcastGenerator <version> <appcast-file>");
            System.exit(1);
        }

        var version = args[0];
        var appcastPath = Path.of(args[1]);

        var privateKeyB64 = requireEnv("SPARKLE_PRIVATE_KEY");
        var baseUrl = requireEnv("APPCAST_BASE_URL").replaceAll("/+$", "");
        var appName = System.getenv("APP_NAME") != null ? System.getenv("APP_NAME") : "Application";
        var releaseNotesUrl = System.getenv("RELEASE_NOTES_URL");
        var installers = collectInstallers();

        if (installers.isEmpty()) {
            log.severe("No installer paths provided via INSTALLER_* environment variables.");
            System.exit(1);
        }

        var privateKey = loadPrivateKey(privateKeyB64);
        var pubDate =
                ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        var enclosures = buildEnclosures(installers, baseUrl, privateKey);
        var newItem = buildItem(appName, version, pubDate, releaseNotesUrl, enclosures);

        var existing =
                Files.exists(appcastPath)
                        ? Files.readString(appcastPath)
                        : minimalRssSkeleton(appName);
        var updated = insertItem(existing, newItem);
        var parentDir = appcastPath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
        Files.writeString(appcastPath, updated);

        log.info(
                "Appcast updated: "
                        + appcastPath
                        + " (version "
                        + version
                        + ", "
                        + installers.size()
                        + " enclosure(s))");
    }

    private static List<Installer> collectInstallers() {
        var list = new ArrayList<Installer>();
        var win = System.getenv("INSTALLER_WINDOWS");
        if (win != null) list.add(new Installer(Path.of(win), "windows", "exe"));
        var mac = System.getenv("INSTALLER_MACOS");
        if (mac != null) list.add(new Installer(Path.of(mac), "macos", "zip"));
        var linux = System.getenv("INSTALLER_LINUX");
        if (linux != null) {
            list.add(
                    new Installer(Path.of(linux), "linux", linux.endsWith(".rpm") ? "rpm" : "deb"));
        }
        return list;
    }

    private static String buildEnclosures(
            List<Installer> installers, String baseUrl, PrivateKey privateKey)
            throws IOException, GeneralSecurityException {
        return installers.stream()
                .map(
                        inst -> {
                            try {
                                var bytes = Files.readAllBytes(inst.path());
                                var sha256 = sha256Hex(bytes);
                                var signature = sign(bytes, privateKey);
                                var fileNamePart = inst.path().getFileName();
                                var fileName =
                                        fileNamePart != null
                                                ? fileNamePart.toString()
                                                : inst.path().toString();
                                var length = Files.size(inst.path());
                                var url = baseUrl + "/" + fileName;
                                var mimeType = mimeType(inst.ext());
                                return """
                          <enclosure
                            url="%s"
                            sparkle:os="%s"
                            length="%d"
                            type="%s"
                            sparkle:edSignature="%s"
                            sparkle:sha256="%s"/>"""
                                        .formatted(
                                                url, inst.os(), length, mimeType, signature,
                                                sha256);
                            } catch (IOException | GeneralSecurityException e) {
                                throw new IllegalStateException(e);
                            }
                        })
                .collect(Collectors.joining("\n"));
    }

    private static String buildItem(
            String appName,
            String version,
            String pubDate,
            String releaseNotesUrl,
            String enclosures) {
        var releaseNotesElement =
                releaseNotesUrl != null
                        ? "\n      <sparkle:releaseNotesLink>"
                                + releaseNotesUrl
                                + "</sparkle:releaseNotesLink>"
                        : "";
        return """
                <item>
                  <title>%s %s</title>
                  <pubDate>%s</pubDate>
                  <sparkle:version>%s</sparkle:version>%s
                  %s
                </item>"""
                .formatted(appName, version, pubDate, version, releaseNotesElement, enclosures);
    }

    private static String requireEnv(String name) {
        var value = System.getenv(name);
        if (value == null) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }

    private static PrivateKey loadPrivateKey(String base64) throws GeneralSecurityException {
        var bytes = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private static String sign(byte[] data, PrivateKey key) throws GeneralSecurityException {
        var sig = Signature.getInstance("Ed25519");
        sig.initSign(key);
        sig.update(data);
        return Base64.getEncoder().encodeToString(sig.sign());
    }

    private static String sha256Hex(byte[] data) throws GeneralSecurityException {
        var digest = MessageDigest.getInstance("SHA-256").digest(data);
        var sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static String mimeType(String ext) {
        return switch (ext) {
            case "exe" -> "application/octet-stream";
            case "zip" -> "application/zip";
            case "deb" -> "application/vnd.debian.binary-package";
            case "rpm" -> "application/x-rpm";
            default -> "application/octet-stream";
        };
    }

    private static String minimalRssSkeleton(String appName) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"
                 xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle"
                 xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>%s</title>
              </channel>
            </rss>
            """
                .formatted(appName);
    }

    private static final Pattern ITEM_MARKER = Pattern.compile("(\\s*<item\\b)");

    private static String insertItem(String existing, String newItem) {
        var matcher = ITEM_MARKER.matcher(existing);
        if (matcher.find()) {
            return existing.substring(0, matcher.start())
                    + "\n    "
                    + newItem
                    + "\n"
                    + existing.substring(matcher.start());
        } else {
            return existing.replace("</channel>", "\n    " + newItem + "\n  </channel>");
        }
    }
}
