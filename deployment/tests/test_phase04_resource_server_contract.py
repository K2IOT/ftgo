import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SERVICES = (
    "order-service",
    "consumer-service",
    "restaurant-service",
    "kitchen-service",
    "accounting-service",
    "delivery-service",
    "order-history-service",
)


class Phase04ResourceServerContractTest(unittest.TestCase):

    def test_every_http_service_has_resource_server_dependencies(self):
        build = (ROOT / "build.gradle").read_text(encoding="utf-8")
        service_block = build[build.index("configure(subprojects.findAll"):build.index("project(':order-service')")]

        self.assertIn("spring-boot-starter-security", service_block)
        self.assertIn("spring-security-oauth2-resource-server", service_block)
        self.assertIn("spring-security-oauth2-jose", service_block)
        self.assertIn("spring-security-test", service_block)

    def test_every_http_service_declares_security_configuration(self):
        for service in SERVICES:
            configurations = list(
                (ROOT / service / "src/main/java").glob("**/config/SecurityConfiguration.java")
            )
            self.assertEqual(
                1,
                len(configurations),
                f"Expected one SecurityConfiguration.java in {service}: {configurations}",
            )
            source = configurations[0].read_text(encoding="utf-8")
            self.assertIn("@EnableWebSecurity", source, service)
            self.assertIn("oauth2ResourceServer", source, service)
            self.assertIn("FtgoJwtAuthenticationConverter", source, service)
            self.assertIn("JwtDecoder", source, service)
            self.assertIn('"/internal/**"', source, service)
            self.assertIn("ftgo-internal", source, service)
            self.assertIn('"/actuator/health/liveness"', source, service)
            self.assertIn('"/actuator/health/readiness"', source, service)

    def test_every_http_service_has_issuer_jwks_and_audience_configuration(self):
        for service in SERVICES:
            configuration = ROOT / service / "src/main/resources/application.yml"
            source = configuration.read_text(encoding="utf-8")
            self.assertIn("issuer-uri:", source, service)
            self.assertIn("jwk-set-uri:", source, service)
            self.assertIn("public-audience:", source, service)
            self.assertIn("internal-audience:", source, service)
            self.assertIn("show-details: when_authorized", source, service)

    def test_gateway_relays_bearer_token_and_uses_real_oidc_baseline(self):
        configuration = (ROOT / "api-gateway/src/main/resources/application.yml").read_text(
            encoding="utf-8"
        )
        default_filters = configuration[
            configuration.index("default-filters:"):configuration.index("routes:")
        ]
        self.assertIn("- TokenRelay", default_filters)
        self.assertIn("Path=/api/admin/payment-settlement/**", configuration)
        self.assertIn("/realms/ftgo", configuration)
        self.assertIn("jwk-set-uri:", configuration)
        self.assertIn("public-audience:", configuration)
        self.assertIn("internal-audience:", configuration)


if __name__ == "__main__":
    unittest.main()
