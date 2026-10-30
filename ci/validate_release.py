import os
import re
import sys
import zipfile

from common import (
    MAX_JAR_SIZE,
    ReleaseType,
    get_changelog,
    get_jar_path,
    get_modrinth_user_agent,
)
from game_versions import get_hangar_game_versions, get_modrinth_game_versions

# Validates a tagged release before anything is published, so a bad release
# fails before any platform has received it.
if __name__ == "__main__":
    CI_COMMIT_TAG = os.getenv("CI_COMMIT_TAG")
    if not CI_COMMIT_TAG:
        raise Exception("CI_COMMIT_TAG not set. Cannot validate release.")

    errors = []

    release_type = ReleaseType.get_release_type(CI_COMMIT_TAG)
    if release_type == ReleaseType.UNKNOWN:
        errors.append(f"Tag '{CI_COMMIT_TAG}' is not a valid version. Expected x.y.z or x.y.z-rc.N")
    else:
        try:
            get_changelog(CI_COMMIT_TAG)
        except Exception as e:
            errors.append(str(e))

    jar_path = get_jar_path(CI_COMMIT_TAG)
    if not os.path.isfile(jar_path):
        errors.append(f"{jar_path} does not exist")
    else:
        jar_size = os.path.getsize(jar_path)
        if jar_size > MAX_JAR_SIZE:
            errors.append(f"{jar_path} is {jar_size} bytes, larger than the {MAX_JAR_SIZE} bytes Hangar accepts")

        with zipfile.ZipFile(jar_path) as jar:
            plugin_yml = jar.read("plugin.yml").decode("utf-8")

        match = re.search(r'^version:\s*"?([^"\r\n]*)"?\s*$', plugin_yml, re.MULTILINE)
        jar_version = match.group(1) if match else None
        if jar_version != CI_COMMIT_TAG:
            errors.append(f"plugin.yml version is '{jar_version}', expected '{CI_COMMIT_TAG}'")

    # Publishing fails if a platform does not know the supported versions yet, e.g. right after a Minecraft release
    try:
        get_modrinth_game_versions(get_modrinth_user_agent(CI_COMMIT_TAG))
    except Exception as e:
        errors.append(str(e))

    try:
        get_hangar_game_versions()
    except Exception as e:
        errors.append(str(e))

    if errors:
        print(f"Release '{CI_COMMIT_TAG}' failed validation:")
        for error in errors:
            print(f"\t- {error}")

        sys.exit(1)

    print(f"Release '{CI_COMMIT_TAG}' is valid.")
