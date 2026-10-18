"""Offline regression checks for publishing boundaries; never contacts Telegram/GitHub."""

from contextlib import contextmanager
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import urllib.error

import build_metadata
import telegram_ci


@contextmanager
def working_directory(directory):
    previous = Path.cwd()
    os.chdir(directory)
    try:
        yield
    finally:
        os.chdir(previous)


def git(directory, *args):
    return subprocess.check_output(["git", "-C", str(directory), *args], text=True, encoding="utf-8", stderr=subprocess.DEVNULL).strip()


def init_repo(directory):
    directory.mkdir()
    git(directory, "init", "-b", "master")
    git(directory, "config", "user.name", "CI test")
    git(directory, "config", "user.email", "ci@example.invalid")


class MetadataTests(unittest.TestCase):
    def test_published_tags_are_not_overwritten(self):
        with patch.dict(os.environ, {
            "GITHUB_REF_NAME": "v1", "GITHUB_API_URL": "https://api.github.com",
            "GITHUB_REPOSITORY": "test/repo", "GH_TOKEN": "test-token",
        }):
            for draft in [True, False]:
                response = io.BytesIO(json.dumps({"draft": draft}).encode())
                with patch.object(build_metadata.urllib.request, "urlopen", return_value=response):
                    if draft:
                        build_metadata.ensure_unpublished()
                    else:
                        with self.assertRaisesRegex(RuntimeError, "already published"):
                            build_metadata.ensure_unpublished()
            for code in [404, 403, 500]:
                error = urllib.error.HTTPError("https://api.github.com", code, "error", {}, None)
                with patch.object(build_metadata.urllib.request, "urlopen", side_effect=error):
                    if code == 404:
                        build_metadata.ensure_unpublished()
                    else:
                        with self.assertRaises(RuntimeError):
                            build_metadata.ensure_unpublished()

    def test_explicit_tag_channels(self):
        for tag, channel in {
            "v26.10.19-C01": "stable", "v26.10.19.C01-Dev": "dev",
            "v1-beta.2": "dev", "v1-RC1": "dev", "v1-DevUI": "devui",
        }.items():
            with self.subTest(tag=tag):
                self.assertEqual(build_metadata.release_channel(tag), channel)

    def test_ci_and_release_use_apk_metadata(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary) / "repo"
            init_repo(root)
            (root / "source.txt").write_text("test", encoding="utf-8")
            git(root, "add", ".")
            git(root, "commit", "-m", "fix: 中文 <message> $(echo never)")
            apk_dir = root / "app/build/outputs/apk/release"
            apk_dir.mkdir(parents=True)
            payload = b"offline fake APK"
            (apk_dir / "app-release.apk").write_bytes(payload)
            build_metadata.write_json(apk_dir / "output-metadata.json", {"elements": [{
                "outputFile": "app-release.apk", "versionCode": 123, "versionName": "1.2.3",
            }]})
            with working_directory(root), patch.dict(os.environ, {
                "GITHUB_RUN_NUMBER": "42", "GITHUB_REF_NAME": "v1.2.3-Dev",
                "GITHUB_REPOSITORY": "test/repo", "GITHUB_SERVER_URL": "https://github.com",
                "GITHUB_RUN_ID": "1234", "GITHUB_OUTPUT": str(Path(temporary) / "outputs"),
            }):
                ci = Path(temporary) / "ci"
                build_metadata.prepare("ci", "release", ci)
                info = json.loads((ci / "build-info.json").read_text(encoding="utf-8"))
                self.assertIn("-ci.42-", info["apk"])
                self.assertEqual(info["sha256"], hashlib.sha256(payload).hexdigest())
                self.assertEqual((ci / info["apk"]).read_bytes(), payload)
                self.assertFalse((ci / "Dev.json").exists())
                release = Path(temporary) / "release"
                build_metadata.prepare("release", "release", release)
                data = json.loads((release / "Dev.json").read_text(encoding="utf-8"))
                self.assertEqual(data["versionCode"], 123)
                self.assertEqual(data["versionName"], "1.2.3")
                self.assertEqual(data["releaseNotes"], "无更新日志")
                self.assertIn("/v1.2.3-Dev/app-release.apk", data["downloadUrl"])
                self.assertFalse((release / "Stable.json").exists())

    def test_index_publish_preserves_newer_versions_and_concurrent_commits(self):
        with tempfile.TemporaryDirectory() as temporary:
            temp = Path(temporary)
            remote, root = temp / "remote.git", temp / "repo"
            subprocess.run(["git", "init", "--bare", str(remote)], check=True, capture_output=True)
            init_repo(root)
            build_metadata.write_json(root / "Stable.json", {"versionCode": 10})
            build_metadata.write_json(root / "Dev.json", {"versionCode": 30})
            git(root, "add", ".")
            git(root, "commit", "-m", "initial")
            git(root, "remote", "add", "origin", str(remote))
            git(root, "push", "-u", "origin", "master")
            original_head = git(root, "rev-parse", "HEAD")
            indexes = temp / "indexes"
            indexes.mkdir()
            for name in ["Stable.json", "Dev.json"]:
                build_metadata.write_json(indexes / name, {"versionCode": 20, "versionName": "new"})

            real_run = subprocess.run
            pushes = 0

            def race_once(command, **kwargs):
                nonlocal pushes
                if command[:3] == ["git", "push", "origin"]:
                    pushes += 1
                    if pushes == 1:
                        # 另一个提交抢先推送；第一次索引推送应失败，之后重新合并。
                        (root / "unrelated.txt").write_text("keep me", encoding="utf-8")
                        git(root, "add", "unrelated.txt")
                        git(root, "commit", "-m", "concurrent change")
                        git(root, "push", "origin", "master")
                return real_run(command, **kwargs)

            with working_directory(root), patch.dict(os.environ, {
                "RUNNER_TEMP": temporary, "GITHUB_REF_NAME": "v2",
            }), patch.object(build_metadata.subprocess, "run", side_effect=race_once):
                build_metadata.publish(indexes)
                self.assertEqual(pushes, 2)
                remote_stable = json.loads(git(root, "show", "origin/master:Stable.json"))
                remote_dev = json.loads(git(root, "show", "origin/master:Dev.json"))
                # 发布后的本地 remote ref 会随成功的 push 更新。
                self.assertEqual(remote_stable["versionCode"], 20)
                self.assertEqual(remote_dev["versionCode"], 30)
                self.assertEqual(git(root, "show", "origin/master:unrelated.txt"), "keep me")
                self.assertEqual(json.loads((root / "Stable.json").read_text())["versionCode"], 10)
                local_head = git(root, "rev-parse", "HEAD")
                self.assertNotEqual(local_head, original_head)
                # 同版本重跑以及较旧 tag 均不产生新的索引提交。
                head = git(root, "rev-parse", "origin/master")
                build_metadata.publish(indexes)
                build_metadata.write_json(indexes / "Stable.json", {"versionCode": 5})
                build_metadata.publish(indexes)
                self.assertEqual(git(root, "rev-parse", "origin/master"), head)
                self.assertEqual(git(root, "rev-parse", "HEAD"), local_head)


class TelegramTests(unittest.TestCase):
    def setUp(self):
        self.info = {
            "runNumber": "42", "versionName": "1.0", "branch": "master",
            "commit": "a" * 40, "commitMessage": "<b>用户 & 提交</b> " + "😀" * 700,
            "runUrl": "https://github.com/test/repo/actions/runs/42",
        }

    def test_caption_escapes_untrusted_commit_text(self):
        text = telegram_ci.caption(self.info)
        self.assertIn("&lt;b&gt;用户 &amp; 提交&lt;/b&gt;", text)
        self.assertLess(len(text.encode("utf-16-le")) // 2, 1024)

    def test_file_size_boundary_switches_to_artifact_link(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"TELEGRAM_CHAT_ID": "@test"}):
            apk = Path(temporary) / "ci.apk"
            for size, expected in [(1, "sendDocument"), (50_000_000, "sendDocument"), (50_000_001, "sendMessage")]:
                with apk.open("wb") as stream:
                    stream.truncate(size)
                method, fields, document = telegram_ci.request_payload(self.info, apk, "https://github.com/artifact")
                self.assertEqual(method, expected)
                self.assertEqual(document is None, expected == "sendMessage")
                self.assertIn("https://github.com/artifact", fields["reply_markup"])

    def test_http_error_does_not_expose_token(self):
        token = "DO_NOT_LOG_THIS_TOKEN"
        error = urllib.error.HTTPError(f"https://api.telegram.org/bot{token}/sendMessage", 403, "Forbidden", {}, None)
        with patch.object(telegram_ci.urllib.request, "urlopen", side_effect=error):
            with self.assertRaises(RuntimeError) as captured:
                telegram_ci.send(token, "sendMessage", {"text": "test"}, None)
        self.assertNotIn(token, str(captured.exception))
        self.assertIn("403", str(captured.exception))


if __name__ == "__main__":
    unittest.main()
