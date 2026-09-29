CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL
);

CREATE TABLE conversations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_one_id BIGINT NOT NULL,
    user_two_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_conversation_one FOREIGN KEY (user_one_id) REFERENCES users(id),
    CONSTRAINT fk_conversation_two FOREIGN KEY (user_two_id) REFERENCES users(id),
    -- Canonical ordering prevents both (Woody, Jugel) and (Jugel, Woody).
    CONSTRAINT ck_conversation_pair CHECK (user_one_id < user_two_id),
    CONSTRAINT uq_conversation_pair UNIQUE (user_one_id, user_two_id)
);

CREATE TABLE messages (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    text VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id)
);
CREATE INDEX idx_message_history ON messages(conversation_id, id);
