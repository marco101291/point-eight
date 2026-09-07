-- Baseline schema: captured from what Hibernate's ddl-auto: update had already built through
-- M0-M5 (DEC-008), not hand-authored from scratch — so a database that already exists ends up
-- with the same shape as one bootstrapped fresh from this file. From here on, schema changes are
-- new versioned migrations (DEC-019), not ddl-auto.
--
-- Foreign-key names are Hibernate's original auto-generated ones for user_hobbies and
-- user_seeking_genders would have been illegible hashes; given names here instead, since a
-- migration is meant to be read, not just executed. matches has no FK to users on purpose: the
-- Match aggregate references users by UserId only, no JPA relationship (DEC-004) — this baseline
-- preserves that rather than introducing a constraint that was never actually enforced.

CREATE TABLE users (
    id uuid NOT NULL,
    active_addiction boolean NOT NULL,
    age integer NOT NULL,
    attachment_intensity double precision NOT NULL,
    attachment_style character varying(20) NOT NULL,
    city character varying(255) NOT NULL,
    commitment_pace_expectation double precision NOT NULL,
    gottman_contempt double precision NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    gottman_criticism double precision NOT NULL,
    cumulative_confidence_score double precision NOT NULL,
    gottman_defensiveness double precision NOT NULL,
    gender character varying(20) NOT NULL,
    infidelity_history boolean NOT NULL,
    profession character varying(255) NOT NULL,
    relationship_history integer NOT NULL,
    seeking_type character varying(20) NOT NULL,
    gottman_stonewalling double precision NOT NULL,
    stress_baseline double precision NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT users_pkey PRIMARY KEY (id),
    CONSTRAINT users_attachment_style_check
        CHECK (attachment_style IN ('ANXIOUS', 'AVOIDANT', 'SECURE', 'DISORGANIZED')),
    CONSTRAINT users_gender_check CHECK (gender IN ('FEMALE', 'MALE', 'NON_BINARY')),
    CONSTRAINT users_seeking_type_check
        CHECK (seeking_type IN ('CASUAL', 'SHORT_TERM', 'LONG_TERM', 'UNDEFINED'))
);

CREATE TABLE user_hobbies (
    user_id uuid NOT NULL,
    hobby character varying(255) NOT NULL,
    CONSTRAINT fk_user_hobbies_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE user_seeking_genders (
    user_id uuid NOT NULL,
    gender character varying(20) NOT NULL,
    CONSTRAINT user_seeking_genders_pkey PRIMARY KEY (user_id, gender),
    CONSTRAINT user_seeking_genders_gender_check CHECK (gender IN ('FEMALE', 'MALE', 'NON_BINARY')),
    CONSTRAINT fk_user_seeking_genders_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE matches (
    id uuid NOT NULL,
    activated_at timestamp(6) with time zone,
    compatibility_score double precision,
    created_at timestamp(6) with time zone NOT NULL,
    ended_at timestamp(6) with time zone,
    expiry_duration_seconds bigint NOT NULL,
    status character varying(20) NOT NULL,
    user_a_id uuid NOT NULL,
    user_b_id uuid NOT NULL,
    CONSTRAINT matches_pkey PRIMARY KEY (id),
    CONSTRAINT matches_status_check
        CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'REJECTED'))
);

CREATE INDEX idx_matches_status ON matches (status);
CREATE INDEX idx_matches_user_a ON matches (user_a_id);
CREATE INDEX idx_matches_user_b ON matches (user_b_id);
