package com.socialnet.messaging.dto;

import java.util.List;

public record ConversationResponse(long id, long participantId, String participantName,
                                   List<MessageResponse> messages) {}
