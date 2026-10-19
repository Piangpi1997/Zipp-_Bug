# Zip_Bug // Antigravity Studio — Signing & Signature Consistency Guide

## 1. Background: Android Signing & Conflicts

When installing an update to an existing package on Android, the Android Package Manager enforces signature parity:
- The certificate of the incoming APK must match the certificate history of the currently installed app.
- If certificates differ, Android rejects installation with:
  ```
  INSTALL_FAILED_UPDATE_INCOMPATIBLE:
  "App not installed as package conflicts with an existing package."
  ```

In Zip_Bug development across Windows, Termux, and GitHub CI, different debug keystores are generated if not shared or synchronized.

---

## 2. Key Environments & Keystore Locations

| Environment | Keystore Location | Default Alias | Default Password |
|---|---|---|---|
| **Windows** | `%USERPROFILE%\.android\debug.keystore` | `androiddebugkey` | `android` |
| **Termux** | `~/.android/debug.keystore` | `androiddebugkey` | `android` |
| **GitHub Actions** | Ephemeral runner keystore | `androiddebugkey` | `android` |

---

## 3. Maintaining Signature Consistency

### Rule A: Debug Key Synchronization
To seamlessly update APKs built in Termux on a physical device with APKs built via Windows Gradle:
1. Export the developer's local `debug.keystore` to Termux:
   ```bash
   cp /path/to/shared/debug.keystore ~/.android/debug.keystore
   ```
2. Ensure both platforms sign debug builds with the same certificate.

### Rule B: Handling Signer Conflict (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)
When switching between builds signed by different keystores (e.g., CI build vs. local Termux build):
1. **Never silently uninstall**: Zip_Bug will never wipe app data without explicit consent.
2. In Zip_Bug **Signing Diagnostics** or **APK Detail Inspector**:
   - Tap **Uninstall Existing App** to prompt the Android system uninstaller.
   - Once uninstalled, tap **Install APK** to install the fresh build.

---

## 4. Production & Release Signing Profiles

For production releases:
- Store keystores outside repository tracking.
- Reference keystore paths and credentials using environment variables:
  - `ZIPBUG_RELEASE_KEYSTORE_PATH`
  - `ZIPBUG_RELEASE_KEY_ALIAS`
  - `ZIPBUG_RELEASE_STORE_PASSWORD`
  - `ZIPBUG_RELEASE_KEY_PASSWORD`
- **Security Rule**: NEVER commit private keystores (`.jks`, `.keystore`) or plaintext credentials to Git.

---

## 5. APK Signature Scheme Verification
Verify APK signatures directly using Android SDK `apksigner`:
```bash
apksigner verify --verbose app-debug.apk
```
Expected output:
```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
```
