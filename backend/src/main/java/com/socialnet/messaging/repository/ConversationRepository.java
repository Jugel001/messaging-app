package com.socialnet.messaging.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.socialnet.messaging.dto.ConversationResponse;
import com.socialnet.messaging.dto.MessageResponse;
import com.socialnet.messaging.dto.UserResponse;

@Repository
public class ConversationRepository {
    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UserResponse> getUsers() {
        return jdbc.query("SELECT id, display_name FROM users ORDER BY id",
                (rs, row) -> new UserResponse(rs.getLong("id"), rs.getString("display_name")));
    }

    // Demo selection only, not authentication. Real login can replace this method later.
    public long resolveUser(String value) {
        try {
            long id = Long.parseLong(value == null ? "" : value);
            if (jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, id) == 1) {
                return id;
            }
        } catch (NumberFormatException ignored) { }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Choose a valid demo user");
    }

    public List<ConversationResponse> getConversations(long userId) {
        return jdbc.query("""
                SELECT c.id, u.id AS participant_id, u.display_name
                FROM conversations c JOIN users u
                  ON u.id = CASE WHEN c.user_one_id = ? THEN c.user_two_id ELSE c.user_one_id END
                WHERE c.user_one_id = ? OR c.user_two_id = ? ORDER BY c.id
                """, (rs, row) -> new ConversationResponse(rs.getLong("id"),
                        rs.getLong("participant_id"), rs.getString("display_name"),
                        getMessages(rs.getLong("id"))), userId, userId, userId);
    }

    public void requireMember(long conversationId, long userId) {
        var matches = jdbc.query("SELECT user_one_id, user_two_id FROM conversations WHERE id = ?",
                (rs, row) -> rs.getLong("user_one_id") == userId || rs.getLong("user_two_id") == userId,
                conversationId);
        if (matches.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        if (!matches.getFirst()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant");
        }
    }

    private List<MessageResponse> getMessages(long conversationId) {
        // Keep the response bounded; older messages remain stored for future pagination.
        return jdbc.query("""
                SELECT id, sender_id, text, created_at FROM messages
                WHERE conversation_id = ? ORDER BY id DESC LIMIT 100
                """, (rs, row) -> message(rs), conversationId).reversed();
    }

    @Transactional
    public MessageResponse saveMessage(long conversationId, long senderId, String text) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO messages (conversation_id, sender_id, text) VALUES (?, ?, ?)",
                    new String[] { "id" });
            statement.setLong(1, conversationId);
            statement.setLong(2, senderId);
            statement.setString(3, text);
            return statement;
        }, key);
        return jdbc.queryForObject("SELECT id, sender_id, text, created_at FROM messages WHERE id = ?",
                (rs, row) -> message(rs), key.getKey().longValue());
    }

    private MessageResponse message(ResultSet rs) throws SQLException {
        return new MessageResponse(rs.getLong("id"), rs.getLong("sender_id"),
                rs.getString("text"), rs.getTimestamp("created_at").toInstant());
    }
}
