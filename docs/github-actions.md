# GitHub Actions

## Pull Requests

Pull requests targeting `main` run:

- `./gradlew :composeApp:desktopTest`
- `./gradlew :composeApp:wasmJsTest`
- `./gradlew :composeApp:verifyRoborazziDebug`
- `npm run web:screenshots`
- `./gradlew :composeApp:compileDebugAndroidTestKotlinAndroid`
- `./gradlew :composeApp:connectedDebugAndroidTest` on a GitHub-hosted Android emulator

Screenshot failures upload Roborazzi and Playwright reports as workflow artifacts so the expected, actual, and diff images can be reviewed from the failed check.

Update screenshot baselines locally with:

```sh
./gradlew :composeApp:recordRoborazziDebug
./gradlew :composeApp:recordRoborazziDesktop
npm run web:screenshots:update
```

Verify screenshot baselines locally with:

```sh
./gradlew :composeApp:verifyRoborazziDebug
./gradlew :composeApp:desktopTest
npm run web:screenshots
```

## Releases

Create a tag named `release/x.x.x`, for example:

```sh
git tag release/1.2.3
git push origin release/1.2.3
```

The release version comes from `gradle.properties`:

```properties
phoebe.versionName=1.2.3
phoebe.versionCode=1002003
```

The release workflow requires the pushed tag to match `phoebe.versionName`, so `phoebe.versionName=1.2.3` must be released with tag `release/1.2.3`. It validates that the version is plain semver, uses `phoebe.versionCode` for Android, builds Android, Linux, Windows, and macOS packages, renames the generated binaries to include `phoebe.versionName`, then creates a draft GitHub release with the generated APK, AAB, DEB, Flatpak bundle, MSI, and DMG assets attached.

## Secrets

Android signing:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded `.jks` or `.keystore` file
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Windows MSI signing uses Azure Artifact Signing with GitHub OIDC:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_SIGNING_ENDPOINT`, for example `https://eus.codesigning.azure.net/`
- `AZURE_SIGNING_ACCOUNT_NAME`
- `AZURE_SIGNING_CERTIFICATE_PROFILE_NAME`

Azure setup:

1. Create an Azure Artifact Signing account.
2. Complete identity validation.
3. Create a certificate profile.
4. Create a Microsoft Entra app registration for GitHub Actions.
5. Add a federated credential for this repository's release workflow.
6. Assign the app registration the `Artifact Signing Certificate Profile Signer` role scoped to the certificate profile.
7. Add the Azure values above as GitHub Actions repository secrets.

Useful links:

- Azure Artifact Signing quickstart: https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart
- Artifact Signing role assignment: https://learn.microsoft.com/en-us/azure/trusted-signing/tutorial-assign-roles
- Artifact Signing GitHub Actions integration: https://github.com/Azure/artifact-signing-action
- GitHub OIDC with Azure: https://docs.github.com/en/actions/security-for-github-actions/security-hardening-your-deployments/configuring-openid-connect-in-azure

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

Detailed macOS and Windows signing setup lives in `docs/release-signing-setup.md`.
