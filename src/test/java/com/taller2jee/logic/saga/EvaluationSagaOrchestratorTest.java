package com.taller2jee.logic.saga;

import com.taller2jee.common.model.EvaluationAnswer;
import com.taller2jee.common.model.EvaluationSubmission;
import com.taller2jee.logic.persistence.ExamRepository;
import com.taller2jee.logic.persistence.StudentRepository;
import com.taller2jee.logic.queue.InMemoryPublisher;
import com.taller2jee.logic.queue.QueuePublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EvaluationSagaOrchestratorTest {

    private Path tempDir;
    private ExamRepository examRepository;
    private StudentRepository studentRepository;
    private final Map<String, String> answerKey = Map.of("Q1", "A", "Q2", "B");

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("taller2jee-test");
        examRepository = new ExamRepository(tempDir.resolve("exam.db").toString());
        studentRepository = new StudentRepository(tempDir.resolve("student.db").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (examRepository != null) {
            examRepository.close();
        }
        if (studentRepository != null) {
            studentRepository.close();
        }
        Files.deleteIfExists(tempDir.resolve("exam.db"));
        Files.deleteIfExists(tempDir.resolve("student.db"));
        Files.deleteIfExists(tempDir);
    }

    @Test
    void processSuccessPersistsAndEmitsEvent() {
        InMemoryPublisher publisher = new InMemoryPublisher();
        EvaluationSagaOrchestrator orchestrator = new EvaluationSagaOrchestrator(
                examRepository,
                studentRepository,
                publisher,
                answerKey
        );

        var result = orchestrator.process(buildSubmission());

        assertEquals(50.0, result.score());
        assertEquals(1, publisher.messages().size());
    }

    @Test
    void processFailureAppliesCompensation() {
        EvaluationSagaOrchestrator orchestrator = new EvaluationSagaOrchestrator(
                examRepository,
                studentRepository,
                new FailingPublisher(),
                answerKey
        );

        assertThrows(DistributedTransactionError.class, () -> orchestrator.process(buildSubmission()));
        assertEquals(0, examRepository.countSubmissions());
        assertEquals(0, studentRepository.countGrades());
        assertEquals(0, studentRepository.countStudents());
    }

    private EvaluationSubmission buildSubmission() {
        return new EvaluationSubmission(
                "EVAL-1",
                "STU-1",
                "Juan",
                "juan@example.com",
                List.of(
                        new EvaluationAnswer("Q1", "A"),
                        new EvaluationAnswer("Q2", "C")
                )
        );
    }

    private static class FailingPublisher implements QueuePublisher {
        @Override
        public void publishEmailResult(com.taller2jee.common.model.EmailEvent event) {
            throw new RuntimeException("queue unavailable");
        }
    }
}
