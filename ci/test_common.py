import pytest

from common import (
    ReleaseType,
    get_changelog,
    get_jar_path,
    get_readme,
    should_include_badge,
)

CHANGELOG = """# Changelog

## [unreleased](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/unreleased) - unreleased

## [2.3.10](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.3.10) - 2025-08-01

### Fixed
- Fix in 2.3.10

## [2.3.1](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.3.1) - 2025-07-01

### Added
- Feature in 2.3.1

### Fixed
- Fix in 2.3.1

## [2.3.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.3.0) - 2025-06-01

## [1.0.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/1.0.0) - 2021-01-14

### Added
- First release
"""

README = """# DiscordRoleSync

[![Discord](https://img.shields.io/discord/1)](https://discord.gg/x)
[![GitLab Issues](https://img.shields.io/gitlab/issues/open/x)](https://gitlab.com/x)
[![Modrinth](https://img.shields.io/modrinth/dt/x)](https://modrinth.com/x)

Some text with an inline [![badge](https://example.com)](https://example.com).
"""


@pytest.fixture
def repo(tmp_path, monkeypatch):
    """Creates a fake repository root, and runs the test from its ci/ directory like CI does."""
    (tmp_path / "CHANGELOG.md").write_text(CHANGELOG)
    (tmp_path / "README.md").write_text(README)
    (tmp_path / "ci").mkdir()
    monkeypatch.chdir(tmp_path / "ci")
    return tmp_path


@pytest.mark.parametrize("version", ["2.3.3", "0.0.1", "10.20.30"])
def test_release_type_release(version):
    assert ReleaseType.get_release_type(version) == ReleaseType.RELEASE


@pytest.mark.parametrize("version", ["2.4.0-rc.1", "3.0.0-rc.12"])
def test_release_type_release_candidate(version):
    assert ReleaseType.get_release_type(version) == ReleaseType.RELEASE_CANDIDATE


@pytest.mark.parametrize("version", [
    "", "2.3", "v2.3.3", "2.3.3-rc", "2.3.3-rc.", "2.3.3-beta.1", "1.0.0-BETA.18", "master-abc1234", "2.3.3 ",
])
def test_release_type_unknown(version):
    assert ReleaseType.get_release_type(version) == ReleaseType.UNKNOWN


def test_get_jar_path():
    assert get_jar_path("2.3.3") == "../build/libs/discord_role_sync-2.3.3.jar"


@pytest.mark.parametrize("line, expected", [
    ("[![Discord](https://img.shields.io/discord/1)](https://discord.gg/x)", True),
    ("[![GitLab Issues](https://img.shields.io/x)](https://gitlab.com/x)", True),
    ("[![Modrinth](https://img.shields.io/x)](https://modrinth.com/x)", False),
    ("Discord", False),
])
def test_should_include_badge(line, expected):
    assert should_include_badge(line) == expected


def test_get_readme_removes_badges_not_included(repo):
    readme = get_readme()

    assert "[![Discord]" in readme
    assert "[![GitLab Issues]" in readme
    assert "[![Modrinth]" not in readme
    # Only lines starting with a badge are removed
    assert "Some text with an inline [![badge]" in readme
    assert readme.startswith("# DiscordRoleSync\n")


def test_get_changelog_release(repo):
    assert get_changelog("2.3.1") == "### Added\n- Feature in 2.3.1\n\n### Fixed\n- Fix in 2.3.1"


def test_get_changelog_does_not_match_version_prefix(repo):
    assert get_changelog("2.3.10") == "### Fixed\n- Fix in 2.3.10"


def test_get_changelog_last_version_in_file(repo):
    assert get_changelog("1.0.0") == "### Added\n- First release"


def test_get_changelog_missing_version(repo):
    with pytest.raises(Exception, match="No changelog found for version 2.9.9"):
        get_changelog("2.9.9")


def test_get_changelog_empty_version(repo):
    with pytest.raises(Exception, match="No changelog found for version 2.3.0"):
        get_changelog("2.3.0")


def test_get_changelog_release_candidate(repo):
    changelog = get_changelog("2.4.0-rc.2")

    assert changelog.startswith("This is a release candidate for version 2.4.0.")
    assert "CHANGELOG.md" in changelog


def test_get_changelog_unknown_version(repo):
    with pytest.raises(Exception, match="unknown version type"):
        get_changelog("master-abc1234")
