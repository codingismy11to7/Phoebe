# GitHub Actions

## Pull Requests

Pull requests targeting `main` run:

- `./gradlew :composeApp:desktopTest`
- `./gradlew :composeApp:wasmJsTest`
- `./gradlew :composeApp:verifyRoborazziDebug`
- `npm run web:screenshots`
- `./gradlew :composeApp:compileDebugAndroidTestKotlinAndroid`
- `./gradlew :composeApp:connectedDebugAndroidTest` on a GitHub-hosted Android emulator
- A production-mode Wasm build deployed to `https://phoebe-test.joetr.com/` after web screenshots and Wasm tests pass

Screenshot failures upload Roborazzi and Playwright reports as workflow artifacts so the expected, actual, and diff images can be reviewed from the failed check.

The PR web preview deploy runs only for pull requests from this repository, because GitHub does not expose repository secrets to forked pull requests. `phoebe-test.joetr.com` is a shared preview URL; the newest successful same-repository PR deploy wins.

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

After the GitHub release is created successfully, the workflow builds the Wasm web distribution and deploys it to `https://phoebe.joetr.com/` over SSH with `rsync --delete`.

Web deployment targets:

- DNS: point `phoebe.joetr.com` at the web server with an `A`/`AAAA` record.
- DNS: point `phoebe-test.joetr.com` at the web server with an `A`/`AAAA` record.
- Production Apache document root: `/var/www/phoebe.joetr.com`
- PR preview Apache document root: `/var/www/phoebe-test.joetr.com`
- Release build output: `composeApp/build/dist/wasmJs/productionExecutable/`

Production Apache virtual host:

```apache
<VirtualHost *:80>
    ServerName phoebe.joetr.com
    DocumentRoot /var/www/phoebe.joetr.com

    <Directory /var/www/phoebe.joetr.com>
        Require all granted
        Options -Indexes
        AllowOverride None
        FallbackResource /index.html
    </Directory>

    AddType application/wasm .wasm
</VirtualHost>
```

PR preview Apache virtual host:

```apache
<VirtualHost *:80>
    ServerName phoebe-test.joetr.com
    DocumentRoot /var/www/phoebe-test.joetr.com

    <Directory /var/www/phoebe-test.joetr.com>
        Require all granted
        Options -Indexes
        AllowOverride None
        FallbackResource /index.html
    </Directory>

    AddType application/wasm .wasm
</VirtualHost>
```

Server setup:

```sh
sudo mkdir -p /var/www/phoebe.joetr.com
sudo mkdir -p /var/www/phoebe-test.joetr.com
sudo chown -R deploy:deploy /var/www/phoebe.joetr.com
sudo chown -R deploy:deploy /var/www/phoebe-test.joetr.com
sudo a2ensite phoebe.joetr.com.conf
sudo a2ensite phoebe-test.joetr.com.conf
sudo systemctl reload apache2
sudo certbot --apache -d phoebe.joetr.com
sudo certbot --apache -d phoebe-test.joetr.com
```

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

Web deployment:

- `WEB_DEPLOY_HOST`: server hostname or IP address
- `WEB_DEPLOY_USER`: SSH user that owns `/var/www/phoebe.joetr.com`, for example `deploy`
- `WEB_DEPLOY_SSH_KEY`: private SSH key for the deploy user
- `WEB_DEPLOY_KNOWN_HOSTS`: server host key, for example from `ssh-keyscan phoebe.joetr.com`
- `WEB_DEPLOY_PATH`: `/var/www/phoebe.joetr.com`
- `WEB_PREVIEW_DEPLOY_PATH`: `/var/www/phoebe-test.joetr.com`

Signing secrets are only read during tag releases. Web deploy secrets are read during tag releases and same-repository pull request previews.

Encode binary certificates locally with:

```sh
base64 -i path/to/file -o encoded.txt
```

Detailed macOS and Windows signing setup lives in `docs/release-signing-setup.md`.
