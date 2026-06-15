package net.charlesschlich.server_helper_mod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class StaffItemCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerOrReplaceLiteral(
                dispatcher,
                Commands.literal("enchant")
                        .requires(ServerHelperPermissions::canUseStaffTools)
                        .then(Commands.argument("enchantment", StringArgumentType.word())
                                .suggests((ctx, builder) -> suggestEnchantments(builder))
                                .executes(ctx -> enchantHeldItem(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "enchantment")
                                )))
        );

        registerOrReplaceLiteral(
                dispatcher,
                Commands.literal("repair")
                        .requires(ServerHelperPermissions::canUseStaffTools)
                        .executes(ctx -> repairHeldItem(ctx.getSource()))
        );
    }

    private static int enchantHeldItem(CommandSourceStack src, String rawEnchantmentId) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only a player can use /enchant.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("You are not holding an item.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ResourceLocation id = ResourceLocation.tryParse(rawEnchantmentId);
        if (id == null || !ForgeRegistries.ENCHANTMENTS.containsKey(id)) {
            src.sendFailure(Component.literal("Unknown enchantment: " + rawEnchantmentId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
        if (enchantment == null) {
            src.sendFailure(Component.literal("Unknown enchantment: " + rawEnchantmentId)
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!enchantment.canEnchant(held)) {
            src.sendFailure(Component.literal("That enchantment cannot be applied to ")
                    .withStyle(ChatFormatting.RED)
                    .append(held.getHoverName()));
            return 0;
        }

        int level = enchantment.getMaxLevel();
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(held);
        for (Enchantment existing : enchantments.keySet()) {
            if (existing != enchantment && !existing.isCompatibleWith(enchantment)) {
                src.sendFailure(Component.literal("That enchantment conflicts with an existing enchantment on ")
                        .withStyle(ChatFormatting.RED)
                        .append(held.getHoverName()));
                return 0;
            }
        }

        enchantments.put(enchantment, level);
        EnchantmentHelper.setEnchantments(enchantments, held);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        src.sendSuccess(
                () -> Component.literal("Enchanted ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(held.getHoverName())
                        .append(Component.literal(" with " + id + " " + level + ".")),
                true
        );
        return 1;
    }

    private static int repairHeldItem(CommandSourceStack src) {
        ServerPlayer player;
        try {
            player = src.getPlayerOrException();
        } catch (Exception e) {
            src.sendFailure(Component.literal("Only a player can use /repair.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        ItemStack held = player.getMainHandItem();
        if (held.isEmpty()) {
            src.sendFailure(Component.literal("You are not holding an item.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!held.isDamageableItem()) {
            src.sendFailure(Component.literal("That item cannot be repaired.")
                    .withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!held.isDamaged()) {
            src.sendSuccess(() -> Component.literal("That item is already fully repaired.")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 1;
        }

        held.setDamageValue(0);
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();

        src.sendSuccess(
                () -> Component.literal("Repaired ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(held.getHoverName())
                        .append(Component.literal(".")),
                true
        );
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestEnchantments(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (ResourceLocation id : ForgeRegistries.ENCHANTMENTS.getKeys()) {
            String value = id.toString();
            if (value.startsWith(remaining)) {
                builder.suggest(value);
            }
        }

        return builder.buildFuture();
    }

    private static void registerOrReplaceLiteral(
            CommandDispatcher<CommandSourceStack> dispatcher,
            LiteralArgumentBuilder<CommandSourceStack> builder
    ) {
        String name = builder.getLiteral();
        CommandNode<CommandSourceStack> existing = dispatcher.getRoot().getChild(name);
        if (!(existing instanceof LiteralCommandNode<CommandSourceStack> existingLiteral)) {
            dispatcher.register(builder);
            return;
        }

        LiteralCommandNode<CommandSourceStack> replacement = builder.build();
        for (CommandNode<CommandSourceStack> child : existingLiteral.getChildren()) {
            replacement.addChild(child);
        }

        if (!replaceRootLiteral(dispatcher, name, replacement)) {
            LOGGER.warn("[Server Helper Mod] Could not replace existing /{} command root; staff-only access may depend on the existing command permissions.", name);
            dispatcher.register(builder);
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean replaceRootLiteral(
            CommandDispatcher<CommandSourceStack> dispatcher,
            String name,
            LiteralCommandNode<CommandSourceStack> replacement
    ) {
        try {
            CommandNode<CommandSourceStack> root = dispatcher.getRoot();
            Field childrenField = CommandNode.class.getDeclaredField("children");
            Field literalsField = CommandNode.class.getDeclaredField("literals");
            childrenField.setAccessible(true);
            literalsField.setAccessible(true);

            Map<String, CommandNode<CommandSourceStack>> children =
                    (Map<String, CommandNode<CommandSourceStack>>) childrenField.get(root);
            Map<String, LiteralCommandNode<CommandSourceStack>> literals =
                    (Map<String, LiteralCommandNode<CommandSourceStack>>) literalsField.get(root);

            children.put(name, replacement);
            literals.put(name, replacement);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("[Server Helper Mod] Failed to replace /{} command root", name, e);
            return false;
        }
    }
}
