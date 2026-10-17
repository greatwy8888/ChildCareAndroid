# TOTP secret security and migration plan

Status: design guidance only. This document does not rotate or change the active secret.

## Current threat model

The current Android client verifies TOTP locally. A shared secret embedded in the APK or compiled application can be extracted by a person who obtains the APK or can inspect the installed app. Obfuscation, moving the value to a build-time environment variable, or placing it in BuildConfig does not make a secret confidential once it is packaged into the client.

Android Keystore can protect a key generated or imported into a device's secure storage from ordinary extraction, but it does not by itself solve the initial shared-secret distribution problem if the same TOTP secret is shipped in the APK. It can also make recovery harder if the device is reset or the app's data is lost.

## Do not make an unsafe immediate change

- Do not delete or replace the currently active secret without first establishing a tested parent enrollment and recovery flow.
- Do not assume that changing a GitHub Actions secret rotates a secret already embedded in an APK.
- Do not publish a new provisioning QR code, secret, or recovery code in repository files, build logs, release notes, or public artifacts.
- Do not deploy a changed secret to the child's currently installed Device Owner app until compatibility and recovery have been tested on a spare device.

## Preferred future architecture

1. Define a parent-only enrollment ceremony that shows a new secret/QR code once, with explicit confirmation and a safe offline backup procedure.
2. Version the active credential so the app can distinguish old enrollment from new enrollment without silently invalidating the old authenticator.
3. Require the parent to confirm a valid code from the new authenticator before committing a rotation.
4. Preserve a documented recovery method if the authenticator is lost, the phone is reset, or app data is lost.
5. Consider server-side verification if the product has a trustworthy backend and connectivity is acceptable. Server-side verification reduces reliance on a secret shipped in every APK but introduces account, availability, privacy, and operational requirements.
6. If verification remains fully local, explicitly document that this is a deterrent rather than a secret that can be kept from someone with sufficient access to the APK/device.

## Minimum acceptance tests before rotation

- New enrollment succeeds and an invalid code fails.
- Current secret remains accepted until the parent confirms the new secret.
- A failed rotation leaves the old credential usable.
- App restart and device reboot preserve the selected credential.
- Backup/recovery instructions work on a spare device.
- A test APK and build logs contain no plaintext new secret.
- Rotation has been exercised on a spare device with the same Android/OEM family before any production change.

## Current state

The current app uses local TOTP verification. A full enrollment, rotation, backup, and recovery workflow is not yet implemented. This file is a design checkpoint, not evidence that the current secret has been secured or rotated.
