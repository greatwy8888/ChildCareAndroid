# Security and migration notes

## Current test build

- Test application ID: `com.example.childcare.next`.
- Existing protected installation / Device Owner package: `com.example.childcare`.
- These are separate Android packages. Device Owner state and app-private data do not transfer between them.
- The test build's signing certificate matches the newly created release keystore, not the certificate previously reported for the installed 0.2.0 APK. It therefore must not be treated as an in-place update.

## Critical TOTP limitation

The current test branch still supplies the TOTP shared secret as a string in `MainActivity.kt`. Moving the verification algorithm into `Totp.kt` makes it independently testable, but does **not** make an APK-embedded secret confidential. Anyone who can extract and inspect the APK may recover it.

Before production use:
1. Coordinate a new TOTP secret with the authenticator app and test the new code on a separate device.
2. Do not rotate the secret used by the currently installed protection app until there is a tested recovery path.
3. Never put keystore passwords, private keys, or TOTP seeds into source control, issue comments, or build logs.
4. Treat repository Actions secrets as build-time inputs, not as a way to hide a secret that must be shipped inside the APK.

## Device Owner migration safety

Do not run `dpm remove-active-admin`, uninstall the existing owner, clear its data, or factory-reset the child's phone as an experiment. Device Owner migration may require reprovisioning and can remove local data or temporarily remove the current protection.

Before any migration is approved:
- [ ] Make a separate backup of important phone data and verify that it can be restored.
- [ ] Confirm the old app's exact package, owner component, version and signing-certificate fingerprint.
- [ ] Confirm the proposed APK package ID and signing-certificate fingerprint.
- [ ] Test the proposed APK on a spare device with the same Android major version where possible.
- [ ] Verify correct/wrong/expired TOTP, settings gate, 5–180 minute timer, reboot, screen-off/on, Home, Overview/Recents, notification shade, safe mode/recovery constraints, and uninstall behavior.
- [ ] Prepare a documented recovery route before changing Device Owner state.
- [ ] Obtain explicit confirmation before any destructive or device-management change.

## Test evidence

A successful GitHub Actions build establishes only that the source compiled and the automated unit tests passed. It does not prove that Honor-specific system behavior, kiosk restrictions, boot launch, or relocking works on physical hardware.
