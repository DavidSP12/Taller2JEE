package com.taller2jee.common.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public record EmailEvent(
        String studentEmail,
        String studentName,
        String evaluationId,
        double score
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize EmailEvent", e);
        }
    }

    public static EmailEvent fromJson(String payload) {
        try {
            return MAPPER.readValue(payload, EmailEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid EmailEvent JSON payload", e);
        }
    }
}
