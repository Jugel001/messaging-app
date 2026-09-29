package com.socialnet.messaging.config;

import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import com.socialnet.messaging.repository.ConversationRepository;
import com.socialnet.messaging.websocket.ConversationWebSocketHandler;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer, HandshakeInterceptor {
    private final ConversationWebSocketHandler handler;
    private final ConversationRepository repository;

    public WebSocketConfig(ConversationWebSocketHandler handler, ConversationRepository repository) {
        this.handler = handler;
        this.repository = repository;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").addInterceptors(this);
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        String userId = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("userId");
        try {
            attributes.put("userId", repository.resolveUser(userId));
            return true;
        } catch (ResponseStatusException exception) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) { }
}
