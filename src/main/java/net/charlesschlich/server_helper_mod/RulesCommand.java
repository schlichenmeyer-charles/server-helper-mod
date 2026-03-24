package net.charlesschlich.server_helper_mod;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public class RulesCommand {

    // Change these to your actual server links
    private static final String DISCORD_URL = Config.discordUrl;
    private static final String WEBSITE_URL = Config.websiteUrl;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("rules")
                        .requires(source -> true)
                        .executes(context -> showRules(context.getSource()))
        );
    }

    private static int showRules(CommandSourceStack source) {
        sendHeader(source);
        sendRulesList(source);
        sendLinksSection(source);
        sendFooter(source);
        return 1;
    }

    private static void sendHeader(CommandSourceStack source) {
        MutableComponent header = Component.literal("========= Server Rules =========")
                .withStyle(style -> style
                        .withColor(ChatFormatting.GOLD)
                        .withBold(true));

        source.sendSuccess(() -> header, false);

        MutableComponent subtitle = Component.literal("Please read and follow these rules while playing.")
                .withStyle(ChatFormatting.YELLOW);

        source.sendSuccess(() -> subtitle, false);
        source.sendSuccess(() -> Component.empty(), false);
    }

    private static void sendRulesList(CommandSourceStack source) {
        if (Config.rules == null || Config.rules.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No rules are currently configured.")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        MutableComponent sectionTitle = Component.literal("General Rules")
                .withStyle(style -> style
                        .withColor(ChatFormatting.AQUA)
                        .withBold(true)
                        .withUnderlined(true));

        source.sendSuccess(() -> sectionTitle, false);

        for (int i = 0; i < Config.rules.size(); i++) {
            int index = i + 1;
            String ruleText = Config.rules.get(i);

            MutableComponent line = Component.literal(index + ". ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(
                            Component.literal(ruleText)
                                    .withStyle(style -> style
                                            .withColor(ChatFormatting.WHITE)
                                            .withHoverEvent(new HoverEvent(
                                                    HoverEvent.Action.SHOW_TEXT,
                                                    Component.literal("Rule #" + index)
                                                            .withStyle(ChatFormatting.YELLOW)
                                            ))
                                    )
                    );

            source.sendSuccess(() -> line, false);
        }

        source.sendSuccess(() -> Component.empty(), false);
    }

    private static void sendLinksSection(CommandSourceStack source) {
        MutableComponent sectionTitle = Component.literal("Helpful Links")
                .withStyle(style -> style
                        .withColor(ChatFormatting.LIGHT_PURPLE)
                        .withBold(true)
                        .withUnderlined(true));

        source.sendSuccess(() -> sectionTitle, false);

        MutableComponent discordLine = Component.literal("• Discord: ")
                .withStyle(ChatFormatting.DARK_AQUA)
                .append(
                        Component.literal("[Join Here]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.BLUE)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.OPEN_URL,
                                                DISCORD_URL
                                        ))
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Open the server Discord")
                                                        .withStyle(ChatFormatting.YELLOW)
                                        ))
                                )
                );

        MutableComponent websiteLine = Component.literal("• Website: ")
                .withStyle(ChatFormatting.DARK_GREEN)
                .append(
                        Component.literal("[Open Site]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.BLUE)
                                        .withUnderlined(true)
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.OPEN_URL,
                                                WEBSITE_URL
                                        ))
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Open the server website")
                                                        .withStyle(ChatFormatting.YELLOW)
                                        ))
                                )
                );

        MutableComponent refreshLine = Component.literal("• View rules again: ")
                .withStyle(ChatFormatting.GRAY)
                .append(
                        Component.literal("[Click to run /rules]")
                                .withStyle(style -> style
                                        .withColor(ChatFormatting.GREEN)
                                        .withBold(true)
                                        .withClickEvent(new ClickEvent(
                                                ClickEvent.Action.RUN_COMMAND,
                                                "/rules"
                                        ))
                                        .withHoverEvent(new HoverEvent(
                                                HoverEvent.Action.SHOW_TEXT,
                                                Component.literal("Run /rules again")
                                                        .withStyle(ChatFormatting.YELLOW)
                                        ))
                                )
                );

        source.sendSuccess(() -> discordLine, false);
        source.sendSuccess(() -> websiteLine, false);
        source.sendSuccess(() -> refreshLine, false);
        source.sendSuccess(() -> Component.empty(), false);
    }

    private static void sendFooter(CommandSourceStack source) {
        MutableComponent footer = Component.literal("Punishments are handled at admin discretion.")
                .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withItalic(true));

        source.sendSuccess(() -> footer, false);
    }
}