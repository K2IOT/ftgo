import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOW_ROOT = ROOT / ".github" / "workflows"

ALL_PHASE_WORKFLOWS = (
    "phase-01-debezium-smoke.yml",
    "phase-01-fresh-stack.yml",
    "phase-01-full-test.yml",
    "phase-01-module-diagnostics.yml",
    "phase-01-verification.yml",
    "phase-02-core-order-flow-e2e.yml",
    "phase-02-core-order-flow.yml",
    "phase-02b-payment-settlement.yml",
    "phase-03-distributed-consistency.yml",
    "phase-03-distributed-failure-e2e.yml",
    "phase-03-operations.yml",
    "phase-03-order-history.yml",
    "phase-04-api-contract.yml",
    "phase-04-security-api.yml",
    "phase-04-web-diagnostic.yml",
)

REQUIRED_MERGE_CHECKS = (
    "phase-01-full-test.yml",
    "phase-01-verification.yml",
    "phase-04-api-contract.yml",
    "phase-04-security-api.yml",
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

    def workflow(self, name):
        return (WORKFLOW_ROOT / name).read_text(encoding="utf-8")

    def test_phase_workflows_target_dev_instead_of_historical_agent_branches(self):
        for name in ALL_PHASE_WORKFLOWS:
            source = self.workflow(name)
            self.assertIn("pull_request:", source, name)
            self.assertIn("- dev", source, name)
            self.assertIn("workflow_dispatch:", source, name)
            self.assertNotIn("agent/ftgo-phase-01-runtime-foundation", source, name)
            self.assertNotIn("agent/ftgo-phase-02", source, name)
            self.assertNotIn("agent/ftgo-phase-03", source, name)

    def test_required_checks_run_for_dev_push_and_merge_queue(self):
        for name in REQUIRED_MERGE_CHECKS:
            source = self.workflow(name)
            push = source[source.index("push:"):source.index("pull_request:")]
            self.assertIn("- dev", push, name)
            self.assertIn("merge_group:", source, name)

    def test_dev_merge_verification_retests_exact_dev_sha(self):
        source = self.workflow("dev-merge-verification.yml")
        self.assertIn("push:", source)
        self.assertIn("- dev", source)
        self.assertIn("workflow_dispatch:", source)
        self.assertIn("./gradlew --no-daemon clean test", source)
        self.assertIn("python3 -m unittest discover -s deployment/tests -p 'test_*.py' -v", source)
        self.assertIn("github.sha", source)

    def test_secret_scanning_is_enforced_on_pr_and_dev_push(self):
        workflow = self.workflow("secret-scan.yml")
        scanner = (ROOT / "scripts/ci/scan-secrets.sh").read_text(encoding="utf-8")
        self.assertIn("pull_request:", workflow)
        self.assertIn("push:", workflow)
        self.assertIn("- dev", workflow)
        self.assertIn("merge_group:", workflow)
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

    def test_repository_documents_dev_as_mainline(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("dev", readme)
        self.assertIn("Phase 04", readme)


if __name__ == "__main__":
    unittest.main()
