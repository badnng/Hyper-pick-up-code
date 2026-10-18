"""Exercise the signature gate without compiling an APK or using signing secrets."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("verify_apk.sh").resolve()
EXPECTED = "9400b749bcd477daa317c4026d25ec97dd033be3b748fd89e42a989a093b97ca"
BASH = shutil.which("bash")
if os.name == "nt" and shutil.which("git"):
    git_bash = Path(shutil.which("git")).parents[1] / "bin/bash.exe"
    BASH = str(git_bash) if git_bash.is_file() else BASH


@unittest.skipUnless(BASH, "Bash is required for the APK signature gate")
class SignatureGateTests(unittest.TestCase):
    def verify(self, output, exit_code=0):
        with tempfile.TemporaryDirectory(prefix="hypernote-signature-test-") as temporary:
            root = Path(temporary)
            tools = root / "build-tools/36.1.0"
            tools.mkdir(parents=True)
            signer = tools / "apksigner"
            signer.write_text(
                '#!/usr/bin/env bash\nprintf "%s\\n" "$SIGNER_OUTPUT"\nexit "$SIGNER_EXIT"\n',
                encoding="utf-8", newline="\n",
            )
            signer.chmod(0o755)
            # 不得因为 runner 新增了更高版本就改用它。
            newer = root / "build-tools/37.0.0"
            newer.mkdir()
            (newer / "apksigner").write_text("#!/usr/bin/env bash\nexit 99\n", encoding="utf-8", newline="\n")
            (newer / "apksigner").chmod(0o755)
            return subprocess.run(
                [BASH, SCRIPT.as_posix(), "unused.apk"],
                env={**os.environ, "ANDROID_HOME": root.as_posix(),
                     "SIGNER_OUTPUT": output, "SIGNER_EXIT": str(exit_code)},
                text=True, encoding="utf-8", capture_output=True,
            )

    def test_old_and_new_signer_formats_and_pinned_tool_version(self):
        for output in [
            f"Signer #1 certificate SHA-256 digest: {EXPECTED}",
            f"V2 Signer: certificate SHA-256 digest: {EXPECTED}",
            f"V2 Signer: certificate SHA-256 digest: {EXPECTED}\nV3.1 Signer: certificate SHA-256 digest: {EXPECTED}",
            f"Signer #1 certificate SHA-256 digest: {EXPECTED.upper()}\r\n",
        ]:
            with self.subTest(output=output):
                result = self.verify(output)
                self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
                self.assertIn("APK signature verified", result.stdout)

    def test_wrong_or_additional_certificate_is_rejected(self):
        wrong = "a" * 64
        for output in [
            f"V2 Signer: certificate SHA-256 digest: {wrong}",
            f"Signer #1 certificate SHA-256 digest: {EXPECTED}\nSigner #2 certificate SHA-256 digest: {wrong}",
        ]:
            with self.subTest(output=output):
                result = self.verify(output)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("certificate mismatch", result.stdout)

    def test_unreadable_digest_is_distinct_from_wrong_certificate(self):
        for output in ["", "unrecognized output", f"Source Stamp Signer certificate SHA-256 digest: {EXPECTED}"]:
            with self.subTest(output=output):
                result = self.verify(output)
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Cannot read APK signing certificate", result.stdout)

    def test_failed_apk_verification_cannot_pass_on_matching_digest(self):
        result = self.verify(f"Signer #1 certificate SHA-256 digest: {EXPECTED}", exit_code=7)
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn("APK signature verified", result.stdout)


if __name__ == "__main__":
    unittest.main()
