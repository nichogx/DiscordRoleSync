import argparse

from common import get_changelog

# Writes a version's changelog from CHANGELOG.md to a Markdown file, so it can
# be converted to BBCode for Spigot.
if __name__ == "__main__":
    parser = argparse.ArgumentParser("export_changelog.py")

    parser.add_argument("version", type=str, help="The version to export the changelog of")
    parser.add_argument("--out", type=str, required=True, help="The Markdown file to write the changelog to")

    args = parser.parse_args()

    changelog = get_changelog(args.version)
    with open(args.out, "w") as out:
        out.write(changelog + "\n")
