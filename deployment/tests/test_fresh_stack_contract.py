import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "deployment/tests/docker-compose.fresh-stack.yml"
SMOKE = ROOT / "scripts/smoke/fresh-stack.sh"
BOOTSTRAP = ROOT / "scripts/ci/bootstrap-gradle-wrapper.sh"
VERIFY = ROOT / "scripts/ci/verify-gradle-wrapper.sh"


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

    def test_wrapper_bootstrap_is_checksum_pinned(self):
        self.assertTrue(BOOTSTRAP.is_file())
        self.assertTrue(VERIFY.is_file())
        bootstrap = BOOTSTRAP.read_text()
        self.assertIn('GRADLE_VERSION="8.5"', bootstrap)
        self.assertIn("https://services.gradle.org/distributions/", bootstrap)
        self.assertIn("wrapper.jar", bootstrap)
        self.assertIn("d3b261c2820e9e3d8d639ed084900f11f4a86050a8f83342ade7b6bc9b0d2bdd", bootstrap)


if __name__ == "__main__":
    unittest.main()
