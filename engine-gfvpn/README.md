# gfVPN bundled runtime

The two verified SDK files must be present in the pinned `engine-gfvpn/vendor/runtime-bundle-1.0.6.zip` archive (or an authorized build operator's exact archive selected through `-Pozero.gfvpn.sdkArchive=/absolute/path/runtime-bundle-1.0.6.zip`). Gradle checks the archive and both entries before packaging the SDK in Ozero's own APK. No original APK or end-user import is required. Missing vendor input fails full APK assembly. The explicit `ozero.gfvpn.unitTestsOnly` mode permits JVM tests without proprietary assets, not APK packaging.

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

Connection settings follow the reference DEX: global app routing uses Ozero's
existing split-tunnel settings; local-service bypass, configuration updates and
cached nodes are separate SDK switches. The server selector supports all four reference modes, 20 country/proxy groups
and 57 backend groups from the reference APK's bundled selection asset. The
selection is passed unchanged to the SDK; catalog membership does not establish
live server availability. Additional selectors configure transport, simultaneous
connections, concurrent attempts and rotation.
Whitelist and VK-TURN leave managed concurrency at SDK defaults. Settings are
stored and exported through Ozero's existing settings and backup system and
are editable only while the VPN is disconnected.
