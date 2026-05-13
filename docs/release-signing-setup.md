# Release Signing Setup

This project signs Android, Windows, and macOS release artifacts from GitHub Actions. Add secrets in:

`GitHub repo -> Settings -> Secrets and variables -> Actions -> New repository secret`

## macOS Signing And Notarization

Required GitHub secrets:

- `MACOS_CERTIFICATE_BASE64`
- `MACOS_CERTIFICATE_PASSWORD`
- `MACOS_DEVELOPER_IDENTITY`
- `MACOS_KEYCHAIN_PASSWORD`
- `APPLE_ID`
- `APPLE_APP_SPECIFIC_PASSWORD`
- `APPLE_TEAM_ID`

### 1. Create A Developer ID Application Certificate

You need a paid Apple Developer Program account.

On your Mac:

1. Open Keychain Access.
2. Choose `Keychain Access -> Certificate Assistant -> Request a Certificate From a Certificate Authority`.
3. Enter your Apple ID email.
4. Choose `Saved to disk`.
5. Save the `.certSigningRequest` file.

In Apple Developer:

1. Open Certificates, Identifiers & Profiles.
2. Click `+`.
3. Choose `Developer ID Application`.
4. Upload the CSR.
5. Download the generated `.cer`.
6. Double-click the `.cer` to install it into Keychain.

Apple docs: https://developer.apple.com/help/account/create-certificates/create-developer-id-certificates/

### 2. Export The Certificate As P12

In Keychain Access:

1. Go to `login -> My Certificates`.
2. Find `Developer ID Application: Your Name (TEAMID)`.
3. Expand it and confirm a private key is underneath it.
4. Right-click the certificate.
5. Choose `Export`.
6. Save as `.p12`.
7. Set a strong export password.

Add that export password as:

```text
MACOS_CERTIFICATE_PASSWORD
```

### 3. Encode The P12

```sh
base64 -i path/to/developer-id-application.p12 -o macos-cert-base64.txt
```

Paste the contents of `macos-cert-base64.txt` into:

```text
MACOS_CERTIFICATE_BASE64
```

### 4. Get The Signing Identity

Run:

```sh
security find-identity -v -p codesigning
```

Use the value that looks like:

```text
Developer ID Application: Your Name (TEAMID)
```

Add it as:

```text
MACOS_DEVELOPER_IDENTITY
```

### 5. Get Apple Team ID

Open your Apple Developer account membership page and copy the Team ID.

Add it as:

```text
APPLE_TEAM_ID
```

Apple account: https://developer.apple.com/account/

### 6. Create An App-Specific Password

Open:

https://appleid.apple.com/

Then:

1. Sign in.
2. Open `Sign-In and Security`.
3. Open `App-Specific Passwords`.
4. Generate a password named `Phoebe GitHub Notarization`.

Add it as:

```text
APPLE_APP_SPECIFIC_PASSWORD
```

### 7. Add Apple ID Email

Add the Apple ID email used for notarization as:

```text
APPLE_ID
```

### 8. Generate A Temporary CI Keychain Password

```sh
openssl rand -base64 32
```

Add the generated value as:

```text
MACOS_KEYCHAIN_PASSWORD
```

## Windows MSI Signing

Windows signing uses Azure Artifact Signing with GitHub OIDC. No PFX file is stored in GitHub.

Required GitHub secrets:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_SIGNING_ENDPOINT`
- `AZURE_SIGNING_ACCOUNT_NAME`
- `AZURE_SIGNING_CERTIFICATE_PROFILE_NAME`

### 1. Create An Azure Artifact Signing Account

Follow the Azure Artifact Signing quickstart:

https://learn.microsoft.com/en-us/azure/artifact-signing/quickstart

Create or note:

- Artifact Signing account name
- Region-specific signing endpoint, for example `https://eus.codesigning.azure.net/`

Add these as:

```text
AZURE_SIGNING_ACCOUNT_NAME
AZURE_SIGNING_ENDPOINT
```

### 2. Complete Identity Validation

Complete the publisher identity validation required by Azure Artifact Signing.

### 3. Create A Certificate Profile

Create a certificate profile in the Artifact Signing account.

Add its name as:

```text
AZURE_SIGNING_CERTIFICATE_PROFILE_NAME
```

### 4. Create A Microsoft Entra App Registration

In Azure Portal:

1. Open Microsoft Entra ID.
2. Open App registrations.
3. Create a new registration for GitHub Actions.
4. Copy the Application/client ID.
5. Copy the Directory/tenant ID.

Add them as:

```text
AZURE_CLIENT_ID
AZURE_TENANT_ID
```

The workflow does not require `AZURE_SUBSCRIPTION_ID`; Azure login is configured with `allow-no-subscriptions: true` because signing only needs the OIDC identity and certificate-profile signer role.

### 5. Add A GitHub Federated Credential

On the app registration:

1. Open `Certificates & secrets`.
2. Open `Federated credentials`.
3. Add a credential for GitHub Actions.
4. Scope it to this repository and the `release` environment.

Use these values:

```text
Organization: j-roskopf
Repository: Phoebe
Entity type: Environment
Environment name: release
Audience: api://AzureADTokenExchange
```

That matches the Windows release job's GitHub environment:

```yaml
environment: release
```

GitHub docs:

https://docs.github.com/en/actions/security-for-github-actions/security-hardening-your-deployments/configuring-openid-connect-in-azure

### 6. Assign Signing Role

Assign the app registration this role scoped to the certificate profile:

```text
Artifact Signing Certificate Profile Signer
```

If `azure/login` reports `No subscriptions found`, keep `allow-no-subscriptions: true` in the workflow, which this repository does.

If it reports that a subscription does not exist, remove any `AZURE_SUBSCRIPTION_ID` secret or make sure the workflow is not passing `subscription-id`. This repository does not pass it.

The signing action still requires the certificate-profile signer role above.

Microsoft docs:

https://learn.microsoft.com/en-us/azure/trusted-signing/tutorial-assign-roles

### 7. Verify In GitHub Actions

Push a release tag that matches `phoebe.versionName` in `gradle.properties`.

The Windows job should:

1. Build `Phoebe-<version>.msi`.
2. Authenticate to Azure using OIDC.
3. Sign the MSI with Azure Artifact Signing.
4. Upload the signed MSI to the draft GitHub Release.
