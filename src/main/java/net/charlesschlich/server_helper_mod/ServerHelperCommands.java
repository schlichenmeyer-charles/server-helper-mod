package net.charlesschlich.server_helper_mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class ServerHelperCommands {
    private static final Set<String> REGISTERED_ALIASES = new HashSet<>();
    private static final ThreadLocal<Set<String>> ACTIVE_ALIASES = ThreadLocal.withInitial(HashSet::new);

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
        registerConfiguredAliases(event.getDispatcher());
    }

    public static void registerConfiguredAliases(CommandDispatcher<CommandSourceStack> dispatcher) {
        if (Config.commandAliases == null || Config.commandAliases.isEmpty()) return;

        for (String alias : new TreeSet<>(Config.commandAliases.keySet())) {
            if (isReservedAlias(alias)) continue;

            if (dispatcher.getRoot().getChild(alias) != null && !REGISTERED_ALIASES.contains(alias)) {
                continue;
            }

            if (REGISTERED_ALIASES.add(alias)) {
                dispatcher.register(Commands.literal(alias)
                        .executes(ctx -> executeAlias(ctx.getSource(), alias)));
            }
        }
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("serverhelper")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("reload")
                                .executes(ctx -> reloadServerHelper(ctx.getSource()))
                        )
                        .then(Commands.literal("maintenance")
                                .then(Commands.literal("status")
                                        .executes(ctx -> maintenanceStatus(ctx.getSource()))
                                )
                                .then(Commands.literal("on")
                                        .executes(ctx -> setMaintenance(ctx.getSource(), true))
                                )
                                .then(Commands.literal("off")
                                        .executes(ctx -> setMaintenance(ctx.getSource(), false))
                                )
                        )
                        .then(Commands.literal("status")
                                .executes(ctx -> showStatus(ctx.getSource()))
                        )
                        .then(Commands.literal("testwarn")
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440))
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

                                            RestartScheduler.broadcastTestWarning(src.getServer(), minutes);

                                            src.sendSuccess(
                                                    () -> Component.literal("Sent test warning for " + minutes + " minute(s).")
                                                            .withStyle(ChatFormatting.GREEN),
                                                    false
                                            );

                                            return 1;
                                        })
                                )
                        )
                        .then(Commands.literal("restartstatus")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    MinecraftServer server = src.getServer();

                                    var status = RestartScheduler.getStatus(server);
                                    if (status == null) {
                                        src.sendFailure(Component.literal("No restart scheduled (check config restart times).")
                                                .withStyle(ChatFormatting.RED));
                                        return 0;
                                    }

                                    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
                                    String nextTime = status.nextRestartLocal().format(timeFmt);

                                    long secs = status.secondsRemaining();
                                    if (secs < 0) secs = 0;

                                    Component msg = Component.literal("Next restart: ")
                                            .withStyle(ChatFormatting.GRAY)
                                            .append(Component.literal(nextTime).withStyle(ChatFormatting.GOLD))
                                            .append(Component.literal(" | Seconds remaining: ").withStyle(ChatFormatting.GRAY))
                                            .append(Component.literal(Long.toString(secs)).withStyle(ChatFormatting.GREEN));

                                    src.sendSuccess(() -> msg, false);
                                    return 1;
                                })
                        )
                        .then(Commands.literal("getlocaltime")
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();

                                    ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                                    DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
                                    String timeStr = now.format(fmt);

                                    src.sendSuccess(
                                            () -> Component.literal("Server local time: ")
                                                    .withStyle(ChatFormatting.GRAY)
                                                    .append(Component.literal(timeStr).withStyle(ChatFormatting.GOLD)),
                                            false
                                    );

                                    return 1;
                                })
                        )
        );
    }

    private static int reloadServerHelper(CommandSourceStack src) {
        Config.ReloadResult configReload;
        BanItemEnforcer.SweepStats sweepStats;

        try {
            configReload = Config.reloadFromDisk("reloaded (command)");
            BanItemManager.load();
            sweepStats = BanItemEnforcer.sweepServer(src.getServer());
            registerConfiguredAliases(src.getServer().getCommands().getDispatcher());
            resendCommandTrees(src.getServer());
        } catch (Exception e) {
            src.sendFailure(Component.literal(
                    "Server Helper reload failed. Check the server log for details."
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        src.sendSuccess(() -> Component.literal(
                "Reloaded Server Helper config, rules, restart schedule, aliases, and banned items."
        ).withStyle(ChatFormatting.GREEN), false);

        src.sendSuccess(() -> Component.literal(
                "Config source=" + (configReload.loadedFromDisk() ? configReload.configPath() : "in-memory")
                        + ", restart times=" + Config.restartTimes + ", warn=" + Config.warnMinutes
        ).withStyle(ChatFormatting.GRAY), false);

        src.sendSuccess(() -> Component.literal(
                "Rules loaded=" + (Config.rules != null ? Config.rules.size() : 0)
                        + ", aliases loaded=" + (Config.commandAliases != null ? Config.commandAliases.size() : 0)
                        + ", banned items loaded=" + BanItemManager.getAllBans().size()
                        + ", swept players=" + sweepStats.playersSwept()
                        + ", swept chunks=" + sweepStats.chunksSwept()
                        + ", removed stacks=" + sweepStats.totalStacksRemoved()
        ).withStyle(ChatFormatting.AQUA), false);

        return 1;
    }

    private static int maintenanceStatus(CommandSourceStack src) {
        src.sendSuccess(
                () -> Component.literal("Maintenance mode is ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(Config.maintenanceEnabled ? "ON" : "OFF")
                                .withStyle(Config.maintenanceEnabled ? ChatFormatting.RED : ChatFormatting.GREEN)),
                false
        );
        return 1;
    }

    private static int setMaintenance(CommandSourceStack src, boolean enabled) {
        Config.setMaintenanceEnabled(enabled);

        src.sendSuccess(
                () -> Component.literal("Maintenance mode " + (enabled ? "enabled." : "disabled."))
                        .withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int showStatus(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        double mspt = server.getAverageTickTime();
        double tps = mspt <= 0.0D ? 20.0D : Math.min(20.0D, 1000.0D / mspt);
        int players = server.getPlayerList().getPlayerCount();
        int maxPlayers = server.getPlayerList().getMaxPlayers();

        src.sendSuccess(() -> Component.literal("Server status")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        src.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "TPS=%.2f, MSPT=%.2f, players=%d/%d, maintenance=%s",
                tps,
                mspt,
                players,
                maxPlayers,
                Config.maintenanceEnabled ? "on" : "off"
        )).withStyle(ChatFormatting.GRAY), false);

        for (ServerLevel level : server.getAllLevels()) {
            String dimension = level.dimension().location().toString();
            int entityCount = countEntities(level);
            int loadedChunks = level.getChunkSource().getLoadedChunksCount();
            int dimensionPlayers = level.players().size();
            double dimensionMspt = averageMspt(server.getTickTime(level.dimension()));

            src.sendSuccess(() -> Component.literal(String.format(
                    Locale.ROOT,
                    "%s: MSPT=%.2f, entities=%d, players=%d, loaded chunks=%d",
                    dimension,
                    dimensionMspt,
                    entityCount,
                    dimensionPlayers,
                    loadedChunks
            )).withStyle(ChatFormatting.AQUA), false);
        }

        return 1;
    }

    private static int executeAlias(CommandSourceStack src, String alias) {
        String target = Config.commandAliases == null ? null : Config.commandAliases.get(alias);
        if (target == null || target.isBlank()) {
            src.sendFailure(Component.literal("Alias is no longer configured: " + alias)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        String firstToken = target.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        Set<String> activeAliases = ACTIVE_ALIASES.get();
        if (firstToken.equals(alias) || activeAliases.contains(alias)) {
            src.sendFailure(Component.literal("Alias loop detected for: " + alias)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        activeAliases.add(alias);
        try {
            return src.getServer().getCommands().performPrefixedCommand(src, target);
        } finally {
            activeAliases.remove(alias);
        }
    }

    private static boolean isReservedAlias(String alias) {
        return alias.equals("serverhelper") || alias.equals("banitems") || alias.equals("rules");
    }

    private static void resendCommandTrees(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            server.getCommands().sendCommands(player);
        }
    }

    private static int countEntities(ServerLevel level) {
        int count = 0;
        for (var ignored : level.getAllEntities()) {
            count++;
        }
        return count;
    }

    private static double averageMspt(long[] tickTimes) {
        if (tickTimes == null || tickTimes.length == 0) return 0.0D;

        long total = 0L;
        int samples = 0;
        for (long tickTime : tickTimes) {
            if (tickTime <= 0L) continue;
            total += tickTime;
            samples++;
        }

        if (samples == 0) return 0.0D;
        return (total / (double) samples) / 1_000_000.0D;
    }
}
