import re
from enum import Enum

# (connect, read) timeout in seconds for all HTTP requests
REQUEST_TIMEOUT = (10, 120)

# Hangar does not accept .jars bigger than 10MB.
# Also checked in the gradle-package job in .gitlab-ci.yml.
MAX_JAR_SIZE = 10_000_000


def get_modrinth_user_agent(version):
    # Modrinth wants a custom User-Agent: https://docs.modrinth.com/#section/User-Agents
    return f"nichogx/DiscordRoleSync/{version} (nicho@nicho.dev)"


def get_jar_path(version):
    return f"../build/libs/discord_role_sync-{version}.jar"


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
    return any(line.startswith(f"[![{badge}") for badge in include_badges)

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
                    if line.startswith("## "):
                        # Found next version
                        break

                    changelog += line

        changelog = changelog.strip()
        if not changelog:
            raise Exception(f"No changelog found for version {version} in CHANGELOG.md")

        return changelog

    raise Exception("Cannot get changelog for unknown version type")
