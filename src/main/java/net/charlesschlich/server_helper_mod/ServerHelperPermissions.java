package net.charlesschlich.server_helper_mod;

import net.minecraft.network.chat.Component;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class ServerHelperPermissions {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static final PermissionNode<Boolean> STAFF = new PermissionNode<>(
            Server_helper_mod.MOD_ID,
            "staff",
            PermissionTypes.BOOLEAN,
            (player, playerUUID, context) -> player != null && isOperator(player)
    ).setInformation(
            Component.literal("Server Helper Staff"),
            Component.literal("Marks a player as server staff for /staff, /seen, and /vanish.")
    );

    @SubscribeEvent
    public static void onPermissionNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(STAFF);
    }

    public static boolean isStaff(ServerPlayer player) {
        try {
            return PermissionAPI.getPermission(player, STAFF);
        } catch (Exception ignored) {
            return isOperator(player);
        }
    }

    public static boolean canUseStaffTools(CommandSourceStack src) {
        if (src.hasPermission(2)) return true;
        return src.getEntity() instanceof ServerPlayer player && isStaff(player);
    }

    private static boolean isOperator(ServerPlayer player) {
        return player.getServer() != null && player.getServer().getPlayerList().isOp(player.getGameProfile());
    }
}
