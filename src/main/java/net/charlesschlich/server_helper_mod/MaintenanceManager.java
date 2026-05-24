package net.charlesschlich.server_helper_mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class MaintenanceManager {

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!Config.maintenanceEnabled) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (isOperator(player)) return;

        String message = Config.maintenanceMessage == null || Config.maintenanceMessage.isBlank()
                ? "The server is currently under maintenance. Please try again later."
                : Config.maintenanceMessage;

        player.connection.disconnect(Component.literal(message).withStyle(ChatFormatting.RED));
    }

    private static boolean isOperator(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return server != null && server.getPlayerList().isOp(player.getGameProfile());
    }
}
