# MASQUE (draft)

Isolated Android transport foundation for Cloudflare WARP CONNECT-IP via [usque](https://github.com/Diniboy1123/usque), MIT, pinned to `6aa03fc97d12848dce34eedbd187fb1077b5d1ea`.

- Explicit terms acceptance and upstream registration; no borrowed accounts or third-party generators.
- H3, H2, and ordered AUTO fallback; local SOCKS5 only, upstream public-key pinning and tunneled DNS retained.
- TLS readiness via SOCKS rather than listener-only success.
- Runtime configuration lives in a temporary directory under a caller-supplied app-private no-backup directory. The caller must encrypt persistent profiles using Android Keystore and remove stale workspaces after crashes before production integration.
- Native process logs are discarded to avoid leaking credentials.

CI build command for the upstream Android binary (Go 1.26.3 or newer): `bash scripts/build-masque-binary.sh`. The binary is intentionally not yet packaged in the APK.

This draft does not register an EnginePlugin or expose a user-facing engine. Before enabling it: verify binary on ARM64 Android and lock its provenance; implement encrypted profile storage, settings, backup policy, lifecycle/failure routing, auto-cascade and statistics; verify SOCKS UDP/IPv6 and fail-closed behavior with HEV, process cancellation and port ownership; run complete CI with coverage. Do not merge until these checks and integration are complete. Existing WARP is unchanged.
