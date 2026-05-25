package net.charlesschlich.server_helper_mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class AfkManager {
    private static final long AUTO_AFK_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final double MOVEMENT_EPSILON_SQUARED = 0.0001D;
    private static final Set<UUID> AFK_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_ACTIVITY = new ConcurrentHashMap<>();
    private static final Map<UUID, PositionSnapshot> LAST_POSITIONS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("afk")
                .executes(ctx -> toggleAfk(ctx.getSource())));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            recordActivity(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        AFK_PLAYERS.remove(playerId);
        LAST_ACTIVITY.remove(playerId);
        LAST_POSITIONS.remove(playerId);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // Run once per second.
        if (player.tickCount % 20 != 0) return;

        UUID playerId = player.getUUID();
        PositionSnapshot currentPosition = PositionSnapshot.of(player);
        PositionSnapshot previousPosition = LAST_POSITIONS.put(playerId, currentPosition);
        long now = System.currentTimeMillis();

        if (previousPosition == null) {
            LAST_ACTIVITY.putIfAbsent(playerId, now);
            return;
        }

        if (currentPosition.hasMovedFrom(previousPosition)) {
            markActive(player);
            return;
        }

        long lastActivity = LAST_ACTIVITY.getOrDefault(playerId, now);
        if (!isAfk(player) && now - lastActivity >= AUTO_AFK_MILLIS) {
            setAfk(player, true, true);
        }
    }

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        markActive(event.getPlayer());
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            markActive(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            markActive(player);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getLevel().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            markActive(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!AFK_PLAYERS.contains(event.getEntity().getUUID())) return;

        Component currentName = event.getDisplayName();
        MutableComponent baseName = currentName == null
                ? event.getEntity().getDisplayName().copy()
                : currentName.copy();

        event.setDisplayName(Component.literal("[AFK] ")
                .withStyle(ChatFormatting.GRAY)
                .append(baseName));
    }

    public static boolean isAfk(ServerPlayer player) {
        return AFK_PLAYERS.contains(player.getUUID());
    }

    private static int toggleAfk(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        setAfk(player, !isAfk(player), true);
        return 1;
    }

    private static void markActive(ServerPlayer player) {
        recordActivity(player);
        if (isAfk(player)) {
            setAfk(player, false, true);
        }
    }

    private static void setAfk(ServerPlayer player, boolean afk, boolean announce) {
        UUID playerId = player.getUUID();
        boolean changed = afk ? AFK_PLAYERS.add(playerId) : AFK_PLAYERS.remove(playerId);
        if (!changed) return;

        if (afk) {
            LAST_POSITIONS.put(playerId, PositionSnapshot.of(player));
        } else {
            recordActivity(player);
        }

        player.refreshTabListName();

        if (announce) {
            broadcastAfkStatus(player, afk);
        }
    }

    private static void recordActivity(ServerPlayer player) {
        UUID playerId = player.getUUID();
        LAST_ACTIVITY.put(playerId, System.currentTimeMillis());
        LAST_POSITIONS.put(playerId, PositionSnapshot.of(player));
    }

    private static void broadcastAfkStatus(ServerPlayer player, boolean afk) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        MutableComponent message = Component.empty()
                .append(player.getDisplayName().copy())
                .append(Component.literal(afk ? " is now AFK." : " is no longer AFK.")
                        .withStyle(ChatFormatting.YELLOW));

        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    private record PositionSnapshot(String dimension, double x, double y, double z) {
        static PositionSnapshot of(ServerPlayer player) {
            return new PositionSnapshot(
                    player.level().dimension().location().toString(),
                    player.getX(),
                    player.getY(),
                    player.getZ()
            );
        }

        boolean hasMovedFrom(PositionSnapshot previous) {
            if (!dimension.equals(previous.dimension)) return true;

            double dx = x - previous.x;
            double dy = y - previous.y;
            double dz = z - previous.z;
            return dx * dx + dy * dy + dz * dz > MOVEMENT_EPSILON_SQUARED;
        }
    }
}
