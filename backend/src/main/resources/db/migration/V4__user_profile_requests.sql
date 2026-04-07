CREATE TABLE user_profile_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    proposed_display_name VARCHAR(128) NOT NULL,
    proposed_skills_json JSON NOT NULL,
    proposed_base_capacity DECIMAL(8,2) NOT NULL,
    apply_reason VARCHAR(512),
    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewer_id BIGINT,
    review_comment VARCHAR(512),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_upr_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_upr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id)
);

CREATE INDEX idx_upr_pending ON user_profile_requests (review_status, user_id);
