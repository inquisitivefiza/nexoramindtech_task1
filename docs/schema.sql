-- Reference DDL (PostgreSQL). Hibernate generates the equivalent automatically with
-- spring.jpa.hibernate.ddl-auto=update (the default here); for production, prefer
-- DDL_AUTO=validate together with a migration tool such as Flyway.

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(100)             NOT NULL,
    email         VARCHAR(255)             NOT NULL,
    password_hash VARCHAR(100)             NOT NULL,   -- bcrypt hash, never the raw password
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE tasks (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(200)  NOT NULL,
    description VARCHAR(2000),
    status      VARCHAR(20)   NOT NULL
                CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED')),
    priority    VARCHAR(10)   NOT NULL
                CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    user_id     BIGINT        NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Serve "my tasks, newest first" and "my tasks filtered by status" without scanning the table.
CREATE INDEX idx_tasks_user_created ON tasks (user_id, created_at);
CREATE INDEX idx_tasks_user_status  ON tasks (user_id, status);
