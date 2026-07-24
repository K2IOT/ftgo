import pathlib
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SERVICES = {
    "order-service": "V8__add_versioned_domain_event_outbox.sql",
    "consumer-service": "V4__add_versioned_domain_event_outbox.sql",
    "restaurant-service": "V4__add_versioned_domain_event_outbox.sql",
    "kitchen-service": "V9__add_versioned_domain_event_outbox.sql",
    "accounting-service": "V6__add_versioned_domain_event_outbox.sql",
    "delivery-service": "V3__add_versioned_domain_event_outbox.sql",
}


class Phase03VersionedOutboxProducerContractTest(unittest.TestCase):

    def test_every_domain_publisher_creates_a_versioned_envelope(self):
        for service in SERVICES:
            publisher = self._single(service, "src/main/java/**/messaging/DomainEventPublisher.java")
            source = publisher.read_text(encoding="utf-8")

            self.assertIn("DomainEventEnvelope", source, service)
            self.assertIn("DomainEventMetadata", source, service)
            self.assertIn("aggregateVersion", source, service)
            self.assertIn("envelope.eventId()", source, service)
            self.assertIn("envelope.schemaVersion()", source, service)
            self.assertIn("envelope.aggregateVersion()", source, service)

    def test_every_outbox_entity_persists_event_and_aggregate_versions(self):
        for service in SERVICES:
            entity = self._single(service, "src/main/java/**/messaging/OutboxEntry.java")
            source = entity.read_text(encoding="utf-8")

            self.assertIn('name = "event_id"', source, service)
            self.assertIn('name = "schema_version"', source, service)
            self.assertIn('name = "aggregate_version"', source, service)
            self.assertIn("getEventId", source, service)
            self.assertIn("getSchemaVersion", source, service)
            self.assertIn("getAggregateVersion", source, service)

    def test_every_service_has_forward_only_versioned_outbox_migration(self):
        for service, migration_name in SERVICES.items():
            migration = ROOT / service / "src/main/resources/db/migration" / migration_name
            self.assertTrue(migration.is_file(), f"Missing {migration}")
            sql = migration.read_text(encoding="utf-8").lower()

            self.assertIn("event_id", sql, service)
            self.assertIn("schema_version", sql, service)
            self.assertIn("aggregate_version", sql, service)
            self.assertIn("unique", sql, service)
            self.assertIn("md5", sql, service)

    def test_published_mutable_aggregates_have_optimistic_versions(self):
        expected = {
            "order-service": "Order.java",
            "consumer-service": "Consumer.java",
            "restaurant-service": "Restaurant.java",
            "kitchen-service": "Ticket.java",
            "accounting-service": "Account.java",
            "delivery-service": "Delivery.java",
        }
        for service, aggregate_name in expected.items():
            aggregate = self._single(service, f"src/main/java/**/{aggregate_name}")
            source = aggregate.read_text(encoding="utf-8")
            self.assertIn("@Version", source, service)
            self.assertIn("getVersion", source, service)

    def _single(self, service, pattern):
        matches = list((ROOT / service).glob(pattern))
        self.assertEqual(1, len(matches), f"Expected one {pattern} in {service}: {matches}")
        return matches[0]


if __name__ == "__main__":
    unittest.main()
