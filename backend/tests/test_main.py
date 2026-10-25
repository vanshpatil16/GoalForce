from datetime import datetime, timezone
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from app.main import create_app


TOKEN_A = "a" * 64
TOKEN_B = "b" * 64


@pytest.fixture
def client(tmp_path):
    app = create_app(tmp_path / "test.sqlite3")
    with TestClient(app) as test_client:
        yield test_client


def sample_session(**overrides):
    payload = {
        "id": str(uuid4()),
        "goal": "Study DSA",
        "planned_minutes": 25,
        "started_at": datetime.now(timezone.utc).isoformat(),
        "actual_seconds": 1500,
        "status": "completed",
        "reported_distractions": 0,
    }
    payload.update(overrides)
    return payload


def auth(token=TOKEN_A):
    return {"Authorization": f"Bearer {token}"}


def test_health_check_is_available_without_authentication(client):
    response = client.get("/healthz")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_session_requires_a_device_token(client):
    response = client.get("/v1/sessions")

    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


def test_session_is_saved_and_listed_for_its_device(client):
    payload = sample_session()

    created = client.post("/v1/sessions", json=payload, headers=auth())
    listed = client.get("/v1/sessions", headers=auth())

    assert created.status_code == 201
    assert created.json()["goal"] == "Study DSA"
    assert listed.status_code == 200
    assert [item["id"] for item in listed.json()] == [payload["id"]]


def test_sessions_are_isolated_between_device_tokens(client):
    client.post("/v1/sessions", json=sample_session(), headers=auth(TOKEN_A))

    response = client.get("/v1/sessions", headers=auth(TOKEN_B))

    assert response.status_code == 200
    assert response.json() == []


def test_saving_the_same_session_twice_is_idempotent(client):
    payload = sample_session()

    first = client.post("/v1/sessions", json=payload, headers=auth())
    second = client.post("/v1/sessions", json=payload, headers=auth())
    listed = client.get("/v1/sessions", headers=auth())

    assert first.status_code == second.status_code == 201
    assert len(listed.json()) == 1


def test_another_device_cannot_claim_an_existing_session_id(client):
    payload = sample_session()
    client.post("/v1/sessions", json=payload, headers=auth(TOKEN_A))

    response = client.post("/v1/sessions", json=payload, headers=auth(TOKEN_B))

    assert response.status_code == 409


@pytest.mark.parametrize(
    "overrides",
    [
        {"goal": "   "},
        {"planned_minutes": 20},
        {"actual_seconds": 1501},
        {"actual_seconds": 1499},
        {"reported_distractions": 4},
        {"started_at": "2026-10-25T17:30:00"},
    ],
)
def test_invalid_session_payload_is_rejected(client, overrides):
    response = client.post(
        "/v1/sessions",
        json=sample_session(**overrides),
        headers=auth(),
    )

    assert response.status_code == 422


def test_early_ended_session_accepts_a_partial_duration(client):
    payload = sample_session(
        actual_seconds=321,
        status="ended_early",
        reported_distractions=None,
    )

    response = client.post("/v1/sessions", json=payload, headers=auth())

    assert response.status_code == 201
    assert response.json()["actual_seconds"] == 321


def test_invalid_device_token_is_rejected(client):
    response = client.get(
        "/v1/sessions",
        headers={"Authorization": "Bearer not-a-token"},
    )

    assert response.status_code == 401


def test_preferences_require_a_device_token(client):
    response = client.get("/v1/preferences")

    assert response.status_code == 401


def test_preferences_are_saved_and_loaded_for_their_device(client):
    payload = {
        "preferred_focus_duration": 45,
        "reminders_enabled": True,
    }

    saved = client.put("/v1/preferences", json=payload, headers=auth())
    loaded = client.get("/v1/preferences", headers=auth())

    assert saved.status_code == 200
    assert saved.json() == payload
    assert loaded.status_code == 200
    assert loaded.json() == payload


def test_preferences_are_isolated_between_devices(client):
    client.put(
        "/v1/preferences",
        json={"preferred_focus_duration": 30, "reminders_enabled": False},
        headers=auth(TOKEN_A),
    )

    response = client.get("/v1/preferences", headers=auth(TOKEN_B))

    assert response.status_code == 200
    assert response.json() == {
        "preferred_focus_duration": None,
        "reminders_enabled": None,
    }


@pytest.mark.parametrize(
    "payload",
    [
        {"preferred_focus_duration": 20, "reminders_enabled": True},
        {"preferred_focus_duration": 25, "reminders_enabled": "yes"},
        {"preferred_focus_duration": 25},
    ],
)
def test_invalid_preferences_are_rejected(client, payload):
    response = client.put("/v1/preferences", json=payload, headers=auth())

    assert response.status_code == 422
