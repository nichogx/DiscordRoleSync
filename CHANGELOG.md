# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.0.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/1.4.0) - unreleased

This is a large update! It's recommended to update as soon as possible, as it contains multiple security and bug fixes.
It also vastly improves the plugin's stability.

### Added
- A language file validator, which will update potentially old files existing on servers.
- Basic config file validation, versioning, and updating.
- Support for Discord slash commands

### Changed
- Improved alerts for servers running old or unsupported versions. Messages are more descriptive, and differentiate between release and pre-release, development or test builds.
- The player count on the bot's activity is now updated every 3 minutes, instead of every 30 minutes.

### Removed
- Chat commands (the ones where you configure a prefix and send a message in a channel to get the bot to respond) have been removed. You should now use Discord slash commands.

### Fixed
- Fixes a potential memory leak when starting the server
- Improves thread-safety on a few features, including fixing a race condition when the bot is started and stopped in quick succession.
- Correctly cleans up async bot tasks when shutting down or restarting the Discord bot.
- Fixes a bstats metric that had been broken since the beta (linked users)
- Fixes a bug where the linked user's name would be "null" if they hadn't joined the server before linking.

## [1.3.1](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/1.3.1) - 2024-06-21

### Fixed
- **Important**: fixes a bug with getting Minecraft usernames that broke the -info command and the automatic Discord nickname rename feature. This was due to an API being removed by Mojang.

### Changed
- Username-related information now works for offline servers!
- Updated Japanese translation
- Small performance improvements and reduced .jar size
- Better messages for some server-side errors

## [1.3.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/1.3.0) - 2023-11-09

### Security
- Updated dependencies to fix security vulnerabilities
- Removed the runtime dependency downloader, so you no longer need to restart the server when first installing this plugin

### Fixed
- Fixed a bug with new Discord ID sizes (thanks @saul3740.2 on Discord for reporting!)

## [1.2.2](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/1.2.2) - 2023-11-08

This is a no-op release due to the migration to GitLab for builds and tracking.

## [1.2.1](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.2.1) - 2022-12-05

### Adds

- A config option to use embeds instead of regular messages (disabled by default)
- Config options to change the Discord commands
- A config option to make the bot respond with messages instead of just reacting
- A config option to change the prefix of messages the bot sends on Minecraft

## [1.0.1](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.0.1) - 2021-01-27

### Fixed

- Crash on startup with some SQLite versions

### Removed

- Support for all `1.0.0-BETA.x` versions. 

## [1.0.0](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.0.0) - 2021-01-14

This plugin is now considered released (not in beta anymore), so updates should be less frequent now, and the number of new features added will be limited.

### Added

- German translation

### Fixed

- Bug with the config encoding that happened in a few select systems.

## [1.0.0-BETA.18](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.0.0-BETA.18) - 2020-09-01

### Added

- `-admlink` command so staff can force link users.

## [1.0.0-BETA.17](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.0.0-BETA.17) - 2020-08-26

### Fixed

- An exception that was thrown when users ran the link, unlink or info commands with no arguments.

## [1.0.0-BETA.16](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.0.0-BETA.16) - 2020-08-26

### Fixed

- Conflict between whitelist and verification features. If the user already had the whitelist role before linking and verifying, they wouldn't be added to the whitelist until a server restart, or until the role was removed then added again.

## [1.0.0-BETA.15](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.0.0-BETA.15) - 2020-08-19

### Added

- Option to disable SSL for MySQL connections

### Fixed

- Conflict between the message deletion and the verification features

## [1.0.0-BETA.14](https://github.com/nichogx/DiscordRoleSync/releases/tag/1.0.0-BETA.14) - 2020-08-19

### Added

- BungeeCord support
- Turkish translation

### Fixed

- Bug in MySQL connection
