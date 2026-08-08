#!/usr/bin/env python3
"""Strip SARIF results that carry a non-empty ``suppressions`` array.

SkillSpector's ``--baseline`` flag marks accepted findings with a SARIF
``suppressions`` property so they remain in the audit trail but do not count
toward the risk score. GitHub Code Scanning does not honor SARIF
``suppressions`` natively, so those results would still appear as open alerts.
This filter removes them before ``github/codeql-action/upload-sarif`` while
preserving the tool metadata, rules, and all other SARIF structure.

Usage:
    python scripts/strip-skillspector-suppressions.py <input.sarif> <output.sarif>
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


def strip_suppressed(sarif: dict) -> dict:
    """Return *sarif* with suppressed results removed from every run."""
    if not isinstance(sarif, dict):
        raise ValueError("SARIF input must be a JSON object")
    for run in sarif.get("runs") or []:
        if not isinstance(run, dict):
            continue
        results = run.get("results") or []
        run["results"] = [r for r in results if not _is_suppressed(r)]
    return sarif


def _is_suppressed(result: object) -> bool:
    """Return True when a SARIF result has a non-empty suppressions list."""
    if not isinstance(result, dict):
        return False
    suppressions = result.get("suppressions")
    return isinstance(suppressions, list) and len(suppressions) > 0


def main(argv: list[str]) -> int:
    if len(argv) != 3:
        print(
            f"Usage: {argv[0]} <input.sarif> <output.sarif>",
            file=sys.stderr,
        )
        return 2
    input_path = Path(argv[1])
    output_path = Path(argv[2])
    try:
        data = json.loads(input_path.read_text(encoding="utf-8"))
        data = strip_suppressed(data)
        output_path.write_text(
            json.dumps(data, indent=2, ensure_ascii=False),
            encoding="utf-8",
        )
    except FileNotFoundError as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1
    except json.JSONDecodeError as e:
        print(f"Error: Invalid JSON in {argv[1]}: {e}", file=sys.stderr)
        return 1
    except ValueError as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1
    except OSError as e:
        print(f"Error: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
