# GitHub Actions CI/CD Setup

This directory contains GitHub Actions workflows for automated building and testing.

## Workflow: Build APK (`build-apk.yml`)

Automatically builds the Telegram X APK with NSFW filter on every push and pull request.

### Features

- ✅ Builds debug APK automatically
- ✅ Attempts to build release APK (if keystore configured)
- ✅ Caches Gradle dependencies for faster builds
- ✅ Uploads APK artifacts for download
- ✅ Initializes all git submodules (TDLib, OpenSSL, WebRTC, etc.)
- ✅ Generates build summary with APK sizes

### Required Secrets

To use this workflow, you need to configure the following secrets in your GitHub repository:

1. Go to: `Settings` → `Secrets and variables` → `Actions`
2. Add the following secrets:

#### Required:
- **`TELEGRAM_API_ID`**: Your Telegram API ID
  - Get it from: https://core.telegram.org/api/obtaining_api_id
  - Format: numeric value (e.g., `94575`)

- **`TELEGRAM_API_HASH`**: Your Telegram API Hash
  - Get it from: https://core.telegram.org/api/obtaining_api_id
  - Format: hexadecimal string (e.g., `a3406de8d171bb422bb6ddf3bbd800e2`)

#### Optional (for release builds):
- **`KEYSTORE_FILE`**: Base64 encoded keystore file
  ```bash
  base64 -w 0 your-keystore.jks
  ```
- **`KEYSTORE_PASSWORD`**: Keystore password
- **`KEY_ALIAS`**: Key alias
- **`KEY_PASSWORD`**: Key password

### How to Use

#### Automatic Trigger
The workflow runs automatically on:
- Push to `copilot/add-nsfw-content-filter` or `main` branches
- Pull requests to `main` branch

#### Manual Trigger
1. Go to: `Actions` → `Build APK`
2. Click: `Run workflow`
3. Select branch and click: `Run workflow`

### Download APKs

After the workflow completes:

1. Go to the workflow run page
2. Scroll down to **Artifacts** section
3. Download:
   - `telegram-x-nsfw-debug.zip` - Debug APK (unsigned, for testing)
   - `telegram-x-nsfw-release.zip` - Release APK (if keystore configured)

### Build Time

- **First build**: ~15-30 minutes (downloads and builds all dependencies)
- **Subsequent builds**: ~5-10 minutes (uses cached dependencies)

### Troubleshooting

#### Build fails with "TELEGRAM_API_ID not found"
- Make sure you've added the `TELEGRAM_API_ID` and `TELEGRAM_API_HASH` secrets
- Secrets are case-sensitive

#### Submodules not initialized
- The workflow automatically runs `git checkout --recursive`
- If issues persist, check if `.gitmodules` file exists in the repository

#### Out of disk space
- GitHub Actions runners have limited disk space (~14GB free)
- The Telegram X build with all submodules uses ~5-8GB
- If this occurs, the workflow may need optimization

#### Release build fails
- This is expected if you haven't configured keystore secrets
- Debug APK will still be built successfully
- The workflow uses `continue-on-error: true` for release builds

### Local Testing

To test the workflow configuration locally:

```bash
# Install act (GitHub Actions local runner)
# https://github.com/nektos/act

# Run the workflow
act -j build
```

### Workflow Customization

To customize the workflow, edit `.github/workflows/build-apk.yml`:

- **Change trigger branches**: Modify the `on.push.branches` section
- **Add more build flavors**: Add more `assembleXXX` tasks
- **Configure signing**: Add keystore handling for release builds
- **Add tests**: Insert test tasks before the build steps

### Build Artifacts

The workflow produces:

1. **Debug APK**
   - Filename: `Telegram-X-[version]-[flavor]-debug.apk`
   - Signing: Android debug keystore
   - Use: Testing and development

2. **Release APK** (if keystore configured)
   - Filename: `Telegram-X-[version]-[flavor].apk`
   - Signing: Your release keystore
   - Use: Production distribution

### NSFW Model Note

The workflow includes the placeholder NSFW model from the repository. For production builds:

1. Replace `app/src/main/assets/models/nsfw_mobilenet.tflite` with a trained model
2. Commit the change
3. The next build will include the updated model

Model size impact on APK:
- Placeholder: +70KB
- Production model: +5-20MB

## Additional Workflows

You can add more workflows for:
- **Automated Testing**: Run unit and instrumentation tests
- **Code Quality**: Run linters and static analysis
- **Release Management**: Automatically create GitHub releases
- **Play Store Upload**: Upload to Google Play Console

Example workflow files available in the community.
