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
import http.client
import json
import os
import sys
import urllib.parse
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
    url = f"{safe_base.rstrip('/')}{path}"
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https"):
        raise ValueError(f"Disallowed URL scheme: {parsed.scheme!r}")

    request_path = parsed.path
    if parsed.query:
        request_path = f"{request_path}?{parsed.query}"

    conn_class = (
        http.client.HTTPSConnection
        if parsed.scheme == "https"
        else http.client.HTTPConnection
    )
    conn = conn_class(parsed.netloc, timeout=60)
    try:
        conn.request(
            "GET",
            request_path,
            headers={"api-token": token, "Accept": "application/json"},
        )
        response = conn.getresponse()
        body = response.read().decode(errors="replace")
        try:
            payload = json.loads(body) if body else {}
        except json.JSONDecodeError:
            payload = body[:500]
        return response.status, payload
    finally:
        conn.close()


def flatten_issue(item: dict) -> dict:
    """Codacy PR issues nest fields under commitIssue."""
    nested = item.get("commitIssue")
    if isinstance(nested, dict):
        merged = {**nested, **item}
        merged.pop("commitIssue", None)
        return merged
    return item


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

    for raw in items:
        if not isinstance(raw, dict):
            continue
        item = flatten_issue(raw)
        file_path = item.get("filePath") or item.get("file") or "?"
        line = item.get("lineNumber") or item.get("line") or "?"
        pattern = item.get("patternInfo") or {}
        level = (
            item.get("level")
            or item.get("severity")
            or item.get("priority")
            or (pattern.get("level") if isinstance(pattern, dict) else None)
            or (pattern.get("severityLevel") if isinstance(pattern, dict) else None)
            or "?"
        )
        message = item.get("message") or ""
        if not message and isinstance(pattern, dict):
            message = pattern.get("title") or pattern.get("id") or ""
        pattern_id = pattern.get("id") if isinstance(pattern, dict) else ""
        suffix = f" ({pattern_id})" if pattern_id and pattern_id not in message else ""
        print(f"[{level}] {file_path}:{line} {message}{suffix}")

    print(f"total={len(items)} status={args.status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
