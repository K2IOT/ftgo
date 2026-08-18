import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
API_CONTRACT_WORKFLOW = ROOT / ".github" / "workflows" / "phase-04-api-contract.yml"
FULL_TEST_WORKFLOW = ROOT / ".github" / "workflows" / "phase-01-full-test.yml"


class FinalVerificationContractTest(unittest.TestCase):

    def test_repository_contract_matrix_runs_in_pr_verification(self):
        workflow = API_CONTRACT_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn(
            "python3 -m unittest discover -s deployment/tests -p 'test_*.py' -v",
            workflow,
        )
        self.assertIn("deployment/tests/**", workflow)

    def test_full_gradle_verification_matches_plan(self):
        workflow = FULL_TEST_WORKFLOW.read_text(encoding="utf-8")
        self.assertIn("./gradlew clean test --no-daemon --stacktrace", workflow)
        self.assertNotIn("--continue", workflow)


if __name__ == "__main__":
    unittest.main()
