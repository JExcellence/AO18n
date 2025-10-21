package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.LocaleResolver;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory utility that produces {@link LocaleResolver} implementations targeting available Bukkit APIs. The resolver
 * produced by {@link #createAutoDetecting(Locale)} is used by default in
 * {@link de.jexcellence.jextranslate.api.TranslationService}.
 *
 * <p>Resolvers created here provide optional locale storage and integrate with Bukkit's modern or legacy locale accessors
 * when available, falling back to an in-memory store otherwise.</p>
 *
 * @author JExcellence
 * @since 1.0.0
 * @version 1.0.1
 */
public class LocaleResolverProvider {

    private static final Logger LOGGER = Logger.getLogger(LocaleResolverProvider.class.getName());

    private LocaleResolverProvider() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a {@link LocaleResolver} that auto-detects the best available Bukkit API and stores per-player overrides.
     *
     * @param defaultLocale the fallback locale used when detection fails
     * @return an auto-detecting locale resolver
     */
    @NotNull
    public static LocaleResolver createAutoDetecting(@NotNull final Locale defaultLocale) {
        Objects.requireNonNull(defaultLocale, "Default locale cannot be null");

        if (isModernApiAvailable()) {
            LOGGER.info("Using modern Adventure API for locale detection");
            return new ModernLocaleResolver(defaultLocale);
        }

        if (isLegacyApiAvailable()) {
            LOGGER.info("Using legacy Bukkit API for locale detection");
            return new LegacyLocaleResolver(defaultLocale);
        }

        LOGGER.warning("No locale detection API available - using fallback");
        return new FallbackLocaleResolver(defaultLocale);
    }

    private static boolean isModernApiAvailable() {
        try {
            Player.class.getMethod("locale");
            return true;
        } catch (final NoSuchMethodException exception) {
            return false;
        }
    }

    private static boolean isLegacyApiAvailable() {
        try {
            Player.class.getMethod("getLocale");
            return true;
        } catch (final NoSuchMethodException exception) {
            return false;
        }
    }

    /**
     * Base resolver implementation providing shared storage logic for per-player locales.
     */
    private static abstract class BaseLocaleResolver implements LocaleResolver {
        protected final Map<UUID, Locale> storedLocales = new ConcurrentHashMap<>();
        protected Locale defaultLocale;

        protected BaseLocaleResolver(@NotNull final Locale defaultLocale) {
            this.defaultLocale = Objects.requireNonNull(defaultLocale, "Default locale cannot be null");
        }

        @Override
        @NotNull
        public Locale getDefaultLocale() {
            return this.defaultLocale;
        }

        @Override
        public void setDefaultLocale(@NotNull final Locale locale) {
            this.defaultLocale = Objects.requireNonNull(locale, "Locale cannot be null");
        }

        @Override
        public boolean setPlayerLocale(@NotNull final Player player, @NotNull final Locale locale) {
            Objects.requireNonNull(player, "Player cannot be null");
            Objects.requireNonNull(locale, "Locale cannot be null");
            this.storedLocales.put(player.getUniqueId(), locale);
            return true;
        }

        @Override
        public boolean clearPlayerLocale(@NotNull final Player player) {
            Objects.requireNonNull(player, "Player cannot be null");
            return this.storedLocales.remove(player.getUniqueId()) != null;
        }

        @Override
        public boolean supportsLocaleStorage() {
            return true;
        }

        @NotNull
        protected Optional<Locale> getStoredLocale(@NotNull final Player player) {
            return Optional.ofNullable(this.storedLocales.get(player.getUniqueId()));
        }
    }

    /**
     * Resolver backed by the Adventure API {@code Player#locale()} method.
     */
    private static final class ModernLocaleResolver extends BaseLocaleResolver {

        private ModernLocaleResolver(@NotNull final Locale defaultLocale) {
            super(defaultLocale);
        }

        @Override
        @NotNull
        public Optional<Locale> resolveLocale(@NotNull final Player player) {
            Objects.requireNonNull(player, "Player cannot be null");

            final Optional<Locale> stored = getStoredLocale(player);
            if (stored.isPresent()) {
                return stored;
            }

            try {
                final Locale clientLocale = player.locale();
                return Optional.of(clientLocale);
            } catch (final Exception exception) {
                LOGGER.log(Level.FINE, "Failed to get client locale for player " + player.getName(), exception);
                return Optional.of(this.defaultLocale);
            }
        }
    }

    /**
     * Resolver using the legacy Bukkit {@code Player#getLocale()} method when Adventure API support is unavailable.
     */
    private static final class LegacyLocaleResolver extends BaseLocaleResolver {

        private LegacyLocaleResolver(@NotNull final Locale defaultLocale) {
            super(defaultLocale);
        }

        @Override
        @NotNull
        public Optional<Locale> resolveLocale(@NotNull final Player player) {
            Objects.requireNonNull(player, "Player cannot be null");

            final Optional<Locale> stored = getStoredLocale(player);
            if (stored.isPresent()) {
                return stored;
            }

            try {
                final String localeString = player.getLocale();
                final Locale clientLocale = parseLocaleString(localeString);
                return Optional.of(clientLocale);
            } catch (final Exception exception) {
                LOGGER.log(Level.FINE, "Failed to get client locale for player " + player.getName(), exception);
                return Optional.of(this.defaultLocale);
            }
        }

        @NotNull
        private Locale parseLocaleString(@NotNull final String localeString) {
            final String[] parts = localeString.split("_");
            if (parts.length == 1) {
                return new Locale(parts[0]);
            } else if (parts.length == 2) {
                return new Locale(parts[0], parts[1]);
            } else if (parts.length >= 3) {
                return new Locale(parts[0], parts[1], parts[2]);
            }
            return this.defaultLocale;
        }
    }

    /**
     * Fallback resolver that only uses stored locales and the configured default.
     */
    private static final class FallbackLocaleResolver extends BaseLocaleResolver {

        private FallbackLocaleResolver(@NotNull final Locale defaultLocale) {
            super(defaultLocale);
        }

        @Override
        @NotNull
        public Optional<Locale> resolveLocale(@NotNull final Player player) {
            Objects.requireNonNull(player, "Player cannot be null");
            final Optional<Locale> stored = getStoredLocale(player);
            return stored.isPresent() ? stored : Optional.of(this.defaultLocale);
        }
    }
}
