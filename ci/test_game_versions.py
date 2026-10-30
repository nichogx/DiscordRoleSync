import pytest

import game_versions
from game_versions import (
    filter_supported,
    get_hangar_game_versions,
    get_modrinth_game_versions,
    parse_version,
)


class FakeResponse:
    def __init__(self, json_data, status_code=200):
        self._json = json_data
        self.status_code = status_code
        self.ok = status_code < 400
        self.text = str(json_data)

    def json(self):
        return self._json


@pytest.fixture(autouse=True)
def bounds(monkeypatch):
    """Fixed bounds, so the tests don't change when Renovate updates MAX_GAME_VERSION."""
    monkeypatch.setattr(game_versions, "MIN_GAME_VERSION", "1.8")
    monkeypatch.setattr(game_versions, "MAX_GAME_VERSION", "26.1.1")


def mock_get(monkeypatch, json_data, status_code=200):
    monkeypatch.setattr(game_versions.requests, "get", lambda *args, **kwargs: FakeResponse(json_data, status_code))


def test_parse_version_compares_numerically():
    assert parse_version("1.21.10") > parse_version("1.21.9")
    assert parse_version("26.1") > parse_version("1.21.11")
    assert parse_version("1.21") < parse_version("1.21.1")


def test_filter_supported_keeps_versions_between_bounds_sorted():
    versions = ["26.2", "1.21.10", "1.7.10", "26.1.1", "1.8", "1.21.9", "26.1", "1.8.9", "26.1.2"]

    assert filter_supported(versions, "Test") == ["1.8", "1.8.9", "1.21.9", "1.21.10", "26.1", "26.1.1"]


@pytest.mark.parametrize("missing", ["1.8", "26.1.1"])
def test_filter_supported_missing_bound(missing):
    versions = [v for v in ["1.8", "1.12.2", "26.1.1"] if v != missing]

    with pytest.raises(Exception, match=f"{missing} is not available on Test yet"):
        filter_supported(versions, "Test")


def test_modrinth_only_includes_releases(monkeypatch):
    mock_get(monkeypatch, [
        {"version": "26.2-snapshot-1", "version_type": "snapshot"},
        {"version": "26.1.1", "version_type": "release"},
        {"version": "26.1-rc-1", "version_type": "beta"},
        {"version": "26.1", "version_type": "release"},
        {"version": "1.8", "version_type": "release"},
        {"version": "b1.7.3", "version_type": "beta"},
        {"version": "a1.2.6", "version_type": "alpha"},
    ])

    assert get_modrinth_game_versions("test") == ["1.8", "26.1", "26.1.1"]


def test_modrinth_error(monkeypatch):
    mock_get(monkeypatch, {"error": "down"}, status_code=503)

    with pytest.raises(Exception, match="Modrinth game versions: HTTP 503"):
        get_modrinth_game_versions("test")


def test_hangar_flattens_sub_versions(monkeypatch):
    mock_get(monkeypatch, [
        {"version": "26.2", "subVersions": []},
        {"version": "26.1", "subVersions": ["26.1.2", "26.1.1", "26.1"]},
        {"version": "1.12", "subVersions": ["1.12.2", "1.12.1", "1.12"]},
        {"version": "1.8", "subVersions": ["1.8.8", "1.8"]},
    ])

    assert get_hangar_game_versions() == ["1.8", "1.8.8", "1.12", "1.12.1", "1.12.2", "26.1", "26.1.1"]


def test_hangar_error(monkeypatch):
    mock_get(monkeypatch, {"error": "down"}, status_code=500)

    with pytest.raises(Exception, match="Hangar game versions: HTTP 500"):
        get_hangar_game_versions()
