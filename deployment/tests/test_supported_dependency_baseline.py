import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD = ROOT / "build.gradle"
WRAPPER = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"
DEPENDENCY_AUDIT = ROOT / "scripts" / "ci" / "verify-supported-dependencies.sh"
PLATFORM_SECURITY_WORKFLOW = ROOT / ".github" / "workflows" / "remediation-10-platform-security.yml"
UPGRADE_RUNBOOK = ROOT / "docs" / "operations" / "spring-platform-upgrade-runbook.md"
WORKFLOWS = ROOT / ".github" / "workflows"


class SupportedDependencyBaselineTest(unittest.TestCase):

    def build(self):
        return BUILD.read_text(encoding="utf-8")

    def all_builds(self):
        paths = [BUILD, *sorted(ROOT.glob("*/build.gradle"))]
        return "\n".join(
            f"// {path.relative_to(ROOT)}\n{path.read_text(encoding='utf-8')}"
            for path in paths
        )

    def wrapper(self):
        return WRAPPER.read_text(encoding="utf-8")

    def test_spring_platform_is_on_supported_final_train(self):
        source = self.build()
        boot = re.search(
            r"id 'org\.springframework\.boot' version '([^']+)'", source
        )
        cloud = re.search(
            r"mavenBom 'org\.springframework\.cloud:spring-cloud-dependencies:([^']+)'",
            source,
        )
        self.assertIsNotNone(boot)
        self.assertIsNotNone(cloud)
        self.assertEqual("4.0.7", boot.group(1))
        self.assertEqual("2025.1.2", cloud.group(1))

    def test_gradle_and_dependency_management_plugin_match_upgrade_baseline(self):
        source = self.build()
        wrapper = self.wrapper()
        self.assertIn("id 'io.spring.dependency-management' version '1.1.7'", source)
        self.assertIn("gradleVersion = '8.14.3'", source)
        self.assertIn("gradle-8.14.3-all.zip", wrapper)

    def test_eventuate_uses_single_supported_platform_bom(self):
        source = self.build()
        self.assertIn(
            "mavenBom 'io.eventuate.platform:eventuate-platform-dependencies:2024.0.RELEASE'",
            source,
        )
        self.assertNotIn("io.eventuate.tram.core:eventuate-tram-bom", source)
        self.assertNotIn("io.eventuate.tram.sagas:eventuate-tram-sagas-bom", source)
        self.assertEqual(1, source.count("io.eventuate.platform:eventuate-platform-dependencies"))

    def test_spring_milestone_repository_is_not_used(self):
        self.assertNotIn("repo.spring.io/milestone", self.all_builds())

    def test_boot_managed_dependencies_do_not_repeat_versions(self):
        source = self.all_builds()
        managed_coordinates = (
            "org.testcontainers:testcontainers",
            "org.testcontainers:testcontainers-junit-jupiter",
            "org.testcontainers:testcontainers-kafka",
            "org.testcontainers:testcontainers-mysql",
            "org.testcontainers:testcontainers-cassandra",
            "org.awaitility:awaitility",
            "jakarta.persistence:jakarta.persistence-api",
        )
        for coordinate in managed_coordinates:
            self.assertNotRegex(
                source,
                rf"{re.escape(coordinate)}:[^'\"\s]+",
                f"{coordinate} should inherit its version from the Spring Boot BOM",
            )

    def test_testcontainers_uses_v2_module_coordinates(self):
        source = self.all_builds()
        for legacy_coordinate in (
            "org.testcontainers:junit-jupiter",
            "org.testcontainers:kafka",
            "org.testcontainers:mysql",
            "org.testcontainers:cassandra",
        ):
            self.assertNotIn(legacy_coordinate, source)
        for current_coordinate in (
            "org.testcontainers:testcontainers-junit-jupiter",
            "org.testcontainers:testcontainers-kafka",
            "org.testcontainers:testcontainers-mysql",
            "org.testcontainers:testcontainers-cassandra",
        ):
            self.assertIn(current_coordinate, source)

    def test_unmanaged_test_dependencies_are_centralized(self):
        source = self.build()
        all_builds = self.all_builds()
        self.assertIn("nimbusJoseJwtVersion = '9.37.4'", source)
        self.assertEqual(1, source.count("nimbusJoseJwtVersion ="))
        self.assertNotIn("com.nimbusds:nimbus-jose-jwt:9.37.3", all_builds)
        self.assertNotRegex(all_builds, r"com\.nimbusds:nimbus-jose-jwt:[0-9]")
        self.assertIn(
            'com.nimbusds:nimbus-jose-jwt:${rootProject.ext.nimbusJoseJwtVersion}',
            all_builds,
        )

        self.assertIn("restAssuredVersion = '6.0.1'", source)
        self.assertEqual(1, source.count("restAssuredVersion ="))
        self.assertNotRegex(all_builds, r"io\.rest-assured:rest-assured:[0-9]")
        self.assertIn(
            'io.rest-assured:rest-assured:${rootProject.ext.restAssuredVersion}',
            all_builds,
        )

    def test_platform_upgrade_has_dependency_and_vulnerability_gates(self):
        self.assertTrue(DEPENDENCY_AUDIT.is_file(), "dependency audit script is required")
        self.assertTrue(PLATFORM_SECURITY_WORKFLOW.is_file(), "platform security workflow is required")
        self.assertTrue(UPGRADE_RUNBOOK.is_file(), "Spring platform upgrade runbook is required")

        audit = DEPENDENCY_AUDIT.read_text(encoding="utf-8")
        for dependency in (
            "spring-security",
            "netty",
            "jackson",
            "kafka",
            "mysql",
            "testcontainers",
        ):
            self.assertIn(dependency, audit)
        self.assertIn("dependencyInsight", audit)

        workflow = PLATFORM_SECURITY_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("./gradlew bootJar", workflow)
        self.assertIn("aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25", workflow)
        self.assertRegex(workflow, r"scan-type:\s*['\"]?fs['\"]?")
        self.assertRegex(workflow, r"severity:\s*['\"]HIGH,CRITICAL['\"]")
        self.assertRegex(workflow, r"exit-code:\s*['\"]1['\"]")

    def test_github_workflows_use_supported_gradle_setup(self):
        workflows = sorted(WORKFLOWS.glob("*.yml"))
        self.assertTrue(workflows, "GitHub workflows are required")
        for workflow_path in workflows:
            workflow = workflow_path.read_text(encoding="utf-8")
            self.assertNotIn(
                "Gradle 8.5",
                workflow,
                f"{workflow_path.name} still references the old Gradle 8.5 wrapper",
            )
            self.assertNotRegex(
                workflow,
                r"actions/(?:checkout|setup-java)@v\d+",
                f"{workflow_path.name} must pin checkout/setup-java by commit SHA",
            )
            if "./gradlew" in workflow:
                self.assertIn(
                    "scripts/ci/verify-gradle-wrapper.sh",
                    workflow,
                    f"{workflow_path.name} must verify the Gradle wrapper before running Gradle",
                )

    def test_final_verification_entrypoints_match_plan(self):
        gates = {
            "scripts/smoke/verify-fresh-stack.sh": ".github/workflows/phase-01-fresh-stack.yml",
            "scripts/smoke/verify-core-order-flow.sh": ".github/workflows/phase-02-core-order-flow-e2e.yml",
            "scripts/smoke/verify-payment-settlement.sh": ".github/workflows/phase-02b-payment-settlement.yml",
            "scripts/smoke/verify-distributed-consistency.sh": ".github/workflows/phase-03-distributed-failure-e2e.yml",
        }
        for script_name, workflow_name in gates.items():
            script = ROOT / script_name
            workflow = ROOT / workflow_name
            self.assertTrue(script.is_file(), f"{script_name} is required by the final verification plan")
            self.assertIn(
                f"bash {script_name} --runs 2",
                workflow.read_text(encoding="utf-8"),
                f"{workflow_name} must execute the exact two-run verification entrypoint",
            )


if __name__ == "__main__":
    unittest.main()
