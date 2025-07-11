import json

import requests
import os

from common import (
    ReleaseType,
    get_changelog,
    get_readme,
)

PROJECT_ID = "g5nO2LNq"

SUPPORTED_GAME_VERSIONS = [
    "1.8", "1.8.1", "1.8.2", "1.8.3", "1.8.4", "1.8.5", "1.8.6", "1.8.7", "1.8.8", "1.8.9",
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
    "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7",
]

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

                    "game_versions": SUPPORTED_GAME_VERSIONS,
                    "version_type": channel,
                    "loaders": ["paper", "spigot", "bukkit", "purpur", "folia"],
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
    }, json={"body": get_readme()})

    if not desc_r.ok:
        raise Exception(f"Error updating Modrinth description: HTTP {desc_r.status_code} {desc_r.text}")

    if release_type == ReleaseType.RELEASE:
        print("Unfeaturing old versions...")

        # list versions (returns a JSON array)
        versions_r = requests.get(f"https://api.modrinth.com/v2/project/{PROJECT_ID}/version?featured=true", headers={
            "Authorization": MODRINTH_API_KEY,
            "User-Agent": user_agent,
        })

        if not versions_r.ok:
            print(f"Error checking versions. Not unfeaturing: HTTP {desc_r.status_code} {desc_r.text}")
        else:
            versions = versions_r.json()

            # for each version, unfeature it if it's not the current one
            for version in versions:
                if version['version_number'] != CI_COMMIT_TAG:
                    print(f"Unfeaturing {version['version_number']} (id: {version['id']})")
                    unf_r = requests.patch(f"https://api.modrinth.com/v2/version/{version['id']}", headers={
                        "Authorization": MODRINTH_API_KEY,
                        "User-Agent": user_agent,

                        "Content-Type": "application/json",
                    }, json={
                        "featured": False,
                    })

                    if not unf_r.ok:
                        print(f"Error unfeaturing {version['version_number']}: HTTP {unf_r.status_code} {unf_r.text}. Skipping.")
                    else:
                        print(f"Unfeatured {version['version_number']}")

    print("DONE!")
