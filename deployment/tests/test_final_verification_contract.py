import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
CI_WORKFLOW = ROOT / ".github" / "workflows" / "ci.yml"


class FinalVerificationContractTest(unittest.TestCase):

    def test_repository_contract_matrix_runs_in_canonical_ci(self):
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            "python3 -m unittest discover -s deployment/tests -p 'test_*.py' -v",
            workflow,
        )

    def test_full_gradle_verification_matches_plan(self):
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("./gradlew clean test --no-daemon --stacktrace", workflow)
        self.assertNotIn("--continue", workflow)

    def test_post_merge_canonical_ci_runs_two_stateful_cycles(self):
        workflow = CI_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("runs=2", workflow)
        self.assertIn('"${GITHUB_EVENT_NAME}" == "push"', workflow)
        for script_name in (
            "scripts/smoke/verify-fresh-stack.sh",
            "scripts/smoke/verify-core-order-flow.sh",
            "scripts/smoke/verify-payment-settlement.sh",
            "scripts/smoke/verify-distributed-consistency.sh",
        ):
            self.assertIn(f"bash {script_name} --runs \"${{runs}}\"", workflow)


if __name__ == "__main__":
    unittest.main()
