from __future__ import annotations

from typing import Dict

from common.models import EmailEvent, EvaluationResult, EvaluationSubmission
from logic_service.queue.producer import QueuePublisher
from logic_service.repositories.exam_repository import ExamRepository
from logic_service.repositories.student_repository import StudentRepository


class DistributedTransactionError(RuntimeError):
    pass


class EvaluationSagaOrchestrator:
    def __init__(
        self,
        exam_repository: ExamRepository,
        student_repository: StudentRepository,
        queue_publisher: QueuePublisher,
        answer_key: Dict[str, str],
    ) -> None:
        self.exam_repository = exam_repository
        self.student_repository = student_repository
        self.queue_publisher = queue_publisher
        self.answer_key = answer_key

    def process(self, submission: EvaluationSubmission) -> EvaluationResult:
        score = self._calculate_score(submission)
        submission_id = None
        grade_id = None
        created_student = False

        try:
            submission_id = self.exam_repository.save_submission(submission, score)

            created_student = self.student_repository.upsert_student(
                student_id=submission.student_id,
                name=submission.student_name,
                email=submission.student_email,
            )
            grade_id = self.student_repository.save_grade(
                student_id=submission.student_id,
                evaluation_id=submission.evaluation_id,
                score=score,
            )

            self.queue_publisher.publish_email_result(
                EmailEvent(
                    student_email=submission.student_email,
                    student_name=submission.student_name,
                    evaluation_id=submission.evaluation_id,
                    score=score,
                )
            )
        except Exception as exc:
            if grade_id is not None:
                self.student_repository.delete_grade(grade_id)
            if created_student:
                self.student_repository.delete_student_if_no_grades(submission.student_id)
            if submission_id is not None:
                self.exam_repository.delete_submission(submission_id)
            raise DistributedTransactionError("Saga failed, compensating actions applied") from exc

        return EvaluationResult(
            evaluation_id=submission.evaluation_id,
            student_id=submission.student_id,
            score=score,
            status="PROCESSED",
        )

    def _calculate_score(self, submission: EvaluationSubmission) -> float:
        if not submission.answers:
            return 0.0

        total = len(submission.answers)
        correct = sum(
            1
            for answer in submission.answers
            if self.answer_key.get(answer.question_id) == answer.answer
        )
        return round((correct / total) * 100.0, 2)
