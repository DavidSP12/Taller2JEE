from __future__ import annotations

import json
from pathlib import Path

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from common.models import EvaluationSubmission
from logic_service.db_models import Base, ExamSubmissionRow


class ExamRepository:
    """Repository for exam submissions backed by SQLAlchemy ORM.

    Accepts a file path to a SQLite database.  To use PostgreSQL (or any other
    SQLAlchemy-supported database) instantiate the repository with a full
    connection URL instead, e.g. ``"postgresql+psycopg2://user:pass@host/db"``.
    """

    def __init__(self, db_path: str) -> None:
        # Accept either a bare file path (→ SQLite) or a full SQLAlchemy URL.
        if "://" in db_path:
            url = db_path
        else:
            url = f"sqlite:///{Path(db_path).absolute()}"
        self._engine = create_engine(url)
        Base.metadata.create_all(self._engine)

    def save_submission(self, submission: EvaluationSubmission, score: float) -> int:
        answers_json = json.dumps(
            [{"question_id": a.question_id, "answer": a.answer} for a in submission.answers]
        )
        with Session(self._engine) as session:
            row = ExamSubmissionRow(
                evaluation_id=submission.evaluation_id,
                student_id=submission.student_id,
                answers_json=answers_json,
                score=score,
            )
            session.add(row)
            session.commit()
            session.refresh(row)
            return row.id

    def delete_submission(self, submission_id: int) -> None:
        with Session(self._engine) as session:
            row = session.get(ExamSubmissionRow, submission_id)
            if row is not None:
                session.delete(row)
                session.commit()

    def count_submissions(self) -> int:
        with Session(self._engine) as session:
            return session.scalar(
                select(func.count()).select_from(ExamSubmissionRow)
            ) or 0

