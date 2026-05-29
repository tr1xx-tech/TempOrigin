#!/bin/bash
# OriginOS DSU Space - Automated Android Compilation Bootstrapper
# This script installs Android Commandline Tools and Gradle, then builds the APK.
set -e

echo "========================================================="
echo "   TempOrigin - Automated compilation bootstrap   "
echo "========================================================="

# 1. Download & configure Gradle standalone
echo -e "\n[1/7] Checking Gradle Standalone..."
if [ ! -d "/opt/gradle/gradle-8.5" ]; then
    echo "Downloading Gradle 8.5 binary archive..."
    mkdir -p /opt/gradle
    wget -q https://services.gradle.org/distributions/gradle-8.5-bin.zip -O /tmp/gradle.zip
    echo "Extracting Gradle 8.5..."
    python3 -c "import zipfile; zipfile.ZipFile('/tmp/gradle.zip').extractall('/opt/gradle')"
    rm -f /tmp/gradle.zip
    echo "Gradle 8.5 bootstrapped successfully."
else
    echo "Gradle 8.5 is already installed."
fi

chmod +x /opt/gradle/gradle-8.5/bin/* || true

# Set temporary path environment for local session
export PATH=$PATH:/opt/gradle/gradle-8.5/bin

# 2. Download & configure Android SDK Commandline Tools
echo -e "\n[2/7] Checking Android SDK Commandline Tools..."
if [ ! -d "/root/Android/Sdk/cmdline-tools/latest" ]; then
    echo "Downloading Android SDK cmdline-tools zip..."
    mkdir -p /root/Android/Sdk/cmdline-tools
    # official google android build command line tools
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdline-tools.zip
    echo "Extracting tools..."
    mkdir -p /tmp/cmdline-tools-extracted
    python3 -c "import zipfile; zipfile.ZipFile('/tmp/cmdline-tools.zip').extractall('/tmp/cmdline-tools-extracted')"
    
    # Modern directory layout expects cmdline-tools/latest/bin/sdkmanager
    mv /tmp/cmdline-tools-extracted/cmdline-tools /root/Android/Sdk/cmdline-tools/latest
    rm -rf /tmp/cmdline-tools-extracted /tmp/cmdline-tools.zip
    echo "Android SDK cmdline-tools successfully installed."
else
    echo "Android SDK cmdline-tools is already installed."
fi

SDK_MANAGER="/root/Android/Sdk/cmdline-tools/latest/bin/sdkmanager"
chmod +x "$SDK_MANAGER" || true
chmod +x /root/Android/Sdk/cmdline-tools/latest/bin/* || true

# 3. Accept Google Android Licenses
echo -e "\n[3/7] Accepting Google SDK Licenses..."
yes | $SDK_MANAGER --licenses --sdk_root=/root/Android/Sdk

# 4. Download SDK Platform 34 and Build Tools 34.0.0
echo -e "\n[4/7] Installing Android API 34 SDK Platform & Build Tools..."
$SDK_MANAGER --sdk_root=/root/Android/Sdk "platforms;android-34" "build-tools;34.0.0"

# 5. Dynamic local.properties config
echo -e "\n[5/7] Configuring local.properties..."
echo "sdk.dir=/root/Android/Sdk" > /root/coding/TempOrigin/local.properties
echo "Wrote local.properties successfully."

# 6. Initialize Gradle Wrapper in project
echo -e "\n[6/7] Generating Gradle Wrapper for the project..."
cd /root/coding/TempOrigin
gradle wrapper

# 7. Compile APK
echo -e "\n[7/7] Starting DSU Helper APK compilation (Release mode with R8)..."
./gradlew --stop
./gradlew assembleRelease --no-daemon

echo -e "\n========================================================="
echo "                  COMPILATION COMPLETE!                  "
echo "========================================================="

APK_PATH="/root/coding/TempOrigin/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK_PATH" ]; then
    echo -e "\n[SUCCESS] Optimized Release APK was successfully compiled and is located at:"
    echo -e "   \033[1;32m$APK_PATH\033[0m"
    echo -e "\nYou can easily download this app-release.apk to your phone using SFTP, Termux, or an SSH app file manager (like Termius/JuiceSSH) and install it immediately!"
else
    echo -e "\n[ERROR] Compilation completed, but output APK file could not be found at target destination."
    exit 1
fi
