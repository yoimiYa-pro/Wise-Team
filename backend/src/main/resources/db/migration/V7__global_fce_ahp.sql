-- 全平台 FCE 三维度（管理者/系统/互评）判断矩阵与权向量；单行 id=1
CREATE TABLE global_fce_ahp (
    id BIGINT NOT NULL PRIMARY KEY,
    matrix_json JSON NOT NULL,
    weights_json JSON,
    cr_value DECIMAL(12, 8),
    consistent_flag TINYINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
