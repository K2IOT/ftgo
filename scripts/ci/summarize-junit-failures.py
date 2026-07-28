#!/usr/bin/env python3
"""Print a concise root-cause summary from Gradle JUnit XML reports."""

from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET


LOG_MARKERS = (
    "Circuit breaker fallback",
    "Fetching order details",
    "Failed to fetch",
    "Verified FTGO JWT",
    "WebClient",
    "Connection refused",
    "ERROR",
    "Exception",
)


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: summarize-junit-failures.py <module>", file=sys.stderr)
        return 2

    module = sys.argv[1]
    failures: list[tuple[ET.Element, ET.Element, ET.Element]] = []
    pattern = f"{module}/build/test-results/test/*.xml"
    for filename in sorted(glob.glob(pattern)):
        root = ET.parse(filename).getroot()
        for testcase in root.iter("testcase"):
            failure = testcase.find("failure")
            if failure is None:
                failure = testcase.find("error")
            if failure is not None:
                failures.append((testcase, failure, root))

    print(f"FAILURE_COUNT {len(failures)}")
    if not failures:
        return 0

    testcase, failure, suite = failures[0]
    print(
        "FAILED_TEST "
        f"{testcase.attrib.get('classname', '')}#{testcase.attrib.get('name', '')}"
    )

    body_lines = (failure.text or "").splitlines()
    causes = [
        line.strip()
        for line in body_lines
        if any(
            marker in line
            for marker in (
                "Caused by:",
                "BeanCreationException",
                "UnsatisfiedDependencyException",
                "NoSuchBeanDefinitionException",
                "BeanDefinitionOverrideException",
                "IllegalArgumentException:",
                "ConnectException",
                "BindException",
            )
        )
    ]
    if causes:
        for line in causes[-20:]:
            print(f"ROOT_CAUSE {line[:2000]}")
    else:
        message = failure.attrib.get("message", "")
        print(f"MESSAGE {message[:2000]}")
        for line in body_lines[:30]:
            print(f"DETAIL {line[:2000]}")

    test_logs = "\n".join(
        filter(
            None,
            (
                suite.findtext("system-out", default=""),
                suite.findtext("system-err", default=""),
            ),
        )
    ).splitlines()
    relevant_logs = [line for line in test_logs if any(marker in line for marker in LOG_MARKERS)]
    if relevant_logs:
        print("TEST_LOG_CAUSES")
        for line in relevant_logs[-80:]:
            print(line[:2400])
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
