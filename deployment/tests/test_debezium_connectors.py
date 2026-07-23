import json
import os
import pathlib
import subprocess
import tempfile
import textwrap
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

    def test_status_poll_retries_transient_not_found_after_registration(self):
        with tempfile.TemporaryDirectory() as directory:
            temporary = pathlib.Path(directory)
            fake_bin = temporary / "bin"
            fake_bin.mkdir()
            state_file = temporary / "status-attempts"
            fake_curl = fake_bin / "curl"
            fake_curl.write_text(
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    set -euo pipefail
                    url="${!#}"

                    if [[ "${url}" == "${DEBEZIUM_CONNECT_URL}/connectors" ]]; then
                      printf '%s\n' '[]'
                      exit 0
                    fi

                    if [[ "${url}" == */config ]]; then
                      cat >/dev/null
                      printf '%s\n' '{"name":"order-outbox-connector","config":{}}'
                      exit 0
                    fi

                    if [[ "${url}" == */status ]]; then
                      attempt=0
                      if [[ -f "${FAKE_CURL_STATE}" ]]; then
                        attempt="$(cat "${FAKE_CURL_STATE}")"
                      fi
                      attempt=$((attempt + 1))
                      printf '%s' "${attempt}" >"${FAKE_CURL_STATE}"

                      if [[ "${attempt}" -eq 1 ]]; then
                        printf '%s\n' '{"error_code":404,"message":"connector not visible yet"}' >&2
                        exit 22
                      fi

                      printf '%s\n' '{"connector":{"state":"RUNNING"},"tasks":[{"state":"RUNNING"}]}'
                      exit 0
                    fi

                    printf 'Unexpected curl URL: %s\n' "${url}" >&2
                    exit 2
                    """
                ),
                encoding="utf-8",
            )
            fake_curl.chmod(0o755)

            environment = os.environ.copy()
            environment.update(
                {
                    "PATH": f"{fake_bin}{os.pathsep}{environment['PATH']}",
                    "FAKE_CURL_STATE": str(state_file),
                    "DEBEZIUM_CONNECT_URL": "http://fake-connect",
                    "DEBEZIUM_CONNECTOR_GLOB": "order-outbox.json",
                    "DEBEZIUM_CONNECT_ATTEMPTS": "1",
                    "DEBEZIUM_STATUS_ATTEMPTS": "3",
                }
            )

            result = subprocess.run(
                ["bash", str(REGISTER_SCRIPT)],
                cwd=ROOT.parent,
                env=environment,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stdout)
            self.assertEqual("2", state_file.read_text(encoding="utf-8"))
            self.assertIn("Connector order-outbox-connector is RUNNING", result.stdout)


if __name__ == "__main__":
    unittest.main()
