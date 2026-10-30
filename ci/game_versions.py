import requests

from common import REQUEST_TIMEOUT

# Oldest and newest Minecraft versions the plugin supports. The publish scripts
# send every release version in between that each platform knows about.
MIN_GAME_VERSION = "1.8"
# Updated by Renovate when Mojang releases a new version, together with README.md. Test the plugin on it before merging.
# renovate: datasource=custom.minecraft depName=minecraft
MAX_GAME_VERSION = "26.1.2"


def parse_version(version):
    return tuple(int(part) for part in version.split("."))


def filter_supported(versions, platform):
    """Returns the versions between MIN_GAME_VERSION and MAX_GAME_VERSION, oldest first.

    Raises if the platform does not know about MIN_GAME_VERSION or MAX_GAME_VERSION,
    e.g. when it has not added a new Minecraft version yet.
    """
    versions = set(versions)
    for bound in (MIN_GAME_VERSION, MAX_GAME_VERSION):
        if bound not in versions:
            raise Exception(f"Minecraft version {bound} is not available on {platform} yet")

    min_version = parse_version(MIN_GAME_VERSION)
    max_version = parse_version(MAX_GAME_VERSION)
    supported = [v for v in versions if min_version <= parse_version(v) <= max_version]

    return sorted(supported, key=parse_version)


def get_modrinth_game_versions(user_agent):
    r = requests.get("https://api.modrinth.com/v2/tag/game_version", timeout=REQUEST_TIMEOUT, headers={
        "User-Agent": user_agent,
    })
    if not r.ok:
        raise Exception(f"Error getting Modrinth game versions: HTTP {r.status_code} {r.text}")

    releases = [v["version"] for v in r.json() if v["version_type"] == "release"]
    return filter_supported(releases, "Modrinth")


def get_hangar_game_versions():
    r = requests.get("https://hangar.papermc.io/api/v1/platforms/PAPER/versions", timeout=REQUEST_TIMEOUT, headers={
        "Accept": "application/json",
    })
    if not r.ok:
        raise Exception(f"Error getting Hangar game versions: HTTP {r.status_code} {r.text}")

    # Hangar groups versions by minor version, e.g. 1.21 has 1.21, 1.21.1, ... as sub-versions.
    # Versions without patches, e.g. 26.2, have no sub-versions.
    versions = set()
    for group in r.json():
        versions.add(group["version"])
        versions.update(group["subVersions"])

    return filter_supported(versions, "Hangar")
