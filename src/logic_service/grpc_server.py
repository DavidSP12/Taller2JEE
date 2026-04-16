from __future__ import annotations

import os
from concurrent import futures
from pathlib import Path

import grpc

from common.models import EvaluationAnswer, EvaluationSubmission
from logic_service.queue.producer import RabbitMQPublisher
from logic_service.repositories.exam_repository import ExamRepository
from logic_service.repositories.student_repository import StudentRepository
from logic_service.saga import DistributedTransactionError, EvaluationSagaOrchestrator

from logic_service.generated import evaluation_pb2, evaluation_pb2_grpc


class EvaluationService(evaluation_pb2_grpc.EvaluationServiceServicer):
    def __init__(self, orchestrator: EvaluationSagaOrchestrator) -> None:
        self.orchestrator = orchestrator

    def SubmitEvaluation(self, request, context):  # noqa: N802 - gRPC convention
        submission = EvaluationSubmission(
            evaluation_id=request.evaluation_id,
            student_id=request.student_id,
            student_name=request.student_name,
            student_email=request.student_email,
            answers=[
                EvaluationAnswer(question_id=item.question_id, answer=item.answer)
                for item in request.answers
            ],
        )

        try:
            result = self.orchestrator.process(submission)
        except DistributedTransactionError as exc:
            context.set_code(grpc.StatusCode.ABORTED)
            context.set_details(str(exc))
            return evaluation_pb2.SubmitEvaluationResponse()

        return evaluation_pb2.SubmitEvaluationResponse(
            evaluation_id=result.evaluation_id,
            student_id=result.student_id,
            score=result.score,
            status=result.status,
        )


def build_default_orchestrator() -> EvaluationSagaOrchestrator:
    base_data_path = Path("data")
    exam_repository = ExamRepository(str(base_data_path / "exam.db"))
    student_repository = StudentRepository(str(base_data_path / "student.db"))
    publisher = RabbitMQPublisher(
        host=os.getenv("RABBITMQ_HOST", "localhost"),
        queue_name=os.getenv("RABBITMQ_QUEUE", "email_notifications"),
    )

    answer_key = {"Q1": "A", "Q2": "C", "Q3": "B", "Q4": "D"}

    return EvaluationSagaOrchestrator(
        exam_repository=exam_repository,
        student_repository=student_repository,
        queue_publisher=publisher,
        answer_key=answer_key,
    )


def serve(port: int = 50051) -> None:
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    evaluation_pb2_grpc.add_EvaluationServiceServicer_to_server(
        EvaluationService(build_default_orchestrator()),
        server,
    )
    server.add_insecure_port(f"[::]:{port}")
    server.start()
    print(f"gRPC server listening on {port}")
    server.wait_for_termination()


if __name__ == "__main__":
    serve(port=int(os.getenv("GRPC_PORT", "50051")))
