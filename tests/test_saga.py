import tempfile
import unittest
from pathlib import Path

from common.models import EvaluationAnswer, EvaluationSubmission
from logic_service.queue.producer import InMemoryPublisher, QueuePublisher
from logic_service.repositories.exam_repository import ExamRepository
from logic_service.repositories.student_repository import StudentRepository
from logic_service.saga import DistributedTransactionError, EvaluationSagaOrchestrator


class FailingPublisher(QueuePublisher):
    def publish_email_result(self, event):
        raise RuntimeError("queue unavailable")


class EvaluationSagaTests(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        base = Path(self.temp_dir.name)
        self.exam_db = str(base / "exam.db")
        self.student_db = str(base / "student.db")
        self.answer_key = {"Q1": "A", "Q2": "B"}

    def tearDown(self):
        self.temp_dir.cleanup()

    def _build_submission(self) -> EvaluationSubmission:
        return EvaluationSubmission(
            evaluation_id="EVAL-1",
            student_id="STU-1",
            student_name="Juan",
            student_email="juan@example.com",
            answers=[
                EvaluationAnswer(question_id="Q1", answer="A"),
                EvaluationAnswer(question_id="Q2", answer="C"),
            ],
        )

    def test_process_success_persists_and_emits_event(self):
        publisher = InMemoryPublisher()
        orchestrator = EvaluationSagaOrchestrator(
            exam_repository=ExamRepository(self.exam_db),
            student_repository=StudentRepository(self.student_db),
            queue_publisher=publisher,
            answer_key=self.answer_key,
        )

        result = orchestrator.process(self._build_submission())

        self.assertEqual(result.score, 50.0)
        self.assertEqual(len(publisher.messages), 1)

    def test_process_failure_applies_compensation(self):
        exam_repository = ExamRepository(self.exam_db)
        student_repository = StudentRepository(self.student_db)
        orchestrator = EvaluationSagaOrchestrator(
            exam_repository=exam_repository,
            student_repository=student_repository,
            queue_publisher=FailingPublisher(),
            answer_key=self.answer_key,
        )

        with self.assertRaises(DistributedTransactionError):
            orchestrator.process(self._build_submission())

        self.assertEqual(exam_repository.count_submissions(), 0)
        self.assertEqual(student_repository.count_grades(), 0)


if __name__ == "__main__":
    unittest.main()
