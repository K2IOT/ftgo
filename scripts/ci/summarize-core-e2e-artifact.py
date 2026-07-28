#!/usr/bin/env python3
"""Print concise Core Order Flow E2E failures from an extracted diagnostics artifact."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else "diagnostics")
    print("CORE_FAILURE_SUMMARY")
    found = False

    for xml_path in sorted(root.rglob("TEST-*.xml")):
        try:
            suite = ET.parse(xml_path).getroot()
        except ET.ParseError:
            continue
        for case in suite.findall("testcase"):
            node = case.find("failure")
            if node is None:
                node = case.find("error")
            if node is None:
                continue
            found = True
            print(f"FAILED_TEST {case.get('classname')}#{case.get('name')}")
            message = (node.get("message") or "").replace("\n", " ")
            print(f"MESSAGE {message[:1200]}")
            for line in (node.text or "").splitlines()[:12]:
                print(f"DETAIL {line[:1200]}")

    runner_logs = sorted(root.rglob("runner.log"))
    if runner_logs:
        lines = runner_logs[-1].read_text(encoding="utf-8", errors="replace").splitlines()
        interesting = [
            line
            for line in lines
            if re.search(
                r"failed: status=|AssertionError|expected:|but was:|FAILED|did not become",
                line,
                re.IGNORECASE,
            )
        ]
        for line in interesting[-20:]:
            print(f"RUNNER {line[:1200]}")

    service_pattern = re.compile(
        r"ERROR|Exception|Caused by:|Jwt|JWK|Connection refused|Unsupported|\b500\b|\b503\b",
        re.IGNORECASE,
    )
    for service_name in ("order-service.log", "api-gateway.log"):
        service_logs = sorted(root.rglob(service_name))
        if not service_logs:
            continue
        path = service_logs[-1]
        lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
        matches = [line for line in lines if service_pattern.search(line)]
        print(f"SERVICE_LOG {path}")
        for line in matches[-30:]:
            print(f"SERVICE {line[:1600]}")
        print(f"SERVICE_TAIL {service_name}")
        for line in lines[-12:]:
            print(f"TAIL {line[:1600]}")

    if not found:
        print("NO_JUNIT_FAILURE_FOUND")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
