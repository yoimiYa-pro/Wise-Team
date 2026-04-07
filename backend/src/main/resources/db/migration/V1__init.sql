CREATE TABLE users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128),
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    skills_json JSON,
    base_capacity DECIMAL(8,2) NOT NULL DEFAULT 40,
    avg_performance DECIMAL(6,2) NOT NULL DEFAULT 75,
    delay_history_score DECIMAL(6,4) NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE teams (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    goal TEXT,
    announcement TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    manager_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_teams_manager FOREIGN KEY (manager_id) REFERENCES users (id)
);

CREATE TABLE team_members (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    approval_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_team_user (team_id, user_id),
    CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_tm_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE tasks (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    assignee_id BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    difficulty DECIMAL(4,2) NOT NULL DEFAULT 1.0,
    priority INT NOT NULL DEFAULT 3,
    est_hours DECIMAL(10,2),
    deadline DATE,
    progress INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    required_skills_json JSON,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'GREEN',
    delay_probability DECIMAL(8,6),
    last_risk_eval_at TIMESTAMP NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_tasks_team (team_id),
    KEY idx_tasks_assignee (assignee_id),
    KEY idx_tasks_deadline_status (deadline, status),
    CONSTRAINT fk_tasks_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_creator FOREIGN KEY (creator_id) REFERENCES users (id),
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_id) REFERENCES users (id)
);

CREATE TABLE task_dependencies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    predecessor_id BIGINT NOT NULL,
    successor_id BIGINT NOT NULL,
    UNIQUE KEY uk_pred_succ (predecessor_id, successor_id),
    CONSTRAINT fk_td_pred FOREIGN KEY (predecessor_id) REFERENCES tasks (id) ON DELETE CASCADE,
    CONSTRAINT fk_td_succ FOREIGN KEY (successor_id) REFERENCES tasks (id) ON DELETE CASCADE
);

CREATE TABLE team_ahp (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    matrix_json JSON NOT NULL,
    weights_json JSON,
    cr_value DECIMAL(12,8),
    consistent_flag TINYINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ahp_team (team_id),
    CONSTRAINT fk_ahp_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE
);

CREATE TABLE workload_weekly (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    team_id BIGINT,
    year_week VARCHAR(12) NOT NULL,
    actual_hours DECIMAL(10,2) NOT NULL DEFAULT 0,
    forecast_hours DECIMAL(10,2),
    UNIQUE KEY uk_user_week (user_id, year_week),
    CONSTRAINT fk_wl_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wl_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE SET NULL
);

CREATE TABLE performance_cycles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    cycle_type VARCHAR(16) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    closed_flag TINYINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pc_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE
);

CREATE TABLE performance_reports (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cycle_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    score DECIMAL(6,2),
    detail_json JSON,
    UNIQUE KEY uk_cycle_user (cycle_id, user_id),
    CONSTRAINT fk_pr_cycle FOREIGN KEY (cycle_id) REFERENCES performance_cycles (id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE peer_reviews (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL,
    cycle_id BIGINT,
    target_user_id BIGINT NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    dimension_scores_json JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_peer_target (target_user_id),
    CONSTRAINT fk_peer_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_peer_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_peer_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE system_config (
    config_key VARCHAR(64) PRIMARY KEY,
    config_value TEXT
);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_id BIGINT,
    action VARCHAR(96) NOT NULL,
    resource_type VARCHAR(64),
    resource_id BIGINT,
    detail TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_actor (actor_id),
    KEY idx_audit_created (created_at)
);

INSERT INTO system_config (config_key, config_value) VALUES
('load.smoothing.alpha', '0.4'),
('fce.weights.manager', '0.4'),
('fce.weights.system', '0.35'),
('fce.weights.peer', '0.25');
