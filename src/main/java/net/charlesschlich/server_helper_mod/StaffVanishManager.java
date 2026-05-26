package net.charlesschlich.server_helper_mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class StaffVanishManager {
    private static final Set<UUID> VANISHED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, MobEffectInstance> PREVIOUS_INVISIBILITY = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("staff")
                .executes(ctx -> showStaff(ctx.getSource())));

        dispatcher.register(Commands.literal("vanish")
                .requires(ServerHelperPermissions::canUseStaffTools)
                .executes(ctx -> toggleVanish(ctx.getSource())));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        hideVanishedPlayersFrom(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            clearVanishState(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (isVanished(player)) {
                restoreInvisibility(player);
            }
        }

        VANISHED_PLAYERS.clear();
        PREVIOUS_INVISIBILITY.clear();
    }

    public static boolean isVanished(ServerPlayer player) {
        return VANISHED_PLAYERS.contains(player.getUUID());
    }

    private static int showStaff(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        boolean callerCanSeeVanished = ServerHelperPermissions.canUseStaffTools(src);
        MutableComponent message = Component.literal("Online staff: ")
                .withStyle(ChatFormatting.GRAY);

        int shown = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!ServerHelperPermissions.isStaff(player)) continue;
            boolean vanished = isVanished(player);
            if (vanished && !callerCanSeeVanished) continue;

            if (shown > 0) {
                message.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }

            MutableComponent name = Component.literal(player.getGameProfile().getName())
                    .withStyle(ChatFormatting.GREEN);
            if (AfkManager.isAfk(player)) {
                name.append(Component.literal(" [AFK]").withStyle(ChatFormatting.YELLOW));
            }
            if (vanished) {
                name.append(Component.literal(" [VANISHED]").withStyle(ChatFormatting.DARK_GRAY));
            }

            message.append(name);
            shown++;
        }

        if (shown == 0) {
            src.sendSuccess(() -> Component.literal("No staff are currently online.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        src.sendSuccess(() -> message, false);
        return shown;
    }

    private static int toggleVanish(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        setVanished(player, !isVanished(player));
        return 1;
    }

    private static void setVanished(ServerPlayer player, boolean vanished) {
        UUID playerId = player.getUUID();
        boolean changed = vanished ? VANISHED_PLAYERS.add(playerId) : VANISHED_PLAYERS.remove(playerId);
        if (!changed) return;

        if (vanished) {
            rememberAndApplyInvisibility(player);
            hidePlayerFromNonOperators(player);
        } else {
            restoreInvisibility(player);
            showPlayerToNonOperators(player);
        }

        player.sendSystemMessage(Component.literal(vanished ? "Vanish enabled." : "Vanish disabled.")
                .withStyle(vanished ? ChatFormatting.GRAY : ChatFormatting.GREEN));
        notifyOperators(player, vanished);
    }

    private static void rememberAndApplyInvisibility(ServerPlayer player) {
        MobEffectInstance current = player.getEffect(MobEffects.INVISIBILITY);
        if (current != null) {
            PREVIOUS_INVISIBILITY.put(player.getUUID(), new MobEffectInstance(current));
        }

        player.addEffect(new MobEffectInstance(
                MobEffects.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));
    }

    private static void restoreInvisibility(ServerPlayer player) {
        player.removeEffect(MobEffects.INVISIBILITY);

        MobEffectInstance previous = PREVIOUS_INVISIBILITY.remove(player.getUUID());
        if (previous != null) {
            player.addEffect(previous);
        }
    }

    private static void hidePlayerFromNonOperators(ServerPlayer vanishedPlayer) {
        ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(vanishedPlayer.getUUID()));
        for (ServerPlayer viewer : vanishedPlayer.server.getPlayerList().getPlayers()) {
            if (viewer == vanishedPlayer || isOperator(viewer)) continue;
            viewer.connection.send(packet);
        }
    }

    private static void showPlayerToNonOperators(ServerPlayer visiblePlayer) {
        ClientboundPlayerInfoUpdatePacket packet = ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(visiblePlayer));
        for (ServerPlayer viewer : visiblePlayer.server.getPlayerList().getPlayers()) {
            if (viewer == visiblePlayer || isOperator(viewer)) continue;
            viewer.connection.send(packet);
        }
    }

    private static void hideVanishedPlayersFrom(ServerPlayer viewer) {
        if (isOperator(viewer) || VANISHED_PLAYERS.isEmpty()) return;

        viewer.connection.send(new ClientboundPlayerInfoRemovePacket(List.copyOf(VANISHED_PLAYERS)));
    }

    private static void clearVanishState(ServerPlayer player) {
        if (VANISHED_PLAYERS.remove(player.getUUID())) {
            restoreInvisibility(player);
        } else {
            PREVIOUS_INVISIBILITY.remove(player.getUUID());
        }
    }

    private static void notifyOperators(ServerPlayer vanishedPlayer, boolean vanished) {
        MinecraftServer server = vanishedPlayer.getServer();
        if (server == null) return;

        Component message = Component.literal(vanishedPlayer.getGameProfile().getName()
                        + (vanished ? " vanished." : " is visible again."))
                .withStyle(ChatFormatting.DARK_GRAY);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == vanishedPlayer || !isOperator(player)) continue;
            player.sendSystemMessage(message);
        }
    }

    private static boolean isOperator(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && (server.getPlayerList().isOp(player.getGameProfile())
                || ServerHelperPermissions.isStaff(player));
    }
}
