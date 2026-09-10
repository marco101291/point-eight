-- DEC-023 follow-up: reuse detection needs a lineage to revoke (family_id), and race-safe
-- rotation needs an atomic claim the database itself arbitrates (used_at), not a read-then-write
-- from application code. Existing rows get an empty family_id — they're already single tokens
-- with no real lineage to preserve, and at worst a false-positive reuse detection against one of
-- them just forces a re-login, same as it always could.
ALTER TABLE refresh_tokens ADD COLUMN family_id character varying(36) NOT NULL DEFAULT '';
ALTER TABLE refresh_tokens ALTER COLUMN family_id DROP DEFAULT;
ALTER TABLE refresh_tokens ADD COLUMN used_at timestamp(6) with time zone;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
