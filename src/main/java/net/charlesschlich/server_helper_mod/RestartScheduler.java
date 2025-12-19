package net.charlesschlich.server_helper_mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class RestartScheduler {
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private static Instant nextRestart = null;
    private static final Set<Integer> sentWarnings = new HashSet<>();
    private static long lastCheckMs = 0;


    /** Call when server starts (or config reloads) */
    public static void resetSchedule(MinecraftServer server) {
        sentWarnings.clear();
        nextRestart = computeNextRestart(server);
        lastCheckMs = 0;
    }

    private static Instant computeNextRestart(MinecraftServer server) {
        // Use server/JVM local time. If you want a fixed TZ, read it from config and use ZoneId.of(...)
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);

        List<String> times = Config.restartTimes;
        if (times == null || times.isEmpty()) return null;

        Instant best = null;
        for (String t : times) {
            LocalTime lt;
            try {
                lt = LocalTime.parse(t, HHMM);
            } catch (Exception ignored) { continue; }

            ZonedDateTime candidate = now.with(lt);
            if (!candidate.isAfter(now)) candidate = candidate.plusDays(1);

            Instant inst = candidate.toInstant();
            if (best == null || inst.isBefore(best)) best = inst;
        }
        return best;
    }

    private static void broadcast(MinecraftServer server, String msg, ChatFormatting color, boolean isBold) {
        // Shows as a system chat message to everyone online
        if(isBold){
            server.getPlayerList().broadcastSystemMessage(Component.literal(msg).withStyle(color, ChatFormatting.BOLD), false);
        } else {
            server.getPlayerList().broadcastSystemMessage(Component.literal(msg).withStyle(color), false);

        }
    }

    private static void doServerAction(MinecraftServer server) {
        String cmd = Config.commandToExecute;
        if (cmd == null || cmd.isBlank()) {
            cmd = "stop";
        }
        // Clean shutdown using the command system; wrapper/systemd/AMP should restart the process
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
    }
    public static void broadcastTestWarning(MinecraftServer server, int minutes) {
        ChatFormatting color =
                minutes <= 1 ? ChatFormatting.RED :
                        minutes <= 5 ? ChatFormatting.GOLD :
                                ChatFormatting.YELLOW;

        Component msg = Component.literal("[Server] ")
                .withStyle(ChatFormatting.RED)
                .append(Component.literal("Restart in ").withStyle(color))
                .append(Component.literal(minutes + " minute" + (minutes == 1 ? "" : "s") + "!").withStyle(color));

        server.getPlayerList().broadcastSystemMessage(msg, false);
    }

    public static RestartStatus getStatus(MinecraftServer server) {
        if (server == null) return null;

        // Ensure we have a computed nextRestart
        if (nextRestart == null) {
            nextRestart = computeNextRestart(server);
            sentWarnings.clear();
        }
        if (nextRestart == null) return null;

        long secondsLeft = Duration.between(Instant.now(), nextRestart).getSeconds();

        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime nextLocal = ZonedDateTime.ofInstant(nextRestart, zone);

        return new RestartStatus(nextRestart, nextLocal, secondsLeft);
    }

    public record RestartStatus(Instant nextRestartUtc, ZonedDateTime nextRestartLocal, long secondsRemaining) {}


    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!Config.enableMessages) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        // Throttle checks to ~once per second
        long nowMs = System.currentTimeMillis();
        if (nowMs - lastCheckMs < 1000) return;
        lastCheckMs = nowMs;

        if (nextRestart == null) {
            nextRestart = computeNextRestart(server);
            if (nextRestart == null) return;
        }

        long secondsLeft = Duration.between(Instant.now(), nextRestart).getSeconds();

        // If schedule changed or time passed, roll forward
        if (secondsLeft < -5) {
            resetSchedule(server);
            return;
        }

        // Fire warnings at configured minute marks
        for (int m : Config.warnMinutes) {
            long trigger = m * 60L;
            if (secondsLeft <= trigger && secondsLeft > trigger - 2) { // 2s window
                if (sentWarnings.add(m)) {
                    broadcast(server, "[Server] Restart in " + m + " minute" + (m == 1 ? "" : "s") + "!", ChatFormatting.RED, false);
                }
            }
        }

        // Final countdown message (optional)
        if (secondsLeft == 30) broadcast(server, "[Server] Restart in 30 seconds!", ChatFormatting.DARK_RED, false);
        if (secondsLeft == 10) broadcast(server, "[Server] Restart in 10 seconds!", ChatFormatting.DARK_RED, false);

        // Stop at <= 0
        if (secondsLeft <= 0) {
            broadcast(server, "[Server] Restarting now!", ChatFormatting.DARK_RED, true);
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(),
                    "save-all"
            );

            if (Config.executeAtZero) {
                doServerAction(server);
            }
        }
    }
}
