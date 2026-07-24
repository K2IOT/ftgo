import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
CONNECTOR_DIR = ROOT / "debezium" / "connectors"
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


if __name__ == "__main__":
    unittest.main()
