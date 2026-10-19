"""Upload a CI APK using local Bot API, or hosted Bot API without application credentials."""

import argparse
from contextlib import contextmanager
import html
import json
import os
from pathlib import Path
import re
import signal
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import uuid


MAX_DOCUMENT_BYTES = 2_000_000_000  # 官方 local 模式上限为 2000 MB。
CLOUD_MAX_DOCUMENT_BYTES = 50_000_000  # 官方托管 Bot API 的保守 50 MB 阈值。
LOCAL_API = "http://127.0.0.1:8081"
CLOUD_API = "https://api.telegram.org"


class TelegramAPIError(RuntimeError):
    def __init__(self, method, code, retry_after=0):
        self.code = int(code) if str(code).isdigit() else 0
        self.retry_after = int(retry_after) if str(retry_after).isdigit() else 0
        # 不包含带 Token 的 URL，也不回显服务端 description。
        super().__init__(f"Telegram {method} failed (code {self.code}); check credentials, permissions and network")


def validate_config():
    for name in ("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID"):
        if not os.environ.get(name, "").strip():
            raise ValueError(f"Configure {name} in repository Actions secrets")
    if any(not os.environ.get(name, "").strip() for name in ("TELEGRAM_API_ID", "TELEGRAM_API_HASH")):
        return "cloud"
    api_id = os.environ["TELEGRAM_API_ID"]
    if not api_id.isascii() or not api_id.isdigit() or not 0 < int(api_id) < 2**31:
        raise ValueError("TELEGRAM_API_ID must be the numeric api_id from my.telegram.org")
    if not re.fullmatch(r"[0-9a-fA-F]{32}", os.environ["TELEGRAM_API_HASH"]):
        raise ValueError("TELEGRAM_API_HASH must be the api_hash from my.telegram.org")
    return "local"


def check_config():
    mode = validate_config()
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
            output.write(f"api_mode={mode}\n")
    if mode == "cloud":
        print("Application API ID/Hash is incomplete; falling back to hosted Bot API (50 MB upload limit)")
    else:
        print("Telegram application credentials configured; using temporary local Bot API")
    return mode


def caption(info):
    def escape(value, limit):
        # Telegram 按 UTF-16 计数；先截断再 HTML 转义，避免截断实体/标签。
        text = str(value).encode("utf-16-le")[:limit * 2].decode("utf-16-le", errors="ignore")
        return html.escape(text)

    return (
        f"<b>澎湃记 | CI Build #{escape(info['runNumber'], 20)}</b>\n"
        f"Version: {escape(info['versionName'], 80)}\n"
        f"Branch: {escape(info['branch'], 80)}\n"
        f"Commit: <code>{escape(info['commit'][:7], 7)}</code>\n\n"
        f"Commit message:\n<pre>{escape(info['commitMessage'], 500)}</pre>"
    )


def notification_fields(info, artifact_url):
    return {
        "chat_id": os.environ["TELEGRAM_CHAT_ID"],
        "parse_mode": "HTML",
        "caption": caption(info),
        "reply_markup": json.dumps({"inline_keyboard": [[
            {"text": "下载 Actions 产物", "url": artifact_url},
            {"text": "构建详情", "url": info["runUrl"]},
        ]]}, ensure_ascii=False),
    }


def request_payload(info, apk, artifact_url):
    size = apk.stat().st_size
    if not 0 < size <= MAX_DOCUMENT_BYTES:
        raise ValueError("APK must be nonempty and no larger than the local Bot API limit of 2000 MB")
    fields = notification_fields(info, artifact_url)
    # server 与脚本在同一 runner，直接读取文件；不将整个 APK 载入 Python 内存。
    fields["document"] = apk.resolve().as_uri()
    return fields


def api_call(token, method, fields=None, *, endpoint=LOCAL_API, timeout=30, document=None):
    if document is None:
        body = json.dumps(fields or {}).encode("utf-8")
        content_type = "application/json"
    else:
        # 仅用于不超过 50 MB 的云端附件；大包继续交给 local 服务按文件 URI 读取。
        if not 0 < document.stat().st_size <= CLOUD_MAX_DOCUMENT_BYTES:
            raise ValueError("Hosted Bot API attachment must be nonempty and no larger than 50 MB")
        boundary = uuid.uuid4().hex
        parts = [
            f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode("utf-8")
            for name, value in (fields or {}).items()
        ]
        filename = document.name.replace('"', "%22").replace("\r", "%0D").replace("\n", "%0A")
        parts.append(
            f'--{boundary}\r\nContent-Disposition: form-data; name="document"; filename="{filename}"\r\n'
            'Content-Type: application/vnd.android.package-archive\r\n\r\n'.encode("utf-8")
        )
        parts.extend([document.read_bytes(), f"\r\n--{boundary}--\r\n".encode("utf-8")])
        body = b"".join(parts)
        content_type = f"multipart/form-data; boundary={boundary}"
    request = urllib.request.Request(
        f"{endpoint}/bot{token}/{method}", data=body,
        headers={"Content-Type": content_type}, method="POST",
    )
    # 本地文件路径与 Bot Token 只发送到 loopback，不经过环境中的 HTTP 代理。
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({})) if endpoint == LOCAL_API else urllib.request.build_opener()
    try:
        with opener.open(request, timeout=timeout) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        try:
            result = json.loads(error.read(65536))
            retry_after = int(result.get("parameters", {}).get("retry_after", 0))
        except (ValueError, TypeError, AttributeError):
            retry_after = 0
        raise TelegramAPIError(method, error.code, retry_after) from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise TelegramAPIError(method, 0) from None
    except (ValueError, TypeError):
        raise RuntimeError(f"Telegram {method} returned an invalid response") from None
    if not isinstance(result, dict):
        raise RuntimeError(f"Telegram {method} returned an invalid response")
    if not result.get("ok"):
        raise TelegramAPIError(method, result.get("error_code", 0), result.get("parameters", {}).get("retry_after", 0))
    return result.get("result")


def logout_cloud(token):
    try:
        api_call(token, "logOut", endpoint=CLOUD_API)
    except TelegramAPIError as error:
        # 前一次已退出 cloud，10 分钟内再次访问可返回 401/429。
        # 后续 local getMe 会再次验证 Token；不能把网络或服务故障当作迁移成功。
        if error.code not in (401, 429):
            raise
        print("Cloud Bot API already logged out or in cooldown; verifying local login next")


def wait_ready(token, process, timeout=180):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Local Bot API server exited before becoming ready")
        try:
            api_call(token, "getMe", timeout=10)
            return
        except TelegramAPIError as error:
            if error.code not in (0, 429, 500, 502, 503, 504):
                raise
            delay = max(error.retry_after, 2)
            if delay >= deadline - time.monotonic():
                break
            time.sleep(delay)
    raise RuntimeError("Local Bot API login timed out; check TELEGRAM_API_ID, TELEGRAM_API_HASH and network")


@contextmanager
def local_server(binary):
    # 只缓存服务程序；包含登录状态的目录不能进入 Actions cache/artifact。
    with tempfile.TemporaryDirectory(prefix="telegram-bot-api-state-", dir=os.environ["RUNNER_TEMP"]) as temporary:
        root = Path(temporary)
        (root / "data").mkdir()
        (root / "temp").mkdir()
        server_env = {key: value for key, value in os.environ.items()
                      if key not in ("TELEGRAM_BOT_TOKEN", "TELEGRAM_CHAT_ID")}
        process = subprocess.Popen([
            str(binary), "--local", "--http-ip-address=127.0.0.1", "--http-port=8081",
            f"--dir={root / 'data'}", f"--temp-dir={root / 'temp'}", "--verbosity=0",
        ], cwd=root, env=server_env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            yield process
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=30)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=10)


def publish(binary, token, fields):
    with local_server(binary) as process:
        local_started = False
        try:
            logout_cloud(token)
            local_started = True
            wait_ready(token, process)
            # 只提交一次，网络超时后不自动重发，避免频道重复收到 APK。
            api_call(token, "sendDocument", fields, timeout=1200)
            print("Telegram CI APK uploaded")
        finally:
            if local_started and process.poll() is None:
                try:
                    # 临时 runner 下次会换机器，用 logOut 注销，不调用有 10 分钟限制的 close。
                    api_call(token, "logOut", timeout=15)
                except RuntimeError:
                    print("::warning::Local Bot API logout failed; the temporary server will still be stopped")


def publish_cloud(token, info, apk, artifact_url):
    size = apk.stat().st_size
    if size <= 0:
        raise ValueError("APK must be nonempty")
    fields = notification_fields(info, artifact_url)
    # 云端模式不启动本地服务，也不调用 logOut；只发送一次，避免超时后重复通知。
    if size > CLOUD_MAX_DOCUMENT_BYTES:
        fields["text"] = fields.pop("caption") + (
            "\n\n未配置完整的 Telegram App API，已回退到普通 Bot API。"
            "APK 超过 50 MB 上传限制，请从 Actions 下载（需登录 GitHub）。"
        )
        api_call(token, "sendMessage", fields, endpoint=CLOUD_API)
        print("Telegram CI download link sent; APK exceeds the hosted Bot API 50 MB upload limit")
    else:
        api_call(token, "sendDocument", fields, endpoint=CLOUD_API, timeout=1200, document=apk)
        print("Telegram CI APK uploaded through hosted Bot API")


def main(directory):
    mode = validate_config()
    info = json.loads((directory / "build-info.json").read_text(encoding="utf-8"))
    apk = directory / info["apk"]
    if apk.parent.resolve() != directory.resolve() or not apk.is_file():
        raise ValueError("Invalid APK path")
    if mode == "cloud":
        publish_cloud(os.environ["TELEGRAM_BOT_TOKEN"], info, apk, os.environ["ARTIFACT_URL"])
    else:
        binary = Path(os.environ["TELEGRAM_BOT_API_BINARY"]).resolve()
        if not binary.is_file() or not os.access(binary, os.X_OK):
            raise ValueError("Local Bot API server binary is missing or not executable")
        fields = request_payload(info, apk, os.environ["ARTIFACT_URL"])
        publish(binary, os.environ["TELEGRAM_BOT_TOKEN"], fields)


if __name__ == "__main__":
    def interrupted(signum, frame):
        raise SystemExit(128 + signum)

    signal.signal(signal.SIGTERM, interrupted)
    signal.signal(signal.SIGINT, interrupted)
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path, nargs="?")
    parser.add_argument("--check-config", action="store_true")
    args = parser.parse_args()
    try:
        if args.check_config:
            check_config()
        elif args.directory is not None:
            main(args.directory)
        else:
            parser.error("directory is required for upload")
    except (ValueError, RuntimeError, KeyError) as error:
        print(f"Telegram notification failed: {error}", file=sys.stderr)
        sys.exit(1)
