# Contributing to Pocket Pulse

Keep the monitor lightweight: native Android views, no continuous animation, no network requirement for monitoring, no game hooks, no background service, and no repeated probing of inaccessible endpoints. Network access is exclusively for manually requested GitHub update checks. Display all temperatures in Fahrenheit.

For device-support reports, include the output of **Options → About / readings → Copy device report**, screen resolution/density if known, and whether the app is on the main or secondary display. Do not include serial numbers or account information.

New hardware counters must document their source, units, and semantics. Unknown or inaccessible values must stay unavailable. Clock frequency is not utilization, battery temperature is not chip temperature, and another app's FPS must not be inferred from this monitor's rendering.

Build using `build.ps1` as described in the README. It runs counter/unit and semantic-version checks. Check UI changes in at least 4:3 and 1240 × 1080 landscape windows, plus a small window with compact cards. `tests/SmokeTest.ps1 -Adb <path-to-adb.exe>` exercises the running app on **emulator-5554 only**; it expects default 2-second refresh, unmuted media, English UI, and no extra display connected. `tests/AndroidRegression.ps1` takes the same SDK/JDK paths plus `-Adb` and runs release-selection checks with Android's own JSON implementation.

Keep signing keys, local SDKs, build directories, device-specific files, and private credentials out of commits. Publish the signed APK as a GitHub release asset. Keep the original development key locally if issuing compatible updates; a public release should have its own privately managed release key.

Changes should preserve the distinction between telemetry collection time, this app's measured CPU/PSS, and actual gameplay impact. Claims about game FPS or battery impact require measurements on physical hardware.
