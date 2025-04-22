# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [unreleased](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/unreleased) - unreleased

### Changed
- Updated versions of many dependencies
- Better surface configuration errors when the plugin fails loading

## [2.3.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.3.0) - 2024-12-27

### Added
- You can now add the permission `discordrolesync.bypasswhitelist` for a user to be whitelisted even when not linked, if the plugin's whitelist is enabled. Note that for most permissions plugins, to add a permission to the user they must first attempt to join.

## [2.2.4](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.2.4) - 2024-12-12

### Fixed
- Fixed a bug in which a user would get the linked role assigned before they were fully verified.

## [2.2.3](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.2.3) - 2024-12-09

### Fixed
- Fixed the message that's shown to a user when they're denied entry because they're not verified and the whitelist feature is enabled.

## [2.2.2](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.2.2) - 2024-10-25

### Changed
- Updated French translation

### Fixed
- Fixed issue where users would not get a group added if they already had a group that inherited from that group, when using LuckPerms.

## [2.2.1](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.2.1) - 2024-08-18

### Fixed
- Fixed a potential race condition when processing user groups that could cause groups to not be added correctly, or to be incorrectly removed. This also improves performance on large servers.

### Changed
- Improved the error shown to users when a Geyser user is not found in geysermc's cache.

As a reminder, Geyser support is still experimental.

## [2.2.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.2.0) - 2024-07-30

### Added
- You can now configure the bot to send command replies publicly in the channel, instead of privately (that only the sender can see). This is configurable per command.
- You can now specify a list of roles in which its members will not be renamed by this bot.
- You can now specify a template for renaming Discord users, supporting PlaceholderAPI and the player's Minecraft username.
- Added an option to configure how the plugin will choose to operate with online or offline UUIDs. Online and offline modes can now both be forced or manually set in each command, and there's a fallback mode that will use the offline UUID if the online UUID does not exist. This improves compatibility with login plugins in offline mode.

### Changed
- The placeholder `discord_nick` was broken, and has been replaced with `discord_display_name` which is now correct.
- Improved config file validation, with better and clearer error messages.

### Fixed
- Fixed compatibility with Java versions before Java 21 when using the PlaceholderAPI integration added in 2.1.0
- Fixed a bug with the Geyser/Floodgate integration that prevented Xbox usernames with spaces to be linked.

## [2.1.1](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.1.1) - 2024-07-07

### Fixed
- Fixed compatibility with older Paper versions (MC 1.16 and under) by removing a conflicting dependency.

## [2.1.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.1.0) - 2024-07-04

### Added
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) is now supported (optional). Placeholders can be put on the bot's Discord status. Placeholders with linked player count and player link information were also added. See plugin description for a list of placeholders supported. 

### Changed
- Default translations are no longer copied out of the .jar into the plugin folder. This means you will get updated translations when updating the plugin, instead of using whatever version existed when you first installed the plugin.
- The path for custom translations has been changed from `language` to `translations`.
- Updated Spanish translation

### Fixed
- The error message when a user is not found while linking is now shown properly to the user, as opposed to throwing a cryptic error on the console

## [2.0.0](https://gitlab.com/nichogx/DiscordRoleSync/-/releases/2.0.0) - 2024-07-03

This is a large update! It's recommended to update as soon as possible, as it contains multiple security and bug fixes.
It also vastly improves the plugin's stability.

### Added
- A language file validator, which will update potentially old files existing on servers.
- Basic config file validation, versioning, and updating.
- Support for Discord slash commands
- **Experimental:** Added experimental support for Geyser/Bedrock users. Must be enabled in the config by setting up `experimental.geyser`.

### Changed
- Improved alerts for servers running old or unsupported versions. Messages are more descriptive, and differentiate between release and pre-release, development or test builds.
- The player count on the bot's activity is now updated every 3 minutes, instead of every 30 minutes.
- Embed colors now support custom hex colors, and different colors can be specified for info, error or success messages.
- Verification now needs to be done via a Discord `/verify` command, instead of sending the code to the bot's DM.
- Reduced plugin .jar size by removing unnecessary dependencies that are already bundled with Paper/Spigot.

### Removed
- Chat commands (the ones where you configure a prefix and send a message in a channel to get the bot to respond) have been removed. You should now use Discord slash commands.
  - Message reactions, response deletion, and other chat-related features and config options have also been removed, since there are no more chat commands.

### Fixed
- Fixes a potential memory leak when starting the server
- Improves thread-safety on a few features, including fixing a race condition when the bot is started and stopped in quick succession.
- Correctly cleans up async bot tasks when shutting down or restarting the Discord bot.
- Fixes a bstats metric that had been broken since the beta (linked users)
- Fixes a bug where the linked user's name would be "null" if they hadn't joined the server before linking.
- Fixed a bug where some features did not respect the `/drs reload` command

This update changes the major version because it breaks backwards-compatibility by removing chat commands. It is a drop-in replacement for server owners in every other way. 
Configs and language files will be automatically updated. Databases are fully compatible with 1.x.x.

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
