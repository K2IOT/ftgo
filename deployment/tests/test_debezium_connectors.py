import json
import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]
CONNECTOR_DIR = ROOT / "debezium" / "connectors"
REGISTER_SCRIPT = ROOT / "configure-debezium.sh"

EXPECTED = {
    "order": ("ftgo_order", "mysql-order", "101"),
    "consumer": ("ftgo_consumer", "mysql-consumer", "102"),
    "restaurant": ("ftgo_restaurant", "mysql-restaurant", "103"),
    "kitchen": ("ftgo_kitchen", "mysql-kitchen", "104"),
    "accounting": ("ftgo_accounting", "mysql-accounting", "105"),
    "delivery": ("ftgo_delivery", "mysql-delivery", "106"),
}


class DebeziumConnectorContractTest(unittest.TestCase):

    def test_every_service_has_current_outbox_router_configuration(self):
        self.assertTrue(CONNECTOR_DIR.is_dir(), f"Missing {CONNECTOR_DIR}")

        names = set()
        server_ids = set()
        topic_prefixes = set()

        for service, (database, hostname, server_id) in EXPECTED.items():
            connector_file = CONNECTOR_DIR / f"{service}-outbox.json"
            self.assertTrue(connector_file.is_file(), f"Missing {connector_file}")

            document = json.loads(connector_file.read_text(encoding="utf-8"))
            config = document["config"]

            self.assertEqual(f"{service}-outbox-connector", document["name"])
            self.assertEqual("io.debezium.connector.mysql.MySqlConnector", config["connector.class"])
            self.assertEqual(hostname, config["database.hostname"])
            self.assertEqual("3306", config["database.port"])
            self.assertEqual(server_id, config["database.server.id"])
            self.assertEqual("true", config["database.allowPublicKeyRetrieval"])
            self.assertEqual("disabled", config["database.ssl.mode"])
            self.assertEqual(database, config["database.include.list"])
            self.assertEqual(f"{database}.outbox", config["table.include.list"])

            self.assertEqual(f"ftgo-{service}", config["topic.prefix"])
            self.assertEqual(
                "kafka-1:9092,kafka-2:9092,kafka-3:9092",
                config["schema.history.internal.kafka.bootstrap.servers"],
            )
            self.assertEqual(
                f"schema-history.ftgo-{service}",
                config["schema.history.internal.kafka.topic"],
            )

            self.assertEqual("outbox", config["transforms"])
            self.assertEqual(
                "io.debezium.transforms.outbox.EventRouter",
                config["transforms.outbox.type"],
            )
            self.assertEqual("id", config["transforms.outbox.table.field.event.id"])
            self.assertEqual("aggregate_id", config["transforms.outbox.table.field.event.key"])
            self.assertEqual("event_type", config["transforms.outbox.table.field.event.type"])
            self.assertEqual("payload", config["transforms.outbox.table.field.event.payload"])
            self.assertEqual("true", config["transforms.outbox.table.expand.json.payload"])
            self.assertEqual("destination", config["transforms.outbox.route.by.field"])
            self.assertEqual("${routedByValue}", config["transforms.outbox.route.topic.replacement"])
            self.assertEqual(
                "event_type:header:eventType",
                config["transforms.outbox.table.fields.additional.placement"],
            )

            self.assertNotIn("database.server.name", config)
            self.assertNotIn("database.history.kafka.bootstrap.servers", config)
            self.assertNotIn("database.history.kafka.topic", config)
            self.assertNotIn("destination:envelope:destination", config.values())

            names.add(document["name"])
            server_ids.add(config["database.server.id"])
            topic_prefixes.add(config["topic.prefix"])

        self.assertEqual(len(EXPECTED), len(names))
        self.assertEqual(len(EXPECTED), len(server_ids))
        self.assertEqual(len(EXPECTED), len(topic_prefixes))

    def test_registration_is_idempotent_and_verifies_running_state(self):
        script = REGISTER_SCRIPT.read_text(encoding="utf-8")
        self.assertIn("--request PUT", script)
        self.assertIn("--fail-with-body", script)
        self.assertIn("/connectors/${connector_name}/config", script)
        self.assertIn("/connectors/${connector_name}/status", script)
        self.assertIn('"RUNNING"', script)
        self.assertNotIn("curl -X POST http://localhost:8083/connectors", script)


if __name__ == "__main__":
    unittest.main()
