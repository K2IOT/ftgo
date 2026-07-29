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


class Phase05KubernetesContractTest(unittest.TestCase):

    def read_required(self, relative_path: str) -> str:
        path = ROOT / relative_path
        self.assertTrue(path.is_file(), f"Required Kubernetes baseline file is missing: {relative_path}")
        return path.read_text(encoding="utf-8")

    def test_each_service_has_hardened_deployment_service_and_service_account(self):
        for service in SERVICES:
            base = f"deployment/kubernetes/base/{service}"
            deployment = self.read_required(f"{base}/deployment.yaml")
            service_yaml = self.read_required(f"{base}/service.yaml")
            account = self.read_required(f"{base}/serviceaccount.yaml")

            for required in (
                "kind: Deployment",
                "type: RollingUpdate",
                "maxUnavailable: 0",
                "maxSurge: 1",
                f"serviceAccountName: {service}",
                "runAsNonRoot: true",
                "type: RuntimeDefault",
                "allowPrivilegeEscalation: false",
                "readOnlyRootFilesystem: true",
                "drop:",
                "- ALL",
                "startupProbe:",
                "/actuator/health/liveness",
                "readinessProbe:",
                "/actuator/health/readiness",
                "livenessProbe:",
                "requests:",
                "limits:",
                "topologySpreadConstraints:",
                "topology.kubernetes.io/zone",
                "kubernetes.io/hostname",
                "mountPath: /tmp",
                "emptyDir:",
            ):
                self.assertIn(required, deployment, f"{service}: missing {required}")

            self.assertNotIn(":latest", deployment, service)
            self.assertIn("kind: Service", service_yaml, service)
            self.assertIn("type: ClusterIP", service_yaml, service)
            self.assertIn("name: http", service_yaml, service)
            self.assertIn("targetPort: http", service_yaml, service)
            self.assertIn("kind: ServiceAccount", account, service)
            self.assertIn("automountServiceAccountToken: false", account, service)

    def test_base_kustomization_includes_all_workloads_and_resilience_resources(self):
        kustomization = self.read_required("deployment/kubernetes/base/kustomization.yaml")
        self.assertIn("apiVersion: kustomize.config.k8s.io/v1beta1", kustomization)
        for service in SERVICES:
            self.assertRegex(kustomization, rf"(?m)^\s*- {re.escape(service)}$")
        for resource in (
            "availability/pod-disruption-budgets.yaml",
            "autoscaling/gateway-hpa.yaml",
            "autoscaling/keda-kafka-consumers.yaml",
        ):
            self.assertIn(resource, kustomization)

    def test_pdbs_cover_every_service(self):
        pdbs = self.read_required(
            "deployment/kubernetes/base/availability/pod-disruption-budgets.yaml"
        )
        self.assertEqual(8, pdbs.count("kind: PodDisruptionBudget"))
        for service in SERVICES:
            self.assertIn(f"name: {service}", pdbs)
            self.assertIn(f"app.kubernetes.io/name: {service}", pdbs)

    def test_gateway_hpa_uses_cpu_and_concurrency_with_stabilization(self):
        hpa = self.read_required("deployment/kubernetes/base/autoscaling/gateway-hpa.yaml")
        for required in (
            "apiVersion: autoscaling/v2",
            "kind: HorizontalPodAutoscaler",
            "name: api-gateway",
            "minReplicas:",
            "maxReplicas:",
            "type: Resource",
            "name: cpu",
            "type: Pods",
            "name: http_server_active_requests",
            "stabilizationWindowSeconds:",
        ):
            self.assertIn(required, hpa)

    def test_event_consumers_scale_from_bounded_kafka_lag(self):
        keda = self.read_required(
            "deployment/kubernetes/base/autoscaling/keda-kafka-consumers.yaml"
        )
        self.assertGreaterEqual(keda.count("kind: ScaledObject"), 5)
        self.assertIn("type: kafka", keda)
        self.assertIn("bootstrapServers:", keda)
        self.assertIn("consumerGroup:", keda)
        self.assertIn("lagThreshold:", keda)
        self.assertIn("minReplicaCount:", keda)
        self.assertIn("maxReplicaCount:", keda)
        self.assertIn("fallback:", keda)

    def test_environment_overlays_only_set_environment_specific_values(self):
        expected_replicas = {
            "dev": {service: 1 for service in SERVICES},
            "staging": {service: 2 for service in SERVICES},
            "production": {
                **{service: 2 for service in SERVICES},
                "api-gateway": 3,
                "order-service": 3,
            },
        }
        for environment, replicas in expected_replicas.items():
            overlay = self.read_required(
                f"deployment/kubernetes/overlays/{environment}/kustomization.yaml"
            )
            self.assertIn("../../base", overlay)
            self.assertIn(f"namespace: ftgo-{environment}", overlay)
            self.assertIn("images:", overlay)
            self.assertIn("digest: sha256:", overlay)
            self.assertNotIn(":latest", overlay)
            for service, count in replicas.items():
                pattern = (
                    rf"name:\s*{re.escape(service)}\s+count:\s*{count}(?:\s|$)"
                )
                self.assertRegex(overlay, pattern, f"{environment}: {service} replicas")

    def test_validation_script_builds_and_schema_validates_every_overlay(self):
        script = self.read_required("scripts/k8s/validate-manifests.sh")
        self.assertIn("set -euo pipefail", script)
        self.assertIn("kustomize", script)
        self.assertIn("kubeconform", script)
        self.assertIn("test_phase05_kubernetes_contract", script)
        for environment in ("dev", "staging", "production"):
            self.assertIn(environment, script)
        self.assertIn("-strict", script)
        self.assertIn("-ignore-missing-schemas", script)


if __name__ == "__main__":
    unittest.main()
