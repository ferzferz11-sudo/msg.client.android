#!/bin/bash

# Lavender Messenger Android Deployment Script (Run from client/android)
# This script uploads the compiled APK and version info to the remote server.

REMOTE_USER="ferz"
REMOTE_HOST="159.195.38.145"
REMOTE_PORT="31703"
REMOTE_KEY="$HOME/.ssh/ferzz@x-cart.com"
APK_REMOTE_DIR="/home/ferz/LavenderMessengerAndroid"

# Paths are now relative to the client/android directory
APK_LOCAL="./app/build/outputs/apk/release/app-release.apk"
VERSION_LOCAL="./version.txt"
CHANGELOG_LOCAL="./changelog.txt"
METADATA_LOCAL="./app/build/outputs/apk/release/output-metadata.json"

echo "🚀 Starting Android deployment to $REMOTE_HOST..."

# 1. Build the release APK
echo "🔨 Building Release APK..."
./gradlew assembleRelease
if [ $? -ne 0 ]; then
    echo "❌ Build failed! Please check Android Studio for errors."
    exit 1
fi

if [ ! -f "$APK_LOCAL" ]; then
    echo "❌ Local build not found at $APK_LOCAL"
    echo "   Please build the release version in Android Studio first."
    exit 1
fi

# 1. Sync individual files
echo "📱 Uploading APK, version info and changelog..."
rsync -avz -e "ssh -p $REMOTE_PORT -i $REMOTE_KEY" \
    "$APK_LOCAL" "$VERSION_LOCAL" "$CHANGELOG_LOCAL" "$METADATA_LOCAL" \
    $REMOTE_USER@$REMOTE_HOST:$APK_REMOTE_DIR/

# 2. Rename APK to standard name for download and set permissions
echo "🔄 Finalizing on server..."
ssh -p $REMOTE_PORT -i $REMOTE_KEY $REMOTE_USER@$REMOTE_HOST \
    "mv $APK_REMOTE_DIR/app-release.apk $APK_REMOTE_DIR/lavender.apk && chmod 644 $APK_REMOTE_DIR/lavender.apk $APK_REMOTE_DIR/version.txt $APK_REMOTE_DIR/changelog.txt"

# 3. Sync baseline profiles if they exist
if [ -d "./app/src/main/baselineProfiles" ]; then
    echo "📦 Syncing baseline profiles..."
    rsync -avz -e "ssh -p $REMOTE_PORT -i $REMOTE_KEY" \
        "./app/src/main/baselineProfiles" \
        $REMOTE_USER@$REMOTE_HOST:$APK_REMOTE_DIR/
fi

echo "✅ Android deployment successful!"
echo "🔗 Download URL: http://$REMOTE_HOST:8081/lavender.apk"
echo "📝 Version: $(cat $VERSION_LOCAL)"
