package dev.nicho.rolesync.bot.discord;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.DatabaseHandler;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.PermissionException;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DiscordAgent {

    private final RoleSync plugin;

    public DiscordAgent(RoleSync plugin) {
        this.plugin = plugin;
    }

    public void checkMemberRoles(Member member) {
        try {
            checkMemberRoles(member, plugin.getDb().getLinkedUserInfo(member.getId()));
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while looking for the UUID of a user.\n" +
                    e.getMessage());
        }
    }

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

    public void setPermissions(String uuid, List<String> permsToHave) {
        Runnable task = () -> plugin.getVault().setGroups(uuid, permsToHave);

        // PEX does not support running tasks asynchronously
        if (plugin.getServer().getPluginManager().getPlugin("PermissionsEx") != null) {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } else {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

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
                        error -> plugin.getLogger().warning("Error while adding role: " + error.getMessage()));
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to remove roles for a user.");
        }

        try {
            // reset nickname
            if (plugin.getConfig().getString("changeNicknames").equalsIgnoreCase("after")) {
                member.modifyNickname(null).queue(null, error -> {
                });
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to reset nickname of a user.");
        }
    }

    public void giveRoleAndNickname(Member member, String mcUser) {
        try {
            if (mcUser != null) {
                if (plugin.getConfig().getString("changeNicknames").equalsIgnoreCase("after")) {
                    member.modifyNickname(member.getUser().getName() + " (" + mcUser + ")").queue(null, error -> {
                    });
                } else if (plugin.getConfig().getString("changeNicknames").equalsIgnoreCase("replace")) {
                    member.modifyNickname(mcUser).queue(null, error -> {
                    });
                }
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to change nicknames for a user.");
        }

        try {
            if (plugin.getConfig().getBoolean("giveLinkedRole")) {
                Role role = member.getGuild().getRoleById(plugin.getConfig().getString("linkedRole"));
                if (role == null) {
                    plugin.getLogger().warning("Linked role does not exist.");
                    return;
                }

                member.getGuild().addRoleToMember(member, role).queue(null,
                        error -> plugin.getLogger().warning("Error while adding role: " + error.getMessage()));
            }
        } catch (PermissionException e) {
            plugin.getLogger().warning("Bot has no permissions to add roles for a user.");
        }
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
