#!/usr/bin/env python3
"""List Codacy issues for a GitHub PR (cloud app, not CI).

Reads CODACY_API_TOKEN from the environment or from repo-root .env
(same variables as .env.example). Does not print the token.

Usage:
  python scripts/codacy-pr-issues.py 16
  python scripts/codacy-pr-issues.py 16 --status=all
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


def strip_dotenv_value(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def load_dotenv(path: Path) -> None:
    if not path.is_file():
        return
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        os.environ.setdefault(key.strip(), strip_dotenv_value(value))


def validate_api_base(base: str) -> str:
    parsed = urllib.parse.urlparse(base.strip())
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        raise ValueError(f"CODACY_API_URL must be http(s) with a host: {base!r}")
    return base.strip()


def api_get(base: str, token: str, path: str) -> tuple[int, object]:
    safe_base = validate_api_base(base)
    request = urllib.request.Request(
        f"{safe_base.rstrip('/')}{path}",
        headers={"api-token": token, "Accept": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.status, json.loads(response.read().decode())
    except urllib.error.HTTPError as error:
        body = error.read().decode(errors="replace")
        try:
            payload = json.loads(body)
        except json.JSONDecodeError:
            payload = body[:500]
        return error.code, payload


def main() -> int:
    parser = argparse.ArgumentParser(description="List Codacy PR issues via API v3")
    parser.add_argument("pr", type=int, help="Pull request number")
    parser.add_argument(
        "--status",
        default="new",
        choices=("new", "fixed", "all"),
        help="Issue filter (default: new)",
    )
    parser.add_argument("--owner", default="abapify")
    parser.add_argument("--repo", default="openadt")
    args = parser.parse_args()

    repo_root = Path(__file__).resolve().parents[1]
    load_dotenv(repo_root / ".env")

    token = os.environ.get("CODACY_API_TOKEN", "").strip()
    if not token:
        print(
            "error: CODACY_API_TOKEN not set. Save repo-root .env (see .env.example) or export the variable.",
            file=sys.stderr,
        )
        return 1

    try:
        base = validate_api_base(
            os.environ.get("CODACY_API_URL", "https://api.codacy.com/api/v3")
        )
    except ValueError as error:
        print(f"error: {error}", file=sys.stderr)
        return 1
    path = (
        f"/analysis/organizations/gh/{args.owner}/repositories/{args.repo}"
        f"/pull-requests/{args.pr}/issues?status={args.status}&limit=100"
    )
    status, payload = api_get(base, token, path)
    if status != 200:
        print(f"error: Codacy API HTTP {status}", file=sys.stderr)
        print(json.dumps(payload, indent=2)[:2000], file=sys.stderr)
        return 1

    if not isinstance(payload, dict):
        print(json.dumps(payload, indent=2))
        return 0

    analyzed = payload.get("analyzed")
    if analyzed is not None:
        print(f"analyzed={analyzed}")

    items = payload.get("data") or []
    if not items:
        print("no issues")
        return 0

    for item in items:
        if not isinstance(item, dict):
            continue
        file_path = item.get("filePath") or item.get("file") or "?"
        line = item.get("lineNumber") or item.get("line") or "?"
        level = item.get("level") or item.get("severity") or item.get("priority") or "?"
        message = item.get("message") or ""
        pattern = item.get("patternInfo") or {}
        if not message and isinstance(pattern, dict):
            message = pattern.get("title") or pattern.get("id") or ""
        print(f"[{level}] {file_path}:{line} {message}")

    print(f"total={len(items)} status={args.status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
