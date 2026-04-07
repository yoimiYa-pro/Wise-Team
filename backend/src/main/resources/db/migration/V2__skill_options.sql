CREATE TABLE skill_options (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    team_id BIGINT NOT NULL COMMENT '0=全局可选项(管理员); >0=团队可选项(团队管理者)',
    skill_code VARCHAR(64) NOT NULL,
    label VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_team_skill (team_id, skill_code)
);

INSERT INTO skill_options (team_id, skill_code, label, sort_order) VALUES
(0, 'coding', '开发/编码', 10),
(0, 'test', '测试', 20),
(0, 'design', '设计', 30),
(0, 'manage', '管理', 40),
(0, 'system', '系统/运维', 50);
