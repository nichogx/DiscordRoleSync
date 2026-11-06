package dev.nicho.rolesync.bot;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.bot.discord.BotPermissionChecker;
import dev.nicho.rolesync.bot.discord.DiscordAgent;
import dev.nicho.rolesync.bot.listeners.MemberEventsListener;
import dev.nicho.rolesync.bot.listeners.SlashCommandListener;
import dev.nicho.rolesync.config.ConfigSecrets;
import me.clip.placeholderapi.PlaceholderAPI;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class SyncBot extends ListenerAdapter {

    private final RoleSync plugin;
    private final DiscordAgent discordAgent;

    private JDA jda = null;

    private BukkitTask presenceTimer;

    public SyncBot(RoleSync plugin) {
        super();
        this.plugin = plugin;
        this.discordAgent = new DiscordAgent(plugin);
    }

    /**
     * Starts the bot
     *
     * @throws InvalidTokenException if the token is invalid
     * @throws IllegalStateException if the bot is not in the configured server
     */
    public void start() throws InvalidTokenException, IllegalStateException {
        plugin.getLogger().info("Initializing bot");
        JDABuilder builder = JDABuilder
                .create(new ConfigSecrets(plugin.getConfig()).getBotToken(),
                        GatewayIntent.GUILD_MEMBERS,
                        GatewayIntent.GUILD_MODERATION)
                .disableCache(
                        CacheFlag.EMOJI,
                        CacheFlag.STICKER,
                        CacheFlag.ONLINE_STATUS,
                        CacheFlag.SCHEDULED_EVENTS,
                        CacheFlag.VOICE_STATE,
                        CacheFlag.ACTIVITY,
                        CacheFlag.CLIENT_STATUS,
                        CacheFlag.SOUNDBOARD_SOUNDS
                );

        synchronized (this) {
            this.jda = builder.build();
        }

        SlashCommandListener slashCommandListener = new SlashCommandListener(plugin, jda);
        jda.addEventListener(
                this,
                new MemberEventsListener(plugin),
                slashCommandListener
        );

        plugin.getLogger().info("Updating bot commands");
        jda.updateCommands()
                .addCommands(slashCommandListener.getCommandData())
                .queue((cmds) -> {
                    plugin.getLogger().info("Updated bot commands successfully");
                });
    }

    /**
     * Shuts down the bot. Blocks for up to two seconds before forcing shutdown.
     */
    public void shutdown() {
        this.stopTimers();

        if (this.jda == null) return;
        synchronized (this) {
            plugin.getLogger().info("Shutting down bot...");
            this.jda.shutdown();

            try {
                if (!this.jda.awaitShutdown(Duration.ofSeconds(2))) {
                    plugin.getLogger().info("Forcing bot to shut down");
                    this.jda.shutdownNow();
                    this.jda.awaitShutdown();
                }

                plugin.getLogger().info("Bot has been shut down");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void onReady(ReadyEvent event) {

        // Stop all existing timers before re-setting them
        // Will lock `this`.
        stopTimers();

        plugin.getLogger().info("Logged in: " + event.getJDA().getSelfUser().getName());

        if (this.plugin.getConfig().getBoolean("botActivity.enable")) {
            Server server = this.plugin.getServer();

            // Lock to update the timers
            synchronized (this) {
                this.presenceTimer = server.getScheduler().runTaskTimerAsynchronously(this.plugin, () -> {
                    String template = plugin.getConfig().getString("botActivity.status");
                    String msg = template
                            .replace("$online_players$", String.valueOf(server.getOnlinePlayers().size()))
                            .replace("$total_players$", String.valueOf(server.getMaxPlayers()));

                    // Only make a database call if required
                    if (template.contains("$linked_players$")) {
                        try {
                            msg = msg.replace("$linked_players$",
                                    String.valueOf(plugin.getDb().getLinkedUserCount())
                            );
                        } catch (SQLException e) {
                            plugin.getLogger().warning("Error getting linked user count to set on the Bot's activity: " + e.getMessage());
                        }
                    }

                    // PlaceholderAPI if it's available
                    if (plugin.getIntegrationEnabled("PlaceholderAPI")) {
                        msg = PlaceholderAPI.setPlaceholders(null, msg);
                    }

                    this.jda.getPresence().setActivity(Activity.customStatus(msg));
                }, 0L, 3600L); // run every 3 minutes
            }
        }

        try {
            plugin.getDb().forAllLinkedUsers((userInfo) ->
                    Objects.requireNonNull(jda.getGuildById(this.plugin.getConfig().getString("bot.server")))
                            .retrieveMemberById(userInfo.discordId).queue(member -> {
                                if (member != null) {
                                    if (userInfo.verified || !plugin.getConfig().getBoolean("requireVerification")) {
                                        discordAgent.giveRoleAndNickname(member, userInfo.username, userInfo.uuid);
                                    } else {
                                        discordAgent.removeRoleAndNickname(member);
                                    }
                                    discordAgent.checkMemberRoles(member, userInfo);
                                }
                            }, error -> {
                            }));
        } catch (SQLException e) {
            plugin.getLogger().severe("An error occurred while checking all users.\n" +
                    e.getMessage());
        }

        Guild guild = jda.getGuildById(plugin.getConfig().getString("bot.server"));
        if (guild == null) {
            plugin.getLogger().severe("Bot is not a member of the configured server. This plugin will not work correctly.");
        } else {
            for (String warning : BotPermissionChecker.check(plugin.getConfig(), guild)) {
                plugin.getLogger().warning(warning);
            }
        }

        warnAboutOtherServers(event.getJDA().getGuilds());
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        if (isConfiguredServer(event.getGuild())) return;

        plugin.getLogger().warning("Bot was added to the Discord server " + describe(event.getGuild())
                + ", which is not the configured server. Only one server is supported. "
                + "Events from this server will be ignored.");
    }

    /**
     * Logs a warning if the bot is in any server other than the configured one.
     *
     * @param guilds All the servers the bot is in
     */
    void warnAboutOtherServers(List<Guild> guilds) {
        List<String> others = guilds.stream()
                .filter(g -> !isConfiguredServer(g))
                .map(SyncBot::describe)
                .toList();
        if (others.isEmpty()) return;

        plugin.getLogger().warning("Bot is in more than one Discord server, but only the configured server ("
                + plugin.getConfig().getString("bot.server") + ") is supported. "
                + "Events from these servers will be ignored: " + String.join(", ", others));
    }

    private boolean isConfiguredServer(Guild guild) {
        return guild.getId().equals(plugin.getConfig().getString("bot.server"));
    }

    private static String describe(Guild guild) {
        return guild.getName() + " (" + guild.getId() + ")";
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        // Will lock `this`
        this.stopTimers();
    }

    public JDA getJDA() {
        return jda;
    }

    public DiscordAgent getAgent() {
        return discordAgent;
    }

    /**
     * Stops all timers that have been created for the bot.
     */
    private void stopTimers() {
        synchronized (this) {
            if (this.presenceTimer != null) {
                this.presenceTimer.cancel();
                this.presenceTimer = null;
            }
        }
    }

    /**
     * Gets a Discord username from a Discord ID
     *
     * @param discordId The Discord ID to check
     * @return The Discord username. null if not found.
     */
    public @Nullable String getDiscordUsername(String discordId) {
        User user = this.jda.getUserById(discordId);
        if (user == null) return null;

        return user.getName();
    }

    /**
     * Gets a Discord nickname from a Discord ID
     *
     * @param discordId The Discord ID to check
     * @return The Discord nickname. null if not found.
     */
    public @Nullable String getDiscordDisplayName(String discordId) {
        User user = this.jda.getUserById(discordId);
        if (user == null) return null;

        return user.getEffectiveName();
    }
}
