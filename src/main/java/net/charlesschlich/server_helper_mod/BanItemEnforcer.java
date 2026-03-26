package net.charlesschlich.server_helper_mod;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class BanItemEnforcer {

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        BanType type = BanItemManager.getBanType(item);
        if (type == null) return;

        boolean shouldBlock = type == BanType.HARD || isPlacementRestrictedItem(item);
        if (!shouldBlock) return;

        event.setCanceled(true);

        if (!event.getLevel().isClientSide()) {
            event.getEntity().displayClientMessage(
                    Component.literal(
                            type == BanType.HARD
                                    ? "That item is hard-banned and cannot be used."
                                    : "That item is banned from being placed or deployed."
                    ).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        BanType type = BanItemManager.getBanType(item);
        if (type == null) return;

        boolean shouldBlock = type == BanType.HARD || isPlacementRestrictedItem(item);
        if (!shouldBlock) return;

        event.setCanceled(true);

        if (!event.getLevel().isClientSide()) {
            event.getEntity().displayClientMessage(
                    Component.literal(
                            type == BanType.HARD
                                    ? "That item is hard-banned and cannot be used."
                                    : "That item is banned from being placed or deployed."
                    ).withStyle(ChatFormatting.RED),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;
        purgeChunkContainers(chunk);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player.level().isClientSide()) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // Run once per second
        if (player.tickCount % 20 != 0) return;

        int removedInventory = purgePlayerInventory(player);
        int removedEnder = purgeEnderChest(player);
        int removedOpenMenu = purgeOpenContainer(player);

        int total = removedInventory + removedEnder + removedOpenMenu;
        if (total > 0) {
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            player.sendSystemMessage(
                    Component.literal("Removed " + total + " banned item stack(s).")
                            .withStyle(ChatFormatting.RED)
            );
        }
    }

    public static void sweepServer(MinecraftServer server) {
        if (server == null) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            int removedInventory = purgePlayerInventory(player);
            int removedEnder = purgeEnderChest(player);
            int removedOpenMenu = purgeOpenContainer(player);
            int total = removedInventory + removedEnder + removedOpenMenu;

            if (total > 0) {
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();
                player.sendSystemMessage(
                        Component.literal("Removed " + total + " banned item stack(s) during server sweep.")
                                .withStyle(ChatFormatting.RED)
                );
            }
        }
    }

    private static int purgePlayerInventory(ServerPlayer player) {
        int removed = 0;
        Inventory inv = player.getInventory();
        List<String> removedIds = new ArrayList<>();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;

            if (BanItemManager.isHardBanned(stack.getItem())) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                inv.setItem(i, ItemStack.EMPTY);
                removed++;
                if (id != null) removedIds.add(id.toString());
            }
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && BanItemManager.isHardBanned(carried.getItem())) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(carried.getItem());
            player.containerMenu.setCarried(ItemStack.EMPTY);
            removed++;
            if (id != null) removedIds.add(id.toString());
        }

        if (!removedIds.isEmpty()) {
            player.sendSystemMessage(
                    Component.literal("Removed from inventory/cursor: " + String.join(", ", removedIds))
                            .withStyle(ChatFormatting.RED)
            );
        }

        return removed;
    }

    private static int purgeEnderChest(ServerPlayer player) {
        int removed = 0;
        List<String> removedIds = new ArrayList<>();

        Container ec = player.getEnderChestInventory();
        for (int i = 0; i < ec.getContainerSize(); i++) {
            ItemStack stack = ec.getItem(i);
            if (stack.isEmpty()) continue;

            if (BanItemManager.isHardBanned(stack.getItem())) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                ec.setItem(i, ItemStack.EMPTY);
                removed++;
                if (id != null) removedIds.add(id.toString());
            }
        }

        if (removed > 0) {
            ec.setChanged();
            player.sendSystemMessage(
                    Component.literal("Removed from ender chest: " + String.join(", ", removedIds))
                            .withStyle(ChatFormatting.RED)
            );
        }

        return removed;
    }

    private static int purgeOpenContainer(ServerPlayer player) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu == player.inventoryMenu) return 0;

        int removed = 0;
        List<String> removedIds = new ArrayList<>();

        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory()) continue;
            if (slot.container == player.getEnderChestInventory()) continue;

            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;

            if (BanItemManager.isHardBanned(stack.getItem())) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                slot.set(ItemStack.EMPTY);
                slot.setChanged();
                removed++;
                if (id != null) removedIds.add(id.toString());
            }
        }

        if (removed > 0) {
            player.sendSystemMessage(
                    Component.literal("Removed from open container: " + String.join(", ", removedIds))
                            .withStyle(ChatFormatting.RED)
            );
        }

        return removed;
    }

    private static void purgeChunkContainers(LevelChunk chunk) {
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof Container container)) continue;

            boolean automationContainer = isAutomationContainer(blockEntity);
            int removed = purgeContainer(container, automationContainer);

            if (removed > 0) {
                blockEntity.setChanged();
            }
        }
    }

    private static int purgeContainer(Container container, boolean automationContainer) {
        int removed = 0;

        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;

            Item item = stack.getItem();
            boolean shouldRemove =
                    BanItemManager.isHardBanned(item) ||
                            (automationContainer && BanItemManager.isSoftBanned(item));

            if (shouldRemove) {
                container.setItem(i, ItemStack.EMPTY);
                removed++;
            }
        }

        if (removed > 0) {
            container.setChanged();
        }

        return removed;
    }

    private static boolean isAutomationContainer(BlockEntity blockEntity) {
        return blockEntity instanceof DispenserBlockEntity
                || blockEntity instanceof DropperBlockEntity;
    }

    private static boolean isPlacementRestrictedItem(Item item) {
        return item instanceof BlockItem
                || item instanceof BucketItem
                || item instanceof SpawnEggItem
                || item instanceof BoatItem
                || item instanceof MinecartItem
                || item instanceof HangingEntityItem
                || item instanceof ArmorStandItem
                || item instanceof EndCrystalItem;
    }
}