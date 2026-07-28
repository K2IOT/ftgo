#!/usr/bin/env python3
"""Print a concise root-cause summary from Gradle JUnit XML reports."""

from __future__ import annotations

import glob
import sys
import xml.etree.ElementTree as ET


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: summarize-junit-failures.py <module>", file=sys.stderr)
        return 2

    module = sys.argv[1]
    failures: list[tuple[ET.Element, ET.Element]] = []
    pattern = f"{module}/build/test-results/test/*.xml"
    for filename in sorted(glob.glob(pattern)):
        root = ET.parse(filename).getroot()
        for testcase in root.iter("testcase"):
            failure = testcase.find("failure")
            if failure is None:
                failure = testcase.find("error")
            if failure is not None:
                failures.append((testcase, failure))

    print(f"FAILURE_COUNT {len(failures)}")
    if not failures:
        return 0

    testcase, failure = failures[0]
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
        for line in body_lines[:100]:
            print(f"DETAIL {line[:2000]}")
        if len(body_lines) > 100:
            print("DETAIL ...")
        for line in body_lines[-15:]:
            print(f"STACK {line[:2000]}")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
