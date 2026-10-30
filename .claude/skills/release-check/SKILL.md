---
name: release-check
description: Pre-release risk review for DiscordRoleSync. Checks dependency and Minecraft version updates since the last release for breaking changes, finds the riskiest code changes, runs focused bug reviews on them, and writes a step-by-step manual test plan. Use when preparing a new release.
argument-hint: "[base tag]"
disable-model-invocation: true
---

# Release check

Produce a risk report and manual test plan for everything that changed since the last release. This skill is **read-only**: do not edit files, update the CHANGELOG, build, tag or push. The maintainer does the release themselves.

Subagents do the heavy reading so the main session stays small. Launch independent subagents **in a single message** so they run in parallel. Subagents start with no context, so every prompt must include the base tag, the repo path and what to return.

## Step 1: Pick the baseline

If a base tag was passed as an argument (`$ARGUMENTS`), use it. Otherwise:

1. Run `git tag --sort=-v:refname | head -15` and `git describe --tags --abbrev=0`.
2. Ask the user with `AskUserQuestion` which tag to compare against. Offer the most recent final release (no `-rc`) and any newer RC tags, with the commit count for each (`git rev-list --count <tag>..HEAD`). Recommend the last final release.

Then collect a small overview for yourself (do not read the full diff here):

```bash
git log --oneline <BASE>..HEAD
git diff --stat <BASE>..HEAD | tail -1
```

Note the current branch and whether the working tree is clean. If HEAD isn't `master` or there are uncommitted changes, mention it at the top of the report.

## Step 2: Parallel analysis (one message, three subagents)

Use `subagent_type: "general-purpose"` and `model: "sonnet"` for all three.

### 2a. Dependency updates

Prompt template:

> You are checking library updates in the DiscordRoleSync Minecraft plugin (repo at `<REPO>`) for anything that could break it. Read `CLAUDE.md` first. Compare `<BASE>` to `HEAD`. Do not edit any files.
>
> 1. Run `git diff <BASE>..HEAD -- pom.xml` and list every dependency and plugin whose version changed, was added or was removed. Also check `.gitlab-ci.yml` for build image changes (JDK/Maven versions).
> 2. Classify each by scope: **shaded** (compile scope, ends up in the plugin JAR, e.g. JDA, sqlite-jdbc, commons-dbcp, bStats, org.json), **server-provided** (`provided`, e.g. spigot-api, VaultAPI), **build plugin** (shade, compiler, replacer, etc.) or **test-only**. Skip deep research on test-only dependencies and ci/ Python requirements; just list them.
> 3. For each shaded, server-provided and build-plugin change, read the upstream release notes or changelog for **every** version between the old and new one (use WebSearch/WebFetch: GitHub releases, project changelogs). Look for: removed or renamed APIs, behavior changes, new minimum Java version above 17, changed defaults, new transitive dependencies, Discord API/gateway changes (JDA), native library or file format changes (sqlite-jdbc), and anything that affects relocation by maven-shade-plugin.
> 4. For every potentially breaking item, grep the source (`src/main/java`) to see whether the plugin actually uses the affected API or behavior. Remember JDA is relocated to `DiscordRoleSync.net.dv8tion` at runtime.
> 5. Check that `maven-shade-plugin` relocations in `pom.xml` still cover the shaded packages (a new transitive dependency that isn't relocated could clash with other plugins).
>
> Return a table: dependency, old → new version, scope, risk (high/medium/low/none), reason with a link to the upstream note, and the affected files in this repo (or "not used"). Then list concrete things a human should test because of these updates. Keep it under 600 words. Only report risks you can tie to a specific upstream change; do not speculate.

### 2b. Minecraft version updates

Prompt template:

> You are checking whether new Minecraft/server versions could break the DiscordRoleSync plugin (repo at `<REPO>`). Read `CLAUDE.md` first. Compare `<BASE>` to `HEAD`. Do not edit any files.
>
> 1. Find the supported range at both refs: the "tested on Minecraft versions" line in `README.md` and `MAX_GAME_VERSION` in `ci/game_versions.py` (this file may not exist at `<BASE>`; use README then). List every Minecraft release added to the supported range since `<BASE>`.
> 2. List the server APIs the plugin relies on: grep `src/main/java` for `org.bukkit`, `Bukkit.`, events (`@EventHandler`), whitelist calls, `OfflinePlayer`, scheduler use, Vault, PlaceholderAPI, and `plugin.yml` (`api-version`, commands, `depend`/`softdepend`).
> 3. For each new Minecraft version, research (WebSearch/WebFetch) Spigot and Paper release notes and API changes: removed or deprecated-for-removal APIs, event behavior changes (login/pre-login/whitelist events, especially Paper's login event changes), changes to whitelist handling, player profile/UUID resolution, offline player lookups, scheduler or threading rules, plugin loading changes, and minimum Java version. Also note the new Minecraft version numbering (e.g. 26.x) and whether anything in the plugin parses or compares server versions.
> 4. Check backward compatibility of new code: run `git diff <BASE>..HEAD -- src/main/java` and flag any newly used Bukkit/Spigot API, method or enum constant that does not exist on Minecraft 1.8.8, and any Java API newer than Java 17.
>
> Return: the version range change, a list of risks (version, what changed upstream with a link, which plugin code it affects, high/medium/low), the backward-compat findings with file:line, and which Minecraft versions a human should test on and why. Keep it under 600 words. Only report risks tied to a specific upstream change or a specific line of code.

### 2c. Change triage

Prompt template:

> You are triaging all code changes in the DiscordRoleSync Minecraft plugin (repo at `<REPO>`) since `<BASE>` to find the riskiest ones before a release. Read `CLAUDE.md` first. Do not edit any files.
>
> 1. Run `git log --reverse --format='%h %s' <BASE>..HEAD` and `git diff --stat <BASE>..HEAD`. Read the diffs (`git show <sha>` or `git diff <BASE>..HEAD -- <path>`) of everything under `src/main/` and `pom.xml`. Ignore pure test, CI, docs and language-file changes except to note them.
> 2. Group commits into logical changes (one feature or fix may span several commits).
> 3. Rate each change's risk as high, medium or low. Weigh: data loss or corruption (database schema/migrations, account links), config migrations and the config linter (a bad migration or lint rule stops the plugin enabling), whitelist and login handling (could lock players out or let them in), permission/group sync (could grant or strip permissions), threading (DB on main thread, Bukkit API off main thread), plugin enable/disable/reload lifecycle, Discord event handling, external HTTP calls, Java/Minecraft version compatibility, and how much existing behavior the change touches. Small, isolated or test-only changes are low.
> 4. Compare the changes to the `unreleased` section of `CHANGELOG.md` and note any user-visible change that has no entry.
>
> Return a ranked list, highest risk first. For each change: a short title, risk level, commits (short SHAs), files touched under `src/main/`, one or two sentences on what could go wrong, and the user-facing feature it affects. Then the list of changes missing from the CHANGELOG. Keep it under 800 words.

## Step 3: Focused bug reviews

When the triage agent returns, pick the changes rated **high**, plus **medium** ones that touch data, login/whitelist, permissions or lifecycle. Merge small related changes so each reviewer has a coherent scope; aim for 3 to 8 reviewers. If the dependency or Minecraft agents found a code-level risk (e.g. a newly used API that may be missing on 1.8.8), add it to the matching reviewer's scope.

Launch the reviewers in one message with `subagent_type: "general-purpose"` and **no model override** (they use the main session's model).

Prompt template:

> You are reviewing one change in the DiscordRoleSync Minecraft plugin (repo at `<REPO>`) for bugs before a release. Read `CLAUDE.md` first for the project conventions. Do not edit any files.
>
> **Change:** <title and what it does>
> **Commits:** <SHAs> (view with `git show <sha>`; full range is `<BASE>..HEAD`)
> **Files:** <files>
> **Why it's risky:** <triage notes>
>
> Read the diff, then read the surrounding code it interacts with (callers, callees, related classes) as it is at HEAD. Look only for real bugs: wrong behavior, crashes, data loss, race conditions or threading violations (database work on the main thread, Bukkit API from async threads), resource leaks, broken config/database migrations from any older version, upgrade/downgrade problems for existing servers, incompatibility with Minecraft 1.8.8 or Java 17, wrong behavior with MySQL vs SQLite, and edge cases (null/missing members, unlinked users, plugin reload, Discord or Mojang API failures).
>
> Do **not** report style, naming, formatting, missing tests, refactoring suggestions or other nits.
>
> Before reporting a finding, confirm it by reading the code paths involved. For each finding give: severity (critical/high/medium/low), file:line, a one-sentence description, and a concrete failure scenario (inputs or server state → what goes wrong for the admin or players). Mark findings you could not fully confirm as "unconfirmed". If you find no bugs, say so; an empty result is a valid answer. Keep it under 500 words.

## Step 4: Verify and write the report

For each critical or high finding, read the cited code yourself to confirm it before it goes in the report. Drop findings that turn out wrong, and say how many were dropped.

Then write the report in chat with these sections:

1. **Summary**: base tag, commit count, the overall verdict (ready / ready after manual testing / fix first), and the one to three things that matter most.
2. **Bugs found**: confirmed findings, most severe first, with file:line and the failure scenario. Then unconfirmed findings, clearly labelled.
3. **Dependency risks**: only items rated medium or higher, plus a one-line note that the rest were checked.
4. **Minecraft version risks**: the range change, medium-or-higher risks and backward-compat findings.
5. **CHANGELOG gaps**: user-visible changes with no entry (if any).
6. **Manual test plan**: see below.

### Manual test plan

Cover only the risky changes and risky updates, highest risk first. Skip anything the automated tests already verify end to end. For each item:

- **What and why**: the change and what could go wrong.
- **Setup**: the server version to use, config.yml settings (e.g. `requireVerification`, whitelist on/off, `database.type: mysql`), and starting state (e.g. "a database created by `<BASE>`", "an unlinked Discord account with a synced role").
- **Steps**: numbered actions, each saying where it happens: Discord (slash commands, role changes, leaving/kicking/banning a member), the in-game client, or the server console (`/drs reload`, `/reload`, restart).
- **Expected result**: what the admin should see in Discord, in-game, in the player's groups (`/lp user <name> info`) and in `test_server/logs/latest.log`.
- **Failure signs**: what a broken result looks like.

Start the plan with a short common setup section:

1. `mvn package`, then copy `target/discord_role_sync-develop.jar` to `test_server/plugins/`.
2. Set the bot token in `test_server/plugins/DiscordRoleSync/config.yml`.
3. `cd test_server && ./run.sh`, logs in `test_server/logs/latest.log`.

`test_server/` is the maintainer's local, gitignored setup, so don't assume which server version it runs. Recommend testing on the latest supported Minecraft version by default, and leave the choice to the maintainer. When a risk is specific to an older version (e.g. 1.8.8 for backward compatibility), say so and suggest testing on that version too. When a change affects upgrades (database migrations, config migrations), include steps that start from a server running `<BASE>`: install the `<BASE>` JAR, link an account, then swap in the new JAR and check the data and config survived.

End the report with the list of subagents run and anything that could not be checked (e.g. release notes that could not be found).
