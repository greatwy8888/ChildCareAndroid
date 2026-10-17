# Safe physical-device test procedure

> **Use a spare Android device only. Do not install this test package on the child's currently managed phone.** The test package is `com.example.childcare.next`; the existing Device Owner app is `com.example.childcare`. Do not remove the existing owner, clear its data, or factory-reset the child's phone.

## What this test can establish

GitHub Actions passing is not device evidence. This procedure records observed behavior on a specific Android/Honor build. A successful test on one device does not prove behavior on every OEM/OS version.

## Before starting

- [ ] Confirm this is a spare device and back up any data on it.
- [ ] Record manufacturer/model, Android version, security patch level, and whether the test package is Device Owner.
- [ ] Download the APK from the latest successful workflow on `safe-alternative-test`.
- [ ] Confirm package ID is `com.example.childcare.next`; never use it as an update for `com.example.childcare`.
- [ ] Have the parent's authenticator available. Do not copy TOTP seeds into tickets, terminal history, or logs.
- [ ] Ensure there is a known recovery path for the spare device before testing Device Owner / kiosk mode.

## Phase A — ordinary app smoke test (no Device Owner)

1. Install the test APK on the spare device.
2. Launch it and verify the guard screen appears.
3. Enter an incorrect or expired six-digit code; confirm the app does not unlock.
4. Enter the current valid parent code; confirm the app reports the configured unlock duration.
5. Open Settings and verify it requires parent verification; set a short test interval (minimum 5 minutes), then confirm the saved value persists.
6. Start another app and wait through the full interval. Record whether the guard appears automatically. If Android blocks the background launch, verify that the persistent “休息时间到了” recovery notification appears and opens Lock Screen when tapped. This notification is a recovery fallback, **not proof of forced relock**; notification permission and OEM policy may affect visibility.
7. Repeat with the screen turned off/on. Do not infer success solely from a toast or log message.

## Phase B — Device Owner / kiosk test (spare device only)

Only proceed if the spare device can be safely reprovisioned and a recovery route is documented. Device Owner provisioning often requires a freshly provisioned device. Never remove Device Owner from the child's current phone for this test.

Test each item separately and record pass/fail plus exact OS build:

- [ ] Home / Overview (Recents) cannot bypass the guard while locked.
- [ ] Back button cannot bypass the guard while locked.
- [ ] Status bar / notification shade behavior matches the intended policy.
- [ ] Correct TOTP unlocks; incorrect and expired codes do not.
- [ ] Settings remains TOTP-gated and enforces the documented 5–180 minute range.
- [ ] When the timer expires while Lock Screen is foregrounded, the guard is shown.
- [ ] When the timer expires while another app is foregrounded, observe whether Android allows the guard activity to launch. If it does not, record this as a failure; do not claim exact-time relock works.
- [ ] Reopening Lock Screen after the deadline restores the guard.
- [ ] Reboot with the device in a locked state; verify actual visible behavior, not just boot logs.
- [ ] Reboot during an authorized usage interval; confirm the intended policy is to discard that interval and return to the guard.
- [ ] Check app process termination / service restart behavior where safely possible.
- [ ] Confirm the spare device can be recovered before any uninstall/removal experiment.

## Collect diagnostic logs

Connect the spare device over USB with Android Platform Tools installed. Use read-only diagnostics:

```bat
adb devices
adb logcat -c
adb logcat -v time -s MinimalGuardActivity:I MinimalGuardTimer:I MinimalGuardBoot:I
```

Leave the logcat window running while reproducing one test. Stop it with Ctrl+C. Review the output before sharing; never share TOTP codes, secret seeds, account identifiers, or unrelated personal logs.

Useful log markers:
- `MinimalGuardActivity`: activity resumed and whether the saved deadline is expired.
- `MinimalGuardTimer`: timer started and expiry launch requested.
- `MinimalGuardBoot`: boot/update receiver ran and attempted recovery.

A log saying that an activity launch was requested does **not** prove the lock screen became visible. Confirm on the device itself.

## Current limitations and stop conditions

- Android/OEM background-activity restrictions may prevent exact-time relocking when another app is foregrounded. If observed, stop and document it; do not add `lockNow()` or stronger device policy changes without a reviewed recovery plan.
- The TOTP verifier currently uses a source-level shared secret. Treat it as exposed; do not publish or repeat the seed. A parent-controlled re-enrollment and recovery design is required before production use.
- TOTP-authorized uninstall is not implemented. The current code persistently blocks uninstall while the guard is shown. Do not experiment with removing the current Device Owner app.
- Do not run `dpm remove-active-admin`, clear app data, uninstall the child's existing package, or factory-reset the child's phone.
