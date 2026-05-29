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
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class BanItemEnforcer {
    private static final int MAX_CHUNK_PURGES_PER_TICK = 2;
    private static final Set<LevelChunk> LOADED_CHUNKS = ConcurrentHashMap.newKeySet();
    private static final Set<LevelChunk> PENDING_CHUNK_PURGES = ConcurrentHashMap.newKeySet();

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
        LOADED_CHUNKS.add(chunk);
        if (BanItemManager.hasBans()) {
            PENDING_CHUNK_PURGES.add(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getChunk() instanceof LevelChunk chunk) {
            LOADED_CHUNKS.remove(chunk);
            PENDING_CHUNK_PURGES.remove(chunk);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!BanItemManager.hasBans()) {
            PENDING_CHUNK_PURGES.clear();
            return;
        }

        int purgedChunks = 0;
        Iterator<LevelChunk> iterator = PENDING_CHUNK_PURGES.iterator();
        while (iterator.hasNext() && purgedChunks < MAX_CHUNK_PURGES_PER_TICK && event.haveTime()) {
            LevelChunk chunk = iterator.next();
            iterator.remove();

            if (chunk.getLevel().isClientSide()) continue;
            if (!LOADED_CHUNKS.contains(chunk)) continue;

            purgeChunkContainers(chunk);
            purgedChunks++;
        }
    }

    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        int removed = purgeOpenContainer(player);
        if (removed > 0) {
            player.containerMenu.broadcastChanges();
            player.sendSystemMessage(
                    Component.literal("Removed " + removed + " banned item stack(s).")
                            .withStyle(ChatFormatting.RED)
            );
        }
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

    public static SweepStats sweepServer(MinecraftServer server) {
        if (server == null) return new SweepStats(0, 0, 0, 0);

        int playersSwept = 0;
        int playerStacksRemoved = 0;
        int chunksSwept = 0;
        int containerStacksRemoved = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            playersSwept++;
            int removedInventory = purgePlayerInventory(player);
            int removedEnder = purgeEnderChest(player);
            int removedOpenMenu = purgeOpenContainer(player);
            int total = removedInventory + removedEnder + removedOpenMenu;
            playerStacksRemoved += total;

            if (total > 0) {
                player.inventoryMenu.broadcastChanges();
                player.containerMenu.broadcastChanges();
                player.sendSystemMessage(
                        Component.literal("Removed " + total + " banned item stack(s) during server sweep.")
                                .withStyle(ChatFormatting.RED)
                );
            }
        }

        for (LevelChunk chunk : LOADED_CHUNKS) {
            if (chunk.getLevel().isClientSide()) continue;

            chunksSwept++;
            containerStacksRemoved += purgeChunkContainers(chunk);
        }

        return new SweepStats(playersSwept, playerStacksRemoved, chunksSwept, containerStacksRemoved);
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

    private static int purgeChunkContainers(LevelChunk chunk) {
        int removedStacks = 0;

        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            if (!(blockEntity instanceof Container container)) continue;
            if (hasPendingLootTable(blockEntity)) continue;

            boolean automationContainer = isAutomationContainer(blockEntity);
            int removed = purgeContainer(container, automationContainer);
            removedStacks += removed;

            if (removed > 0) {
                blockEntity.setChanged();
            }
        }

        return removedStacks;
    }

    private static boolean hasPendingLootTable(BlockEntity blockEntity) {
        return blockEntity instanceof RandomizableContainerBlockEntity
                && blockEntity.saveWithoutMetadata().contains("LootTable", 8);
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

    public record SweepStats(
            int playersSwept,
            int playerStacksRemoved,
            int chunksSwept,
            int containerStacksRemoved
    ) {
        public int totalStacksRemoved() {
            return playerStacksRemoved + containerStacksRemoved;
        }
    }
}
