package net.charlesschlich.server_helper_mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class SeenManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FMLPaths.CONFIGDIR.get().resolve("server_helper_mod_seen_players.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, SeenRecord>>() {}.getType();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
    private static final Map<String, SeenRecord> SEEN_PLAYERS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        load();
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("seen")
                .requires(ServerHelperPermissions::canUseStaffTools)
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(getKnownNames(ctx.getSource().getServer()), builder))
                        .executes(ctx -> showSeen(ctx.getSource(), StringArgumentType.getString(ctx, "player")))));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        loadIfEmpty();
        SeenRecord record = SeenRecord.fromPlayer(player);
        record.online = true;
        record.lastLoginMillis = System.currentTimeMillis();
        SEEN_PLAYERS.put(key(player.getGameProfile().getName()), record);
        save();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        loadIfEmpty();
        SeenRecord record = SeenRecord.fromPlayer(player);
        record.online = false;
        record.lastLogoutMillis = System.currentTimeMillis();
        SeenRecord previous = SEEN_PLAYERS.get(key(player.getGameProfile().getName()));
        if (previous != null) {
            record.lastLoginMillis = previous.lastLoginMillis;
        }
        SEEN_PLAYERS.put(key(player.getGameProfile().getName()), record);
        save();
    }

    public static void load() {
        SEEN_PLAYERS.clear();

        try {
            if (!Files.exists(FILE_PATH)) {
                save();
                LOGGER.info("[Server Helper Mod] Created seen player file at {}", FILE_PATH);
                return;
            }

            try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
                Map<String, SeenRecord> records = GSON.fromJson(reader, MAP_TYPE);
                if (records != null) {
                    SEEN_PLAYERS.putAll(records);
                }
            }

            LOGGER.info("[Server Helper Mod] Loaded {} seen player entries", SEEN_PLAYERS.size());
        } catch (Exception e) {
            LOGGER.error("[Server Helper Mod] Failed to load seen player data", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE_PATH.getParent());

            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(SEEN_PLAYERS, writer);
            }
        } catch (Exception e) {
            LOGGER.error("[Server Helper Mod] Failed to save seen player data", e);
        }
    }

    public static Collection<String> getKnownNames(MinecraftServer server) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(SEEN_PLAYERS.values().stream().map(record -> record.name).toList());
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                names.add(player.getGameProfile().getName());
            }
        }
        return names;
    }

    private static int showSeen(CommandSourceStack src, String playerName) {
        MinecraftServer server = src.getServer();
        ServerPlayer onlinePlayer = server.getPlayerList().getPlayerByName(playerName);
        if (onlinePlayer != null) {
            sendOnlineSeen(src, onlinePlayer);
            return 1;
        }

        loadIfEmpty();
        SeenRecord record = SEEN_PLAYERS.get(key(playerName));
        if (record == null) {
            src.sendFailure(Component.literal("No seen data found for " + playerName + ".")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        src.sendSuccess(() -> Component.literal(record.name + " was last seen ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(formatTime(record.lastLogoutMillis)).withStyle(ChatFormatting.GOLD)), false);

        src.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Last location: %s at %.1f, %.1f, %.1f",
                record.dimension,
                record.x,
                record.y,
                record.z
        )).withStyle(ChatFormatting.AQUA), false);

        if (record.lastLoginMillis > 0L) {
            src.sendSuccess(() -> Component.literal("Last login: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(formatTime(record.lastLoginMillis)).withStyle(ChatFormatting.GOLD)), false);
        }

        return 1;
    }

    private static void sendOnlineSeen(CommandSourceStack src, ServerPlayer player) {
        boolean afk = AfkManager.isAfk(player);
        boolean vanished = StaffVanishManager.isVanished(player);

        src.sendSuccess(() -> Component.literal(player.getGameProfile().getName() + " is online now.")
                .withStyle(ChatFormatting.GREEN), false);

        src.sendSuccess(() -> Component.literal(String.format(
                Locale.ROOT,
                "Location: %s at %.1f, %.1f, %.1f",
                player.level().dimension().location(),
                player.getX(),
                player.getY(),
                player.getZ()
        )).withStyle(ChatFormatting.AQUA), false);

        src.sendSuccess(() -> Component.literal("Status: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(afk ? "AFK" : "active")
                        .withStyle(afk ? ChatFormatting.YELLOW : ChatFormatting.GREEN))
                .append(Component.literal(vanished ? ", vanished" : "")), false);
    }

    private static void loadIfEmpty() {
        if (SEEN_PLAYERS.isEmpty()) {
            load();
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private static String formatTime(long epochMillis) {
        if (epochMillis <= 0L) return "unknown";
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT);
    }

    private static class SeenRecord {
        String uuid;
        String name;
        boolean online;
        long lastLoginMillis;
        long lastLogoutMillis;
        String dimension;
        double x;
        double y;
        double z;

        static SeenRecord fromPlayer(ServerPlayer player) {
            SeenRecord record = new SeenRecord();
            UUID playerId = player.getUUID();
            record.uuid = playerId.toString();
            record.name = player.getGameProfile().getName();
            record.dimension = player.level().dimension().location().toString();
            record.x = player.getX();
            record.y = player.getY();
            record.z = player.getZ();
            return record;
        }
    }
}
