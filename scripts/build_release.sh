#!/bin/bash
# build_release.sh — сборка release APK для Lavender
# Использование:
#   ./scripts/build_release.sh          # release (signed если есть keystore)
#   ./scripts/build_release.sh debug    # debug сборка (без signing, для теста)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

BUILD_TYPE="${1:-release}"
GRADLE_OPTS="${GRADLE_OPTS:--Xmx512m}"

echo "========================================="
echo " Lavender Android — сборка $BUILD_TYPE"
echo " Gradle opts: $GRADLE_OPTS"
echo "========================================="

# Проверяем version.txt
VERSION_FILE="version.txt"
if [ ! -f "$VERSION_FILE" ]; then
  echo "❌ version.txt не найден в $PROJECT_DIR"
  exit 1
fi
VERSION=$(cat "$VERSION_FILE" | tr -d '[:space:]')
echo "📌 Версия: $VERSION"

# Проверяем keystore для release
KEYSTORE="app/release.keystore"
if [ "$BUILD_TYPE" = "release" ] && [ ! -f "$KEYSTORE" ]; then
  echo ""
  echo "⚠️  release.keystore не найден ($KEYSTORE)"
  echo "   Сборка release будет БЕЗ подписи (не для Google Play)."
  echo "   Для подписи положи release.keystore в app/release.keystore"
  echo "   или используй: ./scripts/build_release.sh debug"
  echo ""
  read -p "Продолжить без подписи? (y/N) " -n 1 -r
  echo
  if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Отменено. Сгенерируй keystore:"
    echo "  keytool -genkey -v -keystore app/release.keystore -alias lavender -keyalg RSA -keysize 2048 -validity 10000"
    exit 1
  fi
fi

# Сборка
echo "🔨 Сборка..."
export GRADLE_OPTS
./gradlew "assemble${BUILD_TYPE^}" --no-daemon 2>&1 | tail -20

# Ищем собранный APK
APK_DIR="app/build/outputs/apk/$BUILD_TYPE"
APK_NAME="app-${BUILD_TYPE}.apk"
APK_PATH="$APK_DIR/$APK_NAME"

# Альтернативные имена APK
if [ ! -f "$APK_PATH" ]; then
  APK_PATH=$(find "$APK_DIR" -name "*.apk" -type f 2>/dev/null | head -1)
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
  # Ищем в корне outputs
  APK_PATH=$(find "app/build/outputs" -name "*.apk" -type f 2>/dev/null | head -1)
fi

if [ -z "$APK_PATH" ] || [ ! -f "$APK_PATH" ]; then
  echo "❌ APK не найден после сборки"
  echo "   Искал в: $APK_DIR"
  exit 1
fi

APK_SIZE=$(du -h "$APK_PATH" | cut -f1)

echo ""
echo "========================================="
echo "✅ Сборка завершена!"
echo "   APK:    $APK_PATH"
echo "   Размер: $APK_SIZE"
echo "========================================="

# Проверка версии в APK
AAPT=$(which aapt2 2>/dev/null || which aapt 2>/dev/null || find ~/Library/Android -name "aapt2" -type f 2>/dev/null | head -1 2>/dev/null)
if [ -n "$AAPT" ]; then
  DETECTED=$($AAPT dump badging "$APK_PATH" 2>/dev/null | grep "versionName" | sed "s/.*versionName='\([^']*\)'.*/\1/")
  if [ -n "$DETECTED" ]; then
    echo "   Версия: $DETECTED"
    if [ "$DETECTED" != "$VERSION" ]; then
      echo "   ⚠️  Версия в APK ($DETECTED) не совпадает с version.txt ($VERSION)!"
    fi
  fi
else
  echo "   (aapt2 не найден — проверь версию вручную)"
fi

echo ""

# Подписан ли APK
if command -v apksigner &>/dev/null || [ -f "$(find ~/Library/Android -name apksigner 2>/dev/null | head -1)" ]; then
  APKSIGNER=$(which apksigner 2>/dev/null || find ~/Library/Android -name "apksigner" -type f 2>/dev/null | head -1)
  if $APKSIGNER verify --print-certs "$APK_PATH" 2>/dev/null | grep -q "Verified"; then
    echo "🔐 APK подписан: Да"
  else
    echo "🔐 APK подписан: Нет (debug или без подписи)"
  fi
  echo ""
fi

echo "Для деплоя на сервер:"
echo "  ./scripts/deploy_android.sh $APK_PATH"
echo ""
