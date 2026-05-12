# GitHub Actions

## Pull Requests

Pull requests targeting `main` run:

- `./gradlew :composeApp:desktopTest`
- `./gradlew :composeApp:wasmJsTest`
- `./gradlew :composeApp:compileDebugAndroidTestKotlinAndroid`
- `./gradlew :composeApp:connectedDebugAndroidTest` on a GitHub-hosted Android emulator

## Releases

Create a tag named `release/x.x.x`, for example:

```sh
git tag release/1.2.3
git push origin release/1.2.3
```

The release workflow validates that the tag is plain semver with a major version greater than `0`, derives Android `versionCode` from it, builds Android, Linux, Windows, and macOS packages, then creates a draft GitHub release with the generated assets. The major version requirement comes from Compose Desktop's macOS package version rules.

## Secrets

Android signing:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded `.jks` or `.keystore` file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Windows installer signing:

- `WINDOWS_CERTIFICATE_BASE64`: base64-encoded `.pfx` certificate
- `WINDOWS_CERTIFICATE_PASSWORD`

macOS signing and notarization:

- `MACOS_CERTIFICATE_BASE64`: base64-encoded Developer ID Application `.p12` certificate
- `MACOS_CERTIFICATE_PASSWORD`
- `MACOS_DEVELOPER_IDENTITY`: certificate identity, for example `Developer ID Application: Name (TEAMID)`
- `MACOS_KEYCHAIN_PASSWORD`: a random password used only for the temporary CI keychain
- `APPLE_ID`: Apple ID used for notarization
- `APPLE_APP_SPECIFIC_PASSWORD`: app-specific password for the Apple ID
- `APPLE_TEAM_ID`

Signing secrets are only read during tag releases. PR checks do not require secrets.

Encode binary certificates locally with:

```sh
base64 -i path/to/file -o encoded.txt
```
