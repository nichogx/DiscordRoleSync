import json

import requests
import os

from common import (
    MAX_JAR_SIZE,
    REQUEST_TIMEOUT,
    ReleaseType,
    get_changelog,
    get_jar_path,
    get_readme,
)

SUPPORTED_GAME_VERSIONS = [
    "1.8", "1.8.8", # Hangar does not list all 1.8 versions
    "1.9.x", "1.10.x", "1.11.x", "1.12.x", "1.13.x", "1.14.x", "1.15.x", "1.16.x", "1.17.x",
    "1.18.x", "1.19.x", "1.20.x", "1.21.x", "26.1.x",
]

if __name__ == "__main__":
    CI_COMMIT_TAG = os.getenv("CI_COMMIT_TAG")
    if not CI_COMMIT_TAG:
        raise Exception("CI_COMMIT_TAG not set. Will not upload version.")

    HANGAR_API_KEY = os.getenv("HANGAR_API_KEY")
    if not HANGAR_API_KEY:
        raise Exception("HANGAR_API_KEY not set. Will not upload version.")

    # Publish to the correct channel
    release_type = ReleaseType.get_release_type(CI_COMMIT_TAG)
    if release_type == ReleaseType.RELEASE:
        channel = "Release"
    elif release_type == ReleaseType.RELEASE_CANDIDATE:
        channel = "Beta"
    else:
        print("Not publishing version to Hangar, as it's not a release or release candidate.")
        exit(0)

    # Ensure artifact size
    file_path = get_jar_path(CI_COMMIT_TAG)
    if os.path.getsize(file_path) > MAX_JAR_SIZE:
        raise Exception(f"Hangar does not accept .jars bigger than 10MB")

    # Authenticate with Hangar
    print("Authenticating with Hangar...")
    auth_r = requests.post(f"https://hangar.papermc.io/api/v1/authenticate?apiKey={HANGAR_API_KEY}", timeout=REQUEST_TIMEOUT)
    if not auth_r.ok:
        raise Exception(f"Failed to authenticate with Hangar: HTTP {auth_r.status_code} {auth_r.text}")

    jwt = auth_r.json()["token"]
    print("Authenticated!")

    # Check if the version was already published, e.g. when retrying the job
    existing_r = requests.get(f"https://hangar.papermc.io/api/v1/projects/DiscordRoleSync/versions/{CI_COMMIT_TAG}", timeout=REQUEST_TIMEOUT, headers={
        "Accept": "application/json",
        "Authorization": f"Bearer {jwt}",
    })

    if existing_r.ok:
        print(f"Version '{CI_COMMIT_TAG}' already exists on Hangar. Skipping upload.")
    elif existing_r.status_code != 404:
        raise Exception(f"Error checking for existing Hangar version: HTTP {existing_r.status_code} {existing_r.text}")
    else:
        # Publish artifact
        print(f"Publishing new version '{CI_COMMIT_TAG}' to Hangar channel '{channel}'...")
        with open(file_path, "rb") as file:
            r = requests.post(
                "https://hangar.papermc.io/api/v1/projects/nichogx/DiscordRoleSync/upload",
                timeout=REQUEST_TIMEOUT,
                headers={
                    "Accept": "application/json",
                    "Authorization": f"Bearer {jwt}",
                },
                files={
                    "files": (f"discord_role_sync-{CI_COMMIT_TAG}.jar", file, "application/octet-stream"),
                    "versionUpload": (None, json.dumps({
                        "version": CI_COMMIT_TAG,
                        "pluginDependencies": {
                            "PAPER": [{
                                "name": "Vault",
                                "required": True,
                                "externalUrl": "https://www.spigotmc.org/resources/vault.34315/",
                                "platform": "PAPER",
                            }],
                        },
                        "platformDependencies": {
                            "PAPER": SUPPORTED_GAME_VERSIONS,
                        },
                        "description": get_changelog(CI_COMMIT_TAG),
                        "files": [{
                            "platforms": ["PAPER"],
                        }],
                        "channel": channel,
                    }), "application/json"),
                },
            )

        if not r.ok:
            raise Exception(f"Error uploading to Hangar: HTTP {r.status_code} {r.text}")

        print("Version has been uploaded!")

    # Update description from readme (only for releases)
    if release_type != ReleaseType.RELEASE:
        print("Not updating description since this is not a full release.")
        exit(0)

    # Update description from readme
    print("Updating description...")
    desc_r = requests.patch("https://hangar.papermc.io/api/v1/pages/editmain/DiscordRoleSync", timeout=REQUEST_TIMEOUT, headers={
        "Authorization": f"Bearer {jwt}",

        "Content-Type": "application/json",
    }, json={"content": get_readme()})

    if not desc_r.ok:
        raise Exception(f"Error updating Hangar description: HTTP {desc_r.status_code} {desc_r.text}")

    print("DONE!")
