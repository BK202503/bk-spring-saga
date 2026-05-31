CREATE TABLE IF NOT EXISTS saga_state (
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    saga_name            VARCHAR(255) NOT NULL,
    status               VARCHAR(32)  NOT NULL,
    context_payload      BYTEA        NOT NULL,
    context_type         VARCHAR(512) NOT NULL,
    completed_steps      TEXT         NOT NULL,
    current_step_index   INTEGER      NOT NULL,
    last_error           TEXT         NULL,
    created_at           TIMESTAMP    NOT NULL,
    updated_at           TIMESTAMP    NOT NULL,
    version              BIGINT       NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_saga_state_status ON saga_state(status);
CREATE INDEX IF NOT EXISTS idx_saga_state_name_status ON saga_state(saga_name, status);
