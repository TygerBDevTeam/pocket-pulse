# Changelog

## 0.2.1

- Fix bottom-row overflow on shorter/high-density handheld windows by replacing the fixed minimum card height with font-measured adaptive profiles and exact viewport geometry.
- Auto-fit is enabled by default on fresh installs and upgrades. Full-size cards stay where they fit; shorter screens use tighter spacing, smaller storage rows and fewer secondary details. Tap tight cards for full readings. Very small windows/large accessibility fonts keep a readable scrolling fallback. Auto-fit can be disabled in Options.
- Choose GPU activity, Display info, or Device uptime for the GPU card in Options. Choice survives restart; display refresh is explicitly labeled separately from game FPS.
- Device reports now include actual window/viewport size, density, font scale and selected card geometry for layout troubleshooting.
- Expand automated checks to 1280 × 960 at multiple densities, no-scroll assertions, GPU replacements and saved selection, with baseline 4:3/Thor-sized/compact/portrait checks retained.
- Same signing key as 0.2.0: install directly over 0.2.0 without losing settings.

## 0.2.0

- Approved compact eight-card landscape layout, with Pocket Pulse and live status moved into the footer.
- Media Volume −, Volume + and Mute / Unmute above Display and Options.
- All displayed temperatures and report temperatures use Fahrenheit.
- Combined internal/microSD free and total storage card, with a saved option to show thermal severity instead.
- Actual fan RPM when readable, otherwise unavailable. PWM/mode values are not treated as RPM.
- Net battery draw or charge in watts when current and voltage are available.
- Manual GitHub release check with version comparison, test-release support, browser downloads, cancellation and error/retry handling.
- New privately retained release key: uninstall 0.1.0 once before installing 0.2.0. Further updates can use the same key normally.
- Expanded telemetry, release-version and on-Android release-selection regression checks.

## 0.1.0

- Initial native six-card second-display monitor.
