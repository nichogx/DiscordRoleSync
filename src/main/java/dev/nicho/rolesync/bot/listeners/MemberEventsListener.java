package dev.nicho.rolesync.bot.listeners;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.bot.discord.DiscordAgent;
import dev.nicho.rolesync.db.DatabaseHandler;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Objects;

public class MemberEventsListener extends ListenerAdapter {

    private final RoleSync plugin;
    private final DiscordAgent discordAgent;

    public MemberEventsListener(RoleSync plugin) {
        this.plugin = plugin;
        this.discordAgent = new DiscordAgent(plugin);
    }

    @Override
    public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
        discordAgent.checkMemberRoles(event.getMember());
    }

    @Override
    public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
        discordAgent.checkMemberRoles(event.getMember());
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
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
        try {
            DatabaseHandler.LinkedUserInfo userInfo = plugin.getDb().getLinkedUserInfo(Objects.requireNonNull(event.getMember()).getId());
            if (userInfo != null) {
                plugin.getDb().removeFromWhitelist(userInfo.uuid);

                discordAgent.setPermissions(userInfo.uuid, null);
            }
        } catch (SQLException | NullPointerException e) {
            plugin.getLogger().severe("An error occurred while removing kicked/banned/left member from whitelist.\n" +
                    e.getMessage());
        }
    }
}
