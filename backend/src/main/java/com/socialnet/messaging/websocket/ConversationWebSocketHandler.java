package com.socialnet.messaging.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;
import com.socialnet.messaging.dto.MessageResponse;
import com.socialnet.messaging.repository.ConversationRepository;

@Component
public class ConversationWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(ConversationWebSocketHandler.class);
    private final ObjectMapper mapper;
    private final ConversationRepository repository;
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    public ConversationWebSocketHandler(ObjectMapper mapper, ConversationRepository repository) {
        this.mapper = mapper;
        this.repository = repository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // Concurrent HTTP sends can write to the same browser connection.
        var safeSession = new ConcurrentWebSocketSessionDecorator(session, 5000, 128 * 1024);
        clients.put(session.getId(), new Client(safeSession,
                (long) session.getAttributes().get("userId"), ConcurrentHashMap.newKeySet()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        Client client = clients.get(session.getId());
        if (client == null) return;
        try {
            var frame = mapper.readTree(message.getPayload());
            var id = frame.path("conversationId");
            if (!"SUBSCRIBE".equals(frame.path("type").asText()) || !id.isIntegralNumber() || !id.canConvertToLong()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            long conversationId = id.asLong();
            repository.requireMember(conversationId, client.userId());
            client.conversations().add(conversationId);
            // Fetching history after this acknowledgement closes the fetch/subscribe gap.
            send(client, mapper.writeValueAsString(Map.of("type", "SUBSCRIBED", "conversationId", conversationId)));
        } catch (RuntimeException exception) {
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }

    public void broadcastMessage(long conversationId, MessageResponse message) {
        try {
            String payload = mapper.writeValueAsString(new ConversationEvent("MESSAGE", conversationId, message));
            for (Client client : clients.values()) {
                if (client.conversations().contains(conversationId)) send(client, payload);
            }
        } catch (RuntimeException exception) {
            // A delivery failure must not make a committed message look like a failed save.
            log.error("Could not broadcast message {}", message.id(), exception);
        }
    }

    private void send(Client client, String payload) {
        try {
            client.session().sendMessage(new TextMessage(payload));
        } catch (IOException | RuntimeException exception) {
            clients.remove(client.session().getId());
            try { client.session().close(CloseStatus.SERVER_ERROR); }
            catch (IOException | RuntimeException ignored) { }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        clients.remove(session.getId());
        session.close(CloseStatus.SERVER_ERROR);
    }

    private record Client(WebSocketSession session, long userId, Set<Long> conversations) {}
    private record ConversationEvent(String type, long conversationId, MessageResponse message) {}
}
