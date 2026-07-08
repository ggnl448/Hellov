name: Build APK

# Runs automatically on every push to main, and can also be triggered
# manually from the "Actions" tab in GitHub ("Run workflow" button).
on:
  push:
    branches: [ "main" ]
  workflow_dispatch: {}

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Set up Gradle 8.7
        # The GitHub runner's preinstalled Gradle (9.x) is not compatible with
        # AGP 8.4.0, which caused "Plugin [id: 'com.android.application'] was
        # not found" errors. This pins Gradle to a version AGP 8.4.0 supports.
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '8.7'

      - name: Generate Gradle wrapper
        run: gradle wrapper --gradle-version 8.7

      - name: Grant execute permission to gradlew
        run: chmod +x ./gradlew

      - name: Build debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk
          path: app/build/outputs/apk/debug/*.apk
