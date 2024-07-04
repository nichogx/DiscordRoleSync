import json

import requests
import os

from common import (
    SUPPORTED_VERSIONS,
    ReleaseType,
    get_changelog,
    get_readme,
)

PROJECT_ID = "g5nO2LNq"

if __name__ == "__main__":
    CI_COMMIT_TAG = os.getenv("CI_COMMIT_TAG")
    if not CI_COMMIT_TAG:
        raise Exception("CI_COMMIT_TAG not set. Will not upload version.")

    MODRINTH_API_KEY = os.getenv("MODRINTH_API_KEY")
    if not MODRINTH_API_KEY:
        raise Exception("MODRINTH_API_KEY not set. Will not upload version.")

    # Modrinth wants a custom User-Agent: https://docs.modrinth.com/#section/User-Agents
    user_agent = f"nichogx/DiscordRoleSync/{CI_COMMIT_TAG} (nicho@nicho.dev)"

    # Publish to the correct channel
    release_type = ReleaseType.get_release_type(CI_COMMIT_TAG)
    if release_type == ReleaseType.RELEASE:
        channel = "release"
    elif release_type == ReleaseType.RELEASE_CANDIDATE:
        channel = "beta"
    else:
        print("Not publishing version to Modrinth, as it's not a release or release candidate.")
        exit(0)

    # Publish artifact
    print(f"Publishing new version '{CI_COMMIT_TAG}' to Modrinth channel '{channel}'...")
    file_path = f"../target/discord_role_sync-{CI_COMMIT_TAG}.jar"
    with open(file_path, "rb") as file:
        r = requests.post(
            "https://api.modrinth.com/v2/version",
            headers={
                "Authorization": MODRINTH_API_KEY,
                "User-Agent": user_agent,
            },
            files={
                f"discord_role_sync-{CI_COMMIT_TAG}.jar": file,
            },
            data={
                "data": json.dumps({
                    "name": CI_COMMIT_TAG,
                    "version_number": CI_COMMIT_TAG,
                    "changelog": get_changelog(CI_COMMIT_TAG),

                    # Modrinth only allows dependencies to other Modrinth plugin, and Vault is not on Modrinth
                    "dependencies": [],

                    "game_versions": SUPPORTED_VERSIONS,
                    "version_type": channel,
                    "loaders": ["paper", "spigot", "bukkit", "purpur"],
                    "featured": release_type == ReleaseType.RELEASE,

                    "project_id": PROJECT_ID,
                    "file_parts": [f"discord_role_sync-{CI_COMMIT_TAG}.jar"],
                })
            },
        )

    if not r.ok:
        raise Exception(f"Error uploading to Modrinth: HTTP {r.status_code} {r.text}")

    print("Version has been uploaded!")

    # Update description from readme (only for releases)
    if release_type != ReleaseType.RELEASE:
        print("Not updating description since this is not a full release.")
        exit(0)

    print("Updating description...")
    desc_r = requests.patch(f"https://api.modrinth.com/v2/project/{PROJECT_ID}", headers={
        "Authorization": MODRINTH_API_KEY,
        "User-Agent": user_agent,

        "Content-Type": "application/json",
    }, json={"body": get_readme("Modrinth")})

    if not desc_r.ok:
        raise Exception(f"Error updating Modrinth description: HTTP {desc_r.status_code} {desc_r.text}")

    print("DONE!")
