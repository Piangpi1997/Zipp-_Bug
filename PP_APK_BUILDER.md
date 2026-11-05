# pp APK Builder

This branch combines the pinned WebToApp source with Zip-Bug's visual studio in one standard-flavor Android application. The original Zip-Bug project remains at the repository root.

## Source

- WebToApp source is under `web-to-app/`, based on commit `a7f5432237f67f691b724c432aa39c33399a8301` from https://github.com/shiaho777/web-to-app. Its Unlicense and third-party notices remain in that directory.
- The imported source keeps its existing builder and AI-provider implementation. Its launcher label is `pp APK Builder`.
- Zip-Bug's visual studio assets and Android activity are added to the builder app. The standard-flavor app has two launcher entries: `pp APK Builder` and `pp Studio`.

## Studio to builder

1. Open `pp Studio` and design a screen.
2. Use `Export HTML` to save the current screen through Android's document picker.
3. Open `pp APK Builder` and import the saved HTML through its existing HTML import workflow.

The exported HTML is a static representation of the selected screen; interactive behavior, multi-screen navigation, and automatic project transfer are not implemented by this integration. The combined flow still requires device testing.

## Build

Use JDK 17 and the Android SDK as described in `web-to-app/README.md`:

```sh
cd web-to-app
./gradlew --no-daemon :app:assembleStandardDebug
```

The debug APK uses the upstream application ID `com.webtoapp` and a debug signing key. It cannot update an existing installation signed by another key. Preserve any existing app data before making installation changes; do not uninstall automatically.

The upstream AI settings can store provider keys on the device. No backend-only key policy or hosted AI backend was added by this integration. Do not commit keys or put them in distributed source code.
