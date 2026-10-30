# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DiscordRoleSync is a Minecraft plugin (Spigot/Paper) that does one-way sync of Discord roles to Minecraft permission groups, with optional whitelist management. It uses JDA for Discord, Vault for permissions, and SQLite (default) or MySQL for storing account links.

## Build & Test Commands

```bash
mvn package   # Build; output in target/discord_role_sync-{version}.jar
mvn test      # Java tests
mvn compile spotbugs:check   # Static analysis; ignored findings are in spotbugs-exclude.xml

# Python tests
cd ci && pip install -r requirements-dev.txt && pytest   # publishing scripts

# Lint Python scripts (config in ruff.toml)
ruff check .

# Validate language files
cd src/test/language_validator
pip install -r requirements.txt
python validate.py en_US.yml ../../main/resources/language
```

Local builds use version `develop`. CI sets versions from tags/branches.

### Test Server
`test_server/` contains a Paper server with LuckPerms, Vault and PlaceholderAPI. Copy the built JAR to `test_server/plugins/`, set the bot token in `test_server/plugins/DiscordRoleSync/config.yml`, then run `cd test_server && ./run.sh`. Logs are in `test_server/logs/latest.log`.

## User Verification Flow
1. User runs `/link <username>` in Discord.
2. If `requireVerification: true`, the user gets a verification code, shown in the whitelist kick message or via `/drs verify` in-game.
3. User runs `/verify <code>` in Discord, and roles and permissions are synced.

## Code Conventions

- **Permissions:** Go through `plugin.getVault()` for permission and group operations. Never instantiate `VaultAPI` directly.
- **Async:** All database operations must run off the main thread via `plugin.getServer().getScheduler().runTaskAsynchronously()`. `checkAsync()` in `DatabaseHandler` warns in dev environments if you don't.
- **Debug logging:** Use `plugin.debugLog()` for debug output. It is only shown in dev builds or when `enableDebugLogging: true`.
- **Shaded JDA:** JDA is relocated to `DiscordRoleSync.net.dv8tion`, so IDE import paths won't match runtime paths.

### Version Support
- **Java:** Must stay Java 17 API compatible (enforced by `maven.compiler.release=17`).
- **Minecraft:** Supports 1.8.8 to 1.21+, compiled against Spigot API 1.12.2. Be careful with APIs that don't exist on 1.8.8.

## Config Changes
- Never manually edit `configVersion` in config.yml.
- When changing config structure, add a migration to `ConfigMigrator` (`config/migrations/`) and test migrating from each supported version to latest.
- Config is validated by `ConfigLinter` on load. The plugin won't enable if validation fails.
- Swapping UUID modes requires a database wipe.

## Language Files
- Only `en_US.yml` and `pt_BR.yml` are actively maintained. Other languages fall back to English for missing keys.
- For every new change, all languages must have the new keys (a copy of the English value is okay), to make translation easier.
- Non-translated keys must be added below the comment block that says translation needed.
- Keys must be kept in the same order in all files.

## Changelog

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Pending changes go under the `unreleased` section at the top, grouped as `### Added`, `### Changed`, `### Fixed`, etc.

- Write for the end user: a server admin downloading the plugin. Describe what changes for them (features, fixes, config, compatibility, supported Minecraft versions), not how it was implemented.
- Only call out dependency updates individually when they matter to admins (e.g. JDA, which affects Discord behavior). Otherwise "Updated dependencies" is enough.
- Group changes that don't affect admins (CI, tests, build tooling, test-only or server-provided dependency bumps, Python scripts) under a broad entry such as "Housekeeping: CI and build improvements".
- Keep entries to one line where possible.

## Publishing
Tags publish automatically to the GitLab registry, Modrinth and Hangar. Spigot has no API and requires manual upload.
