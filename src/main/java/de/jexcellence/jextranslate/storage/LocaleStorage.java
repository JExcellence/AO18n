package de.jexcellence.jextranslate.storage;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface for persisting player language preferences.
 *
 * <p>Implementations of this interface provide different storage backends
 * for saving and retrieving player locale preferences. The system includes
 * an in-memory implementation for simple use cases and a database implementation
 * for persistent storage across server restarts.</p>
 *
 * <p>All implementations must be thread-safe as they may be accessed from
 * multiple threads concurrently.</p>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
public interface LocaleStorage {

    /**
     * Gets the stored locale for a player.
     *
     * @param playerId the player's unique identifier
     * @return an Optional containing the locale if found, or empty if not set
     */
    @NotNull
    Optional<String> getLocale(@NotNull UUID playerId);

    /**
     * Sets the locale for a player.
     *
     * @param playerId the player's unique identifier
     * @param locale   the locale code to store (e.g., "en_US", "de_DE")
     */
    void setLocale(@NotNull UUID playerId, @NotNull String locale);

    /**
     * Removes the stored locale for a player.
     *
     * @param playerId the player's unique identifier
     */
    void removeLocale(@NotNull UUID playerId);

    /**
     * Clears all stored locale preferences.
     *
     * <p>This method removes all player locale mappings from storage.
     * Use with caution as this operation cannot be undone.</p>
     */
    void clearAll();
}
