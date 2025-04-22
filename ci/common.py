import re
from enum import Enum

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
