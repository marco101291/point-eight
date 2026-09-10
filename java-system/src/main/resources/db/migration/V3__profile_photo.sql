-- Photo is Layer 1 (visible, editable) but wasn't modeled at all until the mobile reveal screen
-- (DEC-021) needed a real one instead of a hardcoded mock URL. NOT NULL with a backfill default so
-- existing rows stay valid without a data migration step of their own; new rows always provide a
-- real one via RegisterUserRequest/UpdateUserRequest.
ALTER TABLE users ADD COLUMN photo_url character varying(2048)
    NOT NULL DEFAULT 'https://picsum.photos/seed/pointeight/900/1400';

ALTER TABLE users ALTER COLUMN photo_url DROP DEFAULT;
