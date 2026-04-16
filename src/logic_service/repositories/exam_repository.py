from __future__ import annotations

import json
import sqlite3
from pathlib import Path

from common.models import EvaluationSubmission


class ExamRepository:
    def __init__(self, db_path: str) -> None:
        self.db_path = Path(db_path)
        self.db_path.parent.mkdir(parents=True, exist_ok=True)
        self._init_db()

    def _connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self.db_path)

    def _init_db(self) -> None:
        with self._connect() as conn:
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS exam_submissions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    evaluation_id TEXT NOT NULL,
                    student_id TEXT NOT NULL,
                    answers_json TEXT NOT NULL,
                    score REAL NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """
            )
            conn.commit()

    def save_submission(self, submission: EvaluationSubmission, score: float) -> int:
        answers_json = json.dumps(
            [{"question_id": a.question_id, "answer": a.answer} for a in submission.answers]
        )
        with self._connect() as conn:
            cursor = conn.execute(
                """
                INSERT INTO exam_submissions (evaluation_id, student_id, answers_json, score)
                VALUES (?, ?, ?, ?)
                """,
                (submission.evaluation_id, submission.student_id, answers_json, score),
            )
            conn.commit()
            return int(cursor.lastrowid)

    def delete_submission(self, submission_id: int) -> None:
        with self._connect() as conn:
            conn.execute("DELETE FROM exam_submissions WHERE id = ?", (submission_id,))
            conn.commit()
