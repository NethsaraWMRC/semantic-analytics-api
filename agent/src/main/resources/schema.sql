-- Runs on every startup. Safe to re-run: the table is only created when missing.
-- Named agent_chat_messages, not chat_messages, so it never collides with a chat table
-- that already exists in the target database.
CREATE TABLE IF NOT EXISTS agent_chat_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64)  NOT NULL,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL,
    display_text    TEXT         NULL,
    created_at      DATETIME     NOT NULL,
    INDEX idx_conversation (conversation_id, id)
);
