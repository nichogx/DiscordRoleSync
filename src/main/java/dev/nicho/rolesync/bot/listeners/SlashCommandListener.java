package dev.nicho.rolesync.bot.listeners;

import dev.nicho.rolesync.RoleSync;
import dev.nicho.rolesync.bot.discord.DiscordCommand;
import dev.nicho.rolesync.bot.discord.DiscordAgent;
import dev.nicho.rolesync.bot.discord.ReplyType;
import dev.nicho.rolesync.bot.exceptions.UserErrorException;
import dev.nicho.rolesync.db.DatabaseHandler;
import dev.nicho.rolesync.minecraft.UUIDType;
import dev.nicho.rolesync.minecraft.UserSearch;
import dev.nicho.rolesync.minecraft.UserSearchResult;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

public class SlashCommandListener extends ListenerAdapter {

    private final RoleSync plugin;
    private final JDA jda;

    private final DiscordAgent discordAgent;
    private final UserSearch mojang;

    private final Map<String, DiscordCommand> commands;

    public SlashCommandListener(RoleSync plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;

        this.discordAgent = new DiscordAgent(plugin);
        this.mojang = new UserSearch(plugin);

        this.commands = new HashMap<>();

        String link = plugin.getConfig().getString("commandNames.link", "link");
        this.commands.put(link, new DiscordCommand(link,
                plugin.getLanguage().getString("commandDescriptions.link"),
                cmd -> cmd.setGuildOnly(true)
                        .addOption(OptionType.STRING, "minecraft_username",
                                plugin.getLanguage().getString("commandArguments.minecraftUsername.link"), true),
                event -> {
                    event.deferReply(true).queue();

                    InteractionHook hook = event.getHook();
                    hook.setEphemeral(true);

                    String mcUsername = Objects.requireNonNull(event.getOption("minecraft_username")).getAsString();
                    try {
                        this.linkUser(Objects.requireNonNull(event.getMember()).getId(), mcUsername);
                    } catch (IOException | SQLException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, plugin.getLanguage().getString("commandError")).queue();
                        plugin.getLogger().severe("An error occurred while trying to link the user.\n" +
                                e.getMessage());

                        return;
                    } catch (UserErrorException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, e.getMessage()).queue();

                        return;
                    }

                    discordAgent.buildReply(hook, ReplyType.SUCCESS, plugin.getLanguage().getString("successLink")).queue();
                }
        ));

        // The verify command is only enabled if verification is enabled in the config.
        String verify = plugin.getConfig().getString("commandNames.verify", "verify");
        if (plugin.getConfig().getBoolean("requireVerification")) this.commands.put(verify, new DiscordCommand(verify,
                plugin.getLanguage().getString("commandDescriptions.verify"),
                cmd -> cmd.setGuildOnly(true)
                        .addOption(OptionType.INTEGER, "verification_code",
                                plugin.getLanguage().getString("commandArguments.verificationCode"), true),
                event -> {
                    event.deferReply(true).queue();

                    InteractionHook hook = event.getHook();
                    hook.setEphemeral(true);

                    Member member = Objects.requireNonNull(event.getMember());
                    String discordId = member.getId();
                    try {
                        int code = Objects.requireNonNull(event.getOption("verification_code")).getAsInt();
                        if (String.valueOf(code).length() != 6) {
                            throw new UserErrorException(String.format(
                                    plugin.getLanguage().getString("verification.wrongDigits"), 6
                            ));
                        }

                        DatabaseHandler.LinkedUserInfo userInfo = plugin.getDb().getLinkedUserInfo(discordId);
                        if (userInfo == null) {
                            throw new UserErrorException(
                                    plugin.getLanguage().getString("verification.notLinked").replace("%link_command_name%", link)
                            );
                        }

                        boolean success = plugin.getDb().verify(discordId, code);
                        if (!success) {
                            throw new UserErrorException(plugin.getLanguage().getString("verification.wrongCode"));
                        }

                        Guild guild = jda.getGuildById(plugin.getConfig().getString("bot.server"));
                        if (guild == null) {
                            plugin.getLogger().warning("Guild not found after verifying a user.");
                            return;
                        }

                        discordAgent.giveRoleAndNickname(member, userInfo.username);
                        discordAgent.checkMemberRoles(member);
                    } catch (SQLException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, plugin.getLanguage().getString("commandError")).queue();
                        plugin.getLogger().severe("An error occurred while trying to link the user.\n" +
                                e.getMessage());

                        return;
                    } catch (UserErrorException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, e.getMessage()).queue();
                        return;
                    }

                    discordAgent.buildReply(hook, ReplyType.SUCCESS, plugin.getLanguage().getString("successVerify")).queue();
                }
        ));

        String unlink = plugin.getConfig().getString("commandNames.unlink", "unlink");
        this.commands.put(unlink, new DiscordCommand(unlink,
                plugin.getLanguage().getString("commandDescriptions.unlink"),
                cmd -> cmd.setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                        .addOption(OptionType.STRING, "minecraft_username",
                                plugin.getLanguage().getString("commandArguments.minecraftUsername.unlink"))
                        .addOption(OptionType.USER, "discord_user",
                                plugin.getLanguage().getString("commandArguments.discordUser.unlink")),
                event -> {
                    event.deferReply(true).queue();

                    InteractionHook hook = event.getHook();
                    hook.setEphemeral(true);

                    OptionMapping mcUser = event.getOption("minecraft_username");
                    OptionMapping discordUser = event.getOption("discord_user");
                    if ((mcUser == null) == (discordUser == null)) {
                        // Will be true only if mcUser and discordUser
                        // are both specified or both not specified
                        discordAgent.buildReply(hook, ReplyType.ERROR, plugin.getLanguage().getString("incorrectCommandFormat")).queue();
                        return;
                    }

                    try {
                        DatabaseHandler.LinkedUserInfo userInfo = null;
                        if (discordUser != null) {
                            userInfo = plugin.getDb().getLinkedUserInfo(discordUser.getAsUser().getId());
                        } else { // try minecraft nick
                            UserSearchResult uuidSearch = mojang.nameToUUID(mcUser.getAsString());
                            if (uuidSearch != null) userInfo = plugin.getDb().getLinkedUserInfo(uuidSearch.uuid);
                        }

                        if (userInfo == null) {
                            throw new UserErrorException(plugin.getLanguage().getString("userNotLinked"));
                        }

                        // remove all managed permissions before unlinking
                        discordAgent.setPermissions(userInfo.uuid, null);
                        plugin.getDb().unlink(userInfo.uuid);

                        Guild guild = jda.getGuildById(plugin.getConfig().getString("bot.server"));
                        if (guild == null) {
                            plugin.getLogger().warning("Guild not found while trying to remove a linked role.");
                            return;
                        }

                        guild.retrieveMemberById(userInfo.discordId).queue(discordAgent::removeRoleAndNickname);
                    } catch (SQLException | IOException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, plugin.getLanguage().getString("commandError")).queue();
                        plugin.getLogger().severe("An error occurred while trying to unlink the user.\n" +
                                e.getMessage());

                        return;
                    } catch (UserErrorException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, e.getMessage()).queue();

                        return;
                    }

                    discordAgent.buildReply(hook, ReplyType.SUCCESS, plugin.getLanguage().getString("successUnlink")).queue();
                }
        ));

        String info = plugin.getConfig().getString("commandNames.info", "info");
        this.commands.put(info, new DiscordCommand(info,
                plugin.getLanguage().getString("commandDescriptions.info"),
                cmd -> cmd.setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                        .addOption(OptionType.STRING, "minecraft_username",
                                plugin.getLanguage().getString("commandArguments.minecraftUsername.info"))
                        .addOption(OptionType.USER, "discord_user",
                                plugin.getLanguage().getString("commandArguments.discordUser.info")),
                event -> {
                    event.deferReply(true).queue();

                    InteractionHook hook = event.getHook();
                    hook.setEphemeral(true);

                    OptionMapping mcUser = event.getOption("minecraft_username");
                    OptionMapping discordUser = event.getOption("discord_user");
                    if ((mcUser == null) == (discordUser == null)) {
                        // Will be true only if mcUser and discordUser
                        // are both specified or both not specified
                        discordAgent.buildReply(hook, ReplyType.ERROR, plugin.getLanguage().getString("incorrectCommandFormat")).queue();
                        return;
                    }

                    try {
                        DatabaseHandler.LinkedUserInfo userInfo = null;
                        if (discordUser != null) {
                            userInfo = plugin.getDb().getLinkedUserInfo(discordUser.getAsUser().getId());
                        } else { // try minecraft nick
                            UserSearchResult userSearchResult = mojang.nameToUUID(mcUser.getAsString());
                            if (userSearchResult != null) userInfo = plugin.getDb().getLinkedUserInfo(userSearchResult.uuid);
                        }

                        if (userInfo == null) {
                            throw new UserErrorException(plugin.getLanguage().getString("userNotLinked"));
                        }

                        final String mcUserInfo = String.format("%s (`%s` - `%s`)", userInfo.username, userInfo.uuid, UUIDType.getTypeForUUID(userInfo.uuid));
                        jda.retrieveUserById(userInfo.discordId).queue(user -> {
                            String name = "_" + plugin.getLanguage().getString("unknownUser") + "_";
                            if (user != null) {
                                name = user.getAsMention();
                            }

                            discordAgent.buildReply(hook,
                                    plugin.getLanguage().getString("fullLinkedTo")
                                            .replace("%discord_user%", name)
                                            .replace("%minecraft_user%", mcUserInfo)
                            ).queue();
                        });
                    } catch (SQLException | IOException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, plugin.getLanguage().getString("commandError")).queue();
                        plugin.getLogger().severe("An error occurred while trying to unlink the user.\n" +
                                e.getMessage());
                    } catch (UserErrorException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, e.getMessage()).queue();
                    }
                }
        ));

        String admlink = plugin.getConfig().getString("commandNames.admlink", "admlink");
        this.commands.put(admlink, new DiscordCommand(admlink,
                plugin.getLanguage().getString("commandDescriptions.admlink"),
                cmd -> cmd.setGuildOnly(true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MANAGE_ROLES))
                        .addOption(OptionType.STRING, "minecraft_username",
                                plugin.getLanguage().getString("commandArguments.minecraftUsername.admlink"), true)
                        .addOption(OptionType.USER, "discord_user",
                                plugin.getLanguage().getString("commandArguments.discordUser.admlink"), true),
                event -> {
                    event.deferReply(true).queue();

                    InteractionHook hook = event.getHook();
                    hook.setEphemeral(true);

                    String mcUser = Objects.requireNonNull(event.getOption("minecraft_username")).getAsString();
                    User discordUser = Objects.requireNonNull(event.getOption("discord_user")).getAsUser();
                    try {
                        this.linkUser(discordUser.getId(), mcUser);
                    } catch (IOException | SQLException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, plugin.getLanguage().getString("commandError")).queue();
                        plugin.getLogger().severe("An error occurred while trying to link the user.\n" +
                                e.getMessage());

                        return;
                    } catch (UserErrorException e) {
                        discordAgent.buildReply(hook, ReplyType.ERROR, e.getMessage()).queue();

                        return;
                    }

                    discordAgent.buildReply(hook, ReplyType.SUCCESS, plugin.getLanguage().getString("successLink")).queue();
                }
        ));
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;

        String commandName = event.getName();
        DiscordCommand command = this.commands.get(commandName);
        if (command == null) {
            InteractionHook hook = event.getHook();
            hook.setEphemeral(true);

            discordAgent.buildReply(hook, "Unknown command " + commandName + ".").queue();
            return;
        }

        command.run(event);
    }

    public List<SlashCommandData> getCommandData() {
        List<SlashCommandData> commandData = new ArrayList<>();
        for (DiscordCommand command : commands.values()) {
            commandData.add(command.getCommandData());
        }

        return commandData;
    }

    private void linkUser(String discordId, String mcUsername) throws IOException, SQLException, UserErrorException {
        DatabaseHandler.LinkedUserInfo userInfo = plugin.getDb().getLinkedUserInfo(discordId);
        if (userInfo != null) {
            throw new UserErrorException(plugin.getLanguage().getString("discordAlreadyLinked"));
        }

        UserSearchResult result = mojang.nameToUUID(mcUsername);
        if (result == null) {
            throw new UserErrorException(plugin.getLanguage().getString("unknownUser"));
        }

        String uuid = result.uuid;
        userInfo = plugin.getDb().getLinkedUserInfo(uuid);
        if (userInfo != null) {
            throw new UserErrorException(plugin.getLanguage().getString("minecraftAlreadyLinked"));
        }

        plugin.getDb().linkUser(discordId, uuid);
        plugin.getDb().updateUsername(discordId, result.name);

        Objects.requireNonNull(jda.getGuildById(plugin.getConfig().getString("bot.server")))
                .retrieveMemberById(discordId).queue(member -> {
                    if (member != null) {
                        if (!plugin.getConfig().getBoolean("requireVerification")) {
                            discordAgent.giveRoleAndNickname(member, result.name);
                        }
                        discordAgent.checkMemberRoles(member);
                    }
                });
    }
}
