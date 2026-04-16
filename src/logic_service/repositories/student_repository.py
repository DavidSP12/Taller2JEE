from __future__ import annotations

from pathlib import Path

from sqlalchemy import create_engine, func, select
from sqlalchemy.orm import Session

from logic_service.db_models import Base, GradeRow, StudentRow


class StudentRepository:
    """Repository for student records and grades backed by SQLAlchemy ORM.

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

    def upsert_student(self, student_id: str, name: str, email: str) -> bool:
        with Session(self._engine) as session:
            existing = session.get(StudentRow, student_id)
            created = existing is None
            if existing is None:
                session.add(StudentRow(id=student_id, name=name, email=email))
            else:
                existing.name = name
                existing.email = email
            session.commit()
        return created

    def save_grade(self, student_id: str, evaluation_id: str, score: float) -> int:
        with Session(self._engine) as session:
            row = GradeRow(
                student_id=student_id,
                evaluation_id=evaluation_id,
                score=score,
            )
            session.add(row)
            session.commit()
            session.refresh(row)
            return row.id

    def delete_grade(self, grade_id: int) -> None:
        with Session(self._engine) as session:
            row = session.get(GradeRow, grade_id)
            if row is not None:
                session.delete(row)
                session.commit()

    def delete_student_if_no_grades(self, student_id: str) -> None:
        with Session(self._engine) as session:
            count = session.scalar(
                select(func.count())
                .select_from(GradeRow)
                .where(GradeRow.student_id == student_id)
            ) or 0
            if count == 0:
                student = session.get(StudentRow, student_id)
                if student is not None:
                    session.delete(student)
                    session.commit()

    def count_grades(self) -> int:
        with Session(self._engine) as session:
            return session.scalar(select(func.count()).select_from(GradeRow)) or 0

    def count_students(self) -> int:
        with Session(self._engine) as session:
            return session.scalar(select(func.count()).select_from(StudentRow)) or 0

