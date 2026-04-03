# sparkle4j — Design Document

A standalone, dependency-light Java/Kotlin library for in-app update checking and silent update
application. Implements the Sparkle appcast protocol so the same feed works with macOS Sparkle,
WinSparkle, and sparkle4j from a single hosted XML file.

Intended to be extracted into its own repository and published to Maven Central after a working
implementation is validated inside Needlecast.

---

## Goals

- Check a hosted appcast XML for new versions on startup (async, non-blocking)
- Show a native-feeling Swing dialog with release notes and download progress
- Apply the update silently on all three platforms (Windows, macOS, Linux) without requiring the
  user to visit a browser or run a separate installer manually
- Be compatible with the existing Sparkle appcast format so macOS apps can share one feed
- Zero mandatory transitive dependencies beyond the JDK

## Non-Goals

- Updating the bundled JRE (jlink runtime) — only application files are updated
- Rollback / downgrade support
- Delta/patch updates (full installer download only)
- Auto-relaunch without user confirmation
- Android / mobile support
- Anything requiring a persistent background service or daemon

---

## Appcast XML Format

The feed is standard RSS 2.0 with Sparkle namespace extensions. sparkle4j reads only the fields
listed below; all others are ignored and round-tripped safely.

Namespace: `xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle"`

### Minimal example

```xml
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0"
     xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle"
     xmlns:dc="http://purl.org/dc/elements/1.1/">
  <channel>
    <title>Needlecast</title>
    <link>https://github.com/rygel/needlecast</link>

    <item>
      <title>Needlecast 0.7.0</title>
      <pubDate>Fri, 10 Apr 2026 12:00:00 +0000</pubDate>
      <sparkle:version>0.7.0</sparkle:version>
      <sparkle:minimumSystemVersion>21</sparkle:minimumSystemVersion>
      <sparkle:releaseNotesLink>https://raw.githubusercontent.com/rygel/needlecast/main/CHANGELOG.md</sparkle:releaseNotesLink>

      <!-- One <enclosure> per platform. sparkle:os is the discriminator. -->
      <enclosure
        url="https://github.com/rygel/needlecast/releases/download/v0.7.0/needlecast-0.7.0-windows.exe"
        sparkle:os="windows"
        length="185000000"
        type="application/octet-stream"
        sparkle:edSignature="BASE64_ED25519_SIG"/>

      <enclosure
        url="https://github.com/rygel/needlecast/releases/download/v0.7.0/needlecast-0.7.0-macos.zip"
        sparkle:os="macos"
        length="170000000"
        type="application/zip"
        sparkle:edSignature="BASE64_ED25519_SIG"/>

      <enclosure
        url="https://github.com/rygel/needlecast/releases/download/v0.7.0/needlecast-0.7.0.deb"
        sparkle:os="linux"
        length="160000000"
        type="application/vnd.debian.binary-package"
        sparkle:edSignature="BASE64_ED25519_SIG"/>
    </item>

    <!-- Older items can be left in the feed; sparkle4j picks the highest version only -->
    <item>
      <title>Needlecast 0.6.0</title>
      ...
    </item>
  </channel>
</rss>
```

### Fields read by sparkle4j

| Field | Required | Notes |
|---|---|---|
| `sparkle:version` | Yes | Semver string used for comparison |
| `sparkle:os` on `<enclosure>` | Yes | `windows`, `macos`, `linux` |
| `enclosure url` | Yes | Direct download URL |
| `enclosure length` | Yes | Used for progress bar; must be accurate |
| `sparkle:edSignature` | No (but strongly recommended) | Ed25519 signature of the download |
| `sparkle:releaseNotesLink` | No | URL to Markdown or HTML release notes |
| `sparkle:minimumJavaVersion` | No | Minimum Java major version as integer string (custom; `minimumSystemVersion` is macOS-only and left untouched) |
| `pubDate` | No | Used as display date in dialog |

### Appcast generation

The appcast XML must be regenerated on every release. No official Maven plugin exists for this.
Recommended approach: a small `AppcastGenerator` main class in the host project's build tools,
invoked by `exec-maven-plugin` during the `package` phase. It:

1. Reads the version from a system property or `pom.xml`
2. Calculates the SHA-256 of each platform installer
3. Signs each installer with the project's Ed25519 private key
4. Writes the new `<item>` block at the top of the existing appcast XML
5. Outputs the updated file to `target/appcast.xml` for CI to upload to GitHub Pages or as a
   release asset

The private key for signing is stored in CI secrets and never committed to the repository.

---

## Library API

The public API is a single entry point with a Kotlin DSL builder. All methods are
Java-interoperable.

### Kotlin (primary)

```kotlin
// In your app's startup code (after the main window is shown):
Sparkle4j.configure {
    appcastUrl = "https://raw.githubusercontent.com/rygel/needlecast/gh-pages/appcast.xml"
    currentVersion = "0.6.0"                  // injected at build time
    checkIntervalHours = 24                    // default; 0 = every launch
    publicKey = "BASE64_ED25519_PUBLIC_KEY"    // null = skip signature check (not recommended)
    parentComponent = mainWindow               // Swing parent for dialogs
    onUpdateFound = { item -> /* optional hook for custom UI */ }
}.checkInBackground()  // returns immediately; result shown in dialog
```

### Java (interop)

```java
Sparkle4j.builder()
    .appcastUrl("https://...")
    .currentVersion("0.6.0")
    .parentComponent(mainWindow)
    .build()
    .checkInBackground();
```

### Lifecycle methods

```kotlin
interface Sparkle4jInstance {
    /** Fire-and-forget async check. Shows dialog if update found. */
    fun checkInBackground()

    /** Blocking check for use in custom UI flows. Returns null if up to date. */
    fun checkNow(): UpdateItem?

    /** Download and apply the given item immediately (skips the check dialog). */
    fun applyUpdate(item: UpdateItem)

    /** Suppress future checks for this version (persisted to prefs). */
    fun skipVersion(version: String)
}
```

---

## Package Structure

```
io.github.sparkle4j
├── Sparkle4j.kt               Entry point / DSL builder
├── UpdateItem.kt              Data class: version, url, signature, releaseNotes, pubDate
├── AppcastFetcher.kt          HTTP fetch with timeout + ETag caching
├── AppcastParser.kt           RSS/XML → List<UpdateItem>
├── VersionComparator.kt       Semver comparison
├── SignatureVerifier.kt       Ed25519 signature check (optional)
├── UpdateDownloader.kt        HTTP download with progress callback
├── UpdatePreferences.kt       Persists skipped versions and last-check timestamp
├── apply/
│   ├── UpdateApplier.kt       Platform dispatcher
│   ├── WindowsApplier.kt      Download .exe → run /PASSIVE → exit
│   ├── MacosApplier.kt        Download .zip → invoke helper → exit
│   └── LinuxApplier.kt        Download .deb/.rpm → run dpkg/rpm or open browser
└── ui/
    ├── UpdateDialog.kt        Swing dialog: release notes + download progress + buttons
    └── ReleaseNotesPanel.kt   Renders Markdown or HTML release notes
```

---

## Core Components

### AppcastFetcher

- Uses `java.net.http.HttpClient` (Java 11+, no extra dependencies)
- Sends `If-None-Match` / `If-Modified-Since` headers; handles `304 Not Modified` by returning
  the cached response
- Timeout: 10 seconds connect, 30 seconds read
- Fails silently (logs at WARN) — a network failure must never crash or block the app

### AppcastParser

- Uses `javax.xml.parsers.DocumentBuilder` (JDK built-in)
- Reads all `<item>` elements, filters to those that have an `<enclosure>` matching the current OS
- Sorts by `sparkle:version` descending; returns the highest version
- Ignores versions below `sparkle:minimumSystemVersion` if the running JVM's major version is lower

### VersionComparator

- Parses `major.minor.patch[-qualifier]` following SemVer 2.0
- `0.7.0 > 0.6.0` — numeric comparison per segment, not lexicographic
- Pre-release qualifiers (`-alpha`, `-beta`, `-rc.1`) sort below the release version
- Exposes `fun isNewer(candidate: String, current: String): Boolean`

### SignatureVerifier

- Algorithm: Ed25519 (`EdDSA` in `java.security`, available since Java 15)
- Verifies the `sparkle:edSignature` (base64 Ed25519 signature) against the raw bytes of the
  downloaded file using the configured public key
- If `publicKey` is null in the config, verification is skipped with a WARN log
- If verification fails, the update is aborted and an error dialog is shown — the partial download
  is deleted

### UpdateDownloader

- Streams the download to a temp file in the OS temp directory
- Fires a `(bytesRead: Long, totalBytes: Long) -> Unit` callback for progress reporting
- Resumes partial downloads via `Range` header if the temp file already exists from a previous
  interrupted attempt
- Returns the `Path` to the completed temp file

### UpdatePreferences

- Stores: `skippedVersions: Set<String>`, `lastCheckTimestamp: Instant`
- Backed by `java.util.prefs.Preferences` (user node, no file path required)
- Node path: `/io/github/sparkle4j/<appName>`

---

## Swing Update Dialog

Single `JDialog`, modal on the parent component.

### States

```
┌─────────────────────────────────────────────────┐
│  Needlecast 0.7.0 is available                  │
│  You have 0.6.0. Would you like to update?      │
│                                                 │
│  ┌─────────────────────────────────────────┐   │
│  │  Release notes (scrollable)             │   │
│  │  Rendered from Markdown or HTML         │   │
│  └─────────────────────────────────────────┘   │
│                                                 │
│  [Skip This Version]  [Remind Me Later]  [Install]  │
└─────────────────────────────────────────────────┘

-- after Install clicked --

│  Downloading…  [██████████░░░░░░░░░░]  47%      │
│                                        [Cancel] │

-- after download complete --

│  Ready to install. The app will quit and        │
│  relaunch automatically.                        │
│                                  [Install Now]  │
```

### Button behaviour

| Button | Action |
|---|---|
| Skip This Version | Calls `skipVersion(item.version)`, closes dialog |
| Remind Me Later | Closes dialog, will check again next launch |
| Install | Begins download, transitions to progress state |
| Cancel (during download) | Aborts download, deletes temp file, closes dialog |
| Install Now | Calls `UpdateApplier.apply(item, tempFile)` |

### Release notes rendering

- If `sparkle:releaseNotesLink` is an HTML URL: render in a `JEditorPane` with `text/html`
- If it's a Markdown URL (`.md` extension or content sniff): convert to HTML with a minimal
  Markdown renderer (CommonMark subset — headings, bold, lists, code blocks, links only).
  Use `flexmark-java` if already on the classpath; otherwise fall back to plain text.
- If no URL is provided: show "No release notes available."
- Network failure loading release notes: show "Could not load release notes." — do not block the
  dialog

---

## Platform Update Application

### Windows

```
WindowsApplier.apply(item, tempFile: Path)
```

1. Rename `tempFile` to `needlecast-update.exe` in the same temp directory
2. Build the command: `["needlecast-update.exe", "/PASSIVE", "/NORESTART"]`
   - `/PASSIVE` = shows minimal progress UI but no user interaction required
   - `/NORESTART` = Inno Setup does not reboot the machine
3. Launch via `ProcessBuilder` with `inheritIO = false`; do not wait
4. Call `exitProcess(0)` — the installer runs independently and replaces the app

If the installer is NSIS-based instead of Inno Setup, use `/S` (silent) instead of `/PASSIVE`.
The installer type can be detected by a `sparkle:installerType` custom attribute on `<enclosure>`
(`inno` or `nsis`); default is `inno`.

### macOS

```
MacosApplier.apply(item, tempFile: Path)
```

The update ZIP contains a single `.app` bundle at the top level, e.g. `Needlecast.app`.

The macOS applier cannot replace the running `.app` from within itself. It uses a bundled helper
script that waits for the JVM to exit, performs the swap, then relaunches.

**Helper script** — bundled at `Contents/MacOS/sparkle4j-updater` inside the jpackage `.app`:

```bash
#!/bin/bash
# Arguments: <running-app-path> <update-zip-path> <pid-to-wait-for>
APP_PATH="$1"
UPDATE_ZIP="$2"
PARENT_PID="$3"

# Wait for parent JVM to exit
while kill -0 "$PARENT_PID" 2>/dev/null; do
    sleep 0.3
done

# Extract update zip to a temp location
STAGING=$(mktemp -d)
unzip -q "$UPDATE_ZIP" -d "$STAGING"

# Find the .app inside the staging dir
NEW_APP=$(find "$STAGING" -maxdepth 1 -name "*.app" | head -1)
if [[ -z "$NEW_APP" ]]; then
    osascript -e 'display alert "Update failed" message "The update archive did not contain a valid .app bundle."'
    rm -rf "$STAGING"
    exit 1
fi

# Replace the old .app (move to trash, copy new one in)
APP_DIR=$(dirname "$APP_PATH")
/usr/bin/osascript -e "tell application \"Finder\" to move POSIX file \"$APP_PATH\" to trash" 2>/dev/null \
    || rm -rf "$APP_PATH"

cp -R "$NEW_APP" "$APP_DIR/"

# Clean up
rm -rf "$STAGING"
rm -f "$UPDATE_ZIP"

# Relaunch
INSTALLED_APP="$APP_DIR/$(basename "$NEW_APP")"
open "$INSTALLED_APP"
```

**Java side:**

```kotlin
fun apply(item: UpdateItem, tempFile: Path, currentAppPath: Path) {
    val helperScript = currentAppPath
        .resolve("Contents/MacOS/sparkle4j-updater")
    helperScript.toFile().setExecutable(true)

    ProcessBuilder(
        helperScript.toString(),
        currentAppPath.toString(),
        tempFile.toString(),
        ProcessHandle.current().pid().toString()
    ).start()   // fire and forget

    exitProcess(0)
}
```

`currentAppPath` is resolved from `ProcessHandle.current().info().command()` by walking up from
the JVM executable to the `.app` bundle root.

### Linux

Linux users are typically on a distro package manager. Two tiers:

**Tier 1 — Package manager install (preferred):**

```kotlin
fun apply(item: UpdateItem, tempFile: Path) {
    val ext = tempFile.fileName.toString().substringAfterLast('.')
    val command = when (ext) {
        "deb" -> listOf("pkexec", "dpkg", "-i", tempFile.toString())
        "rpm" -> listOf("pkexec", "rpm", "-U", tempFile.toString())
        else  -> null
    }
    if (command != null) {
        ProcessBuilder(command).inheritIO().start().waitFor()
        // pkexec shows a polkit password prompt for root; user experience is acceptable
        exitProcess(0)
    } else {
        // Tier 2 fallback
        openBrowser(item.url)
    }
}
```

**Tier 2 — AppImage or unknown format:**
Open the browser to `item.url`. Display a dialog: "Your update has been downloaded to [path].
You can also download it from [url]."

---

## Security

### Signature verification

Every `<enclosure>` should carry a `sparkle:edSignature` attribute containing the base64-encoded
Ed25519 signature of the installer file bytes. The library verifies this before invoking the
platform applier.

Key generation (one-time, done by the project maintainer):

```bash
# Generate keypair
openssl genpkey -algorithm ed25519 -out private.pem
openssl pkey -in private.pem -pubout -out public.pem

# Sign a release file
openssl pkeyutl -sign -inkey private.pem -in needlecast-0.7.0-windows.exe \
    | base64 -w 0
```

The public key is embedded in the app at build time (injected into the JAR manifest or as a
compile-time constant). The private key is stored in CI secrets only and never committed.

### Transport security

- All URLs in the appcast must be HTTPS. The library rejects HTTP URLs for both the appcast and
  the enclosure download with a logged ERROR and aborted update.
- Certificate validation uses the JVM's default trust store (no custom trust anchors).

### No privilege escalation (Windows, macOS)

The library never calls `sudo` or attempts privilege escalation directly. On Windows the Inno
Setup installer handles UAC internally. On macOS the `.app` bundle replacement operates on the
user's home directory where no elevated permissions are required. Linux uses `pkexec` which
shows the standard polkit dialog.

---

## Configuration Reference

```kotlin
data class Sparkle4jConfig(
    /** URL of the appcast XML feed. Must be HTTPS. */
    val appcastUrl: String,

    /** Running version of the host application. Semver string. */
    val currentVersion: String,

    /**
     * Base64-encoded Ed25519 public key for signature verification.
     * Null disables verification (logs a warning).
     */
    val publicKey: String? = null,

    /** How often to check for updates. 0 = every launch. Default: 24h. */
    val checkIntervalHours: Int = 24,

    /** Swing parent component for dialogs. Null = no parent (dialog is centered on screen). */
    val parentComponent: Component? = null,

    /**
     * Name of the app shown in dialogs. Defaults to the JAR manifest Implementation-Title
     * or "Application" if absent.
     */
    val appName: String = resolveAppName(),

    /**
     * Called on the EDT when an update is found, before the dialog is shown.
     * Return false to suppress the built-in dialog and handle UI yourself.
     */
    val onUpdateFound: ((UpdateItem) -> Boolean)? = null,

    /**
     * Path to the current .app bundle (macOS only). Resolved automatically from
     * ProcessHandle if null.
     */
    val macosAppPath: Path? = null,
)
```

---

## Error Handling

All errors are caught internally. The library must never throw to the caller or crash the host app.

| Error | Behaviour |
|---|---|
| Network failure fetching appcast | Log WARN, silently abort. Retry next launch. |
| Malformed appcast XML | Log ERROR, silently abort. |
| No enclosure for current platform | No update available (not an error). |
| HTTP failure during download | Show error dialog: "Download failed. Try again later." |
| Signature mismatch | Log ERROR, delete temp file, show error dialog. Never apply. |
| Helper script not found (macOS) | Log ERROR, show error dialog, fall back to browser open. |
| Installer exit code non-zero (Windows) | Log WARN. App has already exited; installer shows its own error. |

---

## Maven Coordinates

When extracted to its own repository:

```xml
<dependency>
    <groupId>io.github.sparkle4j</groupId>
    <artifactId>sparkle4j</artifactId>
    <version>1.0.0</version>
</dependency>
```

Target Java baseline: **Java 17** (uses `java.net.http.HttpClient`, `ProcessHandle`, `EdDSA`).
Tested on Java 17 and 21.

No mandatory transitive runtime dependencies. Optional: `flexmark-java` for Markdown rendering
in release notes (detected at runtime via reflection; plain text fallback if absent).

---

## Integration into Needlecast

Inside `MainWindow.kt`, after the window is shown (not during construction):

```kotlin
SwingUtilities.invokeLater {
    Sparkle4j.configure {
        appcastUrl  = "https://rygel.github.io/needlecast/appcast.xml"
        currentVersion = BuildInfo.VERSION          // compile-time constant from MANIFEST.MF
        publicKey   = BuildInfo.UPDATE_PUBLIC_KEY   // compile-time constant
        parentComponent = this@MainWindow
        appName     = "Needlecast"
    }.checkInBackground()
}
```

`BuildInfo.VERSION` and `BuildInfo.UPDATE_PUBLIC_KEY` are read from the JAR manifest
(`Implementation-Version` and a custom `Sparkle4j-PublicKey` attribute) which Maven populates
during the `package` phase.

The jpackage build scripts must bundle `sparkle4j-updater` at `Contents/MacOS/sparkle4j-updater`
(macOS only). The `build-jpackage.sh` script adds `--resource-dir scripts/macos-resources/` and
the helper script lives in that directory.

---

## CI/CD Integration

### Appcast update step (added to release workflow)

```yaml
- name: Update appcast
  env:
    SPARKLE_PRIVATE_KEY: ${{ secrets.SPARKLE_PRIVATE_KEY }}
  run: |
    mvn -pl tools exec:java \
      -Dexec.mainClass=io.github.rygel.needlecast.tools.AppcastGenerator \
      -Dexec.args="${{ github.ref_name }} target/appcast.xml"

- name: Publish appcast to GitHub Pages
  uses: peaceiris/actions-gh-pages@v4
  with:
    github_token: ${{ secrets.GITHUB_TOKEN }}
    publish_dir: target
    keep_files: true
    destination_dir: .
```

The `AppcastGenerator` tool reads the three installer files from `desktop/target/`, signs each
with the private key from the environment, and prepends the new `<item>` to the existing
`appcast.xml` fetched from the `gh-pages` branch.

---

## Testing Strategy

### Unit tests (no Swing, no network)

- `AppcastParserTest`: parse valid feed, missing fields, wrong namespace, malformed XML
- `VersionComparatorTest`: all semver edge cases, pre-release qualifiers
- `SignatureVerifierTest`: valid signature, tampered file, missing key

### Integration tests

- `AppcastFetcherTest`: mock HTTP server (WireMock), ETag caching, timeout behaviour
- `UpdateDownloaderTest`: mock server, progress callbacks, resume after partial download

### Manual / platform tests

Documented in `CONTRIBUTING.md` — must be run before any release:

- Windows: full flow end-to-end with a real Inno Setup installer
- macOS: full flow with the helper script on a real `.app`
- Linux: `.deb` install with `pkexec`

Swing dialog tests use the existing Xvfb Docker container setup (same as Needlecast's
`-Ptest-desktop` profile).
