#!/usr/bin/env python3
import json
import pathlib
import sys


def fail(message: str) -> int:
    print(f"ERROR: {message}", file=sys.stderr)
    return 1


def main() -> int:
    if len(sys.argv) != 2:
        return fail("usage: verify-trivy-java-report.py <trivy-results.json>")

    report_path = pathlib.Path(sys.argv[1])
    if not report_path.is_file():
        return fail(f"Trivy report does not exist: {report_path}")

    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        return fail(f"cannot read Trivy JSON report {report_path}: {exc}")

    results = report.get("Results") or []
    jar_results = []
    package_count = 0

    for result in results:
        if str(result.get("Type", "")).lower() != "jar":
            continue
        packages = result.get("Packages") or []
        if not packages:
            continue
        jar_results.append(result)
        package_count += len(packages)

    if not jar_results:
        return fail(
            "Trivy did not report any Java JAR packages; refusing a vulnerability-scan false green"
        )

    print(f"trivy_java_jar_results={len(jar_results)}")
    print(f"trivy_java_packages={package_count}")
    for result in jar_results:
        print(f"trivy_java_target={result.get('Target', '<unknown>')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
