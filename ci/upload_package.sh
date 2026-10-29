#!/bin/sh
# Uploads files to this tag's generic package in the GitLab package registry.
# Files that are already uploaded are skipped, so the job can be retried.
#
# Usage: upload_package.sh <file>...
set -eu

PACKAGE_URL=${CI_API_V4_URL}/projects/${CI_PROJECT_ID}/packages/generic/discord_role_sync/${CI_COMMIT_TAG}

for FILE in "$@"; do
  NAME=$(basename "$FILE")
  FILE_URL=${PACKAGE_URL}/${NAME}

  if curl --fail --silent --output /dev/null --header "JOB-TOKEN: ${CI_JOB_TOKEN}" "$FILE_URL"; then
    echo "${NAME} already exists. Skipping upload."
    continue
  fi

  curl --fail-with-body \
    --header "JOB-TOKEN: ${CI_JOB_TOKEN}" \
    --upload-file "$FILE" \
    "$FILE_URL"
done
