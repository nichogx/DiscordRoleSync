-- Each Minecraft account can only be linked to one Discord account.
-- Concurrent links could store a Minecraft account twice. Keep one row per account,
-- preferring a verified one, then the lowest Discord ID, and delete the others.
-- SQLite before 3.23 stored the "false" default as the text 'false', which sorts above 1,
-- so verified is compared as (verified = 1).
DELETE FROM `${prefix}_discordmcusers` WHERE EXISTS (
    SELECT 1 FROM `${prefix}_discordmcusers` AS other
    WHERE other.minecraft_uuid = `${prefix}_discordmcusers`.minecraft_uuid
      AND ((other.verified = 1) > (`${prefix}_discordmcusers`.verified = 1)
        OR ((other.verified = 1) = (`${prefix}_discordmcusers`.verified = 1) AND other.discord_id < `${prefix}_discordmcusers`.discord_id))
);

-- SQLite index names are shared by the whole database, so they need the prefix
CREATE UNIQUE INDEX `${prefix}_discordmcusers_minecraft_uuid` ON `${prefix}_discordmcusers` (`minecraft_uuid`);
