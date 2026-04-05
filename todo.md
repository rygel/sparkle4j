# sparkle4j TODO

## In Progress — PR #21 (feat/more-tests)
- [x] Commit and push latest round of tests (130 tests, ~55% coverage)
- [ ] Verify build passes after GodClass PMD exclude
- [ ] Push updated PR

## Tests Still Needed

### High value (testable logic, currently low coverage)
- [ ] **Sparkle4jInstanceImpl** (32%) — test `isCheckDue` interval logic, `fetchAndParseBestUpdate`, `installUpdate` signature verification path, `installUpdate` with null signature (skip verification)
- [ ] **AppcastGenerator** (58%) — test `collectInstallers` with env vars (needs ProcessBuilder mock or integration test), full `main` method integration test with temp files
- [ ] **ReleaseNotesPanel** (66%) — test constructor with various inputs (null URL, inline markdown, inline HTML, inline plain text)

### Hard to test (System.exit, ProcessBuilder, Swing)
- [ ] **UpdateDialog** (0%) — Swing modal dialog; would need headless mode or container testing
- [ ] **LinuxApplier** (0%) — calls `pkexec`, `System.exit`, `Desktop.browse`
- [ ] **MacosApplier** (0%) — calls shell scripts, `System.exit`
- [ ] **WindowsApplier** (0%) — calls `ProcessBuilder`, `System.exit`

## Coverage Summary (130 tests)
| Class | Coverage |
|-------|----------|
| SignatureVerifier | 100% |
| Sparkle4jBuilder | 100% |
| Sparkle4jConfig | 100% |
| UpdateItem | 100% |
| AppcastGenerator.Installer | 100% |
| UpdatePreferences | 99% |
| VersionComparator | 98% |
| AppcastParser | 93% |
| AppcastFetcher | 92% |
| UpdateDownloader | 90% |
| Sparkle4j | 88% |
| UpdateApplier | 68% |
| ReleaseNotesPanel | 66% |
| AppcastGenerator | 58% |
| Sparkle4jInstanceImpl | 32% |
| UpdateDialog | 0% |
| LinuxApplier | 0% |
| MacosApplier | 0% |
| WindowsApplier | 0% |

## Other TODO
- [ ] Add Javadoc to `Sparkle4jBuilder` setter methods (what each parameter expects)
- [ ] Add `package-info.java` for the main package
- [ ] Consider making `AppcastParser.currentOs()` package-private
- [ ] Maven Central publishing: verify namespace, GPG key, settings.xml
