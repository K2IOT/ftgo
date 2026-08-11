import hashlib
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "deployment/tests/docker-compose.fresh-stack.yml"
SMOKE = ROOT / "scripts/smoke/fresh-stack.sh"
DELIVERY_PICKUP_BRIDGE = ROOT / "scripts/smoke/assert-delivery-pickup-bridge.sh"
BOOTSTRAP = ROOT / "scripts/ci/bootstrap-gradle.sh"
VERIFY = ROOT / "scripts/ci/verify-gradle-wrapper.sh"
WRAPPER_JAR = ROOT / "gradle/wrapper/gradle-wrapper.jar"
WRAPPER_PROPERTIES = ROOT / "gradle/wrapper/gradle-wrapper.properties"
GRADLEW = ROOT / "gradlew"
GRADLEW_BAT = ROOT / "gradlew.bat"


class FreshStackContractTest(unittest.TestCase):
    def test_smoke_compose_uses_pinned_images_and_healthchecks(self):
        content = COMPOSE.read_text()
        self.assertNotIn(":latest", content)
        for image in (
            "mysql:8.0.36",
            "confluentinc/cp-kafka:7.5.0",
            "redis:7.2.4-alpine",
            "scylladb/scylla:5.2",
        ):
            self.assertIn(f"image: {image}", content)
        self.assertEqual(4, content.count("healthcheck:"))

    def test_smoke_script_checks_every_runtime_and_repeats(self):
        content = SMOKE.read_text()
        for module in (
            "api-gateway",
            "order-service",
            "consumer-service",
            "restaurant-service",
            "kitchen-service",
            "accounting-service",
            "delivery-service",
            "order-history-service",
        ):
            self.assertIn(module, content)
        self.assertIn('FRESH_STACK_RUNS:-2', content)
        self.assertIn('assert-order-event.sh', content)

    def test_delivery_starts_without_restaurant_bridge_configuration(self):
        smoke = SMOKE.read_text()

        self.assertFalse(DELIVERY_PICKUP_BRIDGE.exists())
        self.assertNotIn('run_delivery_pickup_bridge_smoke', smoke)
        self.assertNotIn('assert-delivery-pickup-bridge.sh', smoke)
        self.assertNotIn('RESTAURANT_SERVICE_URL=', smoke)
        self.assertIn('boot_and_assert "${run_number}" delivery-service 8086', smoke)

    def test_gradle_wrapper_and_bootstrap_are_checksum_pinned(self):
        for path in (BOOTSTRAP, VERIFY, WRAPPER_JAR, WRAPPER_PROPERTIES, GRADLEW, GRADLEW_BAT):
            self.assertTrue(path.is_file())

        self.assertEqual(
            "7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172",
            hashlib.sha256(WRAPPER_JAR.read_bytes()).hexdigest(),
        )

        wrapper = WRAPPER_PROPERTIES.read_text()
        self.assertIn("gradle-8.14.3-all.zip", wrapper)
        self.assertIn(
            "distributionSha256Sum=ed1a8d686605fd7c23bdf62c7fc7add1c5b23b2bbc3721e661934ef4a4911d7c",
            wrapper,
        )

        bootstrap = BOOTSTRAP.read_text()
        self.assertIn('GRADLE_VERSION="8.14.3"', bootstrap)
        self.assertIn('gradle-${GRADLE_VERSION}-bin.zip', bootstrap)
        self.assertIn("https://services.gradle.org/distributions/", bootstrap)
        self.assertIn("bd71102213493060956ec229d946beee57158dbd89d0e62b91bca0fa2c5f3531", bootstrap)
        self.assertIn('gradle/wrapper/gradle-wrapper.jar', GRADLEW.read_text())
        self.assertIn('-jar "$APP_HOME/gradle/wrapper/gradle-wrapper.jar"', GRADLEW.read_text())


if __name__ == "__main__":
    unittest.main()
