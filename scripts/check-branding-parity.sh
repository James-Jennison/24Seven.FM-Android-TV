#!/usr/bin/env bash
# Read-only check that the TV app's shared brand assets still match the mobile Player's.
# Usage: scripts/check-branding-parity.sh <mobile-player-checkout>
# Exit codes: 0 all shared files identical, 1 a file differs or is missing, 2 usage error.
set -euo pipefail

mobile=${1:-}
[[ -n $mobile && -d $mobile/app/src/main ]] || { echo "usage: $0 <mobile-player-checkout>" >&2; exit 2; }
tv=$(cd "$(dirname "$0")/.." && pwd)

shared=(
  res/drawable/app_icon_foreground.xml
  res/drawable/app_icon_monochrome.xml
  res/drawable-nodpi/app_logo.png
  res/mipmap-anydpi-v26/ic_launcher.xml
  res/mipmap-anydpi-v33/ic_launcher.xml
  res/mipmap-hdpi/ic_launcher.png
  res/mipmap-mdpi/ic_launcher.png
  res/mipmap-xhdpi/ic_launcher.png
  res/mipmap-xxhdpi/ic_launcher.png
  res/mipmap-xxxhdpi/ic_launcher.png
  res/values/colors.xml
  java/com/codeframe78/twentyfourseven/player/ui/theme/RadioTheme.kt
)

status=0
for file in "${shared[@]}"; do
  if [[ ! -f $tv/app/src/main/$file || ! -f $mobile/app/src/main/$file ]]; then
    echo "MISSING $file"; status=1
  elif cmp -s "$tv/app/src/main/$file" "$mobile/app/src/main/$file"; then
    echo "SAME    $file"
  else
    echo "DIFF    $file"; status=1
  fi
done
((status == 0)) && echo "branding-parity=passed (${#shared[@]} files)" || echo "branding-parity=failed" >&2
exit $status
