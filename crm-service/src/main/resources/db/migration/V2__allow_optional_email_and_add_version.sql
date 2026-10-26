-- Keep the original migration and its case-insensitive email uniqueness intact.
-- PostgreSQL permits multiple NULL values in this unique index for offline contacts.
ALTER TABLE contacts ALTER COLUMN email DROP NOT NULL;

-- Existing rows start at version zero; Hibernate uses it to reject competing writes.
ALTER TABLE contacts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
