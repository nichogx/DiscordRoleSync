# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

DiscordRoleSync is a Minecraft plugin (Spigot/Paper) that syncs Discord roles to Minecraft permission groups. It uses JDA (Java Discord API) to communicate with Discord and Vault/PermissionsProvider for Minecraft permissions. The plugin supports both SQLite (default) and MySQL databases for storing user links.

**Key Features:**
- One-way sync from Discord roles to Minecraft permission groups
- Optional whitelist management based on Discord roles
- User verification system with codes
- Discord nickname syncing
- PlaceholderAPI integration
- Multi-language support (8+ languages)

## Build & Test Commands

### Building
```bash
# Build with Maven (requires Java 11+, compiles to Java 8 bytecode)
mvn package

# Run tests only
mvn test

# The output JAR will be in target/discord_role_sync-{version}.jar
```

**Important:** When built locally, version is set to `develop`. CI sets versions automatically based on tags/branches.

### Testing
```bash
# Run Java tests
mvn test

# Run Python infrastructure tests (for CI/publishing scripts)
cd spigot_page && pip install -r requirements.txt && pytest

# Validate language files
cd src/test/language_validator
pip install -r requirements.txt
python validate.py en_US.yml ../../main/resources/language
```

### Running Locally
A test server is included at `test_server/`:
```bash
cd test_server
# Copy your built JAR to test_server/plugins/DiscordRoleSync/
./run.sh  # Runs Paper 1.20.6
```

The test server includes LuckPerms, Vault, and PlaceholderAPI for testing.

## Architecture

### Plugin Lifecycle (RoleSync.java)
1. **onLoad()**: Config migration → Config validation (linting) → Language loading
2. **onEnable()**: Database setup → PermissionsProvider initialization → Bot startup → Event listener registration → Metrics
3. **onDisable()**: Bot shutdown → Cleanup metric cachers

### Core Components

**RoleSync (main class)**
- Manages plugin lifecycle
- Coordinates between bot, database, and permissions
- Handles config/language loading with migration system
- Provides centralized logging including debug logging for dev builds

**SyncBot (bot/SyncBot.java)**
- JDA wrapper and lifecycle manager
- Manages Discord connection and listeners
- Updates bot presence/activity status
- Provides Discord username/nickname lookups
- Runs initial role sync for all linked users on startup

**DiscordAgent (bot/discord/DiscordAgent.java)**
- Executes Discord operations (add/remove roles, change nicknames)
- Handles role syncing logic
- Manages linked role assignment

**DatabaseHandler (db/DatabaseHandler.java + implementations)**
- Abstract class with SQLiteHandler and MySQLHandler implementations
- Stores Discord ID ↔ Minecraft UUID mappings
- Tracks verification status and codes
- All database operations must run asynchronously (not on main thread)

**PermissionsProvider (minecraft/permissions/PermissionsProvider.java)**
- Abstract class with VaultAPI implementation
- Manages adding/removing players to/from permission groups
- Queries managed groups from config

### Config System

**Config Migration (config/migrations/)**
- `ConfigMigrator`: Sequentially applies migrations from old versions to current
- `ConfigMigration`: Defines migrations with renamed keys and transformation functions
- Migrations run on plugin load, old config backed up before replacement

**Config Linting (config/linter/)**
- `ConfigLinter`: Validates config structure before use
- `LintRule`: Defines validation rules
- Plugin will not enable if config validation fails

### User Verification Flow
1. User runs `/link username` in Discord
2. If `requireVerification: true`, user receives verification code
3. User either:
   - Gets kicked by whitelist (sees code in kick message)
   - Types `/drs verify` in-game to see code
4. User runs `/verify <code>` in Discord to complete verification
5. Roles and permissions sync after verification

### Slash Commands (Discord)
- `/link <username>` - Link Minecraft account
- `/admlink <discordId> <username>` - Admin force-link
- `/unlink <discordId|username>` - Unlink account
- `/info <discordId|username>` - Get link info
- `/verify <code>` - Verify link

All admin commands require MANAGE_ROLES permission by default (configurable in Discord).

### Minecraft Commands
- `/drs reload` - Reload config (permission: `discordrolesync.reload`)
- `/drs botrestart` - Restart Discord bot (permission: `discordrolesync.botrestart`)
- `/drs verify` - Get verification code (all players)

### Event Listeners

**Discord (bot/listeners/)**
- `SlashCommandListener`: Handles Discord slash commands
- `MemberEventsListener`: Handles role updates, member join/leave

**Minecraft (listeners/)**
- `PlayerJoinListener`: Sync roles on join, notify admins of updates
- `WhitelistLoginListener`: Manages whitelist enforcement

### UUID Modes (minecraft/UUIDMode.java)
- `DEFAULT`: Auto-detect based on server mode
- `ONLINE`: Always use Mojang API (online mode UUIDs)
- `OFFLINE`: Always use offline mode UUIDs
- `FALLBACK`: Try Mojang first, fall back to offline if not found
- `MANUAL`: Let user choose during linking

**Note:** Swapping UUID modes requires database wipe.

### Database Schema
Table: `{prefix}_discordmcusers`
- `discord_id` (varchar 24, PRIMARY KEY)
- `minecraft_uuid` (varchar 36)
- `whitelisted` (boolean)
- `verification_code` (int)
- `verified` (boolean)
- `username_when_linked` (text, nullable)

### Language System
- Language files in `src/main/resources/language/` (bundled)
- Custom translations in `plugins/DiscordRoleSync/translations/` (user-editable)
- Auto-updates custom translations with missing keys from bundled versions
- Falls back to en_US if language not found

### Integration Points
- **Vault**: Required dependency for permissions (via PermissionsProvider)
- **PlaceholderAPI**: Optional, enables placeholders in bot status and provides DRS placeholders
- **Geyser**: Experimental support for Bedrock players (prefix username with `.`)

## Code Conventions

### Permissions
- The codebase recently transitioned from using Vault directly to an abstract `PermissionsProvider` class
- This allows for future support of LuckPerms API directly or other permission systems
- When working with permissions, always use `plugin.getPermissionsProvider()`, never instantiate VaultAPI directly

### Async Operations
- All database operations MUST run asynchronously via `plugin.getServer().getScheduler().runTaskAsynchronously()`
- Discord bot operations automatically run on JDA's thread pool
- Use `checkAsync()` in DatabaseHandler to enforce this

### Config Access
- Always use `plugin.getConfig().getString("key")` for config values
- Use `plugin.getLanguage().getString("key")` for translations
- Config keys support `.` notation for nested values

### Logging
- Use `plugin.getLogger()` for normal logging
- Use `plugin.debugLog()` for debug messages (only shown in dev builds or when `enableDebugLogging: true`)
- Log levels: `info()` for normal, `warning()` for issues, `severe()` for errors

### Shading/Relocation
Maven shade plugin relocates:
- `org.bstats` → `DiscordRoleSync.org.bstats` (conflicts with other plugins)
- `net.dv8tion` (JDA) → `DiscordRoleSync.net.dv8tion` (conflicts with plugins using old JDA versions)

### Version Support
- **Java**: Targets Java 11 officially, but must maintain Java 8 API compatibility (set in pom.xml)
- **Minecraft**: 1.8.8 to 1.21+ (uses Spigot API 1.12.2 for compatibility)
- Don't use Java APIs newer than Java 8 unless absolutely necessary

## Testing

### Unit Tests
- Located in `src/test/java/`
- Use JUnit 5 and Mockito
- Run with `mvn test`
- Tests cover: UUID handling, Mojang API, config linting, version detection

### Manual Testing
Use the included test server:
1. Build plugin: `mvn package`
2. Copy JAR to `test_server/plugins/DiscordRoleSync/`
3. Configure `test_server/plugins/DiscordRoleSync/config.yml` with your bot token
4. Run: `cd test_server && ./run.sh`
5. Check `test_server/logs/latest.log` for output

## Common Gotchas

### Database Threading
Database calls on the main thread will throw an error from `checkAsync()`. Always wrap in `runTaskAsynchronously()`.

### Offline Mode Servers
- Linking is case-sensitive in offline mode
- UUID mode complications with "login plugins" that change UUIDs post-join
- Use `FALLBACK` or `MANUAL` UUID mode if issues occur

### Config Versioning
- Don't manually edit `configVersion` in config.yml
- Always add migrations to `ConfigMigrator` when changing config structure
- Test migrations from each supported version to latest

### JDA/Discord
- Bot requires GUILD_MEMBERS intent enabled in Discord Developer Portal
- Bot needs MANAGE_ROLES permission in Discord server
- Shaded JDA means import paths in IDE won't match runtime paths

### Language Files
- Only `en_US.yml` and `pt_BR.yml` are actively maintained
- Other languages may lag behind and fall back to English for missing keys 
- Language file updates happen automatically on plugin load
- For every new change, we require that all languages have the new keys (a copy of the english values is okay), to make translation efforts easier
- Non translated keys must be added below the comment block that says translation needed
- The keys should be kept in the same order for all files

## CI/CD

Pipeline stages: lint → test → build → pre-publish → publish

**On every push:**
- SAST security scanning
- Language file validation
- Maven tests (Java 11)
- Python infrastructure tests

**On tag creation:**
- Builds with tag as version
- Publishes to GitLab registry, Modrinth, Hangar
- Spigot requires manual upload (no API)

## Dependencies

**Packaged (shaded into JAR):**
- JDA 5.4.0 (Discord API)
- bStats (metrics)
- commons-io, commons-dbcp, org.json

**Server-provided:**
- Spigot API 1.12.2
- Vault (required)
- SQLite JDBC (bundled with Paper/Spigot)
- Guava (bundled with Paper/Spigot)

**Optional:**
- PlaceholderAPI (for placeholder support)
- LuckPerms, UltraPermissions, PowerRanks (via Vault)
