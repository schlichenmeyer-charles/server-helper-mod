package net.charlesschlich.server_helper_mod;

import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

@Mod.EventBusSubscriber(modid = Server_helper_mod.MOD_ID)
public class FtbChunksForceLoadManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FTB_CHUNKS_MOD_ID = "ftbchunks";
    private static final String FTB_TEAMS_MOD_ID = "ftbteams";
    private static final String FTB_LIBRARY_MOD_ID = "ftblibrary";
    private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();

    private static ReflectionApi api;
    private static boolean reflectionFailed;
    private static boolean availabilityLogged;
    private static long nextCheckAtMillis;

    private FtbChunksForceLoadManager() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        resetSchedule();
        runAutomaticCheck(event.getServer(), "startup");
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;
        if (!Config.ftbChunksUnloadInactiveEnabled) return;

        long now = System.currentTimeMillis();
        if (nextCheckAtMillis > now) return;

        runAutomaticCheck(event.getServer(), "scheduled");
    }

    public static void resetSchedule() {
        nextCheckAtMillis = 0L;
    }

    public static boolean isIntegrationAvailable() {
        return requiredModsLoaded() && getApi() != null;
    }

    public static String availabilityMessage() {
        if (!requiredModsLoaded()) {
            return "FTB Chunks integration is inactive because ftbchunks, ftbteams, and ftblibrary are not all installed.";
        }

        if (getApi() == null) {
            return "FTB Chunks integration is inactive because the expected FTB Chunks API/runtime classes could not be resolved.";
        }

        return "FTB Chunks integration is available.";
    }

    public static CleanupResult unloadInactiveForceLoadedChunks(CommandSourceStack source) {
        if (!Config.ftbChunksUnloadInactiveEnabled) {
            return CleanupResult.inactive("Automatic FTB Chunks cleanup is disabled in Server Helper config.");
        }

        return unloadInactiveForceLoadedChunks(source, System.currentTimeMillis());
    }

    public static CleanupResult unloadAllForceLoadedChunks(CommandSourceStack source) {
        if (!requiredModsLoaded()) {
            return CleanupResult.inactive(availabilityMessage());
        }
        ReflectionApi resolved = getApi();
        if (resolved == null) return CleanupResult.inactive(availabilityMessage());

        Object manager = resolved.managerInstance();
        if (manager == null) {
            return CleanupResult.inactive("FTB Chunks is installed, but its claimed chunk manager is not initialized yet.");
        }

        int forceLoaded = 0;
        int unloaded = 0;
        int failures = 0;

        for (Object chunk : resolved.allClaimedChunksSnapshot(manager)) {
            try {
                if (!resolved.isForceLoaded(chunk)) continue;

                forceLoaded++;
                resolved.unload(chunk, source);
                unloaded++;
            } catch (Exception e) {
                failures++;
                LOGGER.warn("[Server Helper Mod] Failed to unload an FTB force-loaded chunk", unwrap(e));
            }
        }

        return CleanupResult.active(forceLoaded, unloaded, 0, 0, failures);
    }

    public static CleanupResult forceLoadedStatus() {
        if (!requiredModsLoaded()) {
            return CleanupResult.inactive(availabilityMessage());
        }
        ReflectionApi resolved = getApi();
        if (resolved == null) return CleanupResult.inactive(availabilityMessage());

        Object manager = resolved.managerInstance();
        if (manager == null) {
            return CleanupResult.inactive("FTB Chunks is installed, but its claimed chunk manager is not initialized yet.");
        }

        int forceLoaded = 0;
        Set<Object> teams = java.util.Collections.newSetFromMap(new IdentityHashMap<>());

        for (Object chunk : resolved.allClaimedChunksSnapshot(manager)) {
            try {
                if (!resolved.isForceLoaded(chunk)) continue;

                forceLoaded++;
                Object teamData = resolved.getTeamData(chunk);
                if (teamData != null) teams.add(teamData);
            } catch (Exception e) {
                LOGGER.warn("[Server Helper Mod] Failed to inspect an FTB force-loaded chunk", unwrap(e));
            }
        }

        return CleanupResult.active(forceLoaded, 0, 0, teams.size(), 0);
    }

    private static CleanupResult unloadInactiveForceLoadedChunks(CommandSourceStack source, long nowMillis) {
        if (!requiredModsLoaded()) {
            return CleanupResult.inactive(availabilityMessage());
        }
        ReflectionApi resolved = getApi();
        if (resolved == null) return CleanupResult.inactive(availabilityMessage());

        Object manager = resolved.managerInstance();
        if (manager == null) {
            return CleanupResult.inactive("FTB Chunks is installed, but its claimed chunk manager is not initialized yet.");
        }

        long cutoffMillis = nowMillis - (Config.ftbChunksInactiveDays * MILLIS_PER_DAY);
        int forceLoaded = 0;
        int unloaded = 0;
        int failures = 0;
        Set<Object> inactiveTeams = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> activeTeams = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Map<Object, Long> lastLoginByTeam = new IdentityHashMap<>();

        for (Object chunk : resolved.allClaimedChunksSnapshot(manager)) {
            try {
                if (!resolved.isForceLoaded(chunk)) continue;

                forceLoaded++;
                Object teamData = resolved.getTeamData(chunk);
                if (teamData == null) {
                    failures++;
                    continue;
                }

                long lastLoginMillis = lastLoginByTeam.computeIfAbsent(teamData, resolved::lastLoginTime);
                if (lastLoginMillis <= cutoffMillis) {
                    inactiveTeams.add(teamData);
                    resolved.unload(chunk, source);
                    unloaded++;
                } else {
                    activeTeams.add(teamData);
                }
            } catch (Exception e) {
                failures++;
                LOGGER.warn("[Server Helper Mod] Failed to inspect or unload an inactive FTB force-loaded chunk", unwrap(e));
            }
        }

        return CleanupResult.active(forceLoaded, unloaded, inactiveTeams.size(), activeTeams.size(), failures);
    }

    private static void runAutomaticCheck(MinecraftServer server, String reason) {
        long now = System.currentTimeMillis();
        nextCheckAtMillis = now + Duration.ofMinutes(Config.ftbChunksCheckIntervalMinutes).toMillis();

        if (!Config.ftbChunksUnloadInactiveEnabled) return;

        CleanupResult result = unloadInactiveForceLoadedChunks(server.createCommandSourceStack(), now);
        if (!result.active()) {
            if (!availabilityLogged) {
                LOGGER.info("[Server Helper Mod] {}", result.message());
                availabilityLogged = true;
            }
            return;
        }

        if (result.unloadedChunks() > 0 || result.failures() > 0) {
            LOGGER.info(
                    "[Server Helper Mod] FTB Chunks {} cleanup: forceLoaded={}, unloaded={}, inactiveTeams={}, activeTeams={}, failures={}",
                    reason,
                    result.forceLoadedChunks(),
                    result.unloadedChunks(),
                    result.inactiveTeams(),
                    result.activeTeams(),
                    result.failures()
            );
        }
    }

    private static boolean requiredModsLoaded() {
        ModList modList = ModList.get();
        return modList.isLoaded(FTB_CHUNKS_MOD_ID)
                && modList.isLoaded(FTB_TEAMS_MOD_ID)
                && modList.isLoaded(FTB_LIBRARY_MOD_ID);
    }

    private static ReflectionApi getApi() {
        if (api != null) return api;
        if (reflectionFailed) return null;

        try {
            api = ReflectionApi.create();
            return api;
        } catch (Exception e) {
            reflectionFailed = true;
            LOGGER.warn("[Server Helper Mod] Failed to initialize FTB Chunks integration", unwrap(e));
            return null;
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof InvocationTargetException invocationTargetException
                && invocationTargetException.getCause() != null) {
            return invocationTargetException.getCause();
        }
        return throwable;
    }

    public record CleanupResult(
            boolean active,
            String message,
            int forceLoadedChunks,
            int unloadedChunks,
            int inactiveTeams,
            int activeTeams,
            int failures
    ) {
        static CleanupResult inactive(String message) {
            return new CleanupResult(false, message, 0, 0, 0, 0, 0);
        }

        static CleanupResult active(int forceLoadedChunks, int unloadedChunks, int inactiveTeams, int activeTeams, int failures) {
            return new CleanupResult(true, "", forceLoadedChunks, unloadedChunks, inactiveTeams, activeTeams, failures);
        }
    }

    private record ReflectionApi(
            Method getInstance,
            Method getAllClaimedChunks,
            Method isForceLoaded,
            Method getTeamData,
            Method getLastLoginTime,
            Method unload
    ) {
        static ReflectionApi create() throws ReflectiveOperationException {
            Class<?> managerClass = Class.forName("dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl");
            Class<?> claimedChunkClass = Class.forName("dev.ftb.mods.ftbchunks.api.ClaimedChunk");
            Class<?> chunkTeamDataClass = Class.forName("dev.ftb.mods.ftbchunks.api.ChunkTeamData");

            return new ReflectionApi(
                    managerClass.getMethod("getInstance"),
                    managerClass.getMethod("getAllClaimedChunks"),
                    claimedChunkClass.getMethod("isForceLoaded"),
                    claimedChunkClass.getMethod("getTeamData"),
                    chunkTeamDataClass.getMethod("getLastLoginTime"),
                    claimedChunkClass.getMethod("unload", CommandSourceStack.class)
            );
        }

        Object managerInstance() {
            try {
                return getInstance.invoke(null);
            } catch (Exception e) {
                throw new IllegalStateException("Could not get FTB Chunks claimed chunk manager", unwrap(e));
            }
        }

        Collection<?> allClaimedChunksSnapshot(Object manager) {
            try {
                Object chunks = getAllClaimedChunks.invoke(manager);
                if (chunks instanceof Collection<?> collection) {
                    return new ArrayList<>(collection);
                }
                return java.util.List.of();
            } catch (Exception e) {
                throw new IllegalStateException("Could not read FTB claimed chunks", unwrap(e));
            }
        }

        boolean isForceLoaded(Object chunk) {
            try {
                return Boolean.TRUE.equals(isForceLoaded.invoke(chunk));
            } catch (Exception e) {
                throw new IllegalStateException("Could not read FTB force-loaded state", unwrap(e));
            }
        }

        Object getTeamData(Object chunk) {
            try {
                return getTeamData.invoke(chunk);
            } catch (Exception e) {
                throw new IllegalStateException("Could not read FTB chunk team data", unwrap(e));
            }
        }

        long lastLoginTime(Object teamData) {
            try {
                Object value = getLastLoginTime.invoke(teamData);
                if (value instanceof Number number) {
                    return number.longValue();
                }
                return System.currentTimeMillis();
            } catch (Exception e) {
                throw new IllegalStateException("Could not read FTB team last-login time", unwrap(e));
            }
        }

        void unload(Object chunk, CommandSourceStack source) {
            try {
                unload.invoke(chunk, source);
            } catch (Exception e) {
                throw new IllegalStateException("Could not unload FTB force-loaded chunk", unwrap(e));
            }
        }
    }
}
