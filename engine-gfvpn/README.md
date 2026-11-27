# gfVPN bundled runtime

The exact reference APK is a verified offline build input. No external download URL is embedded. The build extracts the pinned DEX and ARM64 SDK entries into generated APK assets; a missing input fails the build rather than producing a broken release. Use `-PgfvpnSourceApk=/absolute/path/to/source.apk` for a local build.

On ARM64 devices the engine installs its verified read-only runtime into a
private versioned directory automatically and starts it using a dedicated
classloader and process. No file picker or post-install APK import is required.
The closed runtime executes with the application's UID; process separation
is not a security sandbox. Distribution requires permission from the rights
holder. Account and server access controls remain unchanged.

API 29+ uses the separate pinned open-source HEV 2.20.0 with mapdns and JNI
thread-state checks; older supported Android versions retain the existing HEV.
Other engines are unaffected. IPv6 and proxy-only mode remain disabled.

CI verifies the exact two bundled files and the HEV JNI package in debug and
release APKs. Run the existing opt-in ARM64 SDK and full TUN smoke tests from a
fresh install, without transferring any SDK files, before production release.
Successful CI alone does not establish backend connectivity.
