package dev.nicho.rolesync.util;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class JDAUtils {

    public static void reactAndDelete(String reaction, Message message, FileConfiguration configs) {
        message.addReaction(reaction).queue();

        if (configs.getBoolean("deleteCommands")) {
            message.delete().queueAfter(configs.getInt("deleteAfter"), TimeUnit.SECONDS);
        }
    }

    public static boolean hasRoleFromList(Member member, List<String> roleList, JDA bot) {
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
