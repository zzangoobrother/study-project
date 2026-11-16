CREATE TABLE IF NOT EXISTS short_url (
    id           BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    short_key    VARCHAR(16)   CHARACTER SET ascii COLLATE ascii_bin NULL,
    original_url VARCHAR(2048) NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    CONSTRAINT uk_short_url_short_key UNIQUE (short_key)
);
