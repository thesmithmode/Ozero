---
title: "HTTPS Validation Belongs in Downloader, Not Parser"
aliases: [https-gate-layer, validation-layer-ownership, parser-vs-downloader-https]
tags: [architecture, testing, kotlin, android, gotcha]
sources:
  - "daily/2026-05-16 (1).md"
created: 2026-05-16
updated: 2026-05-16
---

# HTTPS Validation Belongs in Downloader, Not Parser

Placing protocol/security policy checks (e.g., "URL must be https://") inside a parser or data validator creates an untestable boundary: MockWebServer and other test infrastructure always serve `http://`, so any test that creates a realistic test server will fail the policy check. The parser's job is to validate format (is this a valid URI structure?), not policy (is this URI secure?). Protocol enforcement belongs at the download/network layer where it can be configured or bypassed for test environments.

## Key Points

- Parser validates **format**: is the URI absolute? Does it have a valid scheme? Is the host non-empty?
- Downloader enforces **policy**: only fetch https:// URLs in production
- Mixing policy into parsers makes all tests using `http://` test servers structurally broken — not a test infrastructure problem, a design problem
- MockWebServer always uses `http://` — this is by design and cannot be changed
- The fix in Ozero: `LockFileParser` required `https://` URLs → removed requirement to absolute URI only; `Sha256Verifier` downloader enforces https in production

## Details

### The Layer Confusion

A common mistake when adding security hardening: the developer wants to ensure URLs are https, finds the parser where URLs are first validated, and adds the check there. This feels correct — "validate early, fail fast." But it conflates two separate concerns:

1. **Syntactic validity** (parser responsibility): Is this a parseable URI? Is it absolute vs relative? Does it have a scheme?
2. **Security policy** (network layer responsibility): Is the scheme https? Is the host trusted? Does the certificate pin match?

The parser must be pure and deterministic against any input. The network layer can apply environment-specific policy (production: https required; test: http allowed; staging: both allowed).

### The MockWebServer Problem

```kotlin
// In parser — BROKEN for tests
fun parseArtifactUrl(raw: String): Uri {
    val uri = Uri.parse(raw)
    require(uri.scheme == "https") { "Only https URLs allowed" }  // TEST ALWAYS FAILS
    return uri
}

// Test
@Test fun `valid artifact url parsed`() {
    val server = MockWebServer()
    server.start()
    // server.url("/artifact") returns http://localhost:PORT/artifact
    val result = parseArtifactUrl(server.url("/artifact").toString())  // THROWS
}
```

Every test using `MockWebServer`, `OkHttpMockInterceptor`, or any embedded HTTP server will fail. The test itself is valid — it's testing correct behavior — but the parser rejects the test URL.

### The Fix Pattern

```kotlin
// In parser — validates only format
fun parseArtifactUrl(raw: String): Uri {
    val uri = Uri.parse(raw)
    require(uri.isAbsolute) { "URL must be absolute, got: $raw" }
    return uri
}

// In downloader — enforces policy
class Sha256Verifier(private val allowInsecure: Boolean = false) {
    fun download(url: Uri) {
        if (!allowInsecure) {
            require(url.scheme == "https") { "Only https downloads allowed in production" }
        }
        // ...
    }
}
```

The downloader can be constructed with `allowInsecure = true` in test configurations, or the test can stub the downloader entirely without needing a real network call.

### Broader Principle

This pattern applies beyond https/http:
- Authentication tokens: validate presence in parser, validate value in auth middleware
- Domain allowlists: parse hostname in URL parser, check allowlist in request interceptor
- File size limits: parse Content-Length in header parser, enforce limit in downloader

The parser layer should be stateless and environment-agnostic. Policy requires context (what environment, what configuration, what principal) that parsers should not have access to.

## Related Concepts

- [[concepts/yaml-biginteger-parsing-trap]] - Another parser-layer trap where the parser's type handling causes unexpected behavior
- [[concepts/byedpi-mock-server-ci-fragility]] - Mock server infrastructure limitations causing test failures; parallel problem: test infrastructure constrains what the tested code can require
- [[concepts/cascade-unresolved-import-masking]] - Different layer: import/compile errors masking real validation errors

## Sources

- [[daily/2026-05-16 (1).md]] - Session 12:09 + текущая сессия: `LockFileParser` had `https://` gate → blocked `MockWebServer` tests (always `http://`); removed to absolute URI check only; https enforcement moved to download layer
