package de.jexcellence.jextranslate.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for VersionDetector class.
 * Note: Some tests may behave differently depending on the test environment.
 */
@DisplayName("VersionDetector")
class VersionDetectorTest {

    @Nested
    @DisplayName("Server Type Detection")
    class ServerTypeDetectionTests {

        @Test
        @DisplayName("should have exactly one server type detected")
        void shouldHaveExactlyOneServerTypeDetected() {
            // This test verifies the detection logic is mutually exclusive
            // In a test environment without Bukkit, all will be false except isBukkit
            VersionDetector detector = new VersionDetector();

            int trueCount = 0;
            if (detector.isPaper()) trueCount++;
            if (detector.isPurpur()) trueCount++;
            if (detector.isFolia()) trueCount++;
            if (detector.isSpigot()) trueCount++;
            if (detector.isBukkit()) trueCount++;

            assertEquals(1, trueCount, "Exactly one server type should be detected");
        }

        @Test
        @DisplayName("should return non-null server type")
        void shouldReturnNonNullServerType() {
            VersionDetector detector = new VersionDetector();

            assertNotNull(detector.getServerType());
        }
    }

    @Nested
    @DisplayName("Version Information")
    class VersionInformationTests {

        @Test
        @DisplayName("should return non-null minecraft version")
        void shouldReturnNonNullMinecraftVersion() {
            VersionDetector detector = new VersionDetector();

            assertNotNull(detector.getMinecraftVersion());
        }

        @Test
        @DisplayName("should return non-null server version")
        void shouldReturnNonNullServerVersion() {
            VersionDetector detector = new VersionDetector();

            assertNotNull(detector.getServerVersion());
        }
    }

    @Nested
    @DisplayName("Adventure Support")
    class AdventureSupportTests {

        @Test
        @DisplayName("should have consistent adventure support flags")
        void shouldHaveConsistentAdventureSupportFlags() {
            VersionDetector detector = new VersionDetector();

            // If native adventure is available, platform is not required
            if (detector.hasNativeAdventure()) {
                assertFalse(detector.requiresAdventurePlatform());
            } else {
                assertTrue(detector.requiresAdventurePlatform());
            }
        }

        @Test
        @DisplayName("native adventure should be true for Paper/Purpur/Folia")
        void nativeAdventureShouldBeTrueForPaperPurpurFolia() {
            VersionDetector detector = new VersionDetector();

            if (detector.isPaper() || detector.isPurpur() || detector.isFolia()) {
                assertTrue(detector.hasNativeAdventure());
            }
        }
    }

    @Nested
    @DisplayName("Class Detection")
    class ClassDetectionTests {

        @Test
        @DisplayName("should detect existing class")
        void shouldDetectExistingClass() {
            VersionDetector detector = new VersionDetector();

            assertTrue(detector.hasClass("java.lang.String"));
        }

        @Test
        @DisplayName("should not detect non-existing class")
        void shouldNotDetectNonExistingClass() {
            VersionDetector detector = new VersionDetector();

            assertFalse(detector.hasClass("com.nonexistent.FakeClass"));
        }
    }

    @Nested
    @DisplayName("Environment Summary")
    class EnvironmentSummaryTests {

        @Test
        @DisplayName("should return non-empty environment summary")
        void shouldReturnNonEmptyEnvironmentSummary() {
            VersionDetector detector = new VersionDetector();

            String summary = detector.getEnvironmentSummary();

            assertNotNull(summary);
            assertFalse(summary.isEmpty());
        }

        @Test
        @DisplayName("should include server type in summary")
        void shouldIncludeServerTypeInSummary() {
            VersionDetector detector = new VersionDetector();

            String summary = detector.getEnvironmentSummary();

            assertTrue(summary.contains(detector.getServerType().getDisplayName()));
        }
    }

    @Nested
    @DisplayName("Server Type Enum")
    class ServerTypeEnumTests {

        @Test
        @DisplayName("should have display names for all server types")
        void shouldHaveDisplayNamesForAllServerTypes() {
            for (VersionDetector.ServerType type : VersionDetector.ServerType.values()) {
                assertNotNull(type.getDisplayName());
                assertFalse(type.getDisplayName().isEmpty());
            }
        }
    }
}
