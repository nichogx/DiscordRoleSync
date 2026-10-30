-- The schema from before database migrations were added.
-- IF NOT EXISTS lets databases created by older plugin versions be adopted as they are.
CREATE TABLE IF NOT EXISTS `${prefix}_discordmcusers` (
    `discord_id` varchar(24) NOT NULL,
    `minecraft_uuid` varchar(36) NOT NULL,
    `whitelisted` boolean NOT NULL DEFAULT false,
    `verification_code` INT NOT NULL DEFAULT 0,
    `verified` boolean NOT NULL DEFAULT false,
    `username_when_linked` TEXT NULL,
    PRIMARY KEY (`discord_id`)
);
