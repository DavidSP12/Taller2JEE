from __future__ import annotations

import grpc

from logic_service.generated import evaluation_pb2, evaluation_pb2_grpc


def submit_sample_evaluation(host: str = "localhost", port: int = 50051) -> None:
    channel = grpc.insecure_channel(f"{host}:{port}")
    stub = evaluation_pb2_grpc.EvaluationServiceStub(channel)

    response = stub.SubmitEvaluation(
        evaluation_pb2.SubmitEvaluationRequest(
            evaluation_id="EVAL-2026-01",
            student_id="STU-100",
            student_name="Ana Perez",
            student_email="ana@example.com",
            answers=[
                evaluation_pb2.Answer(question_id="Q1", answer="A"),
                evaluation_pb2.Answer(question_id="Q2", answer="C"),
                evaluation_pb2.Answer(question_id="Q3", answer="B"),
            ],
        )
    )

    print(
        f"Resultado => evaluation_id={response.evaluation_id}, "
        f"student_id={response.student_id}, score={response.score}, status={response.status}"
    )


if __name__ == "__main__":
    submit_sample_evaluation()
