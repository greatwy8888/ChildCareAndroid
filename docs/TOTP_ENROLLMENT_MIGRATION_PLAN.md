# TOTP Enrollment and Safe Migration Plan

## Status

Design only. No production enrollment or key rotation has been implemented. Do not rotate the currently embedded seed as an isolated code change: a failed migration could lock the parent out of the existing Device Owner installation.

## Current security finding

The current TOTP seed is embedded in the public source and therefore must be considered exposed. A seed embedded in an APK cannot be kept secret from someone who can inspect that APK. CI success does not reduce this risk.

Until a tested migration exists:
- Do not publish or repeat the seed or generated codes.
- Do not assume the existing seed proves that a person is the legitimate parent.
- Do not remove Device Owner, clear app data, uninstall the currently installed child-phone app, or reset the phone as part of development.
- Keep the existing installation unchanged and use a spare device for enrollment and recovery tests.

## Required design for a future version

1. **Generate a unique secret per installation.** Use a cryptographically secure random generator; never ship a shared seed in source code, resources, build configuration, or APK.
2. **Explicit enrollment ceremony.** Show a standards-compatible `otpauth://` QR code and manual setup key only during an explicit parent-controlled setup screen. Avoid writing the secret or QR URI to logs, analytics, crash reports, or clipboard automatically.
3. **Confirm enrollment before activation.** Require a valid code from the newly enrolled authenticator before replacing the active credential. Display a clear success state only after the new secret is durably stored.
4. **Protect local storage.** Encrypt the seed using an Android Keystore-backed key, with authenticated encryption and versioned storage. Handle key invalidation and corrupted storage with a documented recovery path; do not silently fall back to an embedded seed.
5. **Prevent accidental lockout.** Keep the current credential active until the new enrollment is confirmed and persisted. Provide a parent-controlled recovery procedure tested on a spare device. Recovery codes, if implemented, must be generated securely, stored only as verifiers, single-use, and never logged.
6. **Plan compatibility explicitly.** Existing installations may have the old embedded credential and Device Owner policies. Use a staged migration with an explicit enrollment state, a clear rollback strategy, and tests for process death/reboot during enrollment. Do not make migration dependent on clearing data or re-provisioning the child's phone.
7. **Test before release.** Test invalid codes, expired codes, clock skew, QR/manual enrollment, process death during each step, reboot, storage/key loss, recovery, and upgrade from the currently installed version. Confirm the parent can regain access before changing the active credential.
8. **Review secret exposure.** Because an APK's client-side logic can be reverse engineered, per-installation enrollment reduces shared-secret exposure but cannot prevent a person with sufficient device/app access from extracting a local credential. Consider stronger server-assisted or hardware-backed threat models only if the product requirements justify their operational costs.

## Uninstall authorization is a separate design

TOTP verification and Android package uninstall are separate controls. Calling `setUninstallBlocked(..., false)` alone does not guarantee that a Device Owner app can safely remove itself or relinquish Device Owner status. Any future uninstall flow must define Android-version/OEM behavior and a recovery path, then be validated on a spare Device Owner device. Until then, do not advertise TOTP-authorized uninstall as supported.

## Release gate

Do not merge a credential migration into the child-phone production path until enrollment, upgrade, recovery, and return-to-lock behavior all pass on a spare device and the exact APK/signature/update path is verified.