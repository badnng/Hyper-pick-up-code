"""Send a CI APK using the hosted Telegram Bot API; no service to deploy."""

import html
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.request
import uuid


# 使用保守的十进制 50 MB 阈值；超限时仍可从 Actions 下载完整 APK。
MAX_DOCUMENT_BYTES = 50_000_000


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


def request_payload(info, apk, artifact_url):
    fields = {
        "chat_id": os.environ["TELEGRAM_CHAT_ID"],
        "parse_mode": "HTML",
        "reply_markup": json.dumps({"inline_keyboard": [[
            {"text": "下载 Actions 产物", "url": artifact_url},
            {"text": "构建详情", "url": info["runUrl"]},
        ]]}, ensure_ascii=False),
    }
    text = caption(info)
    if apk.stat().st_size > MAX_DOCUMENT_BYTES:
        fields["text"] = text + "\n\nAPK 超过 Bot API 的 50 MB 上传限制，请从 Actions 下载（需登录 GitHub）。"
        return "sendMessage", fields, None
    fields["caption"] = text
    return "sendDocument", fields, apk


def send(token, method, fields, document):
    if document is None:
        body = json.dumps(fields).encode("utf-8")
        content_type = "application/json"
    else:
        boundary = uuid.uuid4().hex
        parts = []
        for name, value in fields.items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode("utf-8"))
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="document"; filename="{document.name}"\r\nContent-Type: application/vnd.android.package-archive\r\n\r\n'.encode("utf-8"))
        parts.extend([document.read_bytes(), f"\r\n--{boundary}--\r\n".encode("utf-8")])
        body = b"".join(parts)
        content_type = f"multipart/form-data; boundary={boundary}"
    request = urllib.request.Request(
        f"https://api.telegram.org/bot{token}/{method}", data=body,
        headers={"Content-Type": content_type}, method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            result = json.load(response)
    except urllib.error.HTTPError as error:
        # 不打印异常 URL / 响应正文，防止 Bot Token 出现在日志中。
        raise RuntimeError(f"Telegram HTTP {error.code}; check bot permissions, chat ID and rate limits") from None
    except (urllib.error.URLError, TimeoutError, OSError):
        raise RuntimeError("Telegram network failure; APK remains available in Actions artifacts") from None
    if not result.get("ok"):
        raise RuntimeError(f"Telegram rejected upload (code {result.get('error_code', 'unknown')})")
    print("Telegram CI notification sent")


def main(directory):
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "")
    if not token or not os.environ.get("TELEGRAM_CHAT_ID"):
        raise ValueError("Configure TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID in repository secrets")
    info = json.loads((directory / "build-info.json").read_text(encoding="utf-8"))
    apk = directory / info["apk"]
    if apk.parent.resolve() != directory.resolve() or not apk.is_file():
        raise ValueError("Invalid APK path")
    method, fields, document = request_payload(info, apk, os.environ["ARTIFACT_URL"])
    send(token, method, fields, document)


if __name__ == "__main__":
    try:
        main(Path(sys.argv[1]))
    except (ValueError, RuntimeError, KeyError) as error:
        print(f"Telegram notification failed: {error}", file=sys.stderr)
        sys.exit(1)
