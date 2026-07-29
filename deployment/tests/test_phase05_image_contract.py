import pathlib
import re
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SERVICES = (
    "api-gateway",
    "order-service",
    "consumer-service",
    "restaurant-service",
    "kitchen-service",
    "accounting-service",
    "delivery-service",
    "order-history-service",
)


class Phase05ImageContractTest(unittest.TestCase):

    def read_required(self, relative_path: str) -> str:
        path = ROOT / relative_path
        self.assertTrue(path.is_file(), f"Required Phase 05 file is missing: {relative_path}")
        return path.read_text(encoding="utf-8")

    def test_generic_service_dockerfile_is_hardened_and_reproducible(self):
        dockerfile = self.read_required("docker/service.Dockerfile")

        for build_arg in ("SERVICE", "VERSION", "GIT_SHA"):
            self.assertRegex(dockerfile, rf"(?m)^ARG {build_arg}(?:=|$)")
        self.assertIn(":${SERVICE}:bootJar", dockerfile)
        self.assertNotRegex(dockerfile, r"(?im)^FROM\s+\S+:latest(?:\s|$)")
        self.assertIn("org.opencontainers.image.source", dockerfile)
        self.assertIn("org.opencontainers.image.revision", dockerfile)
        self.assertIn("org.opencontainers.image.version", dockerfile)
        self.assertRegex(dockerfile, r"(?m)^USER\s+[1-9][0-9]*(?::[1-9][0-9]*)?\s*$")
        self.assertIn("/actuator/health/liveness", dockerfile)
        self.assertIn("HEALTHCHECK", dockerfile)
        self.assertIn("/tmp", dockerfile)
        self.assertNotRegex(
            dockerfile,
            r"(?i)(password|secret|token)\s*=\s*[^$\s]+",
            "Dockerfile must not embed credentials",
        )

    def test_docker_build_context_excludes_local_and_secret_material(self):
        dockerignore = self.read_required(".dockerignore")
        for ignored in (".git", ".gradle", "**/build", ".env", "*.pem", "*.key"):
            self.assertIn(ignored, dockerignore)
        self.assertNotIn("gradle/wrapper/gradle-wrapper.jar", dockerignore)

    def test_build_script_builds_all_services_without_latest_tags(self):
        script = self.read_required("scripts/build/build-images.sh")

        self.assertIn("set -euo pipefail", script)
        for service in SERVICES:
            self.assertIn(service, script)
        for build_arg in ("SERVICE", "VERSION", "GIT_SHA"):
            self.assertIn(f"--build-arg {build_arg}=", script)
        self.assertIn("docker/service.Dockerfile", script)
        self.assertRegex(script, r"ftgo/\$\{?service\}?:\$\{?VERSION\}?")
        self.assertNotRegex(script, r"(?i):latest(?:[\"'\s]|$)")

    def test_image_verifier_checks_runtime_security_and_provenance(self):
        script = self.read_required("scripts/build/verify-image.sh")

        self.assertIn("set -euo pipefail", script)
        self.assertIn(".Config.User", script)
        self.assertIn(".Config.Healthcheck", script)
        self.assertIn("org.opencontainers.image.revision", script)
        self.assertIn("org.opencontainers.image.version", script)
        self.assertIn("--read-only", script)
        self.assertIn("--tmpfs", script)
        self.assertIn("/tmp", script)
        self.assertIn("java -version", script)

    def test_infrastructure_compose_uses_pinned_images_and_externalized_credentials(self):
        compose = self.read_required("deployment/docker-compose.infra.yml")

        image_refs = re.findall(r"(?m)^\s*image:\s*([^\s#]+)", compose)
        self.assertGreater(len(image_refs), 0)
        self.assertEqual(
            [],
            [image for image in image_refs if image.endswith(":latest") or ":latest@" in image],
            "Infrastructure images must never use latest",
        )
        for required_image in (
            "mysql:8.0.36",
            "confluentinc/cp-kafka:7.5.0",
            "redis:7.2.4-alpine",
            "scylladb/scylla:5.4",
            "debezium/connect:2.5",
        ):
            self.assertIn(required_image, image_refs)

        for forbidden_literal in (
            "MYSQL_ROOT_PASSWORD: rootpassword",
            "MYSQL_PASSWORD: ftgo_password",
            "VAULT_DEV_ROOT_TOKEN_ID: ftgo-root-token",
        ):
            self.assertNotIn(forbidden_literal, compose)
        for variable in (
            "FTGO_MYSQL_ROOT_PASSWORD",
            "FTGO_MYSQL_USER",
            "FTGO_MYSQL_PASSWORD",
            "FTGO_VAULT_DEV_ROOT_TOKEN",
        ):
            self.assertIn(variable, compose)

    def test_local_environment_template_exists_but_dotenv_is_ignored(self):
        template = self.read_required("deployment/.env.example")
        gitignore = self.read_required(".gitignore")

        for variable in (
            "FTGO_MYSQL_ROOT_PASSWORD",
            "FTGO_MYSQL_USER",
            "FTGO_MYSQL_PASSWORD",
            "FTGO_VAULT_DEV_ROOT_TOKEN",
        ):
            self.assertIn(variable + "=", template)
        self.assertRegex(gitignore, r"(?m)^\.env$")
        self.assertRegex(gitignore, r"(?m)^deployment/\.env$")

    def test_image_build_runbook_documents_immutable_release_flow(self):
        runbook = self.read_required("docs/runbooks/image-build.md")

        for required_text in (
            "build-images.sh",
            "verify-image.sh",
            "GIT_SHA",
            "read-only",
            "non-root",
            "image digest",
        ):
            self.assertIn(required_text, runbook)


if __name__ == "__main__":
    unittest.main()
