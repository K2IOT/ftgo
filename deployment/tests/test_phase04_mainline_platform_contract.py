import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
WORKFLOWS = ROOT / ".github" / "workflows"
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
    def test_phase_workflows_target_dev_pull_requests_and_remain_dispatchable(self):
        workflows = sorted(WORKFLOWS.glob("phase-*.yml"))
        self.assertTrue(workflows)
        for workflow in workflows:
            source = workflow.read_text(encoding="utf-8")
            if "pull_request:" in source:
                self.assertRegex(source, r"pull_request:\s*\n\s+branches:\s*\[dev\]")
            self.assertIn("workflow_dispatch:", source, workflow.name)

    def test_required_checks_run_for_dev_push_and_merge_queue(self):
        required = (
            "phase-01-verification.yml",
            "phase-01-full-gradle-verification.yml",
            "phase-01-module-diagnostics.yml",
            "phase-01-fresh-stack-smoke.yml",
            "phase-02-core-order-flow.yml",
            "phase-02-core-order-flow-e2e.yml",
            "phase-02b-payment-settlement.yml",
            "phase-03-distributed-consistency.yml",
            "phase-03-distributed-failure-e2e.yml",
            "phase-03-order-history-consistency.yml",
            "phase-03-operations-reconciliation.yml",
            "phase-04-security-api.yml",
            "phase-04-api-contract.yml",
        )
        for name in required:
            source = (WORKFLOWS / name).read_text(encoding="utf-8")
            self.assertIn("push:", source, name)
            self.assertRegex(source, r"branches:\s*\[dev\]", name)
            self.assertIn("merge_group:", source, name)

    def test_phase01_push_triggers_no_longer_target_historical_agent_branch(self):
        for workflow in sorted(WORKFLOWS.glob("phase-01-*.yml")):
            source = workflow.read_text(encoding="utf-8")
            self.assertNotIn("agent/phase-01-bootstrap", source, workflow.name)

    def test_dev_merge_verification_retests_exact_dev_sha(self):
        source = (WORKFLOWS / "dev-merge-verification.yml").read_text(encoding="utf-8")
        self.assertIn("branches: [dev]", source)
        self.assertIn("github.sha", source)
        self.assertIn("./gradlew clean test", source)
        self.assertIn("verify-fresh-stack.sh", source)
        self.assertIn("verify-core-order-flow.sh", source)
        self.assertIn("verify-payment-settlement.sh", source)
        self.assertIn("verify-distributed-consistency.sh", source)

    def test_secret_scanning_is_enforced_on_pr_dev_push_and_merge_queue(self):
        source = (WORKFLOWS / "secret-scan.yml").read_text(encoding="utf-8")
        self.assertIn("pull_request:", source)
        self.assertIn("push:", source)
        self.assertIn("branches: [dev]", source)
        self.assertIn("merge_group:", source)
        self.assertIn("gitleaks", source.lower())

    def test_base_service_configuration_contains_no_embedded_database_credentials(self):
        forbidden = (
            "jdbc:mysql://localhost",
            "username: root",
            "password: root",
            "cassandra://localhost",
        )
        for service in HTTP_SERVICES:
            base = (ROOT / service / "src/main/resources/application.yml").read_text(encoding="utf-8")
            for value in forbidden:
                self.assertNotIn(value, base, service)

    def test_local_profile_owns_developer_database_defaults(self):
        for service in HTTP_SERVICES:
            local = ROOT / service / "src/main/resources/application-local.yml"
            self.assertTrue(local.exists(), f"Missing local profile for {service}")
            source = local.read_text(encoding="utf-8")
            self.assertIn("spring:", source, service)

    def test_order_service_does_not_duplicate_hikaricp_dependency(self):
        root_build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        root_block = root_build[
            root_build.index("project(':order-service')"):root_build.index("project(':consumer-service')")
        ]
        module_build = (ROOT / "order-service" / "build.gradle").read_text(encoding="utf-8")
        explicit_hikari_declarations = (
            root_block.count("implementation 'com.zaxxer:HikariCP'")
            + module_build.count("implementation 'com.zaxxer:HikariCP'")
        )
        self.assertLessEqual(
            explicit_hikari_declarations,
            1,
            "HikariCP must not be declared more than once; zero explicit declarations is valid when JPA/Boot manages it transitively",
        )

    def test_repository_documents_dev_as_mainline(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        self.assertIn("`dev` is the integration mainline", readme)
        self.assertIn("Phase 04", readme)


if __name__ == "__main__":
    unittest.main()
