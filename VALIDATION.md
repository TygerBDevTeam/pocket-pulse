# Validation — Pocket Pulse 0.2.0

Performed November 5, 2026 (America/Chicago). Physical handheld compatibility remains to be confirmed.

## Build and package checks

- All native Android Java sources compiled against SDK 36, minimum API 26, target API 36. JDK 17 and Build-Tools 36.0.0; DEX generation succeeded.
- Published Windows-built APK: `Pocket-Pulse-0.2.0.apk`, package `dev.pocketpulse.monitor`, version code 2. ZIP alignment and v2/v3 APK signatures verified. No bundled native libraries or runtime dependencies.
- Requested permissions are `INTERNET` (manual update checks) and `MODIFY_AUDIO_SETTINGS` (media volume). Cleartext traffic is disabled; no broad storage or install-packages permission.
- **52 telemetry/math checks:** counter parsing/deltas/resets, real zero versus unavailable, GPU/RAM/process calculations, ring storage, Celsius-to-Fahrenheit conversion, charging/discharging power units and unsupported properties, and RPM parsing.
- **25 semantic-version checks:** numeric ordering, optional `v`, prerelease ordering, build metadata and malformed/overflowing versions.
- **20 release-selection checks on Android's actual JSON implementation:** skip drafts, same/older/unparseable versions and unfinished/missing/wrong APKs; accept newer test releases; order versions correctly; limit notes; reject non-HTTPS, wrong-host, credential-spoofed and wrong-repository download URLs.

## Android runtime and layout checks

[Successful GitHub verification run](https://github.com/TygerBDevTeam/pocket-pulse/actions/runs/42158102193) built the same app sources on Ubuntu and ran an accelerated Android 16 / API 36 x86_64 emulator. CI generates its own temporary test key; the public release APK is independently built/signed on Windows with the preserved private release key. No private release material is sent to CI.

- Installed and cold-launched successfully; eight-card dashboard and three volume controls found.
- Inspected actual captures at **960 × 720, density 200 (4:3)** and **1240 × 1080, density 320 (Thor-shaped lower-screen window)**. Both landscape layouts fit all eight cards, with this app's usage at bottom right. Shorter layouts use compact cards and can hide charts.
- Captured a 480 × 360 compact window, 720 × 960 portrait window and 1.3× font scale. Verified compact-window scrolling reaches the last card, then inspected the scrolled capture. Physical touch/controller behavior still requires handheld testing.
- Verified pause/resume; zero app frames drawn during a five-second manual pause and during a five-second period hidden behind the launcher. This checks redraw behavior, not zero CPU cost.
- Changed refresh 2 → 5 seconds; verified persistence through process restart; restored 2 seconds.
- Verified single-display fallback and Options controls. Switched Storage → Thermal status, confirmed persistence after process restart, and restored Storage.
- Verified Fahrenheit-only displayed readings and settings; emulator battery at 77.0 °F.
- Verified Mute changes to Unmute and back using Android's media stream.
- Performed a real HTTPS GitHub release check in the running app. It correctly reported no newer published APK for installed 0.2.0 while only 0.1.0 was published.
- Unsupported CPU/GPU utilization and fan RPM stayed unavailable. Internal capacity and absence of microSD displayed independently. Emulator battery current/voltage produced a labeled net charging-power reading.
- No Pocket Pulse entries in the Android crash log throughout the checks.

## Signing migration and private backup

The 0.1.0 release APK's certificate SHA-256 is:

`4e2c831f9a5393516f8c7b29336a6b1dfaa081762a6b7ca4d4e437510ad367c8`

The 0.2.0 release APK's certificate SHA-256 is:

`e702b36a6865af6319990b8231566bfaefac525bf20dd63ee65203c618c2ddde`

Both certificates and package metadata were inspected. The old private key was unavailable, so 0.1.0 must be uninstalled once. A new randomly password-protected key and its password file are retained privately, with a separately verified local backup. Neither key nor password is tracked by Git or included in release assets. Repeated local builds reused the same new certificate.

## Remaining hardware checks

- No physical Retroid Pocket Nova/dual-screen attachment, AYN Thor or Anbernic DDS was connected. OEM sensor permissions, fan endpoints, removable-card readings, secondary-display switching and gaming overhead are not verified on those devices.
- The emulator has one accessible display. Moving the app to an actual second display and keeping it visible alongside a game require hardware testing.
- Real microSD insertion/removal, adopted storage and ambiguous USB/removable-volume labeling require hardware testing. Storage refreshes every 15 seconds.
- Native Android 8–15 compatibility is based on compilation/API guards, not separate runtime tests for each OS.
- No per-game FPS, per-game CPU/GPU/RAM, battery-life comparison, or gameplay-impact measurement was performed. Emulator process CPU values include cold starts and automated test interactions and are not handheld overhead claims.
- The local Windows software emulator did not boot reliably; runtime checks therefore used the hosted accelerated emulator.

The release includes a SHA-256 checksum file for the exact APK and source ZIP. Rebuild using `build.ps1` with the same private signing pair to produce compatible future updates.
