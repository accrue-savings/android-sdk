To be able to access local FFW from simulator, run this commands:
adb reverse tcp:3001 tcp:3001
adb reverse tcp:5173 tcp:5173

## Releasing a new SDK version

### Automated Release (Recommended)

The SDK uses an automated release workflow via GitHub Actions. Simply push to `main` branch and the workflow will:

- Automatically determine the next version based on commit messages
- Update version in build.gradle.kts and AccrueContextData.kt
- Publish the package
- Create a GitHub release

You can also trigger a manual release with a specific version type:

1. Go to Actions tab in GitHub
2. Select "Release Android SDK" workflow
3. Click "Run workflow" and choose version type (patch/minor/major)

### Manual Release (Fallback)

If you need to publish manually:

1. bump the version in build.gradle.kts from the sdk folder
2. run ./gradlew publish from root
