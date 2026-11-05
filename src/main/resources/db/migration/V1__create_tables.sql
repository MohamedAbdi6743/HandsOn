-- Everyone who can log in. Named app_user because "user" is reserved in PostgreSQL.
CREATE TABLE app_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT app_user_role_check  CHECK (role IN ('COORDINATOR', 'VOLUNTEER')),
    CONSTRAINT app_user_email_lower CHECK (email = LOWER(email))
);

-- Something volunteers help with, e.g. "Saturday Food Drive".
CREATE TABLE event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title       VARCHAR(120) NOT NULL,
    description TEXT,
    location    VARCHAR(200) NOT NULL,
    created_by  BIGINT       NOT NULL REFERENCES app_user (id),
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One time slot inside an event, e.g. "Setup crew, 08:00-10:00, 2 spots".
CREATE TABLE shift (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id   BIGINT      NOT NULL REFERENCES event (id) ON DELETE CASCADE,
    title      VARCHAR(80) NOT NULL,
    start_time TIMESTAMP   NOT NULL,
    end_time   TIMESTAMP   NOT NULL,
    capacity   INTEGER     NOT NULL,

    CONSTRAINT shift_time_order       CHECK (end_time > start_time),
    CONSTRAINT shift_capacity_positive CHECK (capacity > 0)
);

CREATE INDEX shift_event_id_idx   ON shift (event_id);
CREATE INDEX shift_start_time_idx ON shift (start_time);

-- Links a volunteer to a shift. One row per person per shift; the status changes over time.
-- No ON DELETE CASCADE here: a shift with sign-ups must not disappear silently.
CREATE TABLE signup (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    shift_id   BIGINT      NOT NULL REFERENCES shift (id),
    user_id    BIGINT      NOT NULL REFERENCES app_user (id),
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT signup_one_per_person UNIQUE (shift_id, user_id),
    CONSTRAINT signup_status_check CHECK (
        status IN ('CONFIRMED', 'WAITLISTED', 'CANCELLED', 'ATTENDED', 'NO_SHOW')
    )
);

CREATE INDEX signup_user_id_idx      ON signup (user_id);
CREATE INDEX signup_shift_status_idx ON signup (shift_id, status);
