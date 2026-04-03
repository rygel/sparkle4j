# sparkle4j

In-app update checking and silent update application for Java desktop apps.

Compatible with the [Sparkle](https://sparkle-project.org/) appcast format — the same XML feed
works for macOS Sparkle, WinSparkle, and sparkle4j.

## Features

- Background update checking with configurable intervals
- Native-feeling Swing dialog with release notes and download progress
- Silent update application on Windows (Inno Setup / NSIS), macOS (.app bundle), and Linux (dpkg / rpm)
- Ed25519 signature verification
- ETag / Last-Modified caching
- Zero runtime dependencies beyond JDK 17+

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.sparkle4j</groupId>
    <artifactId>sparkle4j</artifactId>
    <version>0.5.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("io.github.sparkle4j:sparkle4j:0.5.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'io.github.sparkle4j:sparkle4j:0.5.0'
```

## Quick Start

```java
import io.github.sparkle4j.Sparkle4j;

// In your app's startup, after the main window is visible:
SwingUtilities.invokeLater(() -> {
    Sparkle4j.builder()
        .appcastUrl("https://example.com/appcast.xml")
        .currentVersion("0.5.0")
        .parentComponent(mainWindow)
        .build()
        .checkInBackground();
});
```

That's it. The library checks the feed, shows a dialog if an update is available, downloads it
with a progress bar, and applies it silently.

## Configuration

```java
import io.github.sparkle4j.Sparkle4j;
import io.github.sparkle4j.Sparkle4jInstance;

Sparkle4jInstance updater = Sparkle4j.builder()
    // Required
    .appcastUrl("https://example.com/appcast.xml")
    .currentVersion(BuildInfo.VERSION)

    // Recommended: Ed25519 signature verification
    .publicKey("BASE64_ED25519_PUBLIC_KEY")

    // Optional
    .appName("My App")
    .parentComponent(mainWindow)
    .checkIntervalHours(24)

    // Hook: return false to suppress the built-in dialog
    .onUpdateFound(item -> {
        System.out.println("Update available: " + item.version());
        return true;
    })
    .build();

// Fire-and-forget (shows dialog on EDT if update found)
updater.checkInBackground();
```

## Advanced Usage

### Blocking check (custom UI)

If you want full control over the UI, use `checkNow()` instead of `checkInBackground()`:

```java
Sparkle4jInstance updater = Sparkle4j.builder()
    .appcastUrl("https://example.com/appcast.xml")
    .currentVersion("0.5.0")
    .build();

// Blocking call — run on a background thread, not the EDT
UpdateItem update = updater.checkNow();
if (update != null) {
    System.out.println("New version: " + update.shortVersionString());
    System.out.println("Download URL: " + update.url());
    System.out.println("Release notes: " + update.releaseNotesUrl());
    System.out.println("Critical: " + update.isCriticalUpdate());

    // Download and apply directly (skips the built-in dialog)
    updater.applyUpdate(update);
}
```

### Skip a version

Users can skip versions via the dialog button. You can also do it programmatically:

```java
updater.skipVersion("0.7.0");  // persisted across app restarts
```

### Custom update hook (suppress built-in dialog)

Return `false` from `onUpdateFound` to handle the update entirely in your own UI:

```java
Sparkle4j.builder()
    .appcastUrl("https://example.com/appcast.xml")
    .currentVersion("0.5.0")
    .onUpdateFound(item -> {
        showCustomUpdateBanner(item.shortVersionString(), item.releaseNotesUrl());
        return false;  // suppress the built-in Swing dialog
    })
    .build()
    .checkInBackground();
```

## Appcast Format

The feed is standard RSS 2.0 with Sparkle namespace extensions. One `<enclosure>` per platform:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle"
     xmlns:dc="http://purl.org/dc/elements/1.1/">
  <channel>
    <title>My App</title>

    <item>
      <title>My App 0.7.0</title>
      <pubDate>Fri, 10 Apr 2026 12:00:00 +0000</pubDate>
      <sparkle:version>0.7.0</sparkle:version>
      <sparkle:releaseNotesLink>https://example.com/changelog.md</sparkle:releaseNotesLink>

      <!-- Windows -->
      <enclosure
        url="https://example.com/releases/myapp-0.7.0-windows.exe"
        sparkle:os="windows"
        length="185000000"
        type="application/octet-stream"
        sparkle:edSignature="BASE64_ED25519_SIGNATURE"
        sparkle:installerType="inno"/>

      <!-- macOS -->
      <enclosure
        url="https://example.com/releases/myapp-0.7.0-macos.zip"
        sparkle:os="macos"
        length="170000000"
        type="application/zip"
        sparkle:edSignature="BASE64_ED25519_SIGNATURE"/>

      <!-- Linux -->
      <enclosure
        url="https://example.com/releases/myapp-0.7.0.deb"
        sparkle:os="linux"
        length="160000000"
        type="application/vnd.debian.binary-package"
        sparkle:edSignature="BASE64_ED25519_SIGNATURE"/>
    </item>
  </channel>
</rss>
```

### Supported appcast fields

| Field | Required | Description |
|---|---|---|
| `sparkle:version` | Yes | Machine-readable version for comparison (SemVer) |
| `sparkle:os` on `<enclosure>` | Yes | Platform: `windows`, `macos`, `linux` |
| `enclosure url` | Yes | Direct download URL (must be HTTPS) |
| `enclosure length` | Yes | File size in bytes (used for progress bar) |
| `sparkle:edSignature` | No | Base64 Ed25519 signature of the file |
| `sparkle:shortVersionString` | No | Human-readable display version |
| `sparkle:releaseNotesLink` | No | URL to Markdown or HTML release notes |
| `sparkle:minimumJavaVersion` | No | Minimum Java major version (e.g. `17`) |
| `sparkle:installerType` | No | Windows only: `inno` (default) or `nsis` |
| `sparkle:criticalUpdate` | No | Presence disables "Skip This Version" |
| `pubDate` | No | Displayed in the update dialog |

## Signature Verification

sparkle4j uses Ed25519 for verifying downloads. Generate a keypair once:

```bash
# Generate private key
openssl genpkey -algorithm ed25519 -out private.pem

# Export public key (embed this in your app)
openssl pkey -in private.pem -pubout -outform DER | base64 -w 0

# Sign a release file
openssl pkeyutl -sign -inkey private.pem -in myapp-0.7.0-windows.exe | base64 -w 0
```

Pass the public key to sparkle4j:

```java
Sparkle4j.builder()
    .appcastUrl("https://example.com/appcast.xml")
    .currentVersion("0.5.0")
    .publicKey("MCowBQYDK2VwAyEA...")  // base64 from the export step above
    .build()
    .checkInBackground();
```

Both raw 32-byte keys (Sparkle format) and DER-encoded X.509 keys (Java standard, 44 bytes) are accepted.

If `publicKey` is null, signature verification is skipped with a warning log.

## Platform Behavior

| Platform | What happens after "Install Now" |
|----------|----------------------------------|
| **Windows** | Renames download to `.exe`, launches installer with `/PASSIVE /NORESTART` (Inno) or `/S` (NSIS), exits the app |
| **macOS** | Launches a bundled helper script that waits for the JVM to exit, replaces the `.app` bundle, and relaunches |
| **Linux** | Runs `pkexec dpkg -i` (`.deb`) or `pkexec rpm -U` (`.rpm`) with a polkit password prompt. Falls back to opening the browser for other formats |

## Release Notes Rendering

The update dialog renders release notes from `sparkle:releaseNotesLink`:

- **HTML** URLs are rendered directly in a `JEditorPane`
- **Markdown** URLs (`.md` extension or content-sniffed) are converted to HTML.
  Uses [flexmark-java](https://github.com/vsch/flexmark-java) if on the classpath; otherwise falls
  back to a built-in converter (headings, bold, lists, code blocks, links)
- If no URL is provided, inline `<description>` content is used as a fallback
- Network failures show "Could not load release notes." without blocking the dialog

## Requirements

- **Java 17+** (uses `java.net.http.HttpClient`, `ProcessHandle`, EdDSA, records)
- **Swing** for the built-in update dialog
- **No runtime dependencies** — just the JDK

## License

[Apache License 2.0](LICENSE)
