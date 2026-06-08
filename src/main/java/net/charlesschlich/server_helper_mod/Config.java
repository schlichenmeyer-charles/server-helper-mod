package net.charlesschlich.server_helper_mod;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.config.ConfigFileTypeHandler;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    private static final Logger LOGGER = LogUtils.getLogger();

    // ===== Config Entries =====
    private static final ForgeConfigSpec.BooleanValue ENABLE_MESSAGES;

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RESTART_TIMES;
    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> WARN_MINUTES;
    private static final ForgeConfigSpec.ConfigValue<String> COMMAND_TO_EXECUTE;
    private static final ForgeConfigSpec.BooleanValue EXECUTE_AT_ZERO;

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RULES;
    private static final ForgeConfigSpec.ConfigValue<String> DISCORD_URL;
    private static final ForgeConfigSpec.ConfigValue<String> WEBSITE_URL;
    private static final ForgeConfigSpec.BooleanValue MAINTENANCE_ENABLED;
    private static final ForgeConfigSpec.ConfigValue<String> MAINTENANCE_MESSAGE;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> COMMAND_ALIASES;
    private static final ForgeConfigSpec.BooleanValue FTB_CHUNKS_UNLOAD_INACTIVE_ENABLED;
    private static final ForgeConfigSpec.IntValue FTB_CHUNKS_INACTIVE_DAYS;
    private static final ForgeConfigSpec.IntValue FTB_CHUNKS_CHECK_INTERVAL_MINUTES;

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
                .defineListAllowEmpty(
                        "times",
                        List.of("04:00"),
                        o -> o instanceof String s && s.matches("^\\d{2}:\\d{2}$")
                );

        WARN_MINUTES = BUILDER
                .comment("Send warnings when restart is N minutes away.")
                .defineListAllowEmpty(
                        "warn_minutes",
                        List.of(30, 10, 5, 1),
                        o -> o instanceof Integer i && i >= 0 && i <= 1440
                );

        COMMAND_TO_EXECUTE = BUILDER
                .comment("Server command to execute at the end of the timer (no leading '/'). Example: 'stop' or 'restart'")
                .define("command", "stop");

        EXECUTE_AT_ZERO = BUILDER
                .comment("Whether the server should run the above command when the countdown reaches zero.")
                .define("execute_at_zero", false);

        BUILDER.pop();

        // --- [rules] ---
        BUILDER.push("rules");

        RULES = BUILDER
                .comment("Rules shown when a player runs /rules")
                .defineListAllowEmpty(
                        "rules",
                        List.of(
                                "Be respectful to other players.",
                                "No griefing, stealing, or unauthorized base raiding.",
                                "No profane, sexual, or adult-themed chat or builds.",
                                "No cheating, hacked clients, exploits, or duping.",
                                "Follow admin instructions. Punishment is at admin discretion."
                        ),
                        o -> o instanceof String
                );

        DISCORD_URL = BUILDER
                .comment("Discord invite URL shown in /rules")
                .define("discord_url", "https://discord.gg/yourserver");

        WEBSITE_URL = BUILDER
                .comment("Website URL shown in /rules")
                .define("website_url", "https://yourserver.com");

        BUILDER.pop();

        // --- [maintenance] ---
        BUILDER.push("maintenance");

        MAINTENANCE_ENABLED = BUILDER
                .comment("When true, non-operator players are disconnected after login.")
                .define("enabled", false);

        MAINTENANCE_MESSAGE = BUILDER
                .comment("Disconnect message shown to non-operator players while maintenance mode is enabled.")
                .define("message", "The server is currently under maintenance. Please try again later.");

        BUILDER.pop();

        // --- [aliases] ---
        BUILDER.push("aliases");

        COMMAND_ALIASES = BUILDER
                .comment("Command aliases in alias=target format. Do not include leading slashes. Example: discord=rules")
                .defineListAllowEmpty(
                        "commands",
                        List.of("discord=rules", "website=rules"),
                        o -> o instanceof String s && s.contains("=")
                );

        BUILDER.pop();

        // --- [ftb_chunks] ---
        BUILDER.push("ftb_chunks");

        FTB_CHUNKS_UNLOAD_INACTIVE_ENABLED = BUILDER
                .comment("When true, Server Helper will remove FTB Chunks force-loading from teams that have been inactive for the configured number of days. This is ignored if FTB Chunks/Teams are not installed.")
                .define("unload_inactive_enabled", true);

        FTB_CHUNKS_INACTIVE_DAYS = BUILDER
                .comment("Real-world days since the owning FTB team last had a member log in before its force-loaded chunks are no longer force-loaded.")
                .defineInRange("inactive_days", 7, 1, 3650);

        FTB_CHUNKS_CHECK_INTERVAL_MINUTES = BUILDER
                .comment("How often to check FTB Chunks force-loaded chunks after startup. Manual commands can still be run at any time.")
                .defineInRange("check_interval_minutes", 60, 5, 1440);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    static final ForgeConfigSpec SPEC;

    // ===== Baked Values (what the rest of your mod reads) =====
    public static boolean enableMessages;
    public static List<String> restartTimes;
    public static Set<Integer> warnMinutes;
    public static String commandToExecute;
    public static boolean executeAtZero;

    public static List<String> rules;
    public static String discordUrl;
    public static String websiteUrl;
    public static boolean maintenanceEnabled;
    public static String maintenanceMessage;
    public static Map<String, String> commandAliases;
    public static boolean ftbChunksUnloadInactiveEnabled;
    public static int ftbChunksInactiveDays;
    public static int ftbChunksCheckIntervalMinutes;

    private static void bake() {
        enableMessages = ENABLE_MESSAGES.get();

        restartTimes = List.copyOf(RESTART_TIMES.get());
        warnMinutes = new HashSet<>(WARN_MINUTES.get());

        commandToExecute = COMMAND_TO_EXECUTE.get().trim();
        executeAtZero = EXECUTE_AT_ZERO.get();

        rules = List.copyOf(RULES.get());
        discordUrl = DISCORD_URL.get().trim();
        websiteUrl = WEBSITE_URL.get().trim();

        maintenanceEnabled = MAINTENANCE_ENABLED.get();
        maintenanceMessage = MAINTENANCE_MESSAGE.get().trim();
        commandAliases = parseCommandAliases(COMMAND_ALIASES.get());

        ftbChunksUnloadInactiveEnabled = FTB_CHUNKS_UNLOAD_INACTIVE_ENABLED.get();
        ftbChunksInactiveDays = FTB_CHUNKS_INACTIVE_DAYS.get();
        ftbChunksCheckIntervalMinutes = FTB_CHUNKS_CHECK_INTERVAL_MINUTES.get();
    }

    private static void logChanges(String reason) {
        LOGGER.info(
                "[Server Helper Mod] Config {}: enableMessages={}, restartTimes={}, warnMinutes={}, commandToExecute={}, executeAtZero={}, rules={}, discordUrl={}, websiteUrl={}, maintenanceEnabled={}, maintenanceMessage={}, commandAliases={}, ftbChunksUnloadInactiveEnabled={}, ftbChunksInactiveDays={}, ftbChunksCheckIntervalMinutes={}",
                reason,
                enableMessages,
                restartTimes,
                warnMinutes,
                commandToExecute,
                executeAtZero,
                rules,
                discordUrl,
                websiteUrl,
                maintenanceEnabled,
                maintenanceMessage,
                commandAliases,
                ftbChunksUnloadInactiveEnabled,
                ftbChunksInactiveDays,
                ftbChunksCheckIntervalMinutes
        );
    }

    private static Map<String, String> parseCommandAliases(List<? extends String> entries) {
        Map<String, String> aliases = new HashMap<>();

        for (String entry : entries) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) continue;

            String alias = parts[0].trim().toLowerCase();
            String target = parts[1].trim();
            if (!alias.matches("[a-z0-9_]{1,32}") || target.isBlank()) {
                LOGGER.warn("[Server Helper Mod] Ignoring invalid command alias: {}", entry);
                continue;
            }

            if (target.startsWith("/")) target = target.substring(1);
            aliases.put(alias, target);
        }

        return Map.copyOf(aliases);
    }

    private static void resetSchedulerIfRunning() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            RestartScheduler.resetSchedule(server);
            FtbChunksForceLoadManager.resetSchedule();
        }
    }

    public static ReloadResult reloadFromDisk(String reason) {
        ModConfig modConfig = ConfigTracker.INSTANCE.fileMap().get(Server_helper_mod.MOD_ID + "-common.toml");
        boolean loadedFromDisk = false;
        String configPath = "unknown";

        try {
            if (modConfig != null && modConfig.getConfigData() != null) {
                CommentedConfig configData = modConfig.getConfigData();

                if (configData instanceof CommentedFileConfig fileConfig) {
                    configPath = fileConfig.getNioPath().toString();
                    fileConfig.load();
                    if (!SPEC.isCorrect(fileConfig)) {
                        ConfigFileTypeHandler.backUpConfig(fileConfig);
                    }
                    SPEC.acceptConfig(fileConfig);
                    loadedFromDisk = true;
                } else {
                    SPEC.acceptConfig(configData);
                }
            }

            bake();
            resetSchedulerIfRunning();
            logChanges(reason);
            return new ReloadResult(loadedFromDisk, configPath);
        } catch (Exception e) {
            LOGGER.error("[Server Helper Mod] Failed to reload config from disk", e);
            throw new IllegalStateException("Failed to reload Server Helper config", e);
        }
    }

    public static void reloadForCommonConfig(String reason) {
        bake();
        resetSchedulerIfRunning();
        logChanges(reason);
    }

    public static void setMaintenanceEnabled(boolean enabled) {
        MAINTENANCE_ENABLED.set(enabled);
        MAINTENANCE_ENABLED.save();
        bake();
        logChanges("maintenance updated");
    }

    public record ReloadResult(boolean loadedFromDisk, String configPath) {}

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
