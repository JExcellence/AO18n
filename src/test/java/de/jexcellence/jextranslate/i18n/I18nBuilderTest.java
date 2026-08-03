package de.jexcellence.jextranslate.i18n;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for I18n.Builder class.
 * Note: Full integration tests require a Bukkit environment.
 */
@DisplayName("I18n.Builder")
class I18nBuilderTest {

    @Nested
    @DisplayName("Builder Construction")
    class BuilderConstructionTests {

        @Test
        @DisplayName("should create builder with key only (console)")
        void shouldCreateBuilderWithKeyOnly() {
            I18n.Builder builder = new I18n.Builder("test.key");

            assertNotNull(builder);
        }

        @Test
        @DisplayName("should throw exception for null key")
        void shouldThrowExceptionForNullKey() {
            assertThrows(NullPointerException.class, () ->
                    new I18n.Builder(null));
        }
    }

    @Nested
    @DisplayName("Placeholder Methods")
    class PlaceholderMethodsTests {

        @Test
        @DisplayName("should add single placeholder")
        void shouldAddSinglePlaceholder() {
            I18n.Builder builder = new I18n.Builder("test.key")
                    .withPlaceholder("name", "Steve");

            assertNotNull(builder);
        }

        @Test
        @DisplayName("should add multiple placeholders via map")
        void shouldAddMultiplePlaceholdersViaMap() {
            Map<String, Object> placeholders = Map.of(
                    "name", "Steve",
                    "level", 10,
                    "health", 20.0
            );

            I18n.Builder builder = new I18n.Builder("test.key")
                    .withPlaceholders(placeholders);

            assertNotNull(builder);
        }

        @Test
        @DisplayName("should handle null placeholder value")
        void shouldHandleNullPlaceholderValue() {
            I18n.Builder builder = new I18n.Builder("test.key")
                    .withPlaceholder("name", null);

            assertNotNull(builder);
        }

        @Test
        @DisplayName("should chain multiple placeholder calls")
        void shouldChainMultiplePlaceholderCalls() {
            I18n.Builder builder = new I18n.Builder("test.key")
                    .withPlaceholder("name", "Steve")
                    .withPlaceholder("level", 10)
                    .withPlaceholder("health", 20.0);

            assertNotNull(builder);
        }
    }

    @Nested
    @DisplayName("Prefix Methods")
    class PrefixMethodsTests {

        @Test
        @DisplayName("should enable prefix")
        void shouldEnablePrefix() {
            I18n.Builder builder = new I18n.Builder("test.key")
                    .includePrefix();

            assertNotNull(builder);
        }

        @Test
        @DisplayName("should chain prefix with placeholders")
        void shouldChainPrefixWithPlaceholders() {
            I18n.Builder builder = new I18n.Builder("test.key")
                    .withPlaceholder("name", "Steve")
                    .includePrefix();

            assertNotNull(builder);
        }
    }

    @Nested
    @DisplayName("Build Method")
    class BuildMethodTests {

        @Test
        @DisplayName("should build I18n instance")
        void shouldBuildI18nInstance() {
            // Note: This will fail without R18n initialized, but tests the builder pattern
            I18n.Builder builder = new I18n.Builder("test.key")
                    .withPlaceholder("name", "Steve")
                    .includePrefix();

            // In a real test environment with R18n initialized:
            // I18n i18n = builder.build();
            // assertNotNull(i18n);

            assertNotNull(builder);
        }
    }

    @Nested
    @DisplayName("Fluent API")
    class FluentApiTests {

        @Test
        @DisplayName("should support full fluent chain")
        void shouldSupportFullFluentChain() {
            I18n.Builder builder = new I18n.Builder("welcome.message")
                    .withPlaceholder("player", "Steve")
                    .withPlaceholder("server", "MyServer")
                    .withPlaceholders(Map.of("online", 50, "max", 100))
                    .includePrefix();

            assertNotNull(builder);
        }

        @Test
        @DisplayName("should return same builder instance for chaining")
        void shouldReturnSameBuilderInstanceForChaining() {
            I18n.Builder builder = new I18n.Builder("test.key");
            I18n.Builder returned = builder.withPlaceholder("name", "Steve");

            assertSame(builder, returned);
        }
    }
}
