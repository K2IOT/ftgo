import pathlib
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


class Phase05SecurityContractTest(unittest.TestCase):

    def read_required(self, relative_path: str) -> str:
        path = ROOT / relative_path
        self.assertTrue(path.is_file(), f"Required security file is missing: {relative_path}")
        return path.read_text(encoding="utf-8")

    def test_base_registers_external_secrets_and_network_policies(self):
        base = self.read_required("deployment/kubernetes/base/kustomization.yaml")
        for resource in (
            "secrets/cluster-secret-store.yaml",
            "network-policies/default-deny.yaml",
            "network-policies/service-allow-list.yaml",
        ):
            self.assertIn(resource, base)

    def test_cluster_secret_store_uses_vault_kubernetes_auth_without_inline_secret(self):
        store = self.read_required(
            "deployment/kubernetes/base/secrets/cluster-secret-store.yaml"
        )
        for required in (
            "kind: ClusterSecretStore",
            "provider:",
            "vault:",
            "server:",
            "auth:",
            "kubernetes:",
            "serviceAccountRef:",
        ):
            self.assertIn(required, store)
        self.assertNotIn("kind: Secret", store)
        self.assertNotIn("tokenSecretRef", store)

    def test_every_service_uses_external_secret_and_environment_reference(self):
        for service in SERVICES:
            base = f"deployment/kubernetes/base/{service}"
            external = self.read_required(f"{base}/external-secret.yaml")
            deployment = self.read_required(f"{base}/deployment.yaml")
            kustomization = self.read_required(f"{base}/kustomization.yaml")

            for required in (
                "apiVersion: external-secrets.io/",
                "kind: ExternalSecret",
                "refreshInterval:",
                "kind: ClusterSecretStore",
                "creationPolicy: Owner",
                f"name: {service}-runtime",
                "remoteRef:",
            ):
                self.assertIn(required, external, service)
            self.assertNotIn("stringData:", external, service)
            self.assertNotIn("data:", external.split("spec:", 1)[0], service)
            self.assertIn("external-secret.yaml", kustomization, service)
            self.assertIn("envFrom:", deployment, service)
            self.assertIn(f"name: {service}-runtime", deployment, service)
            for literal in ("password:", "secret:", "token:"):
                self.assertNotIn(literal, deployment.lower(), service)

    def test_default_deny_blocks_ingress_and_egress(self):
        policy = self.read_required(
            "deployment/kubernetes/base/network-policies/default-deny.yaml"
        )
        self.assertIn("kind: NetworkPolicy", policy)
        self.assertIn("podSelector: {}", policy)
        self.assertIn("- Ingress", policy)
        self.assertIn("- Egress", policy)
        self.assertIn("ingress: []", policy)
        self.assertIn("egress: []", policy)

    def test_allow_list_is_identity_and_port_scoped(self):
        policies = self.read_required(
            "deployment/kubernetes/base/network-policies/service-allow-list.yaml"
        )
        for required in (
            "kubernetes.io/metadata.name: kube-system",
            "k8s-app: kube-dns",
            "kubernetes.io/metadata.name: istio-system",
            "app.kubernetes.io/name: api-gateway",
            "app.kubernetes.io/component: kafka",
            "app.kubernetes.io/component: mysql",
            "app.kubernetes.io/component: redis",
            "app.kubernetes.io/component: scylla",
            "port: 53",
            "port: 8080",
            "port: 9092",
            "port: 3306",
            "port: 6379",
            "port: 9042",
        ):
            self.assertIn(required, policies)
        self.assertNotIn("0.0.0.0/0", policies)

    def test_istio_enforces_strict_mtls_and_principal_authorization(self):
        peer = self.read_required("deployment/kubernetes/istio/peer-authentication.yaml")
        authz = self.read_required("deployment/kubernetes/istio/authorization-policy.yaml")
        kustomization = self.read_required("deployment/kubernetes/istio/kustomization.yaml")

        self.assertIn("mode: STRICT", peer)
        self.assertIn("peer-authentication.yaml", kustomization)
        self.assertIn("authorization-policy.yaml", kustomization)
        self.assertIn("kind: AuthorizationPolicy", authz)
        self.assertIn("action: ALLOW", authz)
        self.assertIn("principals:", authz)
        self.assertIn("/internal/*", authz)
        self.assertIn("cluster.local/ns/ftgo-production/sa/api-gateway", authz)

    def test_overlays_include_mesh_and_production_kafka_acl_resources(self):
        for environment in ("dev", "staging", "production"):
            overlay = self.read_required(
                f"deployment/kubernetes/overlays/{environment}/kustomization.yaml"
            )
            self.assertIn("../../istio", overlay)
        production = self.read_required(
            "deployment/kubernetes/overlays/production/kustomization.yaml"
        )
        self.assertIn("kafka-security.yaml", production)

        kafka = self.read_required(
            "deployment/kubernetes/overlays/production/kafka-security.yaml"
        )
        self.assertGreaterEqual(kafka.count("kind: KafkaUser"), 5)
        self.assertIn("type: scram-sha-512", kafka)
        self.assertIn("type: tls", kafka)
        self.assertIn("resource:", kafka)
        self.assertIn("type: topic", kafka)
        self.assertIn("type: group", kafka)
        self.assertIn("operation: Read", kafka)
        self.assertIn("operation: Write", kafka)

    def test_security_smoke_checks_denied_and_allowed_paths(self):
        script = self.read_required("scripts/k8s/security-smoke.sh")
        self.assertIn("set -euo pipefail", script)
        self.assertIn("kubectl", script)
        self.assertIn("security-probe", script)
        self.assertIn("expect_denied", script)
        self.assertIn("expect_allowed", script)
        self.assertIn("mysql", script)
        self.assertIn("api-gateway", script)
        self.assertIn("order-service", script)
        self.assertIn("trap", script)

    def test_manifest_validator_rejects_inline_secret_material(self):
        script = self.read_required("scripts/k8s/validate-manifests.sh")
        self.assertIn("test_phase05_security_contract", script)
        self.assertIn("data|stringData", script)
        self.assertIn("password|secret|token", script)


if __name__ == "__main__":
    unittest.main()
