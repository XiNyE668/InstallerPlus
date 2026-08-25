# InstallerPlus Android 16 / LineageOS 23.2 hardening overlay

Baseline: `NextAlone/InstallerPlus` branch `main`, reviewed 2026-08-24.

This overlay is intended to be copied over a clean clone of upstream. It does not contain the complete upstream project and remains subject to the upstream AGPL-3.0 license.

## Why the published module fails on Android 16

The official GitHub release is still v1.2 (2024-06-10), before PackageInstaller v2 / Android 16 support. Upstream `main` contains later Android 16 work, but that work was not published as a release.

## Changes in this overlay

- Harden PackageInstaller v2 hooks against current LineageOS 23.2 implementation.
- Hook the exact zero-argument `InstallationFragment.updateUI()` and `UninstallationFragment.updateUI()` methods.
- Read the current install/uninstall stage through the fragment getter first; avoid relying on `LiveData.mData` as the primary path.
- Prefer `InstallLaunch.installRepository` / `UninstallLaunch.uninstallRepository`; retain ViewModel/repository fallbacks.
- Prefer fragment `mAppSnippet`; retain resource-id fallback.
- Do not run the legacy Android Q hook in parallel with the Android 16 v2 hook.
- Android 16 v2 no longer depends on reflective `AssetManager.addAssetPath` resource injection. Module strings are loaded through `createPackageContext`.
- Fix host resource lookup for Google/AOSP/Lineage installer package names.
- Add duplicate-safe/update-safe injected TextView handling and place details directly below the app snippet.
- Remove stale details when the installer transitions away from the user-confirmation stage.
- Reflection helpers are null-safe and catch linkage/reflection errors more defensively.
- Compile/target SDK 36; Java 17; AGP 8.7.3; Kotlin 1.9.24; Gradle 8.9.
- Repair GitHub Actions SDK-path quoting and build/upload a release APK on API 36.
- Version label bumped to `2.0-A16`.
- Add `com.android.permissioncontroller` to optional LSPosed scope for ROM variants that host uninstall UI there.

## Apply

From a clean upstream clone:

```bash
cp -a InstallerPlus-A16-Lineage23.2-v2.0/. /path/to/InstallerPlus/
cd /path/to/InstallerPlus
./gradlew clean assembleRelease
```

Expected APK:

`app/build/outputs/apk/release/app-release.apk`

## LSPosed

Enable InstallerPlus for the system package installer. On LineageOS 23.2 this is normally `com.android.packageinstaller`, then force-stop/restart Package Installer or reboot.

## Verification

Open any APK through the system Package Installer. The confirmation page should show package name, version/version-code, target SDK, and (when available) user. Updating an installed package should show old -> new values.
