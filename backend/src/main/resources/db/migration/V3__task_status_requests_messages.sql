CREATE TABLE task_status_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    apply_reason VARCHAR(512),
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT,
    review_comment VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_tsr_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_tsr_applicant FOREIGN KEY (applicant_id) REFERENCES users (id),
    CONSTRAINT fk_tsr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)
);

CREATE INDEX idx_tsr_pending ON task_status_requests (review_status, task_id);

CREATE TABLE in_app_messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT,
    msg_type VARCHAR(64) NOT NULL,
    read_flag TINYINT NOT NULL DEFAULT 0,
    ref_type VARCHAR(64),
    ref_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_msg_user_created (user_id, created_at),
    KEY idx_msg_unread (user_id, read_flag),
    CONSTRAINT fk_msg_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);
