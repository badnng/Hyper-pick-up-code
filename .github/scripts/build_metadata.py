"""Package CI builds and maintain the existing GitHub-hosted update indexes."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.parse import quote
import urllib.error
import urllib.request


def git(*args, cwd=None):
    return subprocess.check_output(["git", *args], cwd=cwd, text=True, encoding="utf-8").strip()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def output(name, value):
    if "GITHUB_OUTPUT" in os.environ:
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
            stream.write(f"{name}={value}\n")


def apk_metadata(variant):
    directory = Path("app/build/outputs/apk") / variant
    metadata = json.loads((directory / "output-metadata.json").read_text(encoding="utf-8"))
    if len(metadata["elements"]) != 1:
        raise ValueError("Expected exactly one arm64 APK")
    item = metadata["elements"][0]
    apk = directory / item["outputFile"]
    if not apk.is_file() or apk.stat().st_size == 0 or item["versionCode"] <= 0:
        raise ValueError("Missing APK or invalid version metadata")
    return apk, item


def release_channel(tag):
    # 通道由 tag 明确指定，不再猜测一个提交属于哪条分支。
    if re.search(r"-devui(?:[.-]?\d+)?$", tag, re.IGNORECASE):
        return "devui"
    if re.search(r"-(dev|alpha|beta|rc|preview)(?:[.-]?\d+)?$", tag, re.IGNORECASE):
        return "dev"
    return "stable"


def ensure_unpublished():
    tag = quote(os.environ["GITHUB_REF_NAME"], safe="")
    url = f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}/releases/tags/{tag}"
    request = urllib.request.Request(url, headers={
        "Authorization": f"Bearer {os.environ['GH_TOKEN']}",
        "Accept": "application/vnd.github+json",
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            release = json.load(response)
    except urllib.error.HTTPError as error:
        if error.code == 404:
            return
        raise RuntimeError(f"Cannot check release status: HTTP {error.code}") from None
    if not release["draft"]:
        raise RuntimeError("Tag is already published; rerun only the failed update_index job, or publish a new tag")


def prepare(mode, variant, directory):
    directory.mkdir(parents=True, exist_ok=True)
    apk, item = apk_metadata(variant)
    with apk.open("rb") as stream:
        sha256 = hashlib.file_digest(stream, "sha256").hexdigest()
    if mode == "ci":
        sha = git("rev-parse", "HEAD")
        run = os.environ["GITHUB_RUN_NUMBER"]
        safe_version = re.sub(r"[^a-zA-Z0-9._-]", "_", item["versionName"])
        name = f"HyperNote-{safe_version}-ci.{run}-{sha[:7]}-{variant}"
        destination = directory / f"{name}.apk"
        shutil.copy2(apk, destination)
        (directory / "SHA256SUMS").write_text(f"{sha256}  {destination.name}\n", encoding="utf-8")
        write_json(directory / "build-info.json", {
            "versionName": item["versionName"],
            "versionCode": item["versionCode"],
            "variant": variant,
            "apk": destination.name,
            "sha256": sha256,
            "repository": os.environ["GITHUB_REPOSITORY"],
            "branch": os.environ.get("GITHUB_HEAD_REF") or os.environ["GITHUB_REF_NAME"],
            "commit": sha,
            "commitMessage": git("log", "-1", "--format=%B"),
            "runNumber": run,
            "runUrl": f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/actions/runs/{os.environ['GITHUB_RUN_ID']}",
        })
        output("artifact_name", name)
        return

    tag = os.environ["GITHUB_REF_NAME"]
    channel = release_channel(tag)
    changelog = Path("changelog.txt")
    notes = changelog.read_text(encoding="utf-8").strip() if changelog.exists() else "无更新日志"
    (directory / "release-notes.md").write_text(notes + "\n", encoding="utf-8")
    with apk.open("rb") as stream:
        md5 = hashlib.file_digest(stream, "md5").hexdigest()
    data = {
        "versionCode": item["versionCode"],
        "versionName": item["versionName"],
        "releaseNotes": notes,
        "downloadUrl": f"{os.environ['GITHUB_SERVER_URL']}/{os.environ['GITHUB_REPOSITORY']}/releases/download/{quote(tag, safe='')}/app-release.apk",
        "md5": md5,
        "sha256": sha256,
    }
    files = {"stable": ["Stable.json", "Dev.json"], "dev": ["Dev.json"], "devui": ["DevUI.json"]}[channel]
    for filename in files:
        write_json(directory / filename, data)
    output("prerelease", str(channel != "stable").lower())


def publish(directory):
    """Use an isolated checkout; retry fast-forward pushes without losing others' commits."""
    indexes = {p.name: json.loads(p.read_text(encoding="utf-8")) for p in directory.glob("*.json")}
    if not indexes or not set(indexes) <= {"Stable.json", "Dev.json", "DevUI.json"}:
        raise ValueError("Unexpected update index files")
    # RUNNER_TEMP 位于工作区外；绝不 reset 主检出目录。
    with tempfile.TemporaryDirectory(prefix="hypernote-index-", dir=os.environ["RUNNER_TEMP"]) as temporary:
        checkout = Path(temporary) / "checkout"
        git("fetch", "origin", "+refs/heads/master:refs/remotes/origin/master")
        git("worktree", "add", "--detach", str(checkout), "origin/master")
        try:
            git("config", "user.name", "github-actions[bot]", cwd=checkout)
            git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com", cwd=checkout)
            for _ in range(3):
                git("fetch", "origin", "+refs/heads/master:refs/remotes/origin/master", cwd=checkout)
                git("reset", "--hard", "origin/master", cwd=checkout)
                changed = []
                for filename, data in indexes.items():
                    path = checkout / filename
                    current = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
                    # 旧 tag 晚完成或重跑时不能覆盖较新版本，也不替换同 versionCode 的包。
                    if int(current.get("versionCode", 0)) >= data["versionCode"]:
                        print(f"Skip {filename}: current version is already equal or newer")
                        continue
                    write_json(path, data)
                    changed.append(filename)
                if not changed:
                    return
                git("add", "--", *changed, cwd=checkout)
                git("commit", "-m", f"chore: update release indexes for {os.environ['GITHUB_REF_NAME']}", cwd=checkout)
                result = subprocess.run(["git", "push", "origin", "HEAD:refs/heads/master"], cwd=checkout, check=False)
                if result.returncode == 0:
                    return
            raise RuntimeError("Could not update master; check branch protection and rerun this release")
        finally:
            git("worktree", "remove", "--force", str(checkout))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("mode", choices=["ci", "release", "publish", "check-release"])
    parser.add_argument("--variant", choices=["debug", "release"], default="release")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if args.mode == "check-release":
        ensure_unpublished()
    elif args.output is None:
        parser.error("--output is required for packaging and index publication")
    elif args.mode == "publish":
        publish(args.output.resolve())
    else:
        prepare(args.mode, args.variant, args.output.resolve())
