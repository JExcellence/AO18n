package de.jexcellence.jextranslate.util;

import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe LRU cache for parsed MiniMessage components.
 *
 * @author JExcellence
 * @since 3.0.0
 */
public final class MessageCache {

    private static final int DEFAULT_MAX_SIZE = 1000;

    private final Map<String, Component> cache;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final int maxSize;
    private long hits;
    private long misses;

    /**
     * Creates a cache with the default entry limit.
     */
    public MessageCache() {
        this(DEFAULT_MAX_SIZE);
    }

    /**
     * Creates a cache with a caller-provided entry limit.
     *
     * @param maxSize maximum entries to retain before evicting least-recently-used values
     */
    public MessageCache(int maxSize) {
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                return size() > MessageCache.this.maxSize;
            }
        };
    }

    /**
     * Gets a cached component or null if not present.
     *
     * @param key cache key
     * @return cached component when present, otherwise {@code null}
     */
    @Nullable
    public Component get(@NotNull String key) {
        Objects.requireNonNull(key, "Key cannot be null");
        lock.readLock().lock();
        try {
            var result = cache.get(key);
            if (result != null) {
                hits++;
            } else {
                misses++;
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Puts a component in the cache.
     *
     * @param key       cache key
     * @param component component value to cache
     */
    public void put(@NotNull String key, @NotNull Component component) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(component, "Component cannot be null");
        lock.writeLock().lock();
        try {
            cache.put(key, component);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets or computes a component.
     *
     * @param key    cache key
     * @param mapper callback used to compute a value when the key is missing
     * @return cached or computed component for the key
     */
    @NotNull
    public Component computeIfAbsent(@NotNull String key, @NotNull java.util.function.Function<String, Component> mapper) {
        var cached = get(key);
        if (cached != null) {
            return cached;
        }
        var computed = mapper.apply(key);
        put(key, computed);
        return computed;
    }

    /**
     * Clears the cache.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            hits = 0;
            misses = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Returns cache statistics.
     *
     * @return immutable snapshot of current cache metrics
     */
    @NotNull
    public CacheStats stats() {
        lock.readLock().lock();
        try {
            return new CacheStats(cache.size(), maxSize, hits, misses);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cache statistics record.
     *
     * @param size    current number of cached entries
     * @param maxSize configured cache capacity
     * @param hits    number of cache hits
     * @param misses  number of cache misses
     */
    public record CacheStats(int size, int maxSize, long hits, long misses) {
        /**
         * Returns the current hit rate for this snapshot.
         *
         * @return hit ratio in the range {@code 0.0..1.0}
         */
        public double hitRate() {
            var total = hits + misses;
            return total > 0 ? (double) hits / total : 0.0;
        }
    }
}
