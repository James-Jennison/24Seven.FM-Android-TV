#!/usr/bin/env python3
"""Verify the deliberately small Android TV app/site consistency contract."""

from __future__ import annotations

import argparse
from pathlib import Path


TV_REPOSITORY = "https://github.com/James-Jennison/24Seven.FM-Android-TV"


def read(path: Path) -> str:
    if not path.is_file():
        raise RuntimeError(f"Missing required file: {path}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, description: str, failures: list[str]) -> None:
    if needle not in text:
        failures.append(description)


def forbid(text: str, needle: str, description: str, failures: list[str]) -> None:
    if needle in text:
        failures.append(description)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site-root", required=True, type=Path, help="Player product-site checkout root")
    args = parser.parse_args()

    app_root = Path(__file__).resolve().parents[1]
    site_root = args.site_root.resolve()
    failures: list[str] = []

    service = read(app_root / "app/src/main/java/com/codeframe78/twentyfourseven/player/playback/RadioPlaybackService.kt")
    contract = read(app_root / "docs/TV_PRODUCT_CONTRACT.md")
    service_test = read(app_root / "app/src/androidTest/java/com/codeframe78/twentyfourseven/player/playback/RadioPlaybackServiceTest.kt")
    require(contract, "public track metadata (title, artist, and album)", "TV contract does not name rendered public track metadata.", failures)
    require(contract, "force-stop or process death clears any active countdown", "TV contract does not state the process-bound timer policy.", failures)
    forbid(service, "restoreSleepTimer", "Playback service still restores a process-dead timer.", failures)
    forbid(service, "sleepTimerPreferences", "Playback service still persists timer state.", failures)
    require(service_test, "activeTimerDoesNotRestoreWhenPlaybackServiceIsRecreated", "Instrumentation coverage does not assert process-bound timer behavior.", failures)

    tv_pages = {
        "TV landing": site_root / "privacy-site/tv/index.html",
        "TV features": site_root / "privacy-site/tv/features/index.html",
        "TV tester route": site_root / "privacy-site/tv/testing/index.html",
    }
    for label, path in tv_pages.items():
        page = read(path)
        require(page, f"project_github_url: {TV_REPOSITORY}", f"{label} does not target the TV repository.", failures)
        require(page, "verification_label: Internal testing is active. TV Closed track changes are in review with Google Play", f"{label} does not use the TV-specific delivery status.", failures)
        forbid(page, "M42", f"{label} contains mobile release vocabulary.", failures)

    for label in ("TV landing", "TV features"):
        page = read(tv_pages[label])
        require(page, "{{ '/tv/testing/' | relative_url }}", f"{label} does not route recruitment to the TV tester page.", failures)
        forbid(page, "'/product-testing/'", f"{label} still routes TV recruitment to mobile intake.", failures)

    tester = read(tv_pages["TV tester route"])
    for needle in (
        'name="products[]" value="android_tv"',
        'name="tvDevice"',
        'name="tvPlatformVersion"',
        'name="tvDeviceClass"',
        'value="dpad_navigation"',
        'value="sleep_timer"',
        'value="idle_visualizer"',
        'value="privacy_safe_diagnostics"',
    ):
        require(tester, needle, f"TV tester route is missing {needle}.", failures)
    for needle in ("accounts_favorites", "request_safety", "chat_community", "android_auto"):
        forbid(tester, needle, f"TV tester route solicits the mobile-only field {needle}.", failures)

    if failures:
        raise RuntimeError("\n".join(failures))
    print("TV cross-surface validation: valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
