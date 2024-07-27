package dev.nicho.rolesync.bot.discord;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.DatabaseHandler;
import me.clip.placeholderapi.PlaceholderAPI;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;

import java.awt.*;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DiscordAgent {

    private final RoleSync plugin;

    public DiscordAgent(RoleSync plugin) {
        this.plugin = plugin;
    }

    /**
     * Reconciles the user's Minecraft groups, based on the roles they currently have.
     *
     * @param member The Discord member
     */
    public void checkMemberRoles(Member member) {
        try {
            checkMemberRoles(member, plugin.getDb().getLinkedUserInfo(member.getId()));
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while looking for the UUID of a user.\n" +
                    e.getMessage());
        }
    }

    /**
     * Reconciles the user's Minecraft groups, based on the roles they currently have.
     *
     * @param member   The Discord member
     * @param userInfo The LinkedUserInfo object for this user
     */
    public void checkMemberRoles(Member member, DatabaseHandler.LinkedUserInfo userInfo) {
        try {
            ConfigurationSection perms = plugin.getConfig().getConfigurationSection("groups");
            if (userInfo == null) { // user not linked
                return; // ignore
            }

            if (plugin.getConfig().getBoolean("requireVerification") && !userInfo.verified) {
                setPermissions(userInfo.uuid, null);
            } else {
                List<String> permsToHave = new ArrayList<>();
                for (String perm : perms.getKeys(true)) {
                    if (perms.getStringList(perm).isEmpty()) continue;
                    final boolean hasRole = hasRoleFromList(member, perms.getStringList(perm));
                    if (hasRole) {
                        permsToHave.add(perm);
                    }
                }
                setPermissions(userInfo.uuid, permsToHave);
            }

            if (plugin.getConfig().getBoolean("manageWhitelist")) {
                if (hasRoleFromList(member, plugin.getConfig().getStringList("whitelistRoles"))) {
                    plugin.getDb().addToWhitelist(userInfo.uuid);
                } else {
                    plugin.getDb().removeFromWhitelist(userInfo.uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while trying to check roles for the user.\n" +
                    e.getMessage());
        }
    }

    /**
     * Removes all managed groups from the user, leaving only the ones in the list passed in.
     * This is done asynchronously, unless the permission plugin being used is PermissionsEx.
     *
     * @param uuid         UUID for the Minecraft user
     * @param groupsToHave The list of groups to keep
     */
    public void setPermissions(String uuid, List<String> groupsToHave) {
        Runnable task = () -> plugin.getVault().setGroups(uuid, groupsToHave);

        // PEX does not support running tasks asynchronously
        if (plugin.getServer().getPluginManager().getPlugin("PermissionsEx") != null) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    /**
     * Removes the linked role and the nickname from the user, if those
     * are being used.
     *
     * @param member The Discord member
     */
    public void removeRoleAndNickname(Member member) {
        try {
            if (plugin.getConfig().getBoolean("giveLinkedRole")) {
                // remove role
                Role role = member.getGuild().getRoleById(plugin.getConfig().getString("linkedRole"));
                if (role == null) {
                    plugin.getLogger().warning("Linked role does not exist.");
                    return;
                }

                member.getGuild().removeRoleFromMember(member, role).queue(null,
                        error -> plugin.getLogger().warning("Error while adding role: " + error.getMessage())
                );
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to remove roles for a user.");
        }

        try {
            // reset nickname
            if (!plugin.getConfig().getString("discordRename.template").isEmpty()) {
                member.modifyNickname(null).queue(null,
                        error -> plugin.getLogger().warning("Error while changing user's nickname: " + error.getMessage())
                );
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to reset nickname of a user.");
        }
    }

    /**
     * Removes the linked role and the nickname to the user, if those
     * are being used.
     *
     * @param member The Discord member
     * @param mcUser The Minecraft username
     * @param uuid   The Minecraft UUID
     */
    public void giveRoleAndNickname(Member member, String mcUser, String uuid) {
        try {
            List<String> excludedRoles = plugin.getConfig().getStringList("discordRename.excludedRoles");
            String renameTemplate = plugin.getConfig().getString("discordRename.template");
            if (mcUser != null && !renameTemplate.isEmpty() && !hasRoleFromList(member, excludedRoles)) {
                String nick = renameTemplate
                        .replace("$discord_name$", member.getUser().getEffectiveName())
                        .replace("$minecraft_name$", mcUser);

                // PlaceholderAPI if it's available
                if (plugin.getIntegrationEnabled("PlaceholderAPI") && renameTemplate.contains("%")) {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
                    if (player != null) {
                        nick = PlaceholderAPI.setPlaceholders(player, nick);
                    }
                }

                // Truncate to 32 codepoints
                nick = nick.codePoints()
                        .limit(32)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString();

                member.modifyNickname(nick).queue(null,
                        error -> plugin.getLogger().warning("Error while changing user's nickname: " + error.getMessage())
                );
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to change nicknames for a user.");
        }

        try {
            if (plugin.getConfig().getBoolean("giveLinkedRole")) {
                Role role = member.getGuild().getRoleById(plugin.getConfig().getString("linkedRole"));
                if (role == null) {
                    throw new IllegalStateException("Linked role does not exist");
                }

                member.getGuild().addRoleToMember(member, role).queue(null,
                        error -> plugin.getLogger().warning("Error while adding role: " + error.getMessage()));
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to add roles for a user.");
        } catch (IllegalStateException e) {
            plugin.getLogger().severe("Error while giving user the linked role: " + e.getMessage());
        }
    }

    /**
     * Replies to the user's command using the InteractionHook, with an embed
     * if the plugin is configured to do so.
     *
     * @param hook    The InteractionHook to reply to
     * @param message The message to send
     * @return The message create action to be queued
     */
    public WebhookMessageCreateAction<Message> buildReply(InteractionHook hook, String message) {
        return buildReply(hook, ReplyType.INFO, message);
    }

    /**
     * Replies to the user's command using the InteractionHook, with an embed
     * if the plugin is configured to do so.
     *
     * @param hook      The InteractionHook to reply to
     * @param replyType The reply type
     * @param message   The message to send
     * @return The message create action to be queued
     */
    public WebhookMessageCreateAction<Message> buildReply(InteractionHook hook, ReplyType replyType, String message) {
        if (!plugin.getConfig().getBoolean("embed.useEmbed", false)) {
            return hook.sendMessage(message);
        }

        // User has embeds enabled
        String embedTitle = plugin.getConfig().getString("embed.title");

        EmbedBuilder builder = new EmbedBuilder()
                .setTitle(embedTitle)
                .setDescription(message);

        String colorConfig = plugin.getConfig().getString(
                String.format("embed.colors.%s", replyType),
                "WHITE"
        );

        return hook.sendMessageEmbeds(
                builder.setColor(getColorFromString(colorConfig)).build()
        );
    }

    private Color getColorFromString(String colorString) {
        if (colorString.matches("^#[0-9A-Fa-f]{1,6}$")) {
            return Color.decode(colorString);
        }

        try {
            Field field = Class.forName("java.awt.Color").getField(colorString);
            return (Color) field.get(null);
        } catch (Exception e) {
            plugin.getLogger().warning("Error while sending message with embed: embed color " + colorString + " is invalid. Defaulting to WHITE.");
        }

        return Color.WHITE;
    }

    private boolean hasRoleFromList(Member member, List<String> roleList) {
        if (member == null) return false;

        for (String roleID : roleList) {
            Role roleFound = member.getRoles().stream().filter(role -> role.getId().equals(roleID)).findFirst().orElse(null);

            if (roleFound != null) {
                return true;
            }
        }

        return false;
    }
}
