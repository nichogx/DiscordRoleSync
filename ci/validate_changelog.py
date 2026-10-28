import re
import sys
from datetime import date
from itertools import pairwise

CHANGELOG_PATH = "../CHANGELOG.md"

UNRELEASED_HEADING = "## [unreleased](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/unreleased) - unreleased"

# Keep a Changelog section types: https://keepachangelog.com/en/1.1.0/#how
SECTION_TYPES = ["Added", "Changed", "Deprecated", "Removed", "Fixed", "Security"]

VERSION_HEADING_RE = re.compile(r"^## \[(?P<version>[^\]]+)\]\((?P<url>[^)]+)\) - (?P<date>\S+)$")
# x.y.z, x.y.z-rc.N, and the legacy x.y.z-BETA.N
VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)(?:-(?:rc|BETA)\.(\d+))?$")


def version_sort_key(version):
    major, minor, patch, pre = VERSION_RE.match(version).groups()
    # Pre-releases sort before the release they precede
    return int(major), int(minor), int(patch), pre is None, int(pre or 0)


def validate(lines):
    errors = []

    if not lines or lines[0].rstrip("\n") != "# Changelog":
        errors.append("Line 1: the file must start with '# Changelog'")

    versions = []  # (line number, version, date)
    current_version = None
    current_sections = set()
    current_section = None  # (line number, name, has entries)

    def close_section():
        if current_section and not current_section[2]:
            errors.append(f"Line {current_section[0]}: section '{current_section[1]}' has no entries")

    for number, line in enumerate(lines, start=1):
        line = line.rstrip("\n")

        if line.startswith("# "):
            if number != 1:
                errors.append(f"Line {number}: only one top-level '# ' heading is allowed")

        elif line.startswith("## "):
            close_section()
            current_section = None
            current_sections = set()

            if not versions and line != UNRELEASED_HEADING:
                errors.append(f"Line {number}: the first version must be '{UNRELEASED_HEADING}'")

            if line == UNRELEASED_HEADING:
                if versions:
                    errors.append(f"Line {number}: the unreleased section must be the first version")
                current_version = "unreleased"
                versions.append((number, "unreleased", None))
                continue

            match = VERSION_HEADING_RE.match(line)
            if not match:
                errors.append(f"Line {number}: version heading must look like '## [x.y.z](url) - YYYY-MM-DD'")
                current_version = None
                continue

            version, url, release_date = match.group("version", "url", "date")
            current_version = version

            if not VERSION_RE.match(version):
                errors.append(f"Line {number}: '{version}' is not a valid version")
                continue

            if not url.endswith(f"/{version}"):
                errors.append(f"Line {number}: link '{url}' does not point to version '{version}'")

            try:
                parsed_date = date.fromisoformat(release_date)
            except ValueError:
                errors.append(f"Line {number}: '{release_date}' is not a valid YYYY-MM-DD date")
                parsed_date = None

            versions.append((number, version, parsed_date))

        elif line.startswith("### "):
            close_section()
            name = line[len("### "):]
            current_section = (number, name, False)

            if current_version is None:
                errors.append(f"Line {number}: section '{name}' is not under a version")
            if name not in SECTION_TYPES:
                errors.append(f"Line {number}: unknown section '{name}'. Expected one of: {', '.join(SECTION_TYPES)}")
            if name in current_sections:
                errors.append(f"Line {number}: duplicate section '{name}' in version '{current_version}'")
            current_sections.add(name)

        elif line.startswith("#"):
            errors.append(f"Line {number}: only '#', '##' and '###' headings are allowed")

        elif re.match(r"^\s*[*+] ", line):
            errors.append(f"Line {number}: use '- ' for list entries")

        elif re.match(r"^\s*- ", line):
            if current_section:
                current_section = (current_section[0], current_section[1], True)
            elif current_version is None:
                errors.append(f"Line {number}: list entry is not under a version")

    close_section()

    if not versions:
        errors.append("No versions found")

    released = [v for v in versions if v[1] != "unreleased" and VERSION_RE.match(v[1])]

    seen = set()
    for number, version, _ in released:
        if version in seen:
            errors.append(f"Line {number}: duplicate version '{version}'")
        seen.add(version)

    for (_, newer, newer_date), (number, older, older_date) in pairwise(released):
        if version_sort_key(older) >= version_sort_key(newer):
            errors.append(f"Line {number}: version '{older}' must be older than '{newer}' listed above it")
        if newer_date and older_date and older_date > newer_date:
            errors.append(f"Line {number}: date of '{older}' is after the date of '{newer}' listed above it")

    return errors


# Validates the format of CHANGELOG.md, so formatting mistakes are caught in
# merge requests instead of when a release is tagged.
if __name__ == "__main__":
    with open(CHANGELOG_PATH) as f:
        errors = validate(f.readlines())

    if errors:
        print("CHANGELOG.md failed validation:")
        for error in errors:
            print(f"\t- {error}")

        sys.exit(1)

    print("CHANGELOG.md is valid.")
