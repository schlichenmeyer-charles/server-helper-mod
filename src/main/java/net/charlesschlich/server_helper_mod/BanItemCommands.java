package net.charlesschlich.server_helper_mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class BanItemCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("banitems")
                        .then(Commands.literal("list")
                                .executes(ctx -> listBannedItems(ctx.getSource()))
                        )
                        .then(Commands.literal("reload")
                                .requires(src -> src.hasPermission(2))
                                .executes(ctx -> {
                                    BanItemManager.load();
                                    BanItemEnforcer.sweepServer(ctx.getSource().getServer());
                                    ctx.getSource().sendSuccess(
                                            () -> Component.literal("Reloaded banned items and swept loaded inventories/containers.")
                                                    .withStyle(ChatFormatting.GREEN),
                                            false
                                    );
                                    return 1;
                                })
                        )
                        .then(Commands.literal("hardban")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("hand")
                                        .executes(ctx -> banHeldItem(ctx.getSource(), BanType.HARD))
                                )
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestItems(builder))
                                        .executes(ctx -> banItem(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "item"),
                                                BanType.HARD
                                        ))
                                )
                        )
                        .then(Commands.literal("softban")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("hand")
                                        .executes(ctx -> banHeldItem(ctx.getSource(), BanType.SOFT))
                                )
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestItems(builder))
                                        .executes(ctx -> banItem(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "item"),
                                                BanType.SOFT
                                        ))
                                )
                        )
                        .then(Commands.literal("unban")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.literal("hand")
                                        .executes(ctx -> unbanHeldItem(ctx.getSource()))
                                )
                                .then(Commands.argument("item", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestItems(builder))
                                        .executes(ctx -> unbanItem(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "item")
                                        ))
                                )
                        )
        );
    }

    private static int banHeldItem(CommandSourceStack src, BanType type) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only a player can use /banitems <type> hand.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("You are not holding an item.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (id == null) {
            src.sendFailure(Component.literal("Could not resolve held item id.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        BanItemManager.setBan(id, type);
        BanItemEnforcer.sweepServer(src.getServer());

        src.sendSuccess(
                () -> Component.literal("Set " + id + " to " + type.name().toLowerCase() + " ban.")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int unbanHeldItem(CommandSourceStack src) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only a player can use /banitems unban hand.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("You are not holding an item.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(held.getItem());
        if (id == null) {
            src.sendFailure(Component.literal("Could not resolve held item id.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean removed = BanItemManager.removeBan(id);
        if (!removed) {
            src.sendFailure(Component.literal("That item is not currently banned: " + id)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        src.sendSuccess(
                () -> Component.literal("Unbanned item: " + id)
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int banItem(CommandSourceStack src, String rawId, BanType type) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            src.sendFailure(Component.literal("Invalid item id: " + rawId).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!ForgeRegistries.ITEMS.containsKey(id)) {
            src.sendFailure(Component.literal("Unknown item: " + rawId).withStyle(ChatFormatting.RED));
            return 0;
        }

        BanItemManager.setBan(id, type);
        BanItemEnforcer.sweepServer(src.getServer());

        src.sendSuccess(
                () -> Component.literal("Set " + id + " to " + type.name().toLowerCase() + " ban.")
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int unbanItem(CommandSourceStack src, String rawId) {
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            src.sendFailure(Component.literal("Invalid item id: " + rawId).withStyle(ChatFormatting.RED));
            return 0;
        }

        boolean removed = BanItemManager.removeBan(id);
        if (!removed) {
            src.sendFailure(Component.literal("That item is not currently banned: " + id)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        src.sendSuccess(
                () -> Component.literal("Unbanned item: " + id)
                        .withStyle(ChatFormatting.GREEN),
                true
        );
        return 1;
    }

    private static int listBannedItems(CommandSourceStack src) {
        Map<ResourceLocation, BanType> bans = BanItemManager.getAllBans();

        if (bans.isEmpty()) {
            src.sendSuccess(
                    () -> Component.literal("No items are currently banned.")
                            .withStyle(ChatFormatting.YELLOW),
                    false
            );
            return 1;
        }

        src.sendSuccess(
                () -> Component.literal("Banned items:")
                        .withStyle(ChatFormatting.GOLD),
                false
        );

        bans.forEach((id, type) -> src.sendSuccess(
                () -> Component.literal("- " + id + " (" + type.name().toLowerCase() + ")")
                        .withStyle(type == BanType.HARD ? ChatFormatting.RED : ChatFormatting.AQUA),
                false
        ));

        return bans.size();
    }

    private static CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestItems(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();

        for (ResourceLocation id : ForgeRegistries.ITEMS.getKeys()) {
            String s = id.toString();
            if (s.startsWith(remaining)) {
                builder.suggest(s);
            }
        }

        return builder.buildFuture();
    }
}