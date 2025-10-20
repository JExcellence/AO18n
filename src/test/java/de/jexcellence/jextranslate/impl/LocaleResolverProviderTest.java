package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.LocaleResolver;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocaleResolverProviderTest {

    @Test
    void createAutoDetectingUsesModernApiWhenAvailable() {
        Assumptions.assumeTrue(hasPlayerMethod("locale"), "Modern API not available");

        LocaleResolver resolver = LocaleResolverProvider.createAutoDetecting(Locale.ENGLISH);
        assertEquals("ModernLocaleResolver", resolver.getClass().getSimpleName());
        assertTrue(resolver.supportsLocaleStorage());
    }

    @Test
    void createAutoDetectingUsesLegacyApiWhenModernUnavailable() {
        Assumptions.assumeFalse(hasPlayerMethod("locale"), "Modern API available - skipping legacy selection assertion");
        Assumptions.assumeTrue(hasPlayerMethod("getLocale"), "Legacy API not available");

        LocaleResolver resolver = LocaleResolverProvider.createAutoDetecting(Locale.ENGLISH);
        assertEquals("LegacyLocaleResolver", resolver.getClass().getSimpleName());
        assertTrue(resolver.supportsLocaleStorage());
    }

    @Test
    void modernResolverPrefersStoredLocale() throws Exception {
        LocaleResolver resolver = instantiateResolver("ModernLocaleResolver", Locale.ENGLISH);
        Player player = createPlayerMock();

        assertTrue(resolver.setPlayerLocale(player, Locale.JAPAN));
        Optional<Locale> resolved = resolver.resolveLocale(player);

        assertTrue(resolved.isPresent());
        assertEquals(Locale.JAPAN, resolved.orElseThrow());
        verify(player, never()).locale();
    }

    @Test
    void modernResolverFallsBackToDefaultWhenLocaleThrows() throws Exception {
        LocaleResolver resolver = instantiateResolver("ModernLocaleResolver", Locale.GERMANY);
        Player player = createPlayerMock();
        when(player.locale()).thenThrow(new IllegalStateException("boom"));

        Optional<Locale> resolved = resolver.resolveLocale(player);

        assertTrue(resolved.isPresent());
        assertEquals(Locale.GERMANY, resolved.orElseThrow());
    }

    @Test
    void legacyResolverParsesLocaleStrings() throws Exception {
        LocaleResolver resolver = instantiateResolver("LegacyLocaleResolver", Locale.ENGLISH);
        Player player = createPlayerMock();
        when(player.getLocale()).thenReturn("fr_CA");

        Optional<Locale> resolved = resolver.resolveLocale(player);

        assertTrue(resolved.isPresent());
        Locale locale = resolved.orElseThrow();
        assertEquals("fr", locale.getLanguage());
        assertEquals("CA", locale.getCountry());
    }

    @Test
    void legacyResolverPrefersStoredLocale() throws Exception {
        LocaleResolver resolver = instantiateResolver("LegacyLocaleResolver", Locale.ENGLISH);
        Player player = createPlayerMock();

        assertTrue(resolver.setPlayerLocale(player, Locale.CHINA));
        Optional<Locale> resolved = resolver.resolveLocale(player);

        assertTrue(resolved.isPresent());
        assertEquals(Locale.CHINA, resolved.orElseThrow());
        verify(player, never()).getLocale();
    }

    @Test
    void legacyResolverFallsBackToDefaultWhenGetLocaleFails() throws Exception {
        LocaleResolver resolver = instantiateResolver("LegacyLocaleResolver", Locale.ITALY);
        Player player = createPlayerMock();
        when(player.getLocale()).thenThrow(new RuntimeException("boom"));

        Optional<Locale> resolved = resolver.resolveLocale(player);

        assertTrue(resolved.isPresent());
        assertEquals(Locale.ITALY, resolved.orElseThrow());
    }

    @Test
    void fallbackResolverUsesStoredLocaleOverrides() throws Exception {
        LocaleResolver resolver = instantiateResolver("FallbackLocaleResolver", Locale.ENGLISH);
        Player player = createPlayerMock();

        assertTrue(resolver.setPlayerLocale(player, Locale.KOREA));
        Optional<Locale> resolved = resolver.resolveLocale(player);

        assertTrue(resolved.isPresent());
        assertEquals(Locale.KOREA, resolved.orElseThrow());
    }

    @Test
    void fallbackResolverReturnsDefaultWhenNoStoredLocale() throws Exception {
        LocaleResolver resolver = instantiateResolver("FallbackLocaleResolver", Locale.US);
        Player player = createPlayerMock();

        Optional<Locale> resolved = resolver.resolveLocale(player);

        assertTrue(resolved.isPresent());
        assertEquals(Locale.US, resolved.orElseThrow());
    }

    @Test
    void baseResolverSupportsStorageAndClearing() throws Exception {
        LocaleResolver resolver = instantiateResolver("FallbackLocaleResolver", Locale.UK);
        Player player = createPlayerMock();

        assertTrue(resolver.supportsLocaleStorage());
        assertTrue(resolver.setPlayerLocale(player, Locale.CANADA_FRENCH));
        assertTrue(resolver.resolveLocale(player).isPresent());
        assertTrue(resolver.clearPlayerLocale(player));
        assertFalse(resolver.resolveLocale(player).filter(Locale.CANADA_FRENCH::equals).isPresent());
    }

    @Test
    void baseResolverAllowsDefaultLocaleMutation() throws Exception {
        LocaleResolver resolver = instantiateResolver("FallbackLocaleResolver", Locale.ENGLISH);
        Player player = createPlayerMock();

        assertSame(Locale.ENGLISH, resolver.getDefaultLocale());
        resolver.setDefaultLocale(Locale.GERMAN);
        assertSame(Locale.GERMAN, resolver.getDefaultLocale());
        assertEquals(Locale.GERMAN, resolver.resolveLocale(player).orElseThrow());
    }

    private boolean hasPlayerMethod(String methodName) {
        try {
            Player.class.getMethod(methodName);
            return true;
        } catch (NoSuchMethodException exception) {
            return false;
        }
    }

    private LocaleResolver instantiateResolver(String simpleName, Locale defaultLocale)
            throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> clazz : LocaleResolverProvider.class.getDeclaredClasses()) {
            if (clazz.getSimpleName().equals(simpleName)) {
                Constructor<?> constructor = clazz.getDeclaredConstructor(Locale.class);
                constructor.setAccessible(true);
                return (LocaleResolver) constructor.newInstance(defaultLocale);
            }
        }
        fail("Resolver class not found: " + simpleName);
        throw new AssertionError();
    }

    private Player createPlayerMock() {
        Player player = mock(Player.class, Mockito.withSettings().lenient());
        UUID uniqueId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uniqueId);
        when(player.getName()).thenReturn("TestPlayer");
        return player;
    }
}
