package com.taller2jee.logic.persistence;

import com.taller2jee.logic.persistence.entity.GradeEntity;
import com.taller2jee.logic.persistence.entity.StudentEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;

public class StudentRepository {
    private final EntityManagerFactory entityManagerFactory;

    public StudentRepository(String dbPathOrJdbcUrl) {
        this.entityManagerFactory = JpaEntityManagerFactoryProvider.create(dbPathOrJdbcUrl);
    }

    public boolean upsertStudent(String studentId, String name, String email) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            StudentEntity student = em.find(StudentEntity.class, studentId);
            boolean created = student == null;
            if (student == null) {
                em.persist(new StudentEntity(studentId, name, email));
            } else {
                student.setName(name);
                student.setEmail(email);
            }
            tx.commit();
            return created;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public Long saveGrade(String studentId, String evaluationId, double score) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            StudentEntity student = em.find(StudentEntity.class, studentId);
            if (student == null) {
                throw new IllegalStateException("Student does not exist for grade persistence");
            }
            GradeEntity grade = new GradeEntity();
            grade.setStudent(student);
            grade.setEvaluationId(evaluationId);
            grade.setScore(score);
            em.persist(grade);
            tx.commit();
            return grade.getId();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        } finally {
            em.close();
        }
    }

    public void deleteGrade(long gradeId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            GradeEntity grade = em.find(GradeEntity.class, gradeId);
            if (grade != null) {
                em.remove(grade);
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

    public void deleteStudentIfNoGrades(String studentId) {
        EntityManager em = entityManagerFactory.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Long count = em.createQuery("select count(g) from GradeEntity g where g.student.id = :studentId", Long.class)
                    .setParameter("studentId", studentId)
                    .getSingleResult();
            if (count == 0) {
                StudentEntity student = em.find(StudentEntity.class, studentId);
                if (student != null) {
                    em.remove(student);
                }
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

    public long countGrades() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            return em.createQuery("select count(g) from GradeEntity g", Long.class).getSingleResult();
        } finally {
            em.close();
        }
    }

    public long countStudents() {
        EntityManager em = entityManagerFactory.createEntityManager();
        try {
            return em.createQuery("select count(s) from StudentEntity s", Long.class).getSingleResult();
        } finally {
            em.close();
        }
    }

    public void close() {
        if (entityManagerFactory.isOpen()) {
            entityManagerFactory.close();
        }
    }
}
