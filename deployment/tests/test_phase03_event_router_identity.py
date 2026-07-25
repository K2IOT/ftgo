import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
CONNECTOR_DIR = ROOT / "debezium" / "connectors"
SMOKE_SCRIPT = ROOT / "tests" / "run-debezium-smoke.sh"
SERVICES = ("order", "consumer", "restaurant", "kitchen", "accounting", "delivery")


class Phase03EventRouterIdentityContractTest(unittest.TestCase):

    def test_every_outbox_router_uses_stable_event_id(self):
        for service in SERVICES:
            connector_file = CONNECTOR_DIR / f"{service}-outbox.json"
            document = json.loads(connector_file.read_text(encoding="utf-8"))
            config = document["config"]

            self.assertEqual(
                "event_id",
                config["transforms.outbox.table.field.event.id"],
                f"{service} connector must use event_id instead of the numeric outbox row ID",
            )
            self.assertEqual(
                "aggregate_id",
                config["transforms.outbox.table.field.event.key"],
                f"{service} aggregate ID must remain the Kafka partition key",
            )

    def test_cdc_smoke_uses_phase03_outbox_schema_and_event_identity(self):
        source = SMOKE_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("event_id VARCHAR(36) NOT NULL", source)
        self.assertIn("schema_version INT NOT NULL", source)
        self.assertIn("aggregate_version BIGINT NOT NULL", source)
        self.assertIn("EVENT_ID=", source)
        self.assertIn("'eventId', '${EVENT_ID}'", source)
        self.assertIn("'schemaVersion', 1", source)
        self.assertIn("'aggregateVersion', 7", source)
        self.assertIn("grep -q \"id:${EVENT_ID}\"", source)


if __name__ == "__main__":
    unittest.main()
