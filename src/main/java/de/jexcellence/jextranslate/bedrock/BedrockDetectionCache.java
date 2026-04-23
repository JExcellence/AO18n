package de.jexcellence.jextranslate.bedrock;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages caching of Bedrock player detection results for performance optimization.
 *
 * <p>This class caches the Bedrock status of players to avoid repeated lookups to the
 * GeyserService. The cache is automatically cleaned up when players disconnect.
 *
 * <p>The class uses lazy initialization to look up the GeyserService from RPlatform,
 * gracefully degrading if the service is unavailable.
 *
 * @since 3.1.0
 */
public final class BedrockDetectionCache implements Listener {

    private static final Logger LOGGER = Logger.getLogger(BedrockDetectionCache.class.getName());

    /**
     * Thread-safe cache mapping player UUIDs to their Bedrock status.
     */
    private final Map<UUID, Boolean> cache = new ConcurrentHashMap<>();

    /**
     * Lazily initialized GeyserService instance.
     */
    private Object geyserService;

    /**
     * Method reference for isBedrockPlayer(Player) on GeyserService.
     */
    private Method isBedrockPlayerMethod;

    /**
     * Flag indicating whether GeyserService lookup has been attempted.
     */
    private boolean geyserServiceLookupAttempted = false;

    /**
     * Flag indicating whether GeyserService is available.
     */
    private boolean geyserServiceAvailable = false;

    /**
     * Creates a new BedrockDetectionCache.
     */
    public BedrockDetectionCache() {
        // Lazy initialization - GeyserService will be looked up on first use
    }

    /**
     * Checks if a player is a Bedrock player, using the cache if available.
 *
 * <p>On first check for a player, the result is fetched from GeyserService and cached.
     * Subsequent checks return the cached value.
     *
     * @param player the player to check
     * @return true if the player is a Bedrock player, false otherwise or if detection is unavailable
     */
    public boolean isBedrockPlayer(@Nullable Player player) {
        if (player == null) {
            return false;
        }

        UUID playerId = player.getUniqueId();

        // Check cache first
        Boolean cached = cache.get(playerId);
        if (cached != null) {
            return cached;
        }

        // Lookup and cache the result
        boolean isBedrock = lookupBedrockStatus(player);
        cache.put(playerId, isBedrock);
        return isBedrock;
    }

    /**
     * Checks if a player is a Bedrock player by UUID, using the cache if available.
     *
     * @param playerId the player's UUID
     * @return true if the player is cached as Bedrock, false otherwise
     */
    public boolean isBedrockPlayer(@NotNull UUID playerId) {
        Boolean cached = cache.get(playerId);
        return cached != null && cached;
    }

    /**
     * Clears the cache entry for a specific player.
     *
     * @param playerId the UUID of the player to remove from cache
     */
    public void invalidate(@NotNull UUID playerId) {
        cache.remove(playerId);
    }

    /**
     * Clears all cached entries.
     */
    public void invalidateAll() {
        cache.clear();
    }

    /**
     * Gets the current cache size.
     *
     * @return the number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }

    /**
     * Checks if GeyserService is available for Bedrock detection.
     *
     * @return true if GeyserService is available
     */
    public boolean isGeyserServiceAvailable() {
        ensureGeyserServiceLookup();
        return geyserServiceAvailable;
    }

    /**
     * Event handler to clean up cache entries when players disconnect.
     *
     * @param event the player quit event
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        invalidate(event.getPlayer().getUniqueId());
    }

    /**
     * Registers this cache as an event listener with the given plugin.
     *
     * @param plugin the plugin to register with
     */
    public void registerListener(@NotNull Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Shuts down the cache, clearing all entries.
     */
    public void shutdown() {
        invalidateAll();
    }

    // ========== Private Helper Methods ==========

    /**
     * Looks up the Bedrock status of a player from GeyserService.
     */
    private boolean lookupBedrockStatus(@NotNull Player player) {
        ensureGeyserServiceLookup();

        if (!geyserServiceAvailable || geyserService == null || isBedrockPlayerMethod == null) {
            return false;
        }

        try {
            Object result = isBedrockPlayerMethod.invoke(geyserService, player);
            return result instanceof Boolean && (Boolean) result;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking Bedrock player status", e);
            return false;
        }
    }

    /**
     * Ensures GeyserService lookup has been attempted.
     */
    private synchronized void ensureGeyserServiceLookup() {
        if (geyserServiceLookupAttempted) {
            return;
        }

        geyserServiceLookupAttempted = true;

        // Geyser detection is a nice-to-have — we probe the legacy
        // RPlatform class and the modern JExPlatform one in order, and
        // fall through silently on every failure path. Missing Geyser
        // is the normal case on a Java-only server and shouldn't spam
        // the log; successful initialisation still prints at FINE.
        if (tryRPlatformLookup()) return;
        if (tryJExPlatformLookup()) return;
        LOGGER.log(Level.FINE, "Geyser service not available on this server; Bedrock detection disabled");
    }

    /** Legacy RPlatform integration — left in place so older installs still work. */
    private boolean tryRPlatformLookup() {
        try {
            Class<?> rPlatformClass = Class.forName("com.raindropcentral.rplatform.RPlatform");
            Method getInstanceMethod = rPlatformClass.getMethod("getInstance");
            Object rPlatform = getInstanceMethod.invoke(null);
            if (rPlatform == null) return false;
            Method getGeyserServiceMethod = rPlatformClass.getMethod("getGeyserService");
            geyserService = getGeyserServiceMethod.invoke(rPlatform);
            if (geyserService == null) return false;
            isBedrockPlayerMethod = geyserService.getClass().getMethod("isBedrockPlayer", Player.class);
            geyserServiceAvailable = true;
            LOGGER.log(Level.FINE, "Geyser integration (via RPlatform) active for Bedrock detection");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "RPlatform Geyser probe failed: " + e.getMessage());
            return false;
        }
    }

    /** Modern JExPlatform integration — reached for via reflection to avoid a hard compile dep. */
    private boolean tryJExPlatformLookup() {
        try {
            Class<?> platformClass = Class.forName("de.jexcellence.jexplatform.JExPlatform");
            Method getGeyserMethod = platformClass.getMethod("getGeyserService");
            // JExPlatform exposes the singleton through a getInstance() hook,
            // or the active instance held as a static. Try both shapes.
            Object platform;
            try {
                platform = platformClass.getMethod("getInstance").invoke(null);
            } catch (NoSuchMethodException nsme) {
                platform = platformClass.getMethod("get").invoke(null);
            }
            if (platform == null) return false;
            geyserService = getGeyserMethod.invoke(platform);
            if (geyserService == null) return false;
            isBedrockPlayerMethod = geyserService.getClass().getMethod("isBedrockPlayer", Player.class);
            geyserServiceAvailable = true;
            LOGGER.log(Level.FINE, "Geyser integration (via JExPlatform) active for Bedrock detection");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "JExPlatform Geyser probe failed: " + e.getMessage());
            return false;
        }
    }
}
