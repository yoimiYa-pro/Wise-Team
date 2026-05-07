-- 每用户至多一条团队成员记录（全局唯一 user_id）
DELETE tm FROM team_members tm
LEFT JOIN (
    SELECT user_id, MIN(id) AS keep_id
    FROM team_members
    GROUP BY user_id
) k ON tm.user_id = k.user_id AND tm.id = k.keep_id
WHERE k.keep_id IS NULL;

ALTER TABLE team_members ADD UNIQUE KEY uk_team_members_user_id (user_id);
