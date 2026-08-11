import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD = ROOT / "build.gradle"
WRAPPER = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"


class SupportedDependencyBaselineTest(unittest.TestCase):

    def build(self):
        return BUILD.read_text(encoding="utf-8")

    def wrapper(self):
        return WRAPPER.read_text(encoding="utf-8")

    def test_spring_platform_is_on_supported_bridge_or_final_train(self):
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
        self.assertIn(boot.group(1), {"3.5.15", "4.0.7"})
        self.assertIn(cloud.group(1), {"2025.0.3", "2025.1.2"})
        self.assertNotRegex(boot.group(1), r"^3\.2\.")
        self.assertNotRegex(cloud.group(1), r"^2023\.0\.")

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
        self.assertNotIn("repo.spring.io/milestone", self.build())

    def test_boot_managed_test_dependencies_do_not_repeat_versions(self):
        source = self.build()
        managed_coordinates = (
            "org.testcontainers:testcontainers",
            "org.testcontainers:junit-jupiter",
            "org.testcontainers:kafka",
            "org.testcontainers:mysql",
            "org.testcontainers:cassandra",
            "io.rest-assured:rest-assured",
            "org.awaitility:awaitility",
            "com.nimbusds:nimbus-jose-jwt",
        )
        for coordinate in managed_coordinates:
            self.assertNotRegex(
                source,
                rf"{re.escape(coordinate)}:[^'\"\s]+",
                f"{coordinate} should inherit its version from the platform BOM",
            )


if __name__ == "__main__":
    unittest.main()
