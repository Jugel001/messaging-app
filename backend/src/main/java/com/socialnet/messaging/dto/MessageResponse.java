package com.socialnet.messaging.dto;

import java.time.Instant;

public record MessageResponse(long id, long senderId, String text, Instant createdAt) {}
