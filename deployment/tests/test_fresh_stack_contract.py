import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "deployment/tests/docker-compose.fresh-stack.yml"
SMOKE = ROOT / "scripts/smoke/fresh-stack.sh"
DELIVERY_PICKUP_BRIDGE = ROOT / "scripts/smoke/assert-delivery-pickup-bridge.sh"
BOOTSTRAP = ROOT / "scripts/ci/bootstrap-gradle.sh"
VERIFY = ROOT / "scripts/ci/verify-gradle-wrapper.sh"
GRADLEW = ROOT / "gradlew"


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

    def test_smoke_verifies_delivery_pickup_bridge_with_real_restaurant_service(self):
        self.assertTrue(DELIVERY_PICKUP_BRIDGE.is_file())
        smoke = SMOKE.read_text()
        bridge = DELIVERY_PICKUP_BRIDGE.read_text()

        self.assertIn('run_delivery_pickup_bridge_smoke', smoke)
        self.assertIn('assert-delivery-pickup-bridge.sh', smoke)
        self.assertIn('RESTAURANT_SERVICE_URL="http://localhost:8083"', smoke)
        self.assertIn('/internal/restaurants/', bridge)
        self.assertIn('application/problem+json', bridge)
        self.assertIn('RESTAURANT_NOT_FOUND', bridge)

    def test_gradle_bootstrap_is_distribution_checksum_pinned(self):
        self.assertFalse((ROOT / "gradle/wrapper/gradle-wrapper.jar").exists())
        for path in (BOOTSTRAP, VERIFY, GRADLEW):
            self.assertTrue(path.is_file())
        bootstrap = BOOTSTRAP.read_text()
        self.assertIn('GRADLE_VERSION="8.5"', bootstrap)
        self.assertIn('gradle-${GRADLE_VERSION}-bin.zip', bootstrap)
        self.assertIn("https://services.gradle.org/distributions/", bootstrap)
        self.assertIn("9d926787066a081739e8200858338b4a69e837c3a821a33aca9db09dd4a41026", bootstrap)
        self.assertIn("bootstrap-gradle.sh", GRADLEW.read_text())


if __name__ == "__main__":
    unittest.main()
