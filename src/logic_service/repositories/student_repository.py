from __future__ import annotations

import sqlite3
from pathlib import Path


class StudentRepository:
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
                CREATE TABLE IF NOT EXISTS students (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    email TEXT NOT NULL
                )
                """
            )
            conn.execute(
                """
                CREATE TABLE IF NOT EXISTS grades (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id TEXT NOT NULL,
                    evaluation_id TEXT NOT NULL,
                    score REAL NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY(student_id) REFERENCES students(id)
                )
                """
            )
            conn.commit()

    def upsert_student(self, student_id: str, name: str, email: str) -> bool:
        created = False
        with self._connect() as conn:
            existing = conn.execute(
                "SELECT 1 FROM students WHERE id = ? LIMIT 1", (student_id,)
            ).fetchone()
            created = existing is None
            conn.execute(
                """
                INSERT INTO students (id, name, email)
                VALUES (?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name, email=excluded.email
                """,
                (student_id, name, email),
            )
            conn.commit()
        return created

    def save_grade(self, student_id: str, evaluation_id: str, score: float) -> int:
        with self._connect() as conn:
            cursor = conn.execute(
                """
                INSERT INTO grades (student_id, evaluation_id, score)
                VALUES (?, ?, ?)
                """,
                (student_id, evaluation_id, score),
            )
            conn.commit()
            return int(cursor.lastrowid)

    def delete_grade(self, grade_id: int) -> None:
        with self._connect() as conn:
            conn.execute("DELETE FROM grades WHERE id = ?", (grade_id,))
            conn.commit()

    def delete_student_if_no_grades(self, student_id: str) -> None:
        with self._connect() as conn:
            grade_count = conn.execute(
                "SELECT COUNT(*) FROM grades WHERE student_id = ?",
                (student_id,),
            ).fetchone()[0]
            if grade_count == 0:
                conn.execute("DELETE FROM students WHERE id = ?", (student_id,))
            conn.commit()

    def count_grades(self) -> int:
        with self._connect() as conn:
            return int(conn.execute("SELECT COUNT(*) FROM grades").fetchone()[0])
