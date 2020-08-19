package dev.nicho.rolesync.util;

import net.dv8tion.jda.api.entities.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class JDAUtils {

    /**
     * Reacts to a message and deletes it if enabled on the configs
     *
     * @param reaction the reaction to add
     * @param message the message to react to
     * @param configs the configuration file
     */
    public static void reactAndDelete(String reaction, Message message, FileConfiguration configs) {
        message.addReaction(reaction).queue();

        if (message.getChannelType() == ChannelType.TEXT && configs.getBoolean("deleteCommands")) {
            message.delete().queueAfter(configs.getInt("deleteAfter"), TimeUnit.SECONDS);
        }
    }

    /**
     * Checks if a member has a role from a list
     *
     * @param member the member to check
     * @param roleList a list of roles
     * @return true if they have at least one role, false if they have none
     */
    public static boolean hasRoleFromList(Member member, List<String> roleList) {
        if (member != null) {
            for (String roleID : roleList) {
                Role roleFound = member.getRoles().stream().filter(role -> role.getId().equals(roleID)).findFirst().orElse(null);

                if (roleFound != null) {
                    return true;
                }
            }
        }

        return false;
    }
}
