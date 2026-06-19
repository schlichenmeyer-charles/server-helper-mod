package net.charlesschlich.server_helper_mod;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class AutoMuteChatSpamManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FTB_ESSENTIALS_MOD_ID = "ftbessentials";
    private static final Map<UUID, ChatHistory> CHAT_HISTORY = new ConcurrentHashMap<>();

    private static boolean unavailableLogged;

    private AutoMuteChatSpamManager() {
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        if (!Config.autoMuteChatSpamEnabled) return;

        ServerPlayer player = event.getPlayer();
        MinecraftServer server = player.getServer();
        if (server == null) return;

        if (Config.autoMuteChatSpamExemptStaff && ServerHelperPermissions.isStaff(player)) {
            CHAT_HISTORY.remove(player.getUUID());
            return;
        }

        if (!isIntegrationAvailable(server)) {
            logUnavailableOnce(server);
            return;
        }

        long now = System.currentTimeMillis();
        ChatHistory history = CHAT_HISTORY.computeIfAbsent(player.getUUID(), ignored -> new ChatHistory());
        SpamCheckResult result = history.record(event.getRawText(), now);

        if (!result.spam()) return;

        if (mutePlayer(server, player, result.reason())) {
            event.setCanceled(true);
            CHAT_HISTORY.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CHAT_HISTORY.remove(event.getEntity().getUUID());
    }

    public static boolean isIntegrationAvailable(MinecraftServer server) {
        return ModList.get().isLoaded(FTB_ESSENTIALS_MOD_ID)
                && server.getCommands().getDispatcher().getRoot().getChild("mute") != null;
    }

    public static String availabilityMessage(MinecraftServer server) {
        if (!ModList.get().isLoaded(FTB_ESSENTIALS_MOD_ID)) {
            return "Auto mute chat spam is inactive because FTB Essentials is not installed.";
        }

        if (server.getCommands().getDispatcher().getRoot().getChild("mute") == null) {
            return "Auto mute chat spam is inactive because FTB Essentials' mute command is not registered.";
        }

        return "Auto mute chat spam integration is available.";
    }

    private static void logUnavailableOnce(MinecraftServer server) {
        if (unavailableLogged) return;

        LOGGER.info("[Server Helper Mod] {}", availabilityMessage(server));
        unavailableLogged = true;
    }

    private static boolean mutePlayer(MinecraftServer server, ServerPlayer player, String reason) {
        String duration = Config.autoMuteChatSpamDuration;
        CommandSourceStack source = server.createCommandSourceStack();
        String command = "mute " + player.getGameProfile().getName() + " " + duration;

        try {
            int result = server.getCommands().performPrefixedCommand(source, command);
            if (result > 0) {
                LOGGER.info(
                        "[Server Helper Mod] Auto-muted {} for chat spam (reason={}, duration={}) using FTB Essentials.",
                        player.getGameProfile().getName(),
                        reason,
                        duration
                );
                return true;
            }

            LOGGER.warn(
                    "[Server Helper Mod] FTB Essentials mute command returned no result for automatic chat spam mute: player={}, reason={}, duration={}",
                    player.getGameProfile().getName(),
                    reason,
                    duration
            );
            return false;
        } catch (Exception e) {
            LOGGER.warn(
                    "[Server Helper Mod] Failed to auto-mute {} for chat spam using FTB Essentials.",
                    player.getGameProfile().getName(),
                    e
            );
            return false;
        }
    }

    private record ChatHistory(Deque<MessageRecord> messages) {
        ChatHistory() {
            this(new ArrayDeque<>());
        }

        SpamCheckResult record(String rawMessage, long now) {
            long windowMillis = Config.autoMuteChatSpamWindowSeconds * 1000L;
            long oldestAllowed = now - windowMillis;
            while (!messages.isEmpty() && messages.peekFirst().sentAtMillis() < oldestAllowed) {
                messages.removeFirst();
            }

            String normalizedMessage = normalizeMessage(rawMessage);
            messages.addLast(new MessageRecord(normalizedMessage, now));

            int duplicateCount = 0;
            for (MessageRecord message : messages) {
                if (message.normalizedText().equals(normalizedMessage)) {
                    duplicateCount++;
                }
            }

            if (duplicateCount >= Config.autoMuteChatSpamDuplicateMessages) {
                return SpamCheckResult.spam("duplicate_messages");
            }

            if (messages.size() >= Config.autoMuteChatSpamMaxMessages) {
                return SpamCheckResult.spam("message_rate");
            }

            return SpamCheckResult.notSpam();
        }

        private static String normalizeMessage(String message) {
            return message == null
                    ? ""
                    : message.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        }
    }

    private record MessageRecord(String normalizedText, long sentAtMillis) {
    }

    private record SpamCheckResult(boolean spam, String reason) {
        static SpamCheckResult spam(String reason) {
            return new SpamCheckResult(true, reason);
        }

        static SpamCheckResult notSpam() {
            return new SpamCheckResult(false, "");
        }
    }
}
