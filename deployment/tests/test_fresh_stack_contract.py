import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPOSE = ROOT / "deployment/tests/docker-compose.fresh-stack.yml"
SMOKE = ROOT / "scripts/smoke/fresh-stack.sh"
WRAPPER = ROOT / "gradle/wrapper/gradle-wrapper.jar"


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

    def test_checked_in_wrapper_is_present(self):
        self.assertTrue(WRAPPER.is_file())
        self.assertGreater(WRAPPER.stat().st_size, 10_000)


if __name__ == "__main__":
    unittest.main()
