# Security Policy

## Supported Versions

| Version | Supported |
| ------- | --------- |
| 0.x (latest) | Yes |

Only the latest published version receives security fixes. No backports to older minor versions.

## Reporting a Vulnerability

Please **do not** open a public GitHub issue for security vulnerabilities.

Report vulnerabilities privately via [GitHub Security Advisories](https://github.com/rygel/sparkle4j/security/advisories/new).

Include:
- A description of the vulnerability and its impact
- Steps to reproduce or a minimal proof-of-concept
- The version(s) affected

**Expected response time:** Acknowledgement within 7 days. A fix or mitigation plan within 30 days for confirmed vulnerabilities.

## Scope

This library handles:
- Fetching and parsing appcast XML over HTTPS
- Verifying Ed25519 signatures on downloaded files
- Launching system installers (Windows, macOS, Linux)

Security-sensitive areas include signature verification bypass, XML injection/XXE, and path traversal in installer handling. Reports in these areas are given highest priority.
