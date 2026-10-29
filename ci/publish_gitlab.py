import os
import sys

import requests

from common import (
    REQUEST_TIMEOUT,
    ReleaseType,
    get_changelog,
)

if __name__ == "__main__":
    CI_COMMIT_TAG = os.getenv("CI_COMMIT_TAG")
    if not CI_COMMIT_TAG:
        raise Exception("CI_COMMIT_TAG not set. Will not upload version.")

    CI_JOB_TOKEN = os.getenv("CI_JOB_TOKEN")
    if not CI_JOB_TOKEN:
        raise Exception("CI_JOB_TOKEN not set. Will not upload version.")

    CI_PROJECT_ID = os.getenv("CI_PROJECT_ID")
    if not CI_PROJECT_ID:
        raise Exception("CI_PROJECT_ID not set. Will not upload version.")

    CI_API_V4_URL = os.getenv("CI_API_V4_URL")
    if not CI_API_V4_URL:
        raise Exception("CI_API_V4_URL not set. Will not upload version.")

    release_type = ReleaseType.get_release_type(CI_COMMIT_TAG)
    if release_type != ReleaseType.RELEASE:
        print("Not publishing version to Gitlab Releases, as it's not a full release.")
        sys.exit(0)

    # Uploaded by the gitlab-registry job
    package_url = f"{CI_API_V4_URL}/projects/{CI_PROJECT_ID}/packages/generic/discord_role_sync/{CI_COMMIT_TAG}"

    # Publish artifact
    print(f"Publishing new version '{CI_COMMIT_TAG}' to GitLab Releases...")
    r = requests.post(
        f"{CI_API_V4_URL}/projects/{CI_PROJECT_ID}/releases",
        timeout=REQUEST_TIMEOUT,
        headers={
            "JOB-TOKEN": CI_JOB_TOKEN,
        },
        json={
            "id": CI_PROJECT_ID,
            "tag_name": CI_COMMIT_TAG,
            "description": get_changelog(CI_COMMIT_TAG),
            "assets": {
                "links": [{
                    "name": f"discord_role_sync-{CI_COMMIT_TAG}.jar",
                    "url": f"{package_url}/discord_role_sync-{CI_COMMIT_TAG}.jar",
                    "link_type": "package",
                }, {
                    "name": "Spigot page description (BBCode)",
                    "url": f"{package_url}/spigot_page.bbcode",
                    "link_type": "other",
                }, {
                    "name": "Spigot changelog (BBCode)",
                    "url": f"{package_url}/spigot_changelog.bbcode",
                    "link_type": "other",
                }]
            }
        },
    )

    if r.status_code == 409:
        # Release already exists, e.g. when retrying the job
        print(f"Release '{CI_COMMIT_TAG}' already exists on GitLab. Skipping.")
        print("DONE!")
        sys.exit(0)

    if not r.ok:
        raise Exception(f"Error uploading to GitLab: HTTP {r.status_code} {r.text}")

    print("Version has been uploaded!")
    print("DONE!")
