package net.charlesschlich.server_helper_mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class BanItemEvents {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        if (!(item instanceof BlockItem)) return;

        if (BanItemManager.isSoftBanned(item)) {
            event.setCanceled(true);
            event.getEntity().displayClientMessage(
                    Component.literal("That item is banned from being placed.")
                            .withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        Inventory inv = player.getInventory();
        boolean removedAny = false;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            if (!BanItemManager.isHardBanned(item)) continue;

            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            inv.setItem(i, ItemStack.EMPTY);
            removedAny = true;

            if (id != null) {
                player.sendSystemMessage(
                        Component.literal("Removed hard-banned item from inventory: " + id)
                                .withStyle(ChatFormatting.RED)
                );
            }
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && BanItemManager.isHardBanned(carried.getItem())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removedAny = true;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(carried.getItem());
            if (id != null) {
                player.sendSystemMessage(
                        Component.literal("Removed hard-banned item from cursor: " + id)
                                .withStyle(ChatFormatting.RED)
                );
            }
        }

        if (removedAny) {
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
        }
    }
}