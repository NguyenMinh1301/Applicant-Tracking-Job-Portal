-- Example table

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE
);

INSERT INTO users (username)
VALUES ('admin')
ON CONFLICT (username) DO NOTHING;
