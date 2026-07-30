import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]


class OrderIdempotencyContractTest(unittest.TestCase):

    def test_additive_migration_defines_durable_claim_and_replay_fields(self):
        migration = ROOT / "order-service/src/main/resources/db/migration/V10__create_api_idempotency_records.sql"
        self.assertTrue(migration.exists(), f"missing migration: {migration}")
        sql = migration.read_text(encoding="utf-8").lower()

        required_fragments = (
            "create table api_idempotency_records",
            "consumer_id",
            "operation",
            "idempotency_key",
            "request_hash",
            "state",
            "http_status",
            "response_json",
            "resource_id",
            "expires_at",
            "primary key (consumer_id, operation, idempotency_key)",
        )
        for fragment in required_fragments:
            self.assertIn(fragment, sql)

        self.assertRegex(sql, re.compile(r"state\s+varchar\([^)]*\)\s+not null"))
        self.assertNotIn("drop table", sql)
        self.assertNotIn("drop column", sql)

    def test_core_order_flow_harness_uses_the_dedicated_e2e_compose_model(self):
        script = ROOT / "scripts/smoke/verify-core-order-flow.sh"
        self.assertTrue(script.exists(), f"missing core E2E harness: {script}")
        text = script.read_text(encoding="utf-8")

        self.assertIn("deployment/tests/docker-compose.core-order-flow.yml", text)
        self.assertNotIn("deployment/docker-compose.infra.yml", text)
        self.assertIn("mysql redis scylla zookeeper kafka connect", text)
        self.assertIn("'net.ftgo.e2e.CoreOrderFlowTest'", text)
        self.assertIn("'net.ftgo.e2e.SecurityAuthorizationTest'", text)
        self.assertIn("'net.ftgo.e2e.ApiAbuseTest'", text)
        self.assertIn("'net.ftgo.e2e.OrderMutationIdempotencyE2ETest'", text)


if __name__ == "__main__":
    unittest.main()
