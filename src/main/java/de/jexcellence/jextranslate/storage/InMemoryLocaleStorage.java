package de.jexcellence.jextranslate.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link LocaleStorage}.
 *
 * <p>This implementation stores player locale preferences in a thread-safe
 * {@link ConcurrentHashMap}. Data is not persisted across server restarts,
 * making this suitable for development or scenarios where persistence is
 * not required.</p>
 *
 * <p>This is the default storage implementation used when no other storage
 * is configured.</p>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
public class InMemoryLocaleStorage implements LocaleStorage {

    private final Map<UUID, String> locales = new ConcurrentHashMap<>();

    @Override
    @NotNull
    public Optional<String> getLocale(@NotNull UUID playerId) {
        return Optional.ofNullable(locales.get(playerId));
    }

    @Override
    public void setLocale(@NotNull UUID playerId, @NotNull String locale) {
        locales.put(playerId, locale);
    }

    @Override
    public void removeLocale(@NotNull UUID playerId) {
        locales.remove(playerId);
    }

    @Override
    public void clearAll() {
        locales.clear();
    }

    /**
     * Gets the number of stored locale preferences.
     *
     * @return the number of stored entries
     */
    public int size() {
        return locales.size();
    }
}
