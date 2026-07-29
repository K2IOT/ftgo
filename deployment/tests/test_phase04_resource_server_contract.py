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

    def test_every_http_service_declares_fail_closed_security_configuration(self):
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
            self.assertIn("FtgoAuthorizationManagers.publicApi", source, service)
            self.assertIn("FtgoAuthorizationManagers.internalService", source, service)
            self.assertIn("JwtDecoder", source, service)
            self.assertIn('"/internal/**"', source, service)
            self.assertIn("ftgo-internal", source, service)
            self.assertIn("publicAudience", source, service)
            self.assertIn('.anyRequest().denyAll()', source, service)
            self.assertNotIn('.anyRequest().authenticated()', source, service)
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

    def test_gateway_routes_admin_consumer_operations_before_public_consumer_route(self):
        configuration = (ROOT / "api-gateway/src/main/resources/application.yml").read_text(
            encoding="utf-8"
        )
        admin_route = "Path=/admin/consumers/**"
        public_route = "Path=/consumers/**"
        self.assertIn(admin_route, configuration)
        self.assertLess(
            configuration.index(admin_route),
            configuration.index(public_route),
            "Admin consumer route must be evaluated before the general consumer route",
        )

    def test_gateway_declares_request_forwarded_header_and_route_hardening(self):
        configuration = (ROOT / "api-gateway/src/main/resources/application.yml").read_text(
            encoding="utf-8"
        )
        self.assertIn("maxSize: 256KB", configuration)
        self.assertNotIn("maxSize: 10MB", configuration)
        self.assertIn("max-in-memory-size: 256KB", configuration)
        self.assertIn("forward-headers-strategy: none", configuration)
        self.assertIn("trusted-proxies: ${FTGO_GATEWAY_TRUSTED_PROXIES:}", configuration)

        security = (ROOT / "api-gateway/src/main/java/net/ftgo/gateway/security/SecurityConfiguration.java").read_text(
            encoding="utf-8"
        )
        self.assertIn('"/actuator/health/liveness"', security)
        self.assertIn('"/actuator/health/readiness"', security)
        self.assertIn('.pathMatchers("/actuator/**").hasRole("ADMIN")', security)
        self.assertIn("publicApiAccess(publicAudience", security)
        self.assertIn(".anyExchange().denyAll()", security)
        self.assertNotIn(".anyExchange().authenticated()", security)

    def test_gateway_cors_is_explicit_and_fail_closed(self):
        cors = (ROOT / "api-gateway/src/main/java/net/ftgo/gateway/config/CorsGatewayConfiguration.java").read_text(
            encoding="utf-8"
        )
        properties = (ROOT / "api-gateway/src/main/java/net/ftgo/gateway/config/GatewayCorsProperties.java").read_text(
            encoding="utf-8"
        )
        docker = (ROOT / "api-gateway/src/main/resources/application-docker.yml").read_text(
            encoding="utf-8"
        )
        k8s = (ROOT / "api-gateway/src/main/resources/application-k8s.yml").read_text(
            encoding="utf-8"
        )

        self.assertIn("GatewayCorsProperties", cors)
        self.assertIn("setAllowedOrigins", cors)
        self.assertNotIn("setAllowedOriginPatterns", cors)
        self.assertIn('allowedOrigins.contains("*")', cors)
        self.assertIn("private boolean allowCredentials", properties)
        self.assertIn("FTGO_CORS_ALLOWED_ORIGINS", docker)
        self.assertIn("FTGO_CORS_ALLOWED_ORIGINS", k8s)
        self.assertNotIn("allowed-origin-patterns", docker)
        self.assertNotIn("allowed-origin-patterns", k8s)

    def test_gateway_exposes_versioned_public_api_without_duplicating_routes(self):
        version_filter = (
            ROOT
            / "api-gateway/src/main/java/net/ftgo/gateway/filter/ApiVersioningFilter.java"
        ).read_text(encoding="utf-8")
        self.assertIn('VERSION_PREFIX = "/api/v1"', version_filter)
        for path in (
            "/orders",
            "/consumers",
            "/restaurants",
            "/tickets",
            "/deliveries",
            "/order-history",
            "/order-details",
        ):
            self.assertIn(f'"{path}"', version_filter)
        self.assertIn("Deprecation", version_filter)
        self.assertIn("successor-version", version_filter)
        self.assertNotIn("/api/admin/payment-settlement", version_filter)

    def test_mvc_controllers_do_not_define_local_exception_handlers(self):
        violations = []
        for service in SERVICES:
            source_root = ROOT / service / "src/main/java"
            for controller in source_root.glob("**/*Controller.java"):
                source = controller.read_text(encoding="utf-8")
                if "@ExceptionHandler" in source:
                    violations.append(str(controller.relative_to(ROOT)))
        self.assertEqual(
            [],
            violations,
            "Controller-local handlers shadow centralized RFC 9457 advice",
        )

    def test_domain_advices_use_shared_problem_response_factory(self):
        advice_files = (
            "order-service/src/main/java/net/ftgo/order/api/OrderApiExceptionHandler.java",
            "consumer-service/src/main/java/net/ftgo/consumer/api/ConsumerApiExceptionHandler.java",
            "restaurant-service/src/main/java/net/ftgo/restaurant/api/RestaurantApiExceptionHandler.java",
            "delivery-service/src/main/java/net/ftgo/delivery/api/DeliveryApiExceptionHandler.java",
        )
        for relative in advice_files:
            source = (ROOT / relative).read_text(encoding="utf-8")
            self.assertIn("FtgoProblemResponses.response", source, relative)
            self.assertIn("@RestControllerAdvice", source, relative)


if __name__ == "__main__":
    unittest.main()
