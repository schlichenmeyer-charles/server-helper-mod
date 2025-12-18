package net.charlesschlich.server_helper_mod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Set;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Forge's config APIs
@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // Defined Config values
    private static final ForgeConfigSpec.BooleanValue enable_messages = BUILDER
            .comment("Turn on and off the automatic messages.")
            .define("enable_messages", true);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> RESTART_TIMES = BUILDER
            .comment("Daily restart times (24h HH:mm) in server local time e.g. [\"04:00\",\"16:00\"]")
            .defineListAllowEmpty("restart_times", List.of("04:00"), o -> o instanceof String s && s.matches("^\\d{2}:\\d{2}$"));

    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> WARN_MINUTES = BUILDER
            .comment("Send Warnings when restart is N minutes away.")
            .defineListAllowEmpty("warn_minutes", List.of(30,10,5,1), o -> o instanceof Integer i && i >= 0 && i <= 1440);



    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enableMessages;
    public static List<String> restartTimes;
    public static Set<Integer> warnMinutes;

    private  static void bake() {
        enableMessages = enable_messages.get();
        restartTimes = List.copyOf(RESTART_TIMES.get());
        warnMinutes = new java.util.HashSet<>(WARN_MINUTES.get());
    }
    @SubscribeEvent
    static void onLoad(final ModConfigEvent.Loading event) {
        if(event.getConfig().getSpec() != SPEC) return;
        bake();
    }

    @SubscribeEvent
    static void onReload(final ModConfigEvent.Reloading event) {
        if(event.getConfig().getSpec() != SPEC) return;
        bake();
    }
}
