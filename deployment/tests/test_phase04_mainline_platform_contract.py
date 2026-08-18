import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_ROOT = ROOT / ".github" / "workflows"
CI_WORKFLOW = "ci.yml"

EXPECTED_CI_JOBS = (
    "validate:",
    "build:",
    "unit-test:",
    "contract-test:",
    "dependency-security:",
    "smoke-test:",
    "integration-test:",
    "ci-gate:",
)

HTTP_SERVICES = (
    "api-gateway",
    "order-service",
    "consumer-service",
    "restaurant-service",
    "kitchen-service",
    "accounting-service",
    "delivery-service",
    "order-history-service",
)


class Phase04MainlinePlatformContractTest(unittest.TestCase):

    def workflow(self):
        return (WORKFLOW_ROOT / CI_WORKFLOW).read_text(encoding="utf-8")

    def test_repository_has_one_canonical_ci_workflow(self):
        workflow_files = sorted(
            path.name
            for path in WORKFLOW_ROOT.iterdir()
            if path.is_file() and path.suffix in {".yml", ".yaml"}
        )
        self.assertEqual([CI_WORKFLOW], workflow_files)

    def test_ci_targets_dev_pull_requests_pushes_merge_queue_and_manual_dispatch(self):
        source = self.workflow()
        self.assertIn("push:", source)
        self.assertIn("pull_request:", source)
        self.assertIn("- dev", source)
        self.assertIn("merge_group:", source)
        self.assertIn("workflow_dispatch:", source)

    def test_ci_retests_exact_event_sha(self):
        source = self.workflow()
        self.assertIn("ref: ${{ github.sha }}", source)
        self.assertIn('test "$(git rev-parse HEAD)" = "${GITHUB_SHA}"', source)

    def test_ci_exposes_one_standard_stage_graph(self):
        source = self.workflow()
        for job in EXPECTED_CI_JOBS:
            self.assertIn(job, source, job)

        self.assertIn("needs: validate", source)
        self.assertIn("needs: build", source)
        self.assertIn("needs: smoke-test", source)
        self.assertIn("name: Dependency & Vulnerability", source)
        self.assertIn("name: CI Gate", source)

    def test_ci_runs_full_build_test_contract_smoke_and_integration_matrix(self):
        source = self.workflow()
        self.assertIn("./gradlew clean assemble --no-daemon --stacktrace", source)
        self.assertIn("./gradlew clean test --no-daemon --stacktrace", source)
        self.assertNotIn("--continue", source)
        self.assertIn(
            "python3 -m unittest discover -s deployment/tests -p 'test_*.py' -v",
            source,
        )
        self.assertIn("deployment/tests/run-debezium-smoke.sh", source)
        self.assertIn("scripts/smoke/verify-fresh-stack.sh", source)
        self.assertIn("scripts/smoke/verify-core-order-flow.sh", source)
        self.assertIn("scripts/smoke/verify-payment-settlement.sh", source)
        self.assertIn("scripts/smoke/verify-distributed-consistency.sh", source)

    def test_secret_scanning_is_enforced_inside_canonical_ci(self):
        workflow = self.workflow()
        scanner = (ROOT / "scripts/ci/scan-secrets.sh").read_text(encoding="utf-8")
        self.assertIn("scripts/ci/scan-secrets.sh", workflow)
        self.assertIn("zricethezav/gitleaks:v8.24.3", scanner)
        self.assertIn("--no-banner", scanner)

    def test_base_service_configuration_contains_no_embedded_database_credentials(self):
        forbidden = ("ftgo_user", "ftgo_password", "createDatabaseIfNotExist=true")
        for service in HTTP_SERVICES:
            base = (ROOT / service / "src/main/resources/application.yml").read_text(
                encoding="utf-8"
            )
            for value in forbidden:
                self.assertNotIn(value, base, service)

    def test_local_profile_owns_developer_database_defaults(self):
        for service in HTTP_SERVICES:
            local = ROOT / service / "src/main/resources/application-local.yml"
            self.assertTrue(local.exists(), f"Missing local profile for {service}")
            source = local.read_text(encoding="utf-8")
            self.assertIn("spring:", source, service)

    def test_order_service_does_not_duplicate_hikaricp_dependency(self):
        build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        block = build[
            build.index("project(':order-service')"):
            build.index("project(':consumer-service')")
        ]
        self.assertLessEqual(block.count("implementation 'com.zaxxer:HikariCP'"), 1)

    def test_repository_documents_dev_as_mainline(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("`dev` is the integration mainline", readme)
        self.assertIn("Phase 04", readme)


if __name__ == "__main__":
    unittest.main()
