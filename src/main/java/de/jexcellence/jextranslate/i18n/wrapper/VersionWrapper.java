package de.jexcellence.jextranslate.i18n.wrapper;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Simplified wrapper that provides a universal {@link II18nVersionWrapper} implementation
 * for all Minecraft versions. This class replaces the complex version-specific loading
 * with a single, unified approach that works across all supported Minecraft versions.
 *
 * <p>Since all version-specific wrappers use the same Adventure API and have identical
 * functionality, this wrapper eliminates unnecessary complexity by using a single
 * universal implementation that handles version compatibility internally.</p>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
public class VersionWrapper {

    private final II18nVersionWrapper<?> i18nVersionWrapper;

    /**
     * Constructs a new VersionWrapper for the given player, message key, placeholders,
     * and prefix inclusion flag. Uses the universal wrapper implementation that works
     * across all Minecraft versions.
     *
     * @param player        the player for whom the message is being prepared
     * @param key           the message key to retrieve
     * @param placeholders  a map of placeholder replacements
     * @param includePrefix whether to include the prefix in the message
     */
    public VersionWrapper(@NotNull Player player,
                          @NotNull String key,
                          @NotNull Map<String, String> placeholders,
                          boolean includePrefix) {
        // Use the universal wrapper for all versions - Adventure handles compatibility
        this.i18nVersionWrapper = new UniversalI18nWrapper(
                player,
                key,
                placeholders,
                includePrefix
        );
    }

    /**
     * Returns the universal {@link II18nVersionWrapper} instance.
     *
     * @return the universal {@link II18nVersionWrapper} instance
     */
    public II18nVersionWrapper<?> getI18nVersionWrapper() {
        return this.i18nVersionWrapper;
    }
}
