-- The revocable half of DEC-023's access/refresh split. Keyed by the token's own hash, not a
-- surrogate id: a lookup by hash is the only access pattern this table ever needs. Hashed at
-- rest, same reasoning as accounts.password_hash — the raw secret only ever exists transiently on
-- its way back to the caller, never stored.
CREATE TABLE refresh_tokens (
    token_hash character varying(64) NOT NULL,
    user_id uuid NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT refresh_tokens_pkey PRIMARY KEY (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
