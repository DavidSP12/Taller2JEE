from __future__ import annotations

from dataclasses import dataclass, asdict
from typing import Dict, List
import json


@dataclass(frozen=True)
class EvaluationAnswer:
    question_id: str
    answer: str


@dataclass(frozen=True)
class EvaluationSubmission:
    evaluation_id: str
    student_id: str
    student_name: str
    student_email: str
    answers: List[EvaluationAnswer]


@dataclass(frozen=True)
class EvaluationResult:
    evaluation_id: str
    student_id: str
    score: float
    status: str


@dataclass(frozen=True)
class EmailEvent:
    student_email: str
    student_name: str
    evaluation_id: str
    score: float

    def to_json(self) -> str:
        return json.dumps(asdict(self))

    @staticmethod
    def from_json(payload: str) -> "EmailEvent":
        data: Dict[str, object] = json.loads(payload)
        return EmailEvent(
            student_email=str(data["student_email"]),
            student_name=str(data["student_name"]),
            evaluation_id=str(data["evaluation_id"]),
            score=float(data["score"]),
        )
