package com.taller2jee.common.model;

import java.util.List;

public record EvaluationSubmission(
        String evaluationId,
        String studentId,
        String studentName,
        String studentEmail,
        List<EvaluationAnswer> answers
) {
}
