# Contributing

This is a side project in maintenance mode, and I do not have too much time to add new features.

Contributions are very welcome, as long as the changes are relevant for the plugin and high quality. Please ask me (open an issue or ask on Discord) first, if you're not sure if the feature you want to implement will be accepted.

Want to take a stab at something, but not sure what to work on? Feel free to take an [open issue](https://gitlab.com/nichogx/DiscordRoleSync/-/issues)! I do my best to keep them shovel-ready.

## Refactors

I've recently picked up support for this plugin again, and am in the process of cleaning up the code. I'm not a Java expert, so feel free to make clean-up, opinionated contributions if you'd like.

There is a lot of room for improvement. Refactors are very, very welcome, especially if it makes the code more testable (with unit tests).

## Getting a Review

Please make sure you practice good code hygiene. Make a descriptive merge request and please describe what you have done to test your changes.

## Structure

This project is built with Maven, targeting Java 11. While we officially support Java 11 only (as that's what we run in CI), please do not use any APIs that are not available in Java 8 if possible. This plugin is fairly simple and I want to keep support for as many users as possible. This means everything Java 8+ and MC 1.8.8+.

There are a few Python scripts, mainly for CI, but these are not packaged as part of the final release.

### Versioning

Versions are automatically defined by CI.

- When tagged, the tag will be the version. This is how production releases are built and versions defined.
- When built from master, the version will be `master-<sha>`. This is not meant for distribution.
- When built from a branch, the version will be `branch-<sha>`. This is not meant for distribution.
- When built locally, the version will be `develop`. This is not meant for distribution.

## Publishing

Publishing will be done by the maintainer, once changes warrant a new release. 

Release candidates are uploaded to Modrinth and Hangar. Only full releases are uploaded to Spigot.

Once a version is tagged, the release is built automatically and must be uploaded to the places this plugin 
is distributed at. Some of this is done by CI. Before tagging, CHANGELOG.md must be up to date and the new
version number that will be tagged must be added.

### [GitLab](https://gitlab.com/nichogx/DiscordRoleSync/-/releases)

- Release candidates and releases are uploaded as releases
- Publishing is automatic through GitLab CI when a tag is created
  - Release description is pulled from CHANGELOG.md

### [Modrinth](https://modrinth.com/plugin/discordrolesync) 
[![target-Modrinth](https://img.shields.io/modrinth/dt/g5nO2LNq?logo=modrinth&label=Modrinth)](https://modrinth.com/plugin/discordrolesync)

- Release candidates are uploaded to the "Beta" channel
- Releases are uploaded to the "Release" channel
- Publishing and description updates are automatic through GitLab CI when a tag is created
  - Description is made from the README.md
  - Changelog is pulled from CHANGELOG.md

### [Hangar](https://hangar.papermc.io/NichoGX/DiscordRoleSync)
[![target-Hangar](https://img.shields.io/hangar/dt/discordrolesync?logo=data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9Im5vIj8+CjxzdmcKICAgd2lkdGg9IjIxLjM5MTk5OG1tIgogICBoZWlnaHQ9IjI3LjYyMDc0MW1tIgogICB2aWV3Qm94PSIwIDAgMjEuMzkxOTk4IDI3LjYyMDc0MSIKICAgdmVyc2lvbj0iMS4xIgogICBpZD0ic3ZnNzQxIgogICB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciCiAgIHhtbG5zOnN2Zz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPgogIDxkZWZzCiAgICAgaWQ9ImRlZnM3MzgiIC8+CiAgPGcKICAgICBpZD0ibGF5ZXIxIgogICAgIHRyYW5zZm9ybT0idHJhbnNsYXRlKC03Ni4xNzIyMDksLTEyNy42NTA5KSI+CiAgICA8cGF0aAogICAgICAgZD0ibSA5NS44ODgzNjEsMTM5LjUwMDE4IGMgLTIuMDk0NDUsLTAuNzY5NDEgLTUuMTc5NTA0LDIuMjA5NDUgLTYuNjg5NzUyLDYuNzczNjggLTAuNzQ5NjU2LDIuMjc0MDEgLTEuMTExNjA4LDQuOTQxMDEgLTAuNzYzNDE0LDcuNzM0MyBsIC0wLjk2ODczMiwtMC40MDQyOCBjIC0xLjg0NzE1MywtNS43NjA4NiAtMC44MTEzOTQsLTEyLjA2MDA2IDIuMzg5MDIxLC0xNi42MDgwNyB6IgogICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC4zNTI3NzgiCiAgICAgICBpZD0icGF0aDE2Ny02LTEiIC8+CiAgICA8cGF0aAogICAgICAgZD0ibSA5Ny4yMzU3MzEsMTQ5LjM0Nzk3IC03LjE2NzA2MywtMi45NzAwNCBjIDEuMzI1MDM5LC0zLjk4OTIxIDMuNzUzNTcxLC02LjM2OTc1IDUuMzM5NjY2LC02LjM2OTc1IDAuMTY4Mjc2LDAgMC4zMjU2MTUsMC4wMjc5IDAuNDcxNjY2LDAuMDc4NyBsIDAuMTMxOTM5LDAuMDU2MSBjIDAuNTY0MDk0LDAuMjcyMzQgMi4yNTk5MDQsMS43Mzc3OCAxLjIyMzc5Miw5LjIwNTAzIgogICAgICAgc3R5bGU9ImZpbGw6I2ZmZmZmZjtmaWxsLW9wYWNpdHk6MTtmaWxsLXJ1bGU6bm9uemVybztzdHJva2U6bm9uZTtzdHJva2Utd2lkdGg6MC4zNTI3NzgiCiAgICAgICBpZD0icGF0aDE3MS05LTgiIC8+CiAgICA8cGF0aAogICAgICAgZD0ibSA5NC42NjE1MzYsMTMxLjYyODQ3IGMgLTIuMjQ1NzkzLDEuMjEwMDMgLTQuMTIxMTY3LDIuOTYxOTIgLTUuNTU4Mzg5LDUuMDU2MDEgLTMuMTQ3MTQ0LDQuNTY3NDIgLTQuMjI0ODg1LDEwLjc2MzI1IC0yLjU4NTUyMSwxNi41MjY1OCAwLjE5NjUwMSwwLjY5MzIxIDAuNDMyMTU2LDEuMzgxMTMgMC43MTAxNDcsMi4wNjA1OCBsIC05LjU4Njc3NiwtMy45NzgyOCBDIDc0LjAxMTI1MywxNDIuNDA1ODMgNzcuMzQwNDMsMTMxLjgyMjUgODUuMDc3NTg1LDEyNy42NTA5IFoiCiAgICAgICBzdHlsZT0iZmlsbDojZmZmZmZmO2ZpbGwtb3BhY2l0eToxO2ZpbGwtcnVsZTpub256ZXJvO3N0cm9rZTpub25lO3N0cm9rZS13aWR0aDowLjM1Mjc3OCIKICAgICAgIGlkPSJwYXRoMTc1LTItNyIgLz4KICA8L2c+Cjwvc3ZnPgo=&label=Hangar)](https://hangar.papermc.io/NichoGX/DiscordRoleSync)

- Release candidates are uploaded to the "Beta" channel
- Releases are uploaded to the "Release" channel
- Publishing and description updates are automatic through GitLab CI when a tag is created
   - Description is made from the README.md
   - Changelog is pulled from CHANGELOG.md

### [Spigot](https://www.spigotmc.org/resources/discord-role-sync.78829)
[![target-SpigotMC](https://img.shields.io/spiget/downloads/78829?logo=spigotmc&label=SpigotMC)](https://www.spigotmc.org/resources/discord-role-sync.78829)

As far as I can tell, Spigot does not have an API for automatic uploads.

- Only releases are uploaded
  - There's no beta channel in Spigot
- Publishing and description updates are **manual**
  - Publish a new version with the .jar direct download link from the GitLab release
  - The version's changelog needs to be **manually** translated from CHANGELOG.md to Spigot's format
    - The translation script can also be run locally
  - The description is automatically generated to bbcode in an artifact, but must be **manually** updated. Badges must be **manually** removed.
