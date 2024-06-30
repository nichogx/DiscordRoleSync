package dev.nicho.rolesync.bot.discord;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.function.Consumer;
import java.util.function.Function;

public class DiscordCommand {

    private final String name, description;
    private final Consumer<SlashCommandInteractionEvent> implementation;
    private final Function<SlashCommandData, SlashCommandData> modifiers;

    public DiscordCommand(
            String name,
            String description,
            Function<SlashCommandData, SlashCommandData> modifiers,
            Consumer<SlashCommandInteractionEvent> implementation
    ) {
        this.name = name;
        this.description = description;
        this.modifiers = modifiers;
        this.implementation = implementation;
    }

    protected DiscordCommand(
            String name,
            String description,
            Consumer<SlashCommandInteractionEvent> implementation
    ) {
        this(name, description, null, implementation);
    }

    /**
     * Runs the command implementation.
     *
     * @param event the SlashCommandInteractionEvent
     */
    public void run(SlashCommandInteractionEvent event) {
        if (!event.getName().equalsIgnoreCase(name)) {
            throw new IllegalArgumentException("Incorrect event received for command " + name + ": " + event.getName());
        }

        implementation.accept(event);
    }

    /**
     * Gets the SlashCommandData for this command, so it can be registered with JDA.
     *
     * @return the SlashCommandData
     */
    public SlashCommandData getCommandData() {
        SlashCommandData data = Commands.slash(name, description);
        if (this.modifiers == null) {
            return data;
        }

        return this.modifiers.apply(data);
    }
}
