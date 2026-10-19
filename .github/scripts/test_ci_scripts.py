"""Offline regression checks for publishing boundaries; never contacts Telegram/GitHub."""

from contextlib import contextmanager
from email import policy
from email.parser import BytesParser
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import MagicMock, patch
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

    def test_large_apk_uses_local_file_without_loading_it_into_memory(self):
        with patch.dict(os.environ, {"TELEGRAM_CHAT_ID": "@test"}):
            apk = MagicMock()
            apk.resolve.return_value.as_uri.return_value = "file:///tmp/ci%20build.apk"
            for size in (1, 50_000_000, 50_000_001, 2_000_000_000):
                apk.stat.return_value.st_size = size
                fields = telegram_ci.request_payload(self.info, apk, "https://github.com/artifact")
                self.assertEqual(fields["document"], "file:///tmp/ci%20build.apk")
                self.assertEqual(fields["parse_mode"], "HTML")
                self.assertIn("https://github.com/artifact", fields["reply_markup"])
                self.assertLess(len(json.dumps(fields)), 10000)
            apk.read_bytes.assert_not_called()
            for size in (0, 2_000_000_001):
                apk.stat.return_value.st_size = size
                with self.assertRaisesRegex(ValueError, "2000 MB"):
                    telegram_ci.request_payload(self.info, apk, "https://github.com/artifact")

    def test_http_error_does_not_expose_token(self):
        token = "DO_NOT_LOG_THIS_TOKEN"
        error = urllib.error.HTTPError(f"https://api.telegram.org/bot{token}/sendDocument", 403, token, {}, io.BytesIO(b'{}'))
        opener = MagicMock()
        opener.open.side_effect = error
        with patch.object(telegram_ci.urllib.request, "build_opener", return_value=opener):
            with self.assertRaises(RuntimeError) as captured:
                telegram_ci.api_call(token, "sendDocument", {"document": "file:///tmp/build.apk"})
        self.assertNotIn(token, str(captured.exception))
        self.assertIn("403", str(captured.exception))

    def test_api_posts_only_json_to_loopback(self):
        opener = MagicMock()
        opener.open.return_value.__enter__.return_value = io.BytesIO(b'{"ok":true,"result":{"message_id":1}}')
        with patch.object(telegram_ci.urllib.request, "build_opener", return_value=opener) as build_opener:
            result = telegram_ci.api_call("test-token", "sendDocument", {"document": "file:///tmp/a.apk"})
        request = opener.open.call_args.args[0]
        self.assertEqual(request.full_url, "http://127.0.0.1:8081/bottest-token/sendDocument")
        self.assertEqual(json.loads(request.data)["document"], "file:///tmp/a.apk")
        self.assertEqual(build_opener.call_args.args[0].proxies, {})
        self.assertEqual(result["message_id"], 1)

    def test_hosted_api_uploads_actual_multipart_document(self):
        opener = MagicMock()
        opener.open.return_value.__enter__.return_value = io.BytesIO(b'{"ok":true,"result":{"message_id":2}}')
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"TELEGRAM_CHAT_ID": "@test"}), \
                patch.object(telegram_ci.urllib.request, "build_opener", return_value=opener):
            apk = Path(temporary) / "test build.apk"
            apk.write_bytes(b"APK\x00\xff\r\nbytes")
            telegram_ci.publish_cloud("test-token", self.info, apk, "https://github.com/artifact")
            opener.open.assert_called_once()
            request = opener.open.call_args.args[0]
            self.assertEqual(request.full_url, "https://api.telegram.org/bottest-token/sendDocument")
            message = BytesParser(policy=policy.default).parsebytes(
                f"Content-Type: {request.get_header('Content-type')}\r\n\r\n".encode() + request.data
            )
            parts = {part.get_param("name", header="content-disposition"): part for part in message.iter_parts()}
            self.assertEqual(parts["document"].get_filename(), apk.name)
            self.assertEqual(parts["document"].get_payload(decode=True), apk.read_bytes())
            self.assertEqual(parts["parse_mode"].get_payload(decode=True), b"HTML")
            self.assertEqual(parts["caption"].get_payload(decode=True).decode(), telegram_ci.caption(self.info))
            self.assertIn(b"https://github.com/artifact", parts["reply_markup"].get_payload(decode=True))

    def test_hosted_api_upload_limit_and_oversize_link_fallback(self):
        with patch.dict(os.environ, {"TELEGRAM_CHAT_ID": "@test"}):
            for size in (1, 50_000_000, 50_000_001, 2_000_000_001):
                with self.subTest(size=size), patch.object(telegram_ci, "api_call") as api:
                    apk = MagicMock()
                    apk.stat.return_value.st_size = size
                    telegram_ci.publish_cloud("test-token", self.info, apk, "https://github.com/artifact")
                    api.assert_called_once()
                    args, kwargs = api.call_args
                    self.assertEqual(kwargs["endpoint"], telegram_ci.CLOUD_API)
                    if size <= 50_000_000:
                        self.assertEqual(args[1], "sendDocument")
                        self.assertIs(kwargs["document"], apk)
                    else:
                        self.assertEqual(args[1], "sendMessage")
                        self.assertIn("50 MB", args[2]["text"])
                        self.assertNotIn("caption", args[2])
                        self.assertNotIn("document", kwargs)
                    self.assertIn("https://github.com/artifact", args[2]["reply_markup"])
                    apk.read_bytes.assert_not_called()
            with patch.object(telegram_ci, "api_call") as api:
                apk.stat.return_value.st_size = 0
                with self.assertRaisesRegex(ValueError, "nonempty"):
                    telegram_ci.publish_cloud("test-token", self.info, apk, "https://github.com/artifact")
                api.assert_not_called()

    def test_cloud_main_needs_no_application_credentials_binary_or_runner(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {
            "TELEGRAM_BOT_TOKEN": "test-token", "TELEGRAM_CHAT_ID": "@test",
            "ARTIFACT_URL": "https://github.com/artifact",
        }, clear=True), patch.object(telegram_ci, "api_call") as api, \
                patch.object(telegram_ci, "local_server") as server:
            directory = Path(temporary)
            (directory / "app.apk").write_bytes(b"fake APK")
            (directory / "build-info.json").write_text(json.dumps({**self.info, "apk": "app.apk"}), encoding="utf-8")
            telegram_ci.main(directory)
            server.assert_not_called()
            api.assert_called_once()
            self.assertEqual(api.call_args.args[1], "sendDocument")
            self.assertEqual(api.call_args.kwargs["endpoint"], telegram_ci.CLOUD_API)

    def test_cloud_failure_is_not_retried_or_replaced_with_another_message(self):
        for size in (10, 50_000_001):
            with self.subTest(size=size), patch.dict(os.environ, {"TELEGRAM_CHAT_ID": "@test"}), \
                    patch.object(telegram_ci, "api_call", side_effect=telegram_ci.TelegramAPIError("sendDocument", 0)) as api:
                apk = MagicMock()
                apk.stat.return_value.st_size = size
                with self.assertRaises(telegram_ci.TelegramAPIError):
                    telegram_ci.publish_cloud("test-token", self.info, apk, "https://github.com/artifact")
                api.assert_called_once()

    def test_cloud_logout_tolerates_only_already_logged_out_or_cooldown(self):
        for code in (401, 429, 403, 500, 0):
            with self.subTest(code=code), patch.object(telegram_ci, "api_call", side_effect=telegram_ci.TelegramAPIError("logOut", code)):
                if code in (401, 429):
                    telegram_ci.logout_cloud("test-token")
                else:
                    with self.assertRaises(telegram_ci.TelegramAPIError):
                        telegram_ci.logout_cloud("test-token")

    def test_local_server_logs_out_even_if_upload_fails_without_reposting(self):
        process = MagicMock()
        process.poll.return_value = None
        for failed in (True, False):
            with self.subTest(failed=failed):
                def call(token, method, *args, **kwargs):
                    if failed and method == "sendDocument":
                        raise telegram_ci.TelegramAPIError(method, 0)
                    return True

                with patch.object(telegram_ci, "local_server") as server, patch.object(telegram_ci, "logout_cloud"), \
                        patch.object(telegram_ci, "wait_ready"), patch.object(telegram_ci, "api_call", side_effect=call) as api:
                    server.return_value.__enter__.return_value = process
                    if failed:
                        with self.assertRaises(telegram_ci.TelegramAPIError):
                            telegram_ci.publish(Path("server"), "test-token", {"document": "file:///tmp/a.apk"})
                    else:
                        telegram_ci.publish(Path("server"), "test-token", {"document": "file:///tmp/a.apk"})
                    self.assertEqual([item.args[1] for item in api.call_args_list], ["sendDocument", "logOut"])

    def test_native_process_is_stopped_and_session_directory_removed_on_failure(self):
        process = MagicMock()
        process.poll.return_value = None
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {
            "RUNNER_TEMP": temporary, "TELEGRAM_BOT_TOKEN": "secret-token", "TELEGRAM_API_HASH": "b" * 32,
        }), patch.object(telegram_ci.subprocess, "Popen", return_value=process) as start:
            with self.assertRaisesRegex(RuntimeError, "upload failed"):
                with telegram_ci.local_server(Path("server")):
                    self.assertEqual(len(list(Path(temporary).iterdir())), 1)
                    raise RuntimeError("upload failed")
            self.assertEqual(list(Path(temporary).iterdir()), [])
        process.terminate.assert_called_once()
        process.wait.assert_called_once()
        self.assertIn("--local", start.call_args.args[0])
        self.assertIn("--http-ip-address=127.0.0.1", start.call_args.args[0])
        self.assertNotIn("TELEGRAM_BOT_TOKEN", start.call_args.kwargs["env"])

    def test_readiness_checks_authentication_and_detects_process_exit(self):
        process = MagicMock()
        process.poll.return_value = None
        with patch.object(telegram_ci, "api_call", side_effect=[telegram_ci.TelegramAPIError("getMe", 0), {"id": 1}]) as api, \
                patch.object(telegram_ci.time, "sleep"):
            telegram_ci.wait_ready("test-token", process)
            self.assertEqual(api.call_count, 2)
        process.poll.return_value = 1
        with self.assertRaisesRegex(RuntimeError, "exited"):
            telegram_ci.wait_ready("test-token", process)

    def test_readiness_does_not_retry_before_telegram_cooldown(self):
        process = MagicMock()
        process.poll.return_value = None
        with patch.object(telegram_ci, "api_call", side_effect=telegram_ci.TelegramAPIError("getMe", 429, 600)) as api, \
                patch.object(telegram_ci.time, "sleep") as sleep:
            with self.assertRaisesRegex(RuntimeError, "timed out"):
                telegram_ci.wait_ready("test-token", process, timeout=10)
            api.assert_called_once()
            sleep.assert_not_called()

    def test_stalled_server_is_killed_after_graceful_shutdown_timeout(self):
        process = MagicMock()
        process.poll.return_value = None
        process.wait.side_effect = [subprocess.TimeoutExpired("server", 30), 0]
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"RUNNER_TEMP": temporary}), \
                patch.object(telegram_ci.subprocess, "Popen", return_value=process):
            with telegram_ci.local_server(Path("server")):
                pass
            self.assertEqual(list(Path(temporary).iterdir()), [])
        process.terminate.assert_called_once()
        process.kill.assert_called_once()

    def test_configuration_falls_back_when_application_credentials_are_incomplete(self):
        base = {"TELEGRAM_BOT_TOKEN": "secret-token", "TELEGRAM_CHAT_ID": "@test"}
        for app_config in ({}, {"TELEGRAM_API_ID": "12345"}, {"TELEGRAM_API_HASH": "b" * 32},
                           {"TELEGRAM_API_ID": "", "TELEGRAM_API_HASH": ""},
                           {"TELEGRAM_API_ID": " \t", "TELEGRAM_API_HASH": "b" * 32},
                           {"TELEGRAM_API_ID": "12345", "TELEGRAM_API_HASH": "\n"}):
            with self.subTest(app_config=app_config), patch.dict(os.environ, {**base, **app_config}, clear=True):
                self.assertEqual(telegram_ci.validate_config(), "cloud")

    def test_missing_bot_credentials_still_fail_for_both_modes(self):
        for app_config in ({}, {"TELEGRAM_API_ID": "12345", "TELEGRAM_API_HASH": "b" * 32}):
            for missing in ("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID"):
                with self.subTest(app_config=app_config, missing=missing), patch.dict(os.environ, {
                    "TELEGRAM_BOT_TOKEN": "secret-token", "TELEGRAM_CHAT_ID": "@test", **app_config, missing: " ",
                }, clear=True):
                    with self.assertRaisesRegex(ValueError, missing):
                        telegram_ci.validate_config()

    def test_config_check_exports_mode_without_credentials(self):
        for mode in ("cloud", "local"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as temporary:
                output = Path(temporary) / "outputs"
                output.write_text("previous=value\n", encoding="utf-8")
                config = {"TELEGRAM_BOT_TOKEN": "secret-token", "TELEGRAM_CHAT_ID": "@test",
                          "GITHUB_OUTPUT": str(output)}
                if mode == "local":
                    config.update(TELEGRAM_API_ID="12345", TELEGRAM_API_HASH="b" * 32)
                with patch.dict(os.environ, config, clear=True), patch("sys.stdout", new_callable=io.StringIO) as stdout:
                    self.assertEqual(telegram_ci.check_config(), mode)
                self.assertEqual(output.read_text(encoding="utf-8"), f"previous=value\napi_mode={mode}\n")
                for secret in ("secret-token", "12345", "b" * 32):
                    self.assertNotIn(secret, stdout.getvalue())

    def test_complete_application_credentials_are_validated_without_echoing_values(self):
        config = {"TELEGRAM_BOT_TOKEN": "secret-token", "TELEGRAM_CHAT_ID": "@test",
                  "TELEGRAM_API_ID": "12345", "TELEGRAM_API_HASH": "b" * 32}
        with patch.dict(os.environ, config, clear=True):
            self.assertEqual(telegram_ci.validate_config(), "local")
            for name in ("TELEGRAM_API_ID", "TELEGRAM_API_HASH"):
                with patch.dict(os.environ, {name: "secret-invalid-value"}):
                    with self.assertRaises(ValueError) as captured:
                        telegram_ci.validate_config()
                    self.assertIn(name, str(captured.exception))
                    self.assertNotIn("secret-invalid-value", str(captured.exception))


if __name__ == "__main__":
    unittest.main()
