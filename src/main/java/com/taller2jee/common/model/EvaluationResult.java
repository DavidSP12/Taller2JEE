package com.taller2jee.common.model;

public record EvaluationResult(
        String evaluationId,
        String studentId,
        double score,
        String status
) {
}
