package dev.nicho.rolesync;

import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.guild.member.update.GenericGuildMemberUpdateEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nonnull;

public class SyncBot extends ListenerAdapter {

    private JavaPlugin plugin = null;

    public SyncBot(@Nonnull JavaPlugin plugin) {
        super();
        this.plugin = plugin;
        plugin.getLogger().info("Finished initializing bot.");
    }

    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        plugin.getLogger().info("Logged in: " + event.getJDA().getSelfUser().getName());
    }

    @Override
    public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return; // ignore bots

        String message = event.getMessage().getContentRaw();
        String prefix = this.plugin.getConfig().getString("botInfo.prefix");

        if (!message.substring(0, prefix.length()).equals(prefix)) return; // ignore if no prefix

        String[] argv = message.split(" ");
        argv[0] = argv[0].substring(prefix.length()); // remove prefix

        if (argv[0].equalsIgnoreCase("ping")) {
            event.getMessage().getChannel().sendMessage("pong lul").queue();
        }
    }

    @Override
    public void onGenericGuildMemberUpdate(@Nonnull GenericGuildMemberUpdateEvent event) {

    }
}
