package dev.nicho.rolesync.bot.listeners;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.db.SQLiteHandler;
import dev.nicho.rolesync.util.vault.VaultAPI;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberEventsListenerTest {

    private static final String CONFIGURED_SERVER = "100000000000000001";
    private static final String OTHER_SERVER = "100000000000000002";
    private static final String DISCORD_ID = "200000000000000001";
    private static final String UUID = "00000000-0000-0000-0000-000000000001";

    @TempDir
    File tempDir;

    private MockedStatic<Bukkit> bukkit;
    private SQLiteHandler db;
    private VaultAPI vault;
    private MemberEventsListener listener;

    @BeforeEach
    void setUp() throws IOException, SQLException {
        bukkit = Mockito.mockStatic(Bukkit.class);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(config.getString("database.tablePrefix")).thenReturn("drs");
        Mockito.when(config.getString("bot.server")).thenReturn(CONFIGURED_SERVER);

        // Run scheduled tasks right away
        BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class);
        Mockito.when(scheduler.runTaskAsynchronously(Mockito.any(), Mockito.any(Runnable.class)))
                .thenAnswer(inv -> {
                    inv.getArgument(1, Runnable.class).run();
                    return null;
                });
        Server server = Mockito.mock(Server.class);
        Mockito.when(server.getScheduler()).thenReturn(scheduler);
        Mockito.when(server.getPluginManager()).thenReturn(Mockito.mock(PluginManager.class));

        vault = Mockito.mock(VaultAPI.class);

        RoleSync plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("MemberEventsListenerTest"));
        Mockito.when(plugin.getServer()).thenReturn(server);
        Mockito.when(plugin.getVault()).thenReturn(vault);

        db = new SQLiteHandler(plugin, new File(tempDir, "database.db"));
        Mockito.when(plugin.getDb()).thenReturn(db);

        db.linkUser(DISCORD_ID, UUID);
        db.addToWhitelist(UUID);

        listener = new MemberEventsListener(plugin);
    }

    @AfterEach
    void tearDown() throws SQLException {
        db.close();
        bukkit.close();
    }

    private static GuildMemberRemoveEvent removeEvent(String guildId) {
        Guild guild = Mockito.mock(Guild.class);
        Mockito.when(guild.getId()).thenReturn(guildId);
        User user = Mockito.mock(User.class);
        Mockito.when(user.getId()).thenReturn(DISCORD_ID);

        GuildMemberRemoveEvent event = Mockito.mock(GuildMemberRemoveEvent.class);
        Mockito.when(event.getGuild()).thenReturn(guild);
        Mockito.when(event.getUser()).thenReturn(user);
        return event;
    }

    @Test
    void leavingConfiguredServerRemovesFromWhitelist() throws SQLException {
        listener.onGuildMemberRemove(removeEvent(CONFIGURED_SERVER));

        assertFalse(db.getLinkedUserInfo(UUID).whitelisted);
        Mockito.verify(vault).setGroups(UUID, null);
    }

    @Test
    void leavingOtherServerKeepsWhitelistAndGroups() throws SQLException {
        listener.onGuildMemberRemove(removeEvent(OTHER_SERVER));

        assertTrue(db.getLinkedUserInfo(UUID).whitelisted);
        Mockito.verifyNoInteractions(vault);
    }
}
