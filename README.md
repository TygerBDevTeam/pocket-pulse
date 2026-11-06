# Pocket Pulse — Android handheld performance monitor

Version **0.2.0**. Lightweight native Java / Android framework only. Android 8.0 (API 26) or newer, targeting API 36. No runtime libraries or account required.

[Download the latest APK](https://github.com/TygerBDevTeam/pocket-pulse/releases/latest/download/Pocket-Pulse-0.2.0.apk) · [Release notes and downloads](https://github.com/TygerBDevTeam/pocket-pulse/releases)

**Upgrading from 0.1.0:** the original signing key was unavailable on this computer. Uninstall 0.1.0 once, then install 0.2.0. Your Pocket Pulse settings will reset. Future releases signed with the new, privately retained key can install over 0.2.0 normally.

## Dashboard

- Compact top row with **Volume −, Volume +, Mute / Unmute** for Android's media/game audio stream.
- Eight cards in four columns and two rows when the window is wide enough: CPU, GPU, system RAM, battery, storage, fan speed, battery draw, and this app's own CPU/memory usage at bottom right.
- Storage shows **internal and microSD free/total space together**. Options can replace that card with Android thermal status; the choice is saved.
- **Every displayed temperature is Fahrenheit**, including the copied device report.
- Small Pocket Pulse label at bottom left, refresh/collection information in the middle, and Live / Paused at bottom right.
- Options → **Check for updates** checks this repository's published releases, including test builds. A newer release must have a finished APK asset. Download opens in your browser; Android confirms installation. Checks happen only when requested, never automatically or during telemetry polling.

Layouts adapt to 4:3, Thor-shaped landscape lower screens, portrait, split screen, display density, and larger system fonts. Short windows scroll rather than clipping cards. Retroid Pocket Nova with a dual-screen attachment is the user's initial target; AYN Thor and Anbernic DDS are intended targets, not hardware-validated claims.

## Landscape captures

Actual Android emulator captures. Physical-device readings, fonts/density and available sensors vary.

| 4:3 landscape (960 × 720) | Thor-shaped landscape window (1240 × 1080) |
| --- | --- |
| ![4:3 landscape](screenshots/landscape-4-3.png) | ![Thor-shaped lower screen](screenshots/thor-lower-screen.png) |

## Install and choose a display

Download `Pocket-Pulse-0.2.0.apk` from Releases, open it in your handheld's file manager, and allow that file manager to install unknown apps if Android asks. See the one-time signing migration above if 0.1.0 is already installed.

Use the handheld launcher/screen-switch shortcut to open Pocket Pulse on the secondary display, or tap **Display** and choose an accessible display. Android and OEM firmware control whether the move is allowed. Run your game on the other screen. No overlay or background service is required.

## Readings and limits

- **CPU:** system-wide utilization from `/proc/stat` where readable. Android commonly restricts it. CPU frequencies are separate readings and are not utilization.
- **GPU:** Qualcomm KGSL percentage or busy/total counters and clocks where readable. Other drivers without supported endpoints show unavailable. An idle `0/0` counter is unavailable, not 0% load.
- **RAM:** total minus available memory from `ActivityManager`. Device-wide, including the system and some cache; not per-game allocation. Uses binary GiB.
- **Battery:** percentage and Fahrenheit temperature from Android's battery broadcast. Battery temperature is not chip temperature.
- **Storage:** available and total filesystem space, in GiB, sampled every 15 seconds. Internal refers to the app's internal data volume. microSD uses an accessible, non-primary removable volume and app-specific directories, without broad storage permissions. Known USB-labeled volumes are excluded; Android/OEM firmware may not distinguish every removable device. Adopted SD storage may be included in internal space. No mounted card, inaccessible space, and unsupported detection have distinct messages.
- **Fan:** actual RPM from explicit fan-RPM sysfs endpoints, standard `hwmon` fan inputs, or Android's restricted hardware API if permitted. No root required; many devices will not expose RPM to a regular app. Fan mode, PWM, and cooling state are never presented as RPM. A real zero is valid. Failed endpoints are disabled until relaunch.
- **Battery draw/charge:** net battery power from current (microamps) × voltage (millivolts). Magnitude is shown in watts with charging/discharging direction. The heading becomes Battery charge when current flows into the battery. This is not wall-adapter wattage or a game's individual power use. Unsupported current/voltage stays unavailable.
- **Optional thermal status:** Android 10+ severity, not a thermometer. None means no severity reported by firmware, not proof of a cool or unthrottled chip.
- **This app:** whole-process CPU as a percentage of one core (can exceed 100% across cores), plus PSS memory in MiB sampled at most every 15 seconds. Collection time covers reads, not drawing cost or actual game impact.

A dash means unavailable or waiting for a valid interval. There are no estimated/fake utilization numbers. This no-setup version cannot measure another game's FPS or its individual CPU/GPU/RAM usage. Sensor availability, display switching, controller navigation, and gaming impact must be tested on physical handhelds.

## Low activity and permissions

- Default 2-second refresh; saved 1, 2, 5 or 10-second intervals. One telemetry HandlerThread, native views, fixed 90-sample charts, no animation loop.
- Pure black canvas, cards and idle controls. Redraw on updates only. Pausing, hiding, turning the screen off or changing intervals stops/resets history appropriately. A visible Activity on a secondary display continues reading even when another app has focus.
- Keep-awake and optional 25% dimming. The second screen itself still consumes power.
- `INTERNET` is for manual HTTPS GitHub release checks only. `MODIFY_AUDIO_SETTINGS` is for media-volume controls. No broad file access, root, account, analytics, ads, overlay, boot receiver, wake lock or background service.
- Update checks use a separate cancellable thread, timeouts, a bounded response and HTTPS repository-checked download links. They do not block telemetry collection. No credentials or device report are sent.

**Options → About / readings → Copy device report** copies model, Android version, displays, counter sources, storage sources and current readings, with temperatures in °F. Nothing is uploaded. Include this report when reporting device-support issues; exclude serial numbers or account information.

## Build on Windows

Install JDK 17 and Android SDK Platform 36 with Build-Tools 36.0.0, then:

```powershell
.\build.ps1 -JavaHome 'C:\path\to\jdk-17' -AndroidPlatform 'C:\Android\Sdk\platforms\android-36' -BuildTools 'C:\Android\Sdk\build-tools\36.0.0'
```

The script invokes AAPT2, javac, D8, zipalign and apksigner directly and runs standalone telemetry/version checks. No Gradle or runtime dependency resolution. Output is `dist/Pocket-Pulse-<version>.apk` with `dist/SHA256SUMS.txt`; the version comes from the manifest.

On first build it creates `.signing/pocket-pulse-release.p12` (alias `pocketpulse`) and `.signing/signing-password.txt` with a random password. **Back up both privately and use the same pair for future releases.** Optional `-Keystore` and `-SigningPasswordFile` parameters can point to a restored private backup. Never publish the signing directory, password, local tools or backups. A fresh key will not update an existing installation. Source archives omit private material.

See [VALIDATION.md](VALIDATION.md) for verified checks and limitations, [CONTRIBUTING.md](CONTRIBUTING.md) for development guidance, and [CHANGELOG.md](CHANGELOG.md) for changes. MIT licensed.

Android references: [multiple displays](https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)), [memory](https://developer.android.com/reference/android/app/ActivityManager), [battery](https://developer.android.com/reference/android/os/BatteryManager), [thermal status](https://developer.android.com/reference/android/os/PowerManager), [storage](https://developer.android.com/reference/android/os/storage/StorageVolume), [media audio](https://developer.android.com/reference/android/media/AudioManager).
