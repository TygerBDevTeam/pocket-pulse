# Validation — Pocket Pulse 0.1.0

Performed November 5, 2026. This is an installable development prototype, with physical-device compatibility still to be confirmed.

## Passed

- Compiled all Android sources against SDK 36 using JDK 17 and Android Build-Tools 36.0.0; converted to DEX with minimum API 26.
- APK signature verification: v2 and v3 valid. ZIP alignment verified. Package `dev.pocketpulse.monitor`, version 0.1.0, minimum API 26, target API 36. No requested Android permissions and no bundled native libraries.
- 33 standalone regression checks: CPU totals exclude guest double counting; CPU counter deltas/resets; invalid/unavailable GPU counters; valid zero versus unavailable values; RAM calculation; process CPU relative to one core; bounded ring history and reset.
- Installed and cold-launched successfully in an Android 16 / API 36 x86_64 emulator. No Pocket Pulse entries in the Android crash log during smoke testing.
- Inspected actual screenshots at 960 × 720 / density 200 (4:3) and 1240 × 1080 / density 320 (Thor-shaped landscape lower screen). All six cards and controls fit at those test densities. Checked pure-black canvas/cards and readable outlines/text.
- Automated UI checks: all six card titles; pause/resume; changing refresh from 2 to 5 seconds; saved refresh after a process restart; restoring default 2 seconds; one-display explanation; awake/dim/compact options.
- Android graphics statistics recorded zero app frames during a five-second manual pause, and zero app frames during a five-second period hidden behind the launcher. This confirms idle drawing behavior, not a proof of zero process CPU use.
- Unsupported CPU/GPU usage showed unavailable values rather than invented readings; CPU frequency, RAM, battery, thermal status, and the app's own CPU/PSS were displayed.

## Limits and remaining checks

- No physical Retroid, AYN Thor, or Retroid dual-screen attachment was available. Device counter permissions, controller navigation, OEM display switching, actual display densities, and real gaming overhead remain unverified.
- The emulator's native multidisplay console reported `setMultiDisplay not supported`; the one-display fallback was tested. Moving through the app's picker and keeping a game active on another physical display still require handheld testing.
- Android 8–15 compatibility is based on compilation/API guards, not separate runtime tests on every supported version.
- No game FPS, battery-life comparison, or on-device benchmark was performed. APK download size is not runtime RAM or CPU cost. Emulator overhead readings are illustrative, not handheld guarantees.
- The APK uses a locally generated development signing key. Keep the original private key for compatible development updates; do not upload it to a public repository.

## Deliverable

`dist/Pocket-Pulse-0.1.0.apk`: 29,154 bytes.

SHA-256: `5eb537576f52b7b9268084b9175aaeb6523b2790abb1558e6e165c945c524a19`

Rebuild with `build.ps1`; the script reruns signature/alignment verification and the telemetry regression checks, and writes a fresh `dist/SHA256SUMS.txt`.
