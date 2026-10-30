-- Each Minecraft account can only be linked to one Discord account.
-- Concurrent links could store a Minecraft account twice. Keep one row per account,
-- preferring a verified one, then the lowest Discord ID, and delete the others.
DELETE dupe FROM `${prefix}_discordmcusers` AS dupe
    JOIN `${prefix}_discordmcusers` AS other
        ON other.minecraft_uuid = dupe.minecraft_uuid
       AND (other.verified > dupe.verified
         OR (other.verified = dupe.verified AND other.discord_id < dupe.discord_id));

CREATE UNIQUE INDEX `minecraft_uuid` ON `${prefix}_discordmcusers` (`minecraft_uuid`);
