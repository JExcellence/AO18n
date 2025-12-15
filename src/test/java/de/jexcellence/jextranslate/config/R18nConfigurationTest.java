package de.jexcellence.jextranslate.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for R18nConfiguration record.
 */
@DisplayName("R18nConfiguration")
class R18nConfigurationTest {

    @Nested
    @DisplayName("Default Configuration")
    class DefaultConfigurationTests {

        @Test
        @DisplayName("should create default configuration with en_US locale")
        void shouldCreateDefaultConfiguration() {
            R18nConfiguration config = R18nConfiguration.defaultConfiguration();

            assertEquals("en_US", config.defaultLocale());
            assertTrue(config.supportedLocales().contains("en_US"));
            assertEquals("translations", config.translationDirectory());
            assertTrue(config.keyValidationEnabled());
            assertFalse(config.placeholderAPIEnabled());
            assertTrue(config.legacyColorSupport());
            assertFalse(config.debugMode());
        }
    }

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("should return new instance when changing default locale")
        void shouldReturnNewInstanceWhenChangingDefaultLocale() {
            R18nConfiguration original = R18nConfiguration.defaultConfiguration();
            R18nConfiguration modified = original.withDefaultLocale("de_DE");

            assertNotSame(original, modified);
            assertEquals("en_US", original.defaultLocale());
            assertEquals("de_DE", modified.defaultLocale());
        }

        @Test
        @DisplayName("should return new instance when changing supported locales")
        void shouldReturnNewInstanceWhenChangingSupportedLocales() {
            R18nConfiguration original = R18nConfiguration.defaultConfiguration();
            R18nConfiguration modified = original.withSupportedLocales("en_US", "de_DE", "fr_FR");

            assertNotSame(original, modified);
            assertEquals(1, original.supportedLocales().size());
            assertEquals(3, modified.supportedLocales().size());
        }

        @Test
        @DisplayName("should have unmodifiable supported locales set")
        void shouldHaveUnmodifiableSupportedLocalesSet() {
            R18nConfiguration config = R18nConfiguration.defaultConfiguration();

            assertThrows(UnsupportedOperationException.class, () ->
                    config.supportedLocales().add("new_locale"));
        }
    }

    @Nested
    @DisplayName("Locale Support")
    class LocaleSupportTests {

        @Test
        @DisplayName("should check if locale is supported")
        void shouldCheckIfLocaleIsSupported() {
            R18nConfiguration config = R18nConfiguration.defaultConfiguration()
                    .withSupportedLocales("en_US", "de_DE");

            assertTrue(config.isLocaleSupported("en_US"));
            assertTrue(config.isLocaleSupported("de_DE"));
            assertFalse(config.isLocaleSupported("fr_FR"));
        }

        @Test
        @DisplayName("should get best matching locale for exact match")
        void shouldGetBestMatchingLocaleForExactMatch() {
            R18nConfiguration config = R18nConfiguration.defaultConfiguration()
                    .withSupportedLocales("en_US", "de_DE");

            assertEquals("de_DE", config.getBestMatchingLocale("de_DE"));
        }

        @Test
        @DisplayName("should get best matching locale for language fallback")
        void shouldGetBestMatchingLocaleForLanguageFallback() {
            R18nConfiguration config = R18nConfiguration.defaultConfiguration()
                    .withSupportedLocales("en_US", "de");

            assertEquals("de", config.getBestMatchingLocale("de_AT"));
        }

        @Test
        @DisplayName("should get default locale when no match found")
        void shouldGetDefaultLocaleWhenNoMatchFound() {
            R18nConfiguration config = R18nConfiguration.defaultConfiguration();

            assertEquals("en_US", config.getBestMatchingLocale("zh_CN"));
        }
    }

    @Nested
    @DisplayName("Builder")
    class BuilderTests {

        @Test
        @DisplayName("should build configuration with all options")
        void shouldBuildConfigurationWithAllOptions() {
            R18nConfiguration config = new R18nConfiguration.Builder()
                    .defaultLocale("de_DE")
                    .supportedLocales("de_DE", "en_US", "fr_FR")
                    .translationDirectory("lang")
                    .keyValidationEnabled(false)
                    .placeholderAPIEnabled(true)
                    .legacyColorSupport(false)
                    .debugMode(true)
                    .build();

            assertEquals("de_DE", config.defaultLocale());
            assertEquals(3, config.supportedLocales().size());
            assertEquals("lang", config.translationDirectory());
            assertFalse(config.keyValidationEnabled());
            assertTrue(config.placeholderAPIEnabled());
            assertFalse(config.legacyColorSupport());
            assertTrue(config.debugMode());
        }

        @Test
        @DisplayName("should automatically add default locale to supported locales")
        void shouldAutomaticallyAddDefaultLocaleToSupportedLocales() {
            R18nConfiguration config = new R18nConfiguration.Builder()
                    .defaultLocale("ja_JP")
                    .supportedLocales("en_US", "de_DE")
                    .build();

            assertTrue(config.supportedLocales().contains("ja_JP"));
        }
    }

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        @DisplayName("should throw exception for null default locale")
        void shouldThrowExceptionForNullDefaultLocale() {
            assertThrows(IllegalArgumentException.class, () ->
                    new R18nConfiguration(null, Set.of("en_US"), "translations",
                            true, false, true, false, true, 1000, 30, false, false,
                            R18nConfiguration.DEFAULT_MISSING_KEY_HANDLER));
        }

        @Test
        @DisplayName("should throw exception for empty default locale")
        void shouldThrowExceptionForEmptyDefaultLocale() {
            assertThrows(IllegalArgumentException.class, () ->
                    new R18nConfiguration("", Set.of("en_US"), "translations",
                            true, false, true, false, true, 1000, 30, false, false,
                            R18nConfiguration.DEFAULT_MISSING_KEY_HANDLER));
        }

        @Test
        @DisplayName("should throw exception when default locale not in supported locales")
        void shouldThrowExceptionWhenDefaultLocaleNotInSupportedLocales() {
            assertThrows(IllegalArgumentException.class, () ->
                    new R18nConfiguration("de_DE", Set.of("en_US"), "translations",
                            true, false, true, false, true, 1000, 30, false, false,
                            R18nConfiguration.DEFAULT_MISSING_KEY_HANDLER));
        }
    }
}
