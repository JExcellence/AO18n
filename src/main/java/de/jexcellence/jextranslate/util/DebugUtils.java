package de.jexcellence.jextranslate.util;

import de.jexcellence.jextranslate.api.LocaleResolver;
import de.jexcellence.jextranslate.api.TranslatedMessage;
import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import de.jexcellence.jextranslate.api.TranslationService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Helper utilities for inspecting translation state at runtime. Provides debugging output for missing keys, locale
 * resolution, and translation comparisons to aid administrators while synchronizing repository, formatter, and resolver
 * components.
 *
 * <p>Methods expect {@link TranslationService#configure(TranslationService.ServiceConfiguration)} to have been invoked
 * prior to use.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.2
 */
public class DebugUtils {

    private static final Logger LOGGER = TranslationLogger.getLogger(DebugUtils.class);

    private DebugUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Builds a detailed debug report for the supplied translation key and player, including locale resolution details
     * and fallback checks.
     *
     * @param key    the translation key to inspect
     * @param player the player providing locale context
     * @return formatted debug report text
     */
    @NotNull
    public static String debugTranslation(@NotNull final String key, @NotNull final Player player) {
        Objects.requireNonNull(key, "Key cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");

        final StringBuilder debug = new StringBuilder();
        debug.append("=== Translation Debug Report ===\n");
        debug.append("Key: ").append(key).append("\n");
        debug.append("Player: ").append(player.getName()).append(" (").append(player.getUniqueId()).append(")\n");

        try {
            final TranslationService.ServiceConfiguration config = TranslationService.getConfiguration();
            if (config == null) {
                debug.append("ERROR: TranslationService not configured!\n");
                return debug.toString();
            }

            final Optional<Locale> playerLocale = config.localeResolver().resolveLocale(player);
            final Locale resolvedLocale = playerLocale.orElse(config.localeResolver().getDefaultLocale());

            debug.append("Player Locale: ").append(playerLocale.map(Locale::toString).orElse("NOT_FOUND")).append("\n");
            debug.append("Resolved Locale: ").append(resolvedLocale).append("\n");
            debug.append("Default Locale: ").append(config.localeResolver().getDefaultLocale()).append("\n");

            final TranslationKey translationKey = TranslationKey.of(key);
            final Optional<String> translation = config.repository().getTranslation(translationKey, resolvedLocale);

            debug.append("Translation Found: ").append(translation.isPresent()).append("\n");
            if (translation.isPresent()) {
                debug.append("Translation Value: '").append(translation.get()).append("'\n");
            } else {
                debug.append("Translation Value: NOT_FOUND\n");

                if (!resolvedLocale.getCountry().isEmpty()) {
                    final Locale languageOnly = new Locale(resolvedLocale.getLanguage());
                    final Optional<String> fallback = config.repository().getTranslation(translationKey, languageOnly);
                    debug.append("Language-only Fallback (").append(languageOnly).append("): ").append(fallback.isPresent()).append("\n");
                    fallback.ifPresent(s -> debug.append("Fallback Value: '").append(s).append("'\n"));
                }

                final Locale defaultLocale = config.repository().getDefaultLocale();
                if (!resolvedLocale.equals(defaultLocale)) {
                    final Optional<String> defaultTranslation = config.repository().getTranslation(translationKey, defaultLocale);
                    debug.append("Default Locale Fallback (").append(defaultLocale).append("): ").append(defaultTranslation.isPresent()).append("\n");
                    defaultTranslation.ifPresent(s -> debug.append("Default Value: '").append(s).append("'\n"));
                }
            }

            debug.append("\n--- Translation Build Test ---\n");
            final TranslatedMessage message = TranslationService.create(translationKey, player).build();
            debug.append("Message Created: true\n");
            debug.append("Message Key: ").append(message.originalKey().key()).append("\n");
            debug.append("Message Text: '").append(message.asPlainText()).append("'\n");
            debug.append("Message Lines: ").append(message.splitLines().size()).append("\n");

        } catch (final Exception exception) {
            debug.append("ERROR during debug: ").append(exception.getClass().getSimpleName()).append(": ").append(exception.getMessage()).append("\n");
            LOGGER.log(
                    java.util.logging.Level.WARNING,
                    TranslationLogger.message(
                            "Error during translation debug",
                            Map.of("error", exception.getClass().getSimpleName())
                    ),
                    exception
            );
        }

        debug.append("=== End Debug Report ===");
        return debug.toString();
    }

    /**
     * Compares two translations for the same player, returning plain-text differences.
     *
     * @param key1   first translation key
     * @param key2   second translation key
     * @param player the player providing locale context
     * @return comparison report
     */
    @NotNull
    public static String compareTranslations(@NotNull final String key1, @NotNull final String key2, @NotNull final Player player) {
        Objects.requireNonNull(key1, "Key1 cannot be null");
        Objects.requireNonNull(key2, "Key2 cannot be null");
        Objects.requireNonNull(player, "Player cannot be null");

        final StringBuilder comparison = new StringBuilder();
        comparison.append("=== Translation Comparison ===\n");
        comparison.append("Key 1: ").append(key1).append("\n");
        comparison.append("Key 2: ").append(key2).append("\n");
        comparison.append("Player: ").append(player.getName()).append("\n\n");

        try {
            final TranslatedMessage message1 = TranslationService.create(TranslationKey.of(key1), player).build();
            final TranslatedMessage message2 = TranslationService.create(TranslationKey.of(key2), player).build();

            comparison.append("Message 1 Text: '").append(message1.asPlainText()).append("'\n");
            comparison.append("Message 2 Text: '").append(message2.asPlainText()).append("'\n");
            comparison.append("Texts Equal: ").append(message1.asPlainText().equals(message2.asPlainText())).append("\n");

        } catch (final Exception exception) {
            comparison.append("ERROR during comparison: ").append(exception.getMessage()).append("\n");
        }

        comparison.append("=== End Comparison ===");
        return comparison.toString();
    }

    /**
     * Forces a locale cache refresh and repository reload, optionally targeting a specific player.
     *
     * @param player optional player whose locale cache should be cleared; {@code null} clears all caches
     * @return status report string
     */
    @NotNull
    public static String forceRefresh(@Nullable final Player player) {
        final StringBuilder status = new StringBuilder();
        status.append("=== Force Refresh ===\n");

        try {
            if (player != null) {
                TranslationService.clearLocaleCache(player);
                status.append("Cleared locale cache for player: ").append(player.getName()).append("\n");
            } else {
                TranslationService.clearLocaleCache();
                status.append("Cleared locale cache for all players\n");
            }

            final TranslationService.ServiceConfiguration config = TranslationService.getConfiguration();
            if (config != null) {
                config.repository().reload().join();
                status.append("Reloaded translation repository\n");
            } else {
                status.append("WARNING: Service not configured, could not reload repository\n");
            }

            status.append("Refresh completed successfully");
        } catch (final Exception exception) {
            status.append("ERROR during refresh: ").append(exception.getMessage());
            LOGGER.log(
                    java.util.logging.Level.WARNING,
                    TranslationLogger.message(
                            "Error during force refresh",
                            Map.of("error", exception.getClass().getSimpleName())
                    ),
                    exception
            );
        }

        return status.toString();
    }

    /**
     * Provides a textual summary of the current translation system configuration.
     *
     * @return summary string describing repository, formatter, and resolver state
     */
    @NotNull
    public static String getSystemStatus() {
        final StringBuilder status = new StringBuilder();
        status.append("=== Translation System Status ===\n");

        try {
            final TranslationService.ServiceConfiguration config = TranslationService.getConfiguration();
            if (config == null) {
                status.append("Service: NOT_CONFIGURED\n");
                return status.toString();
            }

            status.append("Service: CONFIGURED\n");
            status.append("Repository: ").append(config.repository().getClass().getSimpleName()).append("\n");
            status.append("Formatter: ").append(config.formatter().getClass().getSimpleName()).append("\n");
            status.append("Locale Resolver: ").append(config.localeResolver().getClass().getSimpleName()).append("\n");

            status.append("Default Locale: ").append(config.repository().getDefaultLocale()).append("\n");
            status.append("Available Locales: ").append(config.repository().getAvailableLocales()).append("\n");
            status.append("Total Keys: ").append(config.repository().getAllAvailableKeys().size()).append("\n");

            final TranslationRepository.RepositoryMetadata metadata = config.repository().getMetadata();
            status.append("Repository Type: ").append(metadata.getType()).append("\n");
            status.append("Repository Source: ").append(metadata.getSource()).append("\n");
            status.append("Last Modified: ").append(metadata.getLastModified()).append("\n");
            status.append("Total Translations: ").append(metadata.getTotalTranslations()).append("\n");

        } catch (final Exception exception) {
            status.append("ERROR getting system status: ").append(exception.getMessage()).append("\n");
        }

        status.append("=== End System Status ===");
        return status.toString();
    }
}
