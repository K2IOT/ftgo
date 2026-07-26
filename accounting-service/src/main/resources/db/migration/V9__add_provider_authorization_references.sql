-- Phase 02B Task 2: bind local authorization/void state to provider references.
ALTER TABLE authorizations
    ADD COLUMN provider_authorization_id VARCHAR(255) NULL AFTER request_id,
    ADD COLUMN provider_void_id VARCHAR(255) NULL AFTER void_request_id;

UPDATE authorizations
SET provider_authorization_id = CONCAT('pa_legacy_', id)
WHERE provider_authorization_id IS NULL;

ALTER TABLE authorizations
    MODIFY COLUMN provider_authorization_id VARCHAR(255) NOT NULL;

CREATE UNIQUE INDEX uq_authorization_provider_authorization
    ON authorizations(provider_authorization_id);
CREATE UNIQUE INDEX uq_authorization_provider_void
    ON authorizations(provider_void_id);
