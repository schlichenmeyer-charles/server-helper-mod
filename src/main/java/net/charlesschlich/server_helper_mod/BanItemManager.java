package net.charlesschlich.server_helper_mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class BanItemManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FMLPaths.CONFIGDIR.get().resolve("server_helper_mod_banned_items.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private static final Map<ResourceLocation, BanType> BANNED_ITEMS = new ConcurrentHashMap<>();

    public static void load() {
        BANNED_ITEMS.clear();

        try {
            if (!Files.exists(FILE_PATH)) {
                save();
                LOGGER.info("[Server Helper Mod] Created banned items file at {}", FILE_PATH);
                return;
            }

            try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
                Map<String, String> raw = GSON.fromJson(reader, MAP_TYPE);
                if (raw == null) return;

                for (Map.Entry<String, String> entry : raw.entrySet()) {
                    ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                    if (id == null) {
                        LOGGER.warn("[Server Helper Mod] Invalid banned item id: {}", entry.getKey());
                        continue;
                    }

                    if (!ForgeRegistries.ITEMS.containsKey(id)) {
                        LOGGER.warn("[Server Helper Mod] Unknown item in ban file: {}", id);
                        continue;
                    }

                    try {
                        BanType type = BanType.valueOf(entry.getValue().toUpperCase());
                        BANNED_ITEMS.put(id, type);
                    } catch (IllegalArgumentException ex) {
                        LOGGER.warn("[Server Helper Mod] Invalid ban type for {}: {}", id, entry.getValue());
                    }
                }
            }

            LOGGER.info("[Server Helper Mod] Loaded {} banned item entries", BANNED_ITEMS.size());
        } catch (Exception e) {
            LOGGER.error("[Server Helper Mod] Failed to load banned items", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(FILE_PATH.getParent());

            Map<String, String> raw = new TreeMap<>();
            for (Map.Entry<ResourceLocation, BanType> entry : BANNED_ITEMS.entrySet()) {
                raw.put(entry.getKey().toString(), entry.getValue().name().toLowerCase());
            }

            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(raw, writer);
            }

            LOGGER.info("[Server Helper Mod] Saved {} banned item entries", BANNED_ITEMS.size());
        } catch (Exception e) {
            LOGGER.error("[Server Helper Mod] Failed to save banned items", e);
        }
    }

    public static void setBan(ResourceLocation itemId, BanType type) {
        BANNED_ITEMS.put(itemId, type);
        save();
    }

    public static boolean removeBan(ResourceLocation itemId) {
        BanType removed = BANNED_ITEMS.remove(itemId);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public static BanType getBanType(ResourceLocation itemId) {
        return BANNED_ITEMS.get(itemId);
    }

    public static BanType getBanType(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        return id == null ? null : BANNED_ITEMS.get(id);
    }

    public static boolean isSoftBanned(Item item) {
        BanType type = getBanType(item);
        return type == BanType.SOFT || type == BanType.HARD;
    }

    public static boolean isHardBanned(Item item) {
        return getBanType(item) == BanType.HARD;
    }

    public static Map<ResourceLocation, BanType> getAllBans() {
        return Collections.unmodifiableMap(new TreeMap<>(BANNED_ITEMS));
    }
}