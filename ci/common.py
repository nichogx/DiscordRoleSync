import re
from enum import Enum

SUPPORTED_VERSIONS = [
    "1.8",
    "1.9", "1.9.1", "1.9.2", "1.9.3", "1.9.4",
    "1.10", "1.10.1", "1.10.2",
    "1.11", "1.11.1", "1.11.2",
    "1.12", "1.12.1", "1.12.2",
    "1.13", "1.13.1", "1.13.2",
    "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
    "1.15", "1.15.1", "1.15.2",
    "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
    "1.17", "1.17.1",
    "1.18", "1.18.1", "1.18.2",
    "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
    "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
]


class ReleaseType(Enum):
    UNKNOWN = 1
    RELEASE = 2
    RELEASE_CANDIDATE = 3

    @staticmethod
    def get_release_type(version):
        if re.match(r"^\d+\.\d+\.\d+$", version):
            return ReleaseType.RELEASE

        if re.match(r"^\d+\.\d+\.\d+-rc\.\d+$", version):
            return ReleaseType.RELEASE_CANDIDATE

        return ReleaseType.UNKNOWN


include_badges = ["Discord", "GitLab Issues"]
def should_include_badge(line):
    for badge in include_badges:
        if line.startswith(f"[![{badge}"):
            return True

    return False

def get_readme():
    description = ""
    with open("../README.md") as f:
        for line in f:
            if line.startswith("[![") and not should_include_badge(line):
                continue

            description += line

    return description


def get_changelog(version):
    release_type = ReleaseType.get_release_type(version)

    if release_type == ReleaseType.RELEASE_CANDIDATE:
        return (f"This is a release candidate for version {version[:version.index('-')]}. Please see [GitLab]("
                f"https://gitlab.com/nichogx/DiscordRoleSync/-/blob/master/CHANGELOG.md) for an ongoing changelog.")

    if release_type == ReleaseType.RELEASE:
        changelog = ""
        is_in_changelog = False
        with open("../CHANGELOG.md") as f:
            for line in f:
                if line.startswith(f"## [{version}]"):
                    # Found current version
                    is_in_changelog = True
                    continue

                if is_in_changelog:
                    if line.startswith(f"## "):
                        # Found next version
                        return changelog.strip()

                    changelog += line

            return changelog.strip()

    raise Exception("Cannot get changelog for unknown version type")
