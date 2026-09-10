-- M7: one push token per account (see Account.registerPushToken), overwritten on each new
-- registration rather than modeling multiple devices. Nullable — most accounts won't have a
-- development build installed to register one at all.
ALTER TABLE accounts ADD COLUMN push_token character varying(255);
