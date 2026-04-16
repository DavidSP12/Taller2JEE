package com.taller2jee.logic.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taller2jee.common.model.EvaluationAnswer;
import com.taller2jee.common.model.EvaluationSubmission;
import com.taller2jee.logic.persistence.entity.ExamSubmissionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

import java.util.List;
import java.util.Map;

public class ExamRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EntityManagerFactory entityManagerFactory;

    public ExamRepository(String dbPathOrJdbcUrl) {
        this.entityManagerFactory = JpaEntityManagerFactoryProvider.create(dbPathOrJdbcUrl);
    }

    public Long saveSubmission(EvaluationSubmission submission, double score) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ExamSubmissionEntity entity = new ExamSubmissionEntity();
            entity.setEvaluationId(submission.evaluationId());
            entity.setStudentId(submission.studentId());
            entity.setAnswersJson(serializeAnswers(submission.answers()));
            entity.setScore(score);
            em.persist(entity);
            tx.commit();
            return entity.getId();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void deleteSubmission(Long submissionId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            ExamSubmissionEntity entity = em.find(ExamSubmissionEntity.class, submissionId);
            if (entity != null) {
                em.remove(entity);
            }
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public long countSubmissions() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            return em.createQuery("select count(e) from ExamSubmissionEntity e", Long.class)
                    .getSingleResult();
        } finally {
            em.close();
        }
    }

    public void close() {
        if (entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }

    private String serializeAnswers(List<EvaluationAnswer> answers) {
        List<Map<String, String>> payload = answers.stream()
                .map(answer -> Map.of(
                        "question_id", answer.questionId(),
                        "answer", answer.answer()
                ))
                .toList();
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize answers", e);
        }
    }
}
