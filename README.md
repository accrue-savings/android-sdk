To be able to access local FFW from simulator, run this commands:
adb reverse tcp:3001 tcp:3001
adb reverse tcp:5173 tcp:5173

When you want to publish new SDK version:
1. bump the version in build.gradle.kts from the sdk folder
2. run ./gradlew publish from root
