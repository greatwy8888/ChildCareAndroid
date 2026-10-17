# Safe real-device validation checklist

This checklist is for a **spare Android device or a test profile that can be wiped**, not the currently protected phone.

## Before installing a test APK

- [ ] Confirm the APK came from the latest successful GitHub Actions run on `safe-alternative-test`.
- [ ] Confirm package name is `com.example.childcare.next`; it must not replace `com.example.childcare`.
- [ ] Keep the existing `Lock Screen 0.2.0` installation and Device Owner state unchanged on the child's phone.
- [ ] Record device brand/model, Android version, security patch level, and battery optimization settings.
- [ ] Ensure there is a documented recovery path and the device owner credentials are available.

## Functional checks

1. **Basic unlock:** Generate a current six-digit TOTP. Verify valid codes unlock, malformed/wrong codes do not, and expired codes are rejected.
2. **Duration setting:** Verify default is 40 minutes; accept 5 and 180; reject 4, 181, blank, and non-numeric input.
3. **Timer lifecycle:** On a test-only build or controlled short test configuration, unlock, background the app, and verify the service notification remains visible and the timer reaches its deadline.
4. **Re-entry during active unlock (tracked in [#2](https://github.com/greatwy8888/ChildCareAndroid/issues/2)):** A code change intended to preserve the active authorization window is committed in [`98073cbc`](https://github.com/greatwy8888/ChildCareAndroid/commit/98073cbc9a7b53c80370a2ce8e70767dbc435601). Verify by reopening the app from its icon and tapping the timer notification before the deadline. The device must remain in the same authorized window and must not demand a new TOTP early. **Not yet verified on a physical device.**
5. **Deadline relock (tracked in [#3](https://github.com/greatwy8888/ChildCareAndroid/issues/3)):** At expiry, verify the guard actually becomes foreground while another app is open. Do not count a successful CI build or a non-throwing `startActivity()` call as proof.
6. **Return-to-app fallback:** If automatic foreground relock is blocked, return to the app and verify the expired deadline triggers the guard before normal use continues.
7. **Reboot:** Reboot during locked and unlocked states. Confirm the app returns to locked state and that policy restrictions are reapplied. Repeat on the target OEM because boot activity launches can be restricted.
8. **Home/Overview:** While the app is Device Owner and Lock Task is active, test Home, Overview/Recents, notifications, Settings entry points, and power-menu behavior.
9. **Process death:** Force-stop is not equivalent to normal process reclamation and may be blocked for a Device Owner app. Separately test low-memory process recreation and confirm the persisted deadline is handled.
10. **Uninstall recovery:** Do not test uninstall on the child's phone. On a disposable test device, document the exact Device Owner release/removal path before enabling any uninstall authorization UI.

## Pass/fail recording

For each case record: device model, Android version, exact steps, expected result, observed result, whether the app was foreground/background, and logcat errors. Any failure to force the guard foreground at expiry is a **failure**, even if the expired deadline is retained for a later fallback.

## Known limitations

- Android/OEM background activity launch restrictions can prevent a lock activity from appearing over another app at the exact deadline.
- A fallback that runs only when the activity resumes does not guarantee immediate enforcement while the user remains in another app.
- A TOTP secret embedded in an APK can be extracted. Moving the same secret into build configuration or GitHub Actions secrets does not hide it from APK users.
- The current branch does not yet implement a complete TOTP-authorized uninstall and Device Owner recovery workflow.
- CI builds and unit tests do not substitute for tests on the actual device and Android/OEM version.
