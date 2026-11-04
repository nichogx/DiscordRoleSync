package dev.nicho.rolesync.bot.discord;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.SelfMember;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotPermissionCheckerTest {

    private static final String LINKED_ROLE_ID = "123456789012345678";

    private Guild guild;
    private SelfMember self;
    private Role linkedRole;

    @BeforeEach
    void setUp() {
        guild = mock(Guild.class);
        self = mock(SelfMember.class);
        linkedRole = mock(Role.class);
        when(guild.getSelfMember()).thenReturn(self);
        when(guild.getRoleById(LINKED_ROLE_ID)).thenReturn(linkedRole);
        when(self.canInteract(linkedRole)).thenReturn(true);
    }

    @Test
    void noPermissionsNeededWithOptionalFeaturesOff() throws InvalidConfigurationException {
        YamlConfiguration config = config(false, "");

        assertEquals(Collections.emptyList(), BotPermissionChecker.check(config, guild));
    }

    @Test
    void noWarningsWhenBotHasEveryPermission() throws InvalidConfigurationException {
        YamlConfiguration config = config(true, "$minecraft_name$");
        when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(self.hasPermission(Permission.NICKNAME_MANAGE)).thenReturn(true);

        assertEquals(Collections.emptyList(), BotPermissionChecker.check(config, guild));
    }

    @Test
    void linkedRoleNeedsManageRoles() throws InvalidConfigurationException {
        YamlConfiguration config = config(true, "");

        assertEquals(
                List.of("Bot does not have the Manage Roles permission, so it can't give or remove the linked role (giveLinkedRole)."),
                BotPermissionChecker.check(config, guild)
        );
    }

    @Test
    void linkedRoleMustExist() throws InvalidConfigurationException {
        YamlConfiguration config = config(true, "");
        when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(guild.getRoleById(LINKED_ROLE_ID)).thenReturn(null);

        assertEquals(
                List.of("The linked role (linkedRole) does not exist in the Discord server, so it can't be given to linked users."),
                BotPermissionChecker.check(config, guild)
        );
    }

    @Test
    void linkedRoleMustBeBelowBotRole() throws InvalidConfigurationException {
        YamlConfiguration config = config(true, "");
        when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);
        when(self.canInteract(linkedRole)).thenReturn(false);

        assertEquals(
                List.of("The linked role (linkedRole) is above the bot's highest role, so the bot can't give or remove it. Move the bot's role above it in the Discord server settings."),
                BotPermissionChecker.check(config, guild)
        );
    }

    @Test
    void renameNeedsManageNicknames() throws InvalidConfigurationException {
        YamlConfiguration config = config(false, "$discord_name$ ($minecraft_name$)");
        when(self.hasPermission(Permission.MANAGE_ROLES)).thenReturn(true);

        assertEquals(
                List.of("Bot does not have the Manage Nicknames permission, so it can't rename linked users (discordRename.template)."),
                BotPermissionChecker.check(config, guild)
        );
    }

    private static YamlConfiguration config(boolean giveLinkedRole, String renameTemplate) throws InvalidConfigurationException {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(
                "giveLinkedRole: " + giveLinkedRole + "\n" +
                "linkedRole: \"" + LINKED_ROLE_ID + "\"\n" +
                "discordRename:\n" +
                "  template: \"" + renameTemplate + "\"\n"
        );
        return config;
    }
}
