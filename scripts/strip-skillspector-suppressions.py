#!/usr/bin/env python3
"""Strip accepted external SARIF suppressions before GitHub upload.

SkillSpector's ``--baseline`` flag marks accepted findings with a SARIF
``suppressions`` object whose ``kind`` is ``external``. GitHub Code Scanning
does not honor SARIF ``suppressions`` natively, so this filter removes only
accepted external baseline suppressions before ``upload-sarif``. Rejected,
under-review, or in-source suppressions are preserved.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path


def strip_suppressed(sarif: dict) -> dict:
    """Return *sarif* with accepted external baseline-suppressed results removed."""
    if not isinstance(sarif, dict):
        raise ValueError("SARIF input must be a JSON object")

    runs = sarif.get("runs")
    if not isinstance(runs, list):
        raise ValueError("SARIF 'runs' must be an array")

    for run in runs:
        if not isinstance(run, dict):
            raise ValueError("Each SARIF run must be an object")

        results = run.get("results")
        if results is None:
            continue
        if not isinstance(results, list):
            raise ValueError("SARIF run 'results' must be an array")
        if not all(isinstance(r, dict) for r in results):
            raise ValueError("Each SARIF result must be an object")

        run["results"] = [r for r in results if not _is_suppressed(r)]

    return sarif


def _is_suppressed(result: object) -> bool:
    """Return True when a SARIF result has an accepted external suppression."""
    if not isinstance(result, dict):
        return False

    suppressions = result.get("suppressions")
    if not isinstance(suppressions, list):
        return False

    for entry in suppressions:
        if not isinstance(entry, dict):
            continue
        if entry.get("kind") != "external":
            continue
        status = entry.get("status", "accepted")
        if status == "accepted":
            return True

    return False


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
