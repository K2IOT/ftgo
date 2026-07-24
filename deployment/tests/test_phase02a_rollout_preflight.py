import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts" / "operations" / "phase-02a-rollout-preflight.sh"


class Phase02ARolloutPreflightTest(unittest.TestCase):

    def run_preflight(self, order_count: str, lag_output: str) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temp_dir:
            temp = Path(temp_dir)
            order_count_file = temp / "legacy-order-count.txt"
            lag_file = temp / "delivery-lag.txt"
            order_count_file.write_text(order_count, encoding="utf-8")
            lag_file.write_text(lag_output, encoding="utf-8")

            env = os.environ.copy()
            env.update({
                "PHASE02A_ORDER_COUNT_FILE": str(order_count_file),
                "PHASE02A_KAFKA_LAG_FILE": str(lag_file),
                "PHASE02A_LAG_STABILITY_SECONDS": "0",
            })
            return subprocess.run(
                ["bash", str(SCRIPT)],
                cwd=ROOT,
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )

    def test_clean_orders_and_zero_delivery_lag_pass(self) -> None:
        result = self.run_preflight("0\n", self.lag_output(0, 0, 0))

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Phase 02A rollout preflight passed", result.stdout)

    def test_legacy_in_flight_orders_block_rollout(self) -> None:
        result = self.run_preflight("2\n", self.lag_output(0, 0, 0))

        self.assertNotEqual(0, result.returncode)
        self.assertIn("2 legacy in-flight order", result.stderr)

    def test_delivery_consumer_lag_blocks_rollout(self) -> None:
        result = self.run_preflight("0\n", self.lag_output(0, 3, 0))

        self.assertNotEqual(0, result.returncode)
        self.assertIn("delivery-service consumer lag is 3", result.stderr)

    def test_missing_consumer_partition_rows_fail_closed(self) -> None:
        result = self.run_preflight(
            "0\n",
            "Consumer group 'delivery-service' has no active members.\n",
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("could not verify delivery-service consumer lag", result.stderr)

    def test_order_query_detects_partial_snapshots_in_all_emitting_states(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")

        for column in (
            "pickup_address_street",
            "pickup_address_city",
            "pickup_address_state",
            "pickup_address_zip_code",
        ):
            self.assertIn(f"{column} IS NULL", source)

        for state in (
            "APPROVAL_PENDING",
            "AWAITING_RESTAURANT_ACCEPTANCE",
            "CONFIRMATION_PENDING",
        ):
            self.assertIn(state, source)

    @staticmethod
    def lag_output(*lags: int) -> str:
        header = (
            "GROUP TOPIC PARTITION CURRENT-OFFSET LOG-END-OFFSET "
            "LAG CONSUMER-ID HOST CLIENT-ID"
        )
        rows = [
            "delivery-service net.ftgo.orderservice.domain.Order "
            f"{partition} 10 10 {lag} - - -"
            for partition, lag in enumerate(lags)
        ]
        return "\n".join([header, *rows, ""])


if __name__ == "__main__":
    unittest.main()
