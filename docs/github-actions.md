# GitHub Actions

## Pull Requests

Pull requests targeting `main` run:

- `./gradlew :composeApp:desktopTest`
- `./gradlew :composeApp:wasmJsTest`
- `./gradlew :composeApp:verifyRoborazziDebug`
- `npm run web:screenshots`
- `./gradlew :composeApp:compileDebugAndroidTestKotlinAndroid`
- `./gradlew :composeApp:connectedDebugAndroidTest` on a GitHub-hosted Android emulator
- A production-mode Wasm build deployed to the preview GitHub Pages site at `https://phoebe-test.joetr.com/` after web screenshots and Wasm tests pass

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

After the GitHub release is created successfully, the workflow builds the Wasm web distribution and deploys it to this repository's GitHub Pages site at `https://phoebe.joetr.com/`.

## GitHub Pages

Production uses this repository's GitHub Pages site:

- URL: `https://phoebe.joetr.com/`
- GitHub Pages source: GitHub Actions
- GitHub Pages custom domain: `phoebe.joetr.com`
- Workflow artifact path: `composeApp/build/dist/wasmJs/productionExecutable/`

PR previews use a separate GitHub repository with its own GitHub Pages site:

- URL: `https://phoebe-test.joetr.com/`
- Suggested repository: `phoebe-pages-preview`
- GitHub Pages source in that repository: deploy from branch `gh-pages` at `/`
- GitHub Pages custom domain in that repository: `phoebe-test.joetr.com`
- Workflow behavior: the PR workflow force-syncs the latest successful same-repository PR build into the preview repository's `gh-pages` branch

The production workflow deploys with GitHub Actions, so configure `phoebe.joetr.com` in this repository's Pages settings. The preview workflow deploys to a branch-based Pages site, so it writes `CNAME` and `.nojekyll` into the preview repository's `gh-pages` branch.

## DNS

Configure these records with the DNS provider for `joetr.com`:

```text
phoebe       CNAME  <github-pages-owner>.github.io
phoebe-test  CNAME  <github-pages-owner>.github.io
```

Replace `<github-pages-owner>` with the GitHub account or organization that owns the Pages repositories. For example, if the repositories live under `j-roskopf`, use:

```text
phoebe       CNAME  j-roskopf.github.io
phoebe-test  CNAME  j-roskopf.github.io
```

Do not keep old `A`, `AAAA`, or `CNAME` records for these two subdomains that point at the DigitalOcean droplet. A DNS name cannot have both a `CNAME` and other address records at the same time.

After changing DNS, verify:

```sh
dig +short phoebe.joetr.com
dig +short phoebe-test.joetr.com
```

## Pages Setup

Production repository:

1. Go to Settings -> Pages.
2. Set Build and deployment source to GitHub Actions.
3. Set Custom domain to `phoebe.joetr.com`.
4. Enable Enforce HTTPS once GitHub finishes provisioning the certificate.

Preview repository:

1. Create an empty repository, for example `phoebe-pages-preview`.
2. Go to Settings -> Pages.
3. Set Build and deployment source to Deploy from a branch.
4. Select branch `gh-pages` and folder `/`.
5. Set Custom domain to `phoebe-test.joetr.com`.
6. Enable Enforce HTTPS once GitHub finishes provisioning the certificate.

Create the `gh-pages` branch once if GitHub requires it before selecting it:

```sh
git clone git@github.com:<github-pages-owner>/phoebe-pages-preview.git
cd phoebe-pages-preview
git checkout --orphan gh-pages
printf 'phoebe-test.joetr.com\n' > CNAME
touch .nojekyll
git add CNAME .nojekyll
git commit -m "Initialize preview Pages branch"
git push origin gh-pages
```

## Secrets And Variables

Production GitHub Pages deploys do not need repository secrets. The release workflow uses `GITHUB_TOKEN` with `pages: write` and `id-token: write`.

Preview deploys need two repository secrets in this repository:

- Repository secret `PAGES_PREVIEW_DEPLOY_KEY`: private SSH deploy key with write access to the preview repository.
- Repository secret `PAGES_PREVIEW_REPOSITORY`: preview repository name in `owner/repo` form, for example `j-roskopf/phoebe-pages-preview`.

Create the preview deploy key locally:

```sh
ssh-keygen -t ed25519 -C "github-actions-phoebe-pages-preview" -f ~/.ssh/phoebe_pages_preview_deploy
```

When prompted for a passphrase, press Enter twice so the key can run unattended in CI.

Add the public key to the preview repository:

```sh
cat ~/.ssh/phoebe_pages_preview_deploy.pub
```

In the preview repository, go to Settings -> Deploy keys -> Add deploy key, paste the public key, and enable Allow write access.

Add the private key to this repository:

```sh
cat ~/.ssh/phoebe_pages_preview_deploy
```

In this repository, go to Settings -> Secrets and variables -> Actions -> New repository secret, and paste the full private key as `PAGES_PREVIEW_DEPLOY_KEY`.

Old SSH/Apache deploy secrets are no longer used by the GitHub Pages workflows:

- `WEB_DEPLOY_HOST`
- `WEB_DEPLOY_USER`
- `WEB_DEPLOY_SSH_KEY`
- `WEB_DEPLOY_KNOWN_HOSTS`
- `WEB_DEPLOY_PATH`
- `WEB_PREVIEW_DEPLOY_PATH`

## Signing Secrets

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

Signing secrets are only read during tag releases. PR checks do not require signing secrets.

Encode binary certificates locally with:

```sh
base64 -i path/to/file -o encoded.txt
```

Detailed macOS and Windows signing setup lives in `docs/release-signing-setup.md`.
