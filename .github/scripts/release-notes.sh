#!/usr/bin/env bash
# 從 CHANGELOG.md 擷取指定版本的段落作為 Release 說明。
# 不含「升級後需手動確認的事項」（發版前的內部清單）與發版前提示。
# 用法：release-notes.sh <版本，例如 2.0.0> [CHANGELOG 路徑]
set -euo pipefail
version="$1"
changelog="${2:-CHANGELOG.md}"

notes=$(awk -v hdr="## [${version}]" '
  index($0, "## [") == 1      { in_ver = (index($0, hdr) == 1); skip = 0; next }
  /^\[[^]]+\]: /              { in_ver = 0 }
  !in_ver                     { next }
  /^### 升級後需手動確認的事項/ { skip = 1; next }
  /^### /                     { skip = 0 }
  skip                        { next }
  /^> 發版前/                  { next }
  { print }
' "$changelog")

if [ -z "$(printf '%s' "$notes" | tr -d '[:space:]')" ]; then
  echo "CHANGELOG.md 中找不到版本 ${version} 的段落" >&2
  exit 1
fi
printf '%s\n' "$notes" | sed -e '/./,$!d'
