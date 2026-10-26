CREATE TABLE contacts (
 id UUID PRIMARY KEY,
 name VARCHAR(120) NOT NULL,
 email VARCHAR(254) NOT NULL,
 type VARCHAR(32) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 archived BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE UNIQUE INDEX uq_contact_email_lower ON contacts (LOWER(email));
CREATE INDEX idx_contacts_type_active ON contacts (type, created_at DESC) WHERE archived = FALSE;
