package net.charlesschlich.server_helper_mod;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final Logger LOGGER = LogUtils.getLogger();

    // ===== Config Entries =====
    private static final ForgeConfigSpec.BooleanValue ENABLE_MESSAGES;

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RESTART_TIMES;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> WARN_MINUTES;
    private static final ForgeConfigSpec.ConfigValue<String> RESTART_COMMAND;
    private static final ForgeConfigSpec.BooleanValue STOP_AT_ZERO;

    static {
        // --- [general] ---
        BUILDER.push("general");
        ENABLE_MESSAGES = BUILDER
                .comment("Turn on and off automatic restart messages.")
                .define("enable_messages", true);
        BUILDER.pop();

        // --- [restart] ---
        BUILDER.push("restart");

        RESTART_TIMES = BUILDER
                .comment("Daily restart times (24h HH:mm) in server local time. e.g. [\"04:00\",\"16:00\"]")
                .defineListAllowEmpty("times", List.of("04:00"),
                        o -> o instanceof String s && s.matches("^\\d{2}:\\d{2}$"));

        WARN_MINUTES = BUILDER
                .comment("Send warnings when restart is N minutes away.")
                .defineListAllowEmpty("warn_minutes", List.of(30, 10, 5, 1),
                        o -> o instanceof Integer i && i >= 0 && i <= 1440);

        RESTART_COMMAND = BUILDER
                .comment("Server command to execute at restart time (no leading '/'). Example: 'stop' or 'restart'")
                .define("command", "stop");

        STOP_AT_ZERO = BUILDER
                .comment("Whether the server should be stopped when the countdown reaches zero.")
                .define("stop_at_zero", false);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    static final ForgeConfigSpec SPEC;

    // ===== Baked Values (what the rest of your mod reads) =====
    public static boolean enableMessages;
    public static List<String> restartTimes;
    public static Set<Integer> warnMinutes;
    public static String restartCommand;
    public static boolean stopAtZero;

    private static void bake() {
        enableMessages = ENABLE_MESSAGES.get();

        restartTimes = List.copyOf(RESTART_TIMES.get());
        warnMinutes = new HashSet<>(WARN_MINUTES.get());

        restartCommand = RESTART_COMMAND.get().trim();
        stopAtZero = STOP_AT_ZERO.get();
    }

    private static void logChanges(String reason) {
        LOGGER.info(
                "[Server Helper Mod] Config {}: enableMessages={}, restartTimes={}, warnMinutes={}, restartCommand={}, stopAtZero={}",
                reason, enableMessages, restartTimes, warnMinutes, restartCommand, stopAtZero
        );
    }

    private static void resetSchedulerIfRunning() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) RestartScheduler.resetSchedule(server);
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        bake();
        resetSchedulerIfRunning();
        logChanges("loaded");
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        bake();
        resetSchedulerIfRunning();
        logChanges("reloaded");
    }
}
