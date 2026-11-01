#!/usr/bin/env bash
# Read-only check that the TV app's shared brand assets match the mobile Player at a given git ref.
# The mobile files are read with `git show <ref>:<path>`; the mobile working tree, index and
# checked-out branch are never read or modified, so uncommitted mobile work cannot skew the result.
#
# The TV side is the TV working tree, so the script also prints the TV commit and refuses to report a pass while
# app/src/main has uncommitted changes (a pass must describe a commit). --allow-dirty skips that for local iteration.
#
# Usage: scripts/check-branding-parity.sh [--allow-dirty] <mobile-player-git-repo> [ref=main]
# Exit codes: 0 all shared files identical at the ref, 1 a file differs or is missing (or the TV tree is dirty),
# 2 usage error.
set -euo pipefail

allow_dirty=0
if [[ ${1:-} == --allow-dirty ]]; then allow_dirty=1; shift; fi
mobile=${1:-}
ref=${2:-main}
[[ -n $mobile ]] || { echo "usage: $0 [--allow-dirty] <mobile-player-git-repo> [ref=main]" >&2; exit 2; }
git -C "$mobile" rev-parse --git-dir >/dev/null 2>&1 || { echo "not a git repository: $mobile" >&2; exit 2; }
tv=$(cd "$(dirname "$0")/.." && pwd)
commit=$(git -C "$mobile" rev-parse --verify --quiet "$ref^{commit}") || { echo "unknown ref: $ref" >&2; exit 2; }
echo "mobile ref $ref = $commit"
tv_head=$(git -C "$tv" rev-parse --verify --quiet HEAD) || { echo "TV tree is not a git repository" >&2; exit 2; }
echo "tv HEAD = $tv_head"
if [[ -n $(git -C "$tv" status --porcelain -- app/src/main) ]] && ((allow_dirty == 0)); then
  echo "TV working tree has uncommitted changes under app/src/main; commit them or pass --allow-dirty" >&2
  exit 1
fi

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
  res/values-night/colors.xml
  res/drawable/splash_icon.xml
  res/drawable/splash_icon_animated.xml
  res/drawable-xxhdpi/splash_branding.png
  res/animator/splash_arc_inner.xml
  res/animator/splash_arc_inner_alpha.xml
  res/animator/splash_arc_outer.xml
  res/animator/splash_arc_outer_alpha.xml
  res/animator/splash_dot.xml
  res/animator/splash_dot_alpha.xml
  res/animator/splash_ping_alpha.xml
  res/animator/splash_ping_scale.xml
  res/animator/splash_v_alpha.xml
  res/animator/splash_v_scale.xml
  java/com/codeframe78/twentyfourseven/player/ui/theme/RadioTheme.kt
)

status=0
for file in "${shared[@]}"; do
  path=app/src/main/$file
  if [[ ! -f $tv/$path ]] || ! git -C "$mobile" cat-file -e "$commit:$path" 2>/dev/null; then
    echo "MISSING $file"; status=1
  elif git -C "$mobile" show "$commit:$path" | cmp -s - "$tv/$path"; then
    echo "SAME    $file"
  else
    echo "DIFF    $file"; status=1
  fi
done
if ((status == 0)); then echo "branding-parity=passed (${#shared[@]} files at $commit)"; else echo "branding-parity=failed at $commit" >&2; fi
exit $status
