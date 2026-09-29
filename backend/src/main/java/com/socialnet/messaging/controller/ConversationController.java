package com.socialnet.messaging.controller;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.socialnet.messaging.dto.*;
import com.socialnet.messaging.repository.ConversationRepository;
import com.socialnet.messaging.websocket.ConversationWebSocketHandler;

@RestController
@RequestMapping("/api")
public class ConversationController {
    private static final Logger log = LoggerFactory.getLogger(ConversationController.class);
    private final ConversationRepository repository;
    private final ConversationWebSocketHandler sockets;

    public ConversationController(ConversationRepository repository, ConversationWebSocketHandler sockets) {
        this.repository = repository;
        this.sockets = sockets;
    }

    @GetMapping("/users")
    public List<UserResponse> getUsers() {
        return repository.getUsers();
    }

    @GetMapping("/conversations")
    public List<ConversationResponse> getConversations(
            @RequestHeader(value = "X-User-Id", required = false) String selectedUser) {
        return repository.getConversations(repository.resolveUser(selectedUser));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse createMessage(@PathVariable long conversationId,
            @RequestHeader(value = "X-User-Id", required = false) String selectedUser,
            @RequestBody CreateMessageRequest request) {
        long userId = repository.resolveUser(selectedUser);
        repository.requireMember(conversationId, userId);
        if (request.text() == null || request.text().isBlank() || request.text().length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Message must contain 1–2000 characters");
        }

        // The repository transaction commits before returning: never broadcast an unsaved message.
        MessageResponse message = repository.saveMessage(conversationId, userId, request.text().strip());
        sockets.broadcastMessage(conversationId, message);
        return message;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail invalidRequest(ResponseStatusException exception) {
        return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail databaseUnavailable(Exception exception) {
        log.error("Database operation failed", exception);
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Messages are temporarily unavailable");
    }
}
