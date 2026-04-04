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

sparkle4j is published on [GitHub Packages](https://github.com/rygel/sparkle4j/packages).

### 1. Add the GitHub Packages repository

**Maven** — add to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github-sparkle4j</id>
        <url>https://maven.pkg.github.com/rygel/sparkle4j</url>
    </repository>
</repositories>
```

**Gradle** — add to your `build.gradle` or `settings.gradle`:

```groovy
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/rygel/sparkle4j")
        credentials {
            username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

> GitHub Packages requires authentication even for public packages. You need a GitHub personal
> access token with `read:packages` scope. See
> [GitHub docs](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry)
> for setup details.

### 2. Add the dependency

**Maven:**

```xml
<dependency>
    <groupId>io.github.sparkle4j</groupId>
    <artifactId>sparkle4j</artifactId>
    <version>0.5.1</version>
</dependency>
```

**Gradle:**

```groovy
implementation 'io.github.sparkle4j:sparkle4j:0.5.1'
```

## Quick Start

```java
import io.github.sparkle4j.Sparkle4j;
import javax.swing.SwingUtilities;

// In your app's startup, after the main window is visible:
SwingUtilities.invokeLater(() -> {
    Sparkle4j.builder()
        .appcastUrl("https://example.com/appcast.xml")
        .currentVersion("1.0.0")
        .parentComponent(mainWindow)
        .build()
        .checkInBackground();
});
```

That's it. The library fetches the appcast feed in the background, compares versions, and shows
a dialog if an update is available. The user can view release notes, download the update with a
progress bar, and install it — all without leaving your app.

## How It Works

1. **Check** — fetches the appcast XML over HTTPS (with ETag caching to avoid redundant downloads)
2. **Compare** — finds the highest version for the current platform and compares it to the running version
3. **Dialog** — shows a Swing dialog with release notes and three buttons:

```
┌──────────────────────────────────────────────────┐
│  My App 1.1.0 is available                       │
│  You have 1.0.0. Would you like to update?       │
│                                                  │
│  ┌──────────────────────────────────────────┐    │
│  │  Release notes (scrollable)              │    │
│  │  Rendered from Markdown or HTML          │    │
│  └──────────────────────────────────────────┘    │
│                                                  │
│  [Skip This Version]  [Remind Me Later]  [Install] │
└──────────────────────────────────────────────────┘
```

4. **Download** — streams the installer to a temp file with a progress bar and cancel button
5. **Verify** — checks the Ed25519 signature (if configured) before applying
6. **Apply** — launches the platform installer and exits the app

## Configuration

All options with their defaults:

```java
import io.github.sparkle4j.Sparkle4j;
import io.github.sparkle4j.Sparkle4jInstance;

Sparkle4jInstance updater = Sparkle4j.builder()

    // Required — URL of the appcast XML feed (must be HTTPS)
    .appcastUrl("https://example.com/appcast.xml")

    // Required — the running version of your app (SemVer)
    .currentVersion("1.0.0")

    // Recommended — Base64-encoded Ed25519 public key for signature verification.
    // If null, verification is skipped (not recommended for production).
    .publicKey("MCowBQYDK2VwAyEA...")

    // Optional — name shown in the update dialog (default: JAR manifest Implementation-Title)
    .appName("My App")

    // Optional — Swing parent component for the modal dialog (default: centered on screen)
    .parentComponent(mainWindow)

    // Optional — minimum hours between checks, 0 = every launch (default: 24)
    .checkIntervalHours(24)

    // Optional — macOS only: path to the .app bundle (default: auto-detected)
    .macosAppPath(Path.of("/Applications/MyApp.app"))

    // Optional — called on the EDT when an update is found.
    // Return true to show the built-in dialog, false to suppress it and handle UI yourself.
    .onUpdateFound(item -> {
        System.out.println("Update available: " + item.version());
        return true;  // show built-in dialog
    })

    .build();

// Fire-and-forget (shows dialog on EDT if update found)
updater.checkInBackground();
```

## Advanced Usage

### Blocking check (custom UI)

Use `checkNow()` for full control. Run it on a background thread, not the EDT:

```java
Sparkle4jInstance updater = Sparkle4j.builder()
    .appcastUrl("https://example.com/appcast.xml")
    .currentVersion("1.0.0")
    .build();

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
updater.skipVersion("1.1.0");  // persisted across app restarts via java.util.prefs
```

### Custom update hook (suppress built-in dialog)

Return `false` from `onUpdateFound` to handle the update entirely in your own UI:

```java
Sparkle4j.builder()
    .appcastUrl("https://example.com/appcast.xml")
    .currentVersion("1.0.0")
    .onUpdateFound(item -> {
        // Show your own notification, toast, menu badge, etc.
        showCustomUpdateBanner(item.shortVersionString(), item.releaseNotesUrl());
        return false;  // suppress the built-in Swing dialog
    })
    .build()
    .checkInBackground();
```

## API Reference

### `Sparkle4j`

| Method | Description |
|--------|-------------|
| `static Sparkle4jBuilder builder()` | Creates a new builder |

### `Sparkle4jInstance`

| Method | Returns | Description |
|--------|---------|-------------|
| `checkInBackground()` | `void` | Fire-and-forget async check. Shows dialog on the EDT if an update is found. |
| `checkNow()` | `UpdateItem` | Blocking check. Returns the newest available update, or `null` if up to date. |
| `applyUpdate(UpdateItem)` | `void` | Download and apply immediately (skips the dialog). |
| `skipVersion(String)` | `void` | Suppress future checks for this version (persisted across launches). |

### `UpdateItem` (record)

| Accessor | Type | Description |
|----------|------|-------------|
| `title()` | `String` | Item title from the feed |
| `version()` | `String` | Machine-readable version for comparison |
| `shortVersionString()` | `String` | Human-readable display version |
| `url()` | `String` | Direct download URL (HTTPS) |
| `length()` | `long` | File size in bytes |
| `edSignature()` | `String` | Base64 Ed25519 signature (nullable) |
| `releaseNotesUrl()` | `String` | URL to Markdown/HTML release notes (nullable) |
| `inlineDescription()` | `String` | Inline release notes from `<description>` (nullable) |
| `minimumJavaVersion()` | `Integer` | Minimum Java major version (nullable) |
| `pubDate()` | `String` | Publication date string (nullable) |
| `os()` | `String` | Platform: `"windows"`, `"macos"`, or `"linux"` |
| `isCriticalUpdate()` | `boolean` | If true, user cannot skip this version |
| `installerType()` | `String` | Windows installer type: `"inno"` or `"nsis"` |

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
      <title>My App 1.1.0</title>
      <pubDate>Fri, 10 Apr 2026 12:00:00 +0000</pubDate>
      <sparkle:version>1.1.0</sparkle:version>
      <sparkle:shortVersionString>1.1.0</sparkle:shortVersionString>
      <sparkle:releaseNotesLink>https://example.com/changelog.md</sparkle:releaseNotesLink>

      <!-- Optional: mark as critical (disables "Skip This Version") -->
      <!-- <sparkle:criticalUpdate/> -->

      <!-- Windows -->
      <enclosure
        url="https://example.com/releases/myapp-1.1.0-windows.exe"
        sparkle:os="windows"
        length="185000000"
        type="application/octet-stream"
        sparkle:edSignature="BASE64_ED25519_SIGNATURE"
        sparkle:installerType="inno"/>

      <!-- macOS -->
      <enclosure
        url="https://example.com/releases/myapp-1.1.0-macos.zip"
        sparkle:os="macos"
        length="170000000"
        type="application/zip"
        sparkle:edSignature="BASE64_ED25519_SIGNATURE"/>

      <!-- Linux -->
      <enclosure
        url="https://example.com/releases/myapp-1.1.0.deb"
        sparkle:os="linux"
        length="160000000"
        type="application/vnd.debian.binary-package"
        sparkle:edSignature="BASE64_ED25519_SIGNATURE"/>
    </item>

    <!-- Older versions can remain in the feed; sparkle4j picks the highest -->
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

## Appcast Generator

sparkle4j includes a CLI tool to generate or update the appcast XML on each release:

```bash
java -cp sparkle4j.jar io.github.sparkle4j.tools.AppcastGenerator <version> <appcast-file>
```

### Environment variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SPARKLE_PRIVATE_KEY` | Yes | Base64-encoded PKCS#8 Ed25519 private key |
| `APPCAST_BASE_URL` | Yes | Base download URL (e.g. `https://example.com/releases`) |
| `INSTALLER_WINDOWS` | No | Path to the Windows `.exe` installer |
| `INSTALLER_MACOS` | No | Path to the macOS `.zip` archive |
| `INSTALLER_LINUX` | No | Path to the Linux `.deb` or `.rpm` package |
| `RELEASE_NOTES_URL` | No | URL to the release notes page |
| `APP_NAME` | No | Application name (default: `"Application"`) |

### Example (CI release step)

```bash
export SPARKLE_PRIVATE_KEY="$SPARKLE_SIGNING_KEY"
export APPCAST_BASE_URL="https://github.com/myorg/myapp/releases/download/v1.1.0"
export INSTALLER_WINDOWS="target/myapp-1.1.0-windows.exe"
export INSTALLER_MACOS="target/myapp-1.1.0-macos.zip"
export INSTALLER_LINUX="target/myapp-1.1.0.deb"
export RELEASE_NOTES_URL="https://example.com/changelog.md"
export APP_NAME="My App"

java -cp sparkle4j.jar io.github.sparkle4j.tools.AppcastGenerator 1.1.0 appcast.xml
```

The tool signs each installer with Ed25519, generates SHA-256 hashes, creates a new `<item>` block,
and prepends it to the existing appcast XML (or creates a new one if the file doesn't exist).

## Signature Verification

sparkle4j uses Ed25519 for verifying downloads. Generate a keypair once:

```bash
# Generate private key
openssl genpkey -algorithm ed25519 -out private.pem

# Export public key as base64 (embed this in your app)
openssl pkey -in private.pem -pubout -outform DER | base64 -w 0

# Export private key as base64 PKCS#8 (store in CI secrets for AppcastGenerator)
openssl pkcs8 -topk8 -nocrypt -in private.pem -outform DER | base64 -w 0

# Sign a release file manually
openssl pkeyutl -sign -inkey private.pem -in myapp-1.1.0-windows.exe | base64 -w 0
```

Pass the public key to sparkle4j:

```java
Sparkle4j.builder()
    .appcastUrl("https://example.com/appcast.xml")
    .currentVersion("1.0.0")
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
| **macOS** | Launches a bundled helper script that waits for the JVM to exit, replaces the `.app` bundle, and relaunches. The script must be bundled at `Contents/MacOS/sparkle4j-updater` inside your `.app`. Falls back to opening the browser if the script is missing. |
| **Linux** | Runs `pkexec dpkg -i` (`.deb`) or `pkexec rpm -U` (`.rpm`) with a polkit password prompt, then exits. Falls back to opening the browser for unknown formats (e.g. AppImage). |

## Release Notes Rendering

The update dialog renders release notes from `sparkle:releaseNotesLink`:

- **HTML** — rendered directly in a `JEditorPane`
- **Markdown** (`.md` extension or content-sniffed) — converted to HTML.
  Uses [flexmark-java](https://github.com/vsch/flexmark-java) if on the classpath; otherwise falls
  back to a built-in converter supporting headings, bold, lists, code blocks, and links
- **Inline** — if no URL is provided, the `<description>` element content is used as a fallback
- **Errors** — network failures show "Could not load release notes." without blocking the dialog

## Error Handling

The library never throws exceptions to the caller or crashes the host app.

| Error | Behavior |
|-------|----------|
| Network failure fetching appcast | Logs WARN, silently aborts. Retries next launch. |
| Malformed appcast XML | Logs ERROR, silently aborts. |
| No enclosure for current platform | No update available (not an error). |
| HTTP failure during download | Shows error dialog: "Download failed. Try again later." |
| Signature mismatch | Logs ERROR, deletes temp file, shows error dialog. Never applies. |
| Helper script not found (macOS) | Logs ERROR, falls back to opening the browser. |

## Requirements

- **Java 17+** (uses `java.net.http.HttpClient`, `ProcessHandle`, EdDSA, records)
- **Swing** for the built-in update dialog
- **No runtime dependencies** — just the JDK

## License

[Apache License 2.0](LICENSE)
