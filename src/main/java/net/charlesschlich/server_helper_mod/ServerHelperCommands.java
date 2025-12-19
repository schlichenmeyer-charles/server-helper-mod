package net.charlesschlich.server_helper_mod;


import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.brigadier.arguments.IntegerArgumentType;


@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class ServerHelperCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("serverhelper")
                        .requires(src -> src.hasPermission(2)) // OP-only (permission level 2)
                        .then(Commands.literal("reload")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();

                                    // For COMMON configs: just re-bake + reset scheduler
                                    Config.reloadForCommonConfig("reloaded (command)");

                                    // Message ONLY to the command executor
                                    src.sendSuccess(() -> Component.literal(
                                            "Reloaded. times=" + Config.restartTimes + ", warn=" + Config.warnMinutes
                                    ).withStyle(ChatFormatting.GREEN), false);

                                    return 1;
                                })
                        )
                        .then(Commands.literal("testwarn")
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440))
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

                                            // Broadcast to everyone
                                            RestartScheduler.broadcastTestWarning(src.getServer(), minutes);

                                            // Private confirmation to the executor only
                                            src.sendSuccess(
                                                    () -> Component.literal("Sent test warning for " + minutes + " minute(s).")
                                                            .withStyle(ChatFormatting.GREEN),
                                                    false
                                            );

                                            return 1;
                                        })
                                )
                        )

        );
    }
}
