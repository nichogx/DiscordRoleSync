package dev.nicho.rolesync.bot.discord;

import dev.nicho.rolesync.RoleSync;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.requests.restaction.AuditableRestAction;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscordAgentTest {

    private final List<LogRecord> warnings = new ArrayList<>();
    private DiscordAgent agent;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        Mockito.when(config.getString("discordRename.template")).thenReturn("$minecraft_name$");

        RoleSync plugin = Mockito.mock(RoleSync.class);
        Mockito.when(plugin.getConfig()).thenReturn(config);
        Mockito.when(plugin.getLogger()).thenReturn(logger);

        agent = new DiscordAgent(plugin);
    }

    /**
     * A member that behaves like JDA does for the server owner: changing the
     * nickname throws before any request is sent.
     */
    private static Member owner() {
        Member member = Mockito.mock(Member.class);
        Mockito.when(member.isOwner()).thenReturn(true);
        Mockito.when(member.modifyNickname(Mockito.any()))
                .thenThrow(new HierarchyException("Cannot modify the nickname of the guild owner"));
        return member;
    }

    @Test
    void givingNicknameToOwnerDoesNotWarn() {
        agent.giveRoleAndNickname(owner(), "Steve", "00000000-0000-0000-0000-000000000001");

        assertEquals(List.of(), warnings);
    }

    @Test
    void resettingOwnerNicknameDoesNotWarn() {
        agent.removeRoleAndNickname(owner());

        assertEquals(List.of(), warnings);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonOwnerIsStillRenamed() {
        User user = Mockito.mock(User.class);
        Mockito.when(user.getEffectiveName()).thenReturn("discorduser");
        Member member = Mockito.mock(Member.class);
        Mockito.when(member.getUser()).thenReturn(user);
        Mockito.when(member.modifyNickname(Mockito.any())).thenReturn(Mockito.mock(AuditableRestAction.class));

        agent.giveRoleAndNickname(member, "Steve", "00000000-0000-0000-0000-000000000001");

        ArgumentCaptor<String> nick = ArgumentCaptor.forClass(String.class);
        Mockito.verify(member).modifyNickname(nick.capture());
        assertEquals("Steve", nick.getValue());
        assertEquals(List.of(), warnings);
    }
}
