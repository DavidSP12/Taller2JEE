package com.taller2jee.logic.saga;

import com.taller2jee.common.model.EmailEvent;
import com.taller2jee.common.model.EvaluationResult;
import com.taller2jee.common.model.EvaluationSubmission;
import com.taller2jee.logic.persistence.ExamRepository;
import com.taller2jee.logic.persistence.StudentRepository;
import com.taller2jee.logic.queue.QueuePublisher;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class EvaluationSagaOrchestrator {
    private final ExamRepository examRepository;
    private final StudentRepository studentRepository;
    private final QueuePublisher queuePublisher;
    private final Map<String, String> answerKey;

    public EvaluationSagaOrchestrator(
            ExamRepository examRepository,
            StudentRepository studentRepository,
            QueuePublisher queuePublisher,
            Map<String, String> answerKey
    ) {
        this.examRepository = examRepository;
        this.studentRepository = studentRepository;
        this.queuePublisher = queuePublisher;
        this.answerKey = answerKey;
    }

    public EvaluationResult process(EvaluationSubmission submission) {
        double score = calculateScore(submission);
        Long submissionId = null;
        Long gradeId = null;
        boolean createdStudent = false;

        try {
            submissionId = examRepository.saveSubmission(submission, score);
            createdStudent = studentRepository.upsertStudent(
                    submission.studentId(),
                    submission.studentName(),
                    submission.studentEmail()
            );
            gradeId = studentRepository.saveGrade(submission.studentId(), submission.evaluationId(), score);
            queuePublisher.publishEmailResult(new EmailEvent(
                    submission.studentEmail(),
                    submission.studentName(),
                    submission.evaluationId(),
                    score
            ));
        } catch (Exception e) {
            if (gradeId != null) {
                studentRepository.deleteGrade(gradeId);
            }
            if (createdStudent) {
                studentRepository.deleteStudentIfNoGrades(submission.studentId());
            }
            if (submissionId != null) {
                examRepository.deleteSubmission(submissionId);
            }
            throw new DistributedTransactionError("Saga failed, compensating actions applied", e);
        }

        return new EvaluationResult(submission.evaluationId(), submission.studentId(), score, "PROCESSED");
    }

    private double calculateScore(EvaluationSubmission submission) {
        if (submission.answers().isEmpty()) {
            return 0.0;
        }

        long correct = submission.answers().stream()
                .filter(answer -> answer.answer().equals(answerKey.get(answer.questionId())))
                .count();

        double raw = ((double) correct / submission.answers().size()) * 100.0;
        return BigDecimal.valueOf(raw).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
