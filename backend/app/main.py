import hashlib
import os
import re
import sqlite3
from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path
from typing import Annotated, Literal
from uuid import UUID

from fastapi import Depends, FastAPI, Header, HTTPException, status
from pydantic import BaseModel, Field, StrictBool, field_validator, model_validator


DEFAULT_DB_PATH = Path(
    os.environ.get("ATTENTION_OS_DB_PATH", "data/attention_os.sqlite3")
)
TOKEN_PATTERN = re.compile(r"^[0-9a-f]{64}$")


class SessionPayload(BaseModel):
    id: UUID
    goal: str = Field(min_length=1, max_length=80)
    planned_minutes: int
    started_at: datetime
    actual_seconds: int = Field(ge=0)
    status: Literal["completed", "ended_early"]
    reported_distractions: int | None = Field(default=None, ge=0, le=3)

    @field_validator("goal")
    @classmethod
    def strip_goal(cls, value: str) -> str:
        value = value.strip()
        if not value:
            raise ValueError("goal must not be blank")
        return value

    @field_validator("started_at")
    @classmethod
    def require_timezone(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("started_at must include a timezone")
        return value

    @model_validator(mode="after")
    def validate_duration(self) -> "SessionPayload":
        if self.planned_minutes not in {15, 25, 30, 45, 60}:
            raise ValueError("planned_minutes is not a supported duration")
        if self.actual_seconds > self.planned_minutes * 60:
            raise ValueError("actual_seconds exceeds planned duration")
        if (
            self.status == "completed"
            and self.actual_seconds != self.planned_minutes * 60
        ):
            raise ValueError("completed sessions must reach planned duration")
        return self


class SessionResponse(BaseModel):
    id: UUID
    goal: str
    planned_minutes: int
    started_at: datetime
    actual_seconds: int
    status: Literal["completed", "ended_early"]
    reported_distractions: int | None


class PreferencesPayload(BaseModel):
    preferred_focus_duration: int = Field(strict=True)
    reminders_enabled: StrictBool

    @field_validator("preferred_focus_duration")
    @classmethod
    def validate_preferred_duration(cls, value: int) -> int:
        if value not in {15, 25, 30, 45, 60}:
            raise ValueError("preferred_focus_duration is not supported")
        return value


class PreferencesResponse(BaseModel):
    preferred_focus_duration: Literal[15, 25, 30, 45, 60] | None
    reminders_enabled: bool | None


def _token_hash(authorization: str | None) -> str:
    if authorization is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="A device bearer token is required",
            headers={"WWW-Authenticate": "Bearer"},
        )
    scheme, separator, token = authorization.partition(" ")
    if (
        not separator
        or scheme.lower() != "bearer"
        or TOKEN_PATTERN.fullmatch(token) is None
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="The device bearer token is invalid",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return hashlib.sha256(token.encode("ascii")).hexdigest()


def create_app(database_path: str | Path = DEFAULT_DB_PATH) -> FastAPI:
    path = str(database_path)

    def connect() -> sqlite3.Connection:
        if path != ":memory:":
            Path(path).parent.mkdir(parents=True, exist_ok=True)
        connection = sqlite3.connect(path, timeout=5)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        return connection

    with closing(connect()) as connection:
        connection.executescript(
            """
            CREATE TABLE IF NOT EXISTS devices (
                token_hash TEXT PRIMARY KEY,
                created_at TEXT NOT NULL
            );
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                device_token_hash TEXT NOT NULL
                    REFERENCES devices(token_hash) ON DELETE CASCADE,
                goal TEXT NOT NULL,
                planned_minutes INTEGER NOT NULL,
                started_at TEXT NOT NULL,
                actual_seconds INTEGER NOT NULL,
                status TEXT NOT NULL,
                reported_distractions INTEGER
            );
            CREATE INDEX IF NOT EXISTS sessions_by_device_and_start
                ON sessions(device_token_hash, started_at DESC);
            CREATE TABLE IF NOT EXISTS device_preferences (
                device_token_hash TEXT PRIMARY KEY
                    REFERENCES devices(token_hash) ON DELETE CASCADE,
                preferred_focus_duration INTEGER NOT NULL,
                reminders_enabled INTEGER NOT NULL
                    CHECK (reminders_enabled IN (0, 1))
            );
            """
        )
        connection.commit()

    app = FastAPI(
        title="Attention OS API",
        version="0.1.0",
        description=(
            "Prototype session and focus-preference sync API. App usage and "
            "notification data are not collected."
        ),
    )
    @app.get("/healthz")
    def health_check() -> dict[str, str]:
        return {"status": "ok"}

    def authenticated_device(
        authorization: Annotated[str | None, Header()] = None,
    ) -> str:
        return _token_hash(authorization)

    @app.get("/v1/sessions", response_model=list[SessionResponse])
    def list_sessions(
        device_hash: Annotated[str, Depends(authenticated_device)],
    ) -> list[SessionResponse]:
        with closing(connect()) as connection:
            rows = connection.execute(
                """
                SELECT id, goal, planned_minutes, started_at, actual_seconds,
                       status, reported_distractions
                FROM sessions
                WHERE device_token_hash = ?
                ORDER BY started_at DESC, id DESC
                """,
                (device_hash,),
            ).fetchall()
        return [SessionResponse.model_validate(dict(row)) for row in rows]

    @app.post(
        "/v1/sessions",
        response_model=SessionResponse,
        status_code=status.HTTP_201_CREATED,
    )
    def save_session(
        payload: SessionPayload,
        device_hash: Annotated[str, Depends(authenticated_device)],
    ) -> SessionResponse:
        session = payload.model_dump(mode="json")
        session["started_at"] = payload.started_at.astimezone(timezone.utc).isoformat()
        with closing(connect()) as connection:
            connection.execute(
                """
                INSERT OR IGNORE INTO devices (token_hash, created_at)
                VALUES (?, ?)
                """,
                (device_hash, datetime.now(timezone.utc).isoformat()),
            )
            cursor = connection.execute(
                """
                INSERT INTO sessions (
                    id, device_token_hash, goal, planned_minutes, started_at,
                    actual_seconds, status, reported_distractions
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    goal = excluded.goal,
                    planned_minutes = excluded.planned_minutes,
                    started_at = excluded.started_at,
                    actual_seconds = excluded.actual_seconds,
                    status = excluded.status,
                    reported_distractions = excluded.reported_distractions
                WHERE sessions.device_token_hash = excluded.device_token_hash
                """,
                (
                    str(payload.id),
                    device_hash,
                    session["goal"],
                    payload.planned_minutes,
                    session["started_at"],
                    payload.actual_seconds,
                    payload.status,
                    payload.reported_distractions,
                ),
            )
            if cursor.rowcount != 1:
                raise HTTPException(
                    status_code=status.HTTP_409_CONFLICT,
                    detail="This session ID belongs to another device",
                )
            connection.commit()
        return SessionResponse(**session)

    @app.get("/v1/preferences", response_model=PreferencesResponse)
    def get_preferences(
        device_hash: Annotated[str, Depends(authenticated_device)],
    ) -> PreferencesResponse:
        with closing(connect()) as connection:
            row = connection.execute(
                """
                SELECT preferred_focus_duration, reminders_enabled
                FROM device_preferences
                WHERE device_token_hash = ?
                """,
                (device_hash,),
            ).fetchone()
        if row is None:
            return PreferencesResponse(
                preferred_focus_duration=None,
                reminders_enabled=None,
            )
        return PreferencesResponse(
            preferred_focus_duration=row["preferred_focus_duration"],
            reminders_enabled=bool(row["reminders_enabled"]),
        )

    @app.put("/v1/preferences", response_model=PreferencesResponse)
    def save_preferences(
        payload: PreferencesPayload,
        device_hash: Annotated[str, Depends(authenticated_device)],
    ) -> PreferencesResponse:
        with closing(connect()) as connection:
            connection.execute(
                """
                INSERT OR IGNORE INTO devices (token_hash, created_at)
                VALUES (?, ?)
                """,
                (device_hash, datetime.now(timezone.utc).isoformat()),
            )
            connection.execute(
                """
                INSERT INTO device_preferences (
                    device_token_hash, preferred_focus_duration,
                    reminders_enabled
                )
                VALUES (?, ?, ?)
                ON CONFLICT(device_token_hash) DO UPDATE SET
                    preferred_focus_duration =
                        excluded.preferred_focus_duration,
                    reminders_enabled = excluded.reminders_enabled
                """,
                (
                    device_hash,
                    payload.preferred_focus_duration,
                    int(payload.reminders_enabled),
                ),
            )
            connection.commit()
        return PreferencesResponse(
            preferred_focus_duration=payload.preferred_focus_duration,
            reminders_enabled=payload.reminders_enabled,
        )

    return app


app = create_app()
