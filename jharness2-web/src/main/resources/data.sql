-- Default admin user (password: admin123)
-- BCrypt hash for 'admin123'
INSERT INTO users (username, password_hash, display_name, role, enabled, created_at, updated_at)
SELECT 'admin', '$2a$10$IOZj.XAI7EPVYh1IuG8L5.6k0kqLBsFyAZ5WzzM5lxbh.SIv10sZu', 'Administrator', 'ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

