-- Login credentials (DEC-022), kept in their own table rather than columns on `users`: unlike
-- `matches` (DEC-004, no FK to `users` — cross-aggregate reference only), `accounts` legitimately
-- has one, since an Account is a one-to-one detail of exactly one user, not a separate aggregate.
CREATE TABLE accounts (
    user_id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT accounts_pkey PRIMARY KEY (user_id),
    CONSTRAINT accounts_email_key UNIQUE (email),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
);
