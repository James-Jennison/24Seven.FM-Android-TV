from __future__ import annotations

import os
from pathlib import Path
import subprocess
import tempfile
import textwrap
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "wait-for-focused-window.sh"
PKG = "com.codeframe78.twentyfourseven.tv.debug"
ACT = "com.codeframe78.twentyfourseven.player.MainActivity"


def focus_line(component: str) -> str:
    return f"  mCurrentFocus=Window{{ee6f847 u0 {component}}}\n"


class WaitForFocusedWindowTest(unittest.TestCase):
    def run_script(self, focus_sequence, *args, model="Google TV Streamer", timeout="3", dumpsys_fail=False):
        """Fake adb replays `focus_sequence` (one entry per dumpsys call, last one repeats)."""
        with tempfile.TemporaryDirectory() as tmp:
            tmp_path = Path(tmp)
            (tmp_path / "seq").write_text("\n".join(focus_sequence) + "\n")
            adb = tmp_path / "adb"
            adb.write_text(textwrap.dedent("""\
                #!/usr/bin/env bash
                shift 2  # -s <serial>
                if [[ $1 == shell && $2 == getprop ]]; then echo "$FAKE_MODEL"; exit 0; fi
                if [[ $1 == shell && $2 == dumpsys ]]; then
                  if [[ -n ${FAKE_DUMPSYS_FAIL:-} ]]; then echo "error: device offline" >&2; exit 1; fi
                  n=$(cat "$FAKE_TMP/n" 2>/dev/null || echo 0); echo $((n+1)) > "$FAKE_TMP/n"
                  total=$(wc -l < "$FAKE_TMP/seq")
                  line=$(sed -n "$(( n < total ? n+1 : total ))p" "$FAKE_TMP/seq")
                  [[ $line == NULL ]] && echo '  mCurrentFocus=null' || printf '%b\\n' "$line"
                  exit 0
                fi
                exit 1
            """))
            adb.chmod(0o755)
            env = {**os.environ, "PATH": f"{tmp}:{os.environ['PATH']}", "FAKE_MODEL": model, "FAKE_TMP": tmp}
            if dumpsys_fail:
                env["FAKE_DUMPSYS_FAIL"] = "1"
            return subprocess.run(
                [str(SCRIPT), "--device", "SERIAL", "--package", PKG, "--timeout", timeout, *args],
                env=env, capture_output=True, text=True, timeout=30,
            )

    def test_ready_after_delayed_focus_runs_command(self):
        launcher = focus_line("com.google.android.apps.tv.launcherx/Home").strip()
        app = focus_line(f"{PKG}/{ACT}").strip()
        result = self.run_script(["NULL", launcher, app], "--activity", ACT, "--", "echo", "SENT")
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("SENT", result.stdout)
        self.assertIn("focus-wait=ready", result.stderr)

    def test_input_withheld_on_timeout(self):
        result = self.run_script(["NULL"], "--", "echo", "SENT", timeout="1")
        self.assertEqual(result.returncode, 4)
        self.assertNotIn("SENT", result.stdout)
        self.assertIn("focus-wait=failed", result.stderr)

    def test_other_app_focus_is_not_ready(self):
        other = focus_line("com.google.android.apps.tv.launcherx/Home").strip()
        result = self.run_script([other], timeout="1")
        self.assertEqual(result.returncode, 4)

    def test_wrong_activity_is_not_ready(self):
        app = focus_line(f"{PKG}/com.example.Other").strip()
        result = self.run_script([app], "--activity", ACT, timeout="1")
        self.assertEqual(result.returncode, 4)

    def test_wrong_device_model_rejected_before_waiting(self):
        app = focus_line(f"{PKG}/{ACT}").strip()
        result = self.run_script([app], "--expect-model", "Google TV Streamer", model="LCARS_PADD")
        self.assertEqual(result.returncode, 3)

    def test_adb_failure_exits_5_promptly(self):
        result = self.run_script(["NULL"], timeout="10", dumpsys_fail=True)
        self.assertEqual(result.returncode, 5)
        self.assertIn("device offline", result.stderr)

    def test_unparseable_dumpsys_warns_and_times_out(self):
        result = self.run_script(["  something=else"], timeout="1")
        self.assertEqual(result.returncode, 4)
        self.assertIn("no mCurrentFocus line", result.stderr)

    def test_trailing_command_status_propagates(self):
        app = focus_line(f"{PKG}/{ACT}").strip()
        result = self.run_script([app], "--", "bash", "-c", "exit 7")
        self.assertEqual(result.returncode, 7)

    def test_model_with_quote_is_handled(self):
        app = focus_line(f"{PKG}/{ACT}").strip()
        result = self.run_script([app], "--expect-model", "Google TV Streamer", model="it's; $(touch x)")
        self.assertEqual(result.returncode, 3)

    def test_usage_errors(self):
        result = subprocess.run([str(SCRIPT), "--device", "X"], capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        result = subprocess.run(
            [str(SCRIPT), "--device", "X", "--package", "bad;rm", "--timeout", "1"],
            capture_output=True, text=True,
        )
        self.assertEqual(result.returncode, 2)


if __name__ == "__main__":
    unittest.main()
