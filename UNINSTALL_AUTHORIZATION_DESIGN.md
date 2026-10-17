# Uninstall authorization design

Status: design only; no uninstall authorization is implemented by this document.

## Current behavior

The guard currently applies `DevicePolicyManager.setUninstallBlocked(admin, packageName, true)` while the guard is shown. The app does not yet provide a complete TOTP-authorized uninstall workflow.

## Why a simple unlock-then-uninstall button is unsafe

Android's official `DevicePolicyManager.clearDeviceOwnerApp(String)` API is deprecated (API 26) and documented as intended for testing purposes. The API documentation warns that clearing the owner is irreversible through that call, that the app loses device control, and that clearing policies is best-effort: some policies may remain. Device Owner provisioning is normally available only during device setup, before setup completes.

Official references:
- https://developer.android.com/reference/android/app/admin/DevicePolicyManager#clearDeviceOwnerApp(java.lang.String)
- https://source.android.com/docs/devices/admin

Therefore, this project must not silently call `clearDeviceOwnerApp()` after TOTP validation as a routine uninstall mechanism. That could leave the device in a partially managed state and may not be recoverable without a reset/reprovisioning workflow.

## Safer product decision

Treat the following as separate actions:

1. **Temporary screen unlock:** Verify TOTP and grant the configured usage interval. This must not alter Device Owner status or uninstall policy.
2. **Parent-authorized protection disable:** Verify TOTP, require an explicit second confirmation, clearly explain that device-wide restrictions will be removed and that re-enabling Device Owner may require device setup/factory reset. Do not offer this action until the exact recovery path is tested on the target Honor/Android version.
3. **App uninstall:** Only offer after a supported and verified device-management release process exists. Do not claim that TOTP alone can authorize uninstall while the app remains Device Owner and uninstall-blocked.

## Required safeguards before implementation

- Test on a spare device, never the currently protected phone.
- Record Honor model, Android version, and exact DPM behavior.
- Establish a recoverable path before changing any owner or uninstall policy.
- Never auto-clear Device Owner or uninstall restrictions during boot, app startup, ordinary unlock, or timer expiry.
- Keep the app locked and show a clear error if a policy operation fails; do not report success merely because no exception was thrown.
- Do not store a reusable TOTP secret in plaintext source and assume it is secret in the APK. APK-embedded secrets can be extracted. A future enrollment design should define secret provisioning, rotation, backup, and recovery before changing the currently deployed code.
- Verify all relevant behavior with the target device, including whether restrictions remain after any test release action.

## Current implementation status

- TOTP validation exists for screen unlock and settings.
- Device Owner mode is used for kiosk restrictions.
- Uninstall is currently blocked while the guard is active.
- A TOTP-authorized uninstall/release workflow is **not implemented**.
- No production-device action should be taken based on this design note alone.
