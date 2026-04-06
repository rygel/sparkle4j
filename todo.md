# sparkle4j TODO

## Tests — Done (PR #21)
- 165 tests, 72.3% overall coverage
- Up from 46 tests / 27.7% at start

## Coverage Summary
| Class | Coverage |
|-------|----------|
| SignatureVerifier | 100% |
| Sparkle4jBuilder | 100% |
| Sparkle4jConfig | 100% |
| UpdateItem | 100% |
| VersionComparator | 100% |
| UpdatePreferences | 99% |
| UpdateDownloader | 97% |
| AppcastParser | 94% |
| AppcastFetcher | 94% |
| Sparkle4j | 88% |
| UpdateApplier | 83% |
| ReleaseNotesPanel | 76% |
| WindowsApplier | 74% |
| Sparkle4jInstanceImpl | 72% |
| AppcastGenerator | 58% |
| UpdateDialog | 51% |
| MacosApplier | 0% |
| LinuxApplier | 0% |

## Remaining uncovered (hard to test)
- **LinuxApplier / MacosApplier** — System.exit, ProcessBuilder, pkexec
- **AppcastGenerator.main** — System.exit, env vars, file I/O (CLI entry point)
- **UpdateDialog** — Swing modal dialog (51% from constructor path coverage)

## Other TODO
- [ ] Add Javadoc to `Sparkle4jBuilder` setter methods
- [ ] Add `package-info.java` for the main package
- [ ] Maven Central: verify namespace, GPG key, settings.xml
