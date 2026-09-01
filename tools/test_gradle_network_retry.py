import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipUnless(os.name != "nt" and shutil.which("bash"), "requires a POSIX bash workspace")
class GradleNetworkRetryTest(unittest.TestCase):
    def run_helper(self, mode):
        root = Path(__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            shutil.copy2(root / "tools" / "gradle-network-retry.sh", folder / "retry.sh")
            (folder / "gradlew").write_text("""#!/usr/bin/env bash
count=0; [ ! -f .count ] || count=$(cat .count); count=$((count + 1)); echo "$count" > .count
if [ "$MODE" = transient ] && [ "$count" -eq 1 ]; then echo 'Received status code 429 from server' >&2; exit 1; fi
if [ "$MODE" = failure ]; then echo 'TEST FAILED' >&2; exit 1; fi
exit 0
""", encoding="utf-8", newline="\n")
            os.chmod(folder / "gradlew", 0o755)
            env = dict(os.environ, MODE=mode, GRADLE_RETRY_SLEEP_SECONDS="0")
            run = subprocess.run(["bash", "retry.sh", "test"], cwd=folder, env=env,
                                 text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
            return run, int((folder / ".count").read_text())

    def test_retries_only_a_transient_repository_response(self):
        run, count = self.run_helper("transient")
        self.assertEqual(0, run.returncode, run.stdout)
        self.assertEqual(2, count)

    def test_does_not_retry_a_real_test_failure(self):
        run, count = self.run_helper("failure")
        self.assertNotEqual(0, run.returncode)
        self.assertEqual(1, count)


if __name__ == "__main__":
    unittest.main()
