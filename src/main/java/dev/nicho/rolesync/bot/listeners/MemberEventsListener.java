package dev.nicho.rolesync.bot.listeners;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.bot.discord.DiscordAgent;
import dev.nicho.rolesync.db.DatabaseHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;

public class MemberEventsListener extends ListenerAdapter {

    private final RoleSync plugin;
    private final DiscordAgent discordAgent;

    public MemberEventsListener(RoleSync plugin) {
        this.plugin = plugin;
        this.discordAgent = new DiscordAgent(plugin);
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        if (!isConfiguredServer(event.getGuild())) return;
        discordAgent.checkMemberRoles(event.getMember());
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        if (!isConfiguredServer(event.getGuild())) return;
        discordAgent.checkMemberRoles(event.getMember());
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        if (!isConfiguredServer(event.getGuild())) return;
        try {
            DatabaseHandler.LinkedUserInfo userInfo = plugin.getDb().getLinkedUserInfo(event.getMember().getId());
            if (userInfo != null) {
                if (!plugin.getConfig().getBoolean("requireVerification") || userInfo.verified) {
                    discordAgent.giveRoleAndNickname(event.getMember(), userInfo.username, userInfo.uuid);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while checking if a new member is linked.\n" +
                    e.getMessage());
        }
    }

    @Override
    public void onGuildMemberRemove(@NotNull GuildMemberRemoveEvent event) {
        if (!isConfiguredServer(event.getGuild())) return;
        try {
            DatabaseHandler.LinkedUserInfo userInfo = plugin.getDb().getLinkedUserInfo(event.getUser().getId());
            if (userInfo != null) {
                plugin.getDb().removeFromWhitelist(userInfo.uuid);

                discordAgent.setPermissions(userInfo.uuid, null);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while removing kicked/banned/left member from whitelist.\n" +
                    e.getMessage());
        }
    }

    /**
     * Only the server in bot.server is supported. Events from any other server the bot
     * is in must be ignored, or they would change the groups and whitelist of linked users.
     */
    private boolean isConfiguredServer(Guild guild) {
        return guild.getId().equals(plugin.getConfig().getString("bot.server"));
    }
}
