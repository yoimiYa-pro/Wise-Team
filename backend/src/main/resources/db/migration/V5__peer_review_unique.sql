-- 同一周期内，同一评价人对同一被评价人仅允许一条互评（与应用层校验一致；防止并发或旧版本绕过）
DELETE t1 FROM peer_reviews t1
INNER JOIN peer_reviews t2
  ON t1.cycle_id <=> t2.cycle_id
  AND t1.reviewer_user_id = t2.reviewer_user_id
  AND t1.target_user_id = t2.target_user_id
  AND t1.id > t2.id;

ALTER TABLE peer_reviews
  ADD UNIQUE KEY uk_peer_cycle_reviewer_target (cycle_id, reviewer_user_id, target_user_id);
