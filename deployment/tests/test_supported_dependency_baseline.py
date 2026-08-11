import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
BUILD = ROOT / "build.gradle"
WRAPPER = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"


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
        self.assertNotIn("repo.spring.io/milestone", self.all_builds())

    def test_platform_managed_dependencies_do_not_repeat_versions(self):
        source = self.all_builds()
        managed_coordinates = (
            "org.testcontainers:testcontainers",
            "org.testcontainers:junit-jupiter",
            "org.testcontainers:kafka",
            "org.testcontainers:mysql",
            "org.testcontainers:cassandra",
            "io.rest-assured:rest-assured",
            "org.awaitility:awaitility",
            "jakarta.persistence:jakarta.persistence-api",
        )
        for coordinate in managed_coordinates:
            self.assertNotRegex(
                source,
                rf"{re.escape(coordinate)}:[^'\"\s]+",
                f"{coordinate} should inherit its version from the platform BOM",
            )

    def test_unmanaged_nimbus_version_is_centralized_and_patched(self):
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


if __name__ == "__main__":
    unittest.main()
