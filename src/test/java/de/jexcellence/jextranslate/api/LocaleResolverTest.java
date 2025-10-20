package de.jexcellence.jextranslate.api;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocaleResolverTest {

    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUp() {
        this.player = mock(Player.class, Mockito.RETURNS_DEEP_STUBS);
        this.playerId = UUID.randomUUID();
        when(this.player.getUniqueId()).thenReturn(this.playerId);
    }

    @Test
    void defaultOptionalMethodsReturnFalseAndDoNotInvokeCoreHooks() {
        TrackingLocaleResolver resolver = new TrackingLocaleResolver(Locale.ENGLISH);

        boolean setPlayerLocaleResult = resolver.setPlayerLocale(this.player, Locale.GERMAN);
        boolean clearPlayerLocaleResult = resolver.clearPlayerLocale(this.player);
        boolean supportsLocaleStorageResult = resolver.supportsLocaleStorage();

        assertAll(
            () -> assertFalse(setPlayerLocaleResult),
            () -> assertFalse(clearPlayerLocaleResult),
            () -> assertFalse(supportsLocaleStorageResult),
            () -> assertEquals(0, resolver.resolveInvocations),
            () -> assertEquals(0, resolver.getDefaultInvocations),
            () -> assertEquals(0, resolver.setDefaultInvocations)
        );
    }

    @Test
    void resolveLocaleRoutesThroughStubForCachedAndExceptionalFlows() {
        TrackingLocaleResolver resolver = new TrackingLocaleResolver(Locale.ENGLISH);
        resolver.cacheLocale(this.playerId, Locale.GERMAN);

        Optional<Locale> cachedLocale = resolver.resolveLocale(this.player);

        assertAll(
            () -> assertTrue(cachedLocale.isPresent()),
            () -> assertEquals(Locale.GERMAN, cachedLocale.orElseThrow()),
            () -> assertEquals(1, resolver.resolveInvocations)
        );

        Locale defaultLocale = resolver.getDefaultLocale();
        assertAll(
            () -> assertEquals(Locale.ENGLISH, defaultLocale),
            () -> assertEquals(1, resolver.getDefaultInvocations)
        );

        resolver.setDefaultLocale(Locale.FRENCH);
        assertAll(
            () -> assertEquals(1, resolver.setDefaultInvocations),
            () -> assertEquals(Locale.FRENCH, resolver.getDefaultLocale()),
            () -> assertEquals(2, resolver.getDefaultInvocations)
        );

        resolver.clearCachedLocales();
        resolver.enableExceptionOnResolve();

        assertThrows(IllegalStateException.class, () -> resolver.resolveLocale(this.player));
        assertEquals(2, resolver.resolveInvocations);
    }

    private static final class TrackingLocaleResolver implements LocaleResolver {

        private final Map<UUID, Locale> cachedLocales = new HashMap<>();
        private Locale defaultLocale;
        private boolean throwOnResolve;
        private int resolveInvocations;
        private int getDefaultInvocations;
        private int setDefaultInvocations;

        private TrackingLocaleResolver(Locale defaultLocale) {
            this.defaultLocale = defaultLocale;
        }

        @Override
        public Optional<Locale> resolveLocale(Player player) {
            this.resolveInvocations++;
            Locale cached = this.cachedLocales.get(player.getUniqueId());
            if (cached != null) {
                return Optional.of(cached);
            }
            if (this.throwOnResolve) {
                throw new IllegalStateException("Forced resolve failure");
            }
            return Optional.of(this.defaultLocale);
        }

        @Override
        public Locale getDefaultLocale() {
            this.getDefaultInvocations++;
            return this.defaultLocale;
        }

        @Override
        public void setDefaultLocale(Locale locale) {
            this.setDefaultInvocations++;
            this.defaultLocale = locale;
        }

        private void cacheLocale(UUID playerId, Locale locale) {
            this.cachedLocales.put(playerId, locale);
        }

        private void clearCachedLocales() {
            this.cachedLocales.clear();
        }

        private void enableExceptionOnResolve() {
            this.throwOnResolve = true;
        }
    }
}
