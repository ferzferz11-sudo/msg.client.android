#!/bin/bash
# release.sh — выпуск нового релиза Android клиента
#
# Использование:
#   ./release.sh <version>
#   Пример: ./release.sh 1.1.2.9
#
# Что делает:
# 1. Проверяет что version.txt совпадает с аргументом
# 2. Коммитит и пушит все изменения
# 3. Создаёт git tag v<version>
# 4. Деплоит APK на сервер (если APK уже собран)
# 5. Обновляет version.txt и versions.json на сервере
# 6. Создаёт GitHub Release с changelog и APK
#
# Требования:
# - APK уже собран и загружен на сервер: /var/www/lavender/lavender.apk
# - SSH доступ на сервер через alias "lava" (без пароля)
# - gh CLI для GitHub releases + авторизация (gh auth login)

set -e

VERSION="$1"
if [ -z "$VERSION" ]; then
  echo "❌ Укажи версию: ./release.sh 1.1.2.9"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
SERVER="lava"
SERVER_DIR="/var/www/lavender"
ARCHIVE_DIR="$SERVER_DIR/archive/android"
APK_PATH="$SERVER_DIR/lavender.apk"

echo "🚀 Выпуск релиза v$VERSION"
echo ""

# 1. Проверяем version.txt
CURRENT_VERSION=$(cat "$PROJECT_DIR/version.txt" | tr -d '[:space:]')
if [ "$CURRENT_VERSION" != "$VERSION" ]; then
  echo "❌ version.txt = $CURRENT_VERSION, ожидается $VERSION"
  echo "   Обнови version.txt перед релизом"
  exit 1
fi
echo "✅ version.txt = $VERSION"

# 2. Проверяем APK на сервере
APK_SIZE=$(ssh "$SERVER" "du -h $APK_PATH 2>/dev/null | cut -f1" 2>/dev/null)
if [ -z "$APK_SIZE" ]; then
  echo "❌ APK не найден на сервере: $APK_PATH"
  exit 1
fi
echo "✅ APK на сервере: $APK_SIZE"

# 3. Git commit & push
cd "$PROJECT_DIR"
if [ -n "$(git status --porcelain)" ]; then
  echo "→ Коммит незакоммиченных изменений..."
  git add -A
  git commit -m "release: v$VERSION" --allow-empty
  git push origin "$(git branch --show-current)"
  echo "✅ Изменения запушены"
else
  echo "✅ Нет незакоммиченных изменений"
fi

# 4. Git tag
if git tag -l "v$VERSION" | grep -q "v$VERSION"; then
  echo "⚠️  Tag v$VERSION уже существует, пропускаем"
else
  git tag "v$VERSION"
  git push origin "v$VERSION"
  echo "✅ Tag v$VERSION создан"
fi

# 5. Деплой на сервер (архив + version.txt + versions.json)
echo "→ Деплой на сервер..."
TODAY=$(date +%Y-%m-%d)

ssh "$SERVER" "bash -c '
  mkdir -p $ARCHIVE_DIR/$VERSION
  cp $APK_PATH $ARCHIVE_DIR/$VERSION/lavender.apk
  echo $VERSION > $SERVER_DIR/version.txt
'"

# Обновляем versions.json
ssh "$SERVER" "VERSION='$VERSION' TODAY='$TODAY' python3 << 'PYEOF'
import json, os, sys
v = os.environ['VERSION']
t = os.environ['TODAY']
os.chdir('/var/www/lavender/archive')
old = []
if os.path.exists('versions.json'):
    with open('versions.json', 'r') as f:
        old = json.load(f)
entry = {'version': v, 'date': t, 'client': {'android': '/archive/android/' + v + '/lavender.apk'}}
old = [e for e in old if e['version'] != v]
old.insert(0, entry)
with open('versions.json', 'w') as f:
    json.dump(old, f, indent=2, ensure_ascii=False)
print('  versions.json обновлён:', [e['version'] for e in old[:3]])
PYEOF"

echo "✅ Сервер обновлён"

# 6. GitHub Release с APK
if command -v gh &> /dev/null; then
  echo "→ Создание GitHub Release..."
  # Извлекаем changelog для этой версии
  CHANGELOG=$(awk "/^## \\[$VERSION\\]/{flag=1; next} /^## \\[/{flag=0} flag" "$PROJECT_DIR/CHANGELOG.md" | sed '/^$/d' | head -30)
  if [ -z "$CHANGELOG" ]; then
    CHANGELOG="См. CHANGELOG.md"
  fi

  # Создаём release и прикрепляем APK
  cd "$PROJECT_DIR"
  gh release create "v$VERSION" \
    "$APK_PATH" \
    --title "Lavender Android v$VERSION" \
    --notes "$CHANGELOG" \
    2>/dev/null || {
      echo "  Release уже существует, пробуем загрузить APK..."
      gh release upload "v$VERSION" "$APK_PATH" --clobber 2>/dev/null || echo "⚠️  Не удалось загрузить APK"
    }
  echo "✅ GitHub Release создан с APK"
else
  echo "⚠️  gh CLI не найден, GitHub Release пропущен"
  echo "   Создай вручную: https://github.com/ferzferz11-sudo/msg.client.android/releases/new"
  echo "   И прикрепи APK: $APK_PATH"
fi

echo ""
echo "🎉 Релиз v$VERSION готов!"
echo "   APK:   http://13.140.25.249/download"
echo "   Архив: http://13.140.25.249/archive"
echo "   Tag:   https://github.com/ferzferz11-sudo/msg.client.android/releases/tag/v$VERSION"
