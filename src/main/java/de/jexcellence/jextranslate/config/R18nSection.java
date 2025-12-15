package de.jexcellence.jextranslate.config;

import de.jexcellence.configmapper.sections.CSAlways;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration section for R18n translation settings loaded from YAML.
 *
 * <p>This class represents the structure of the translation.yml configuration file
 * and provides access to default language, supported languages, and advanced settings.</p>
 *
 * <p><strong>Example YAML structure:</strong></p>
 * <pre>{@code
 * # Default language (fallback when a translation is missing)
 * defaultLanguage: "en_US"
 *
 * # List of supported languages (full locale codes)
 * supportedLanguages:
 *   - "en_US"
 *   - "de_DE"
 *   - "es_ES"
 *   - "fr_FR"
 *   - "ja_JP"
 *   - "zh_CN"
 *
 * # Advanced configuration options
 * settings:
 *   validateKeys: true
 *   placeholderAPI: true
 *   legacyColors: true
 *   debug: false
 *   cacheTranslations: true
 * }</pre>
 *
 * @author JExcellence
 * @version 2.0.0
 * @since 2.0.0
 */
@CSAlways
public class R18nSection {

    private String defaultLanguage;
    private List<String> supportedLanguages;
    private Map<String, Object> settings;

    /**
     * Default constructor for YAML deserialization.
     */
    public R18nSection() {
        // Default constructor for YAML mapping
    }

    /**
     * Retrieves the default language for the application.
     * If no default language is set, it returns "en_US" as the default.
     *
     * @return the default language code (e.g., "en_US")
     */
    @NotNull
    public String getDefaultLanguage() {
        return this.defaultLanguage == null || this.defaultLanguage.isEmpty()
                ? "en_US"
                : this.defaultLanguage;
    }

    /**
     * Sets the default language.
     *
     * @param defaultLanguage the default language code
     */
    public void setDefaultLanguage(String defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    /**
     * Retrieves the list of supported languages for the application.
     * If no supported languages are set, it defaults to a list containing the default language.
     *
     * @return a list of supported language codes (e.g., ["en_US", "de_DE"])
     */
    @NotNull
    public List<String> getSupportedLanguages() {
        return this.supportedLanguages == null || this.supportedLanguages.isEmpty()
                ? Collections.singletonList(this.getDefaultLanguage())
                : this.supportedLanguages;
    }

    /**
     * Sets the list of supported languages.
     *
     * @param supportedLanguages the list of supported language codes
     */
    public void setSupportedLanguages(List<String> supportedLanguages) {
        this.supportedLanguages = supportedLanguages;
    }

    /**
     * Retrieves the advanced settings map.
     *
     * @return the settings map, or an empty map if not configured
     */
    @NotNull
    public Map<String, Object> getSettings() {
        return this.settings == null ? Collections.emptyMap() : this.settings;
    }

    /**
     * Sets the advanced settings map.
     *
     * @param settings the settings map
     */
    public void setSettings(Map<String, Object> settings) {
        this.settings = settings;
    }

    /**
     * Checks if key validation is enabled.
     *
     * @return true if key validation is enabled (default: true)
     */
    public boolean isValidateKeysEnabled() {
        return getBooleanSetting("validateKeys", true);
    }

    /**
     * Checks if PlaceholderAPI integration is enabled.
     *
     * @return true if PlaceholderAPI is enabled (default: false)
     */
    public boolean isPlaceholderAPIEnabled() {
        return getBooleanSetting("placeholderAPI", false);
    }

    /**
     * Checks if legacy color code support is enabled.
     *
     * @return true if legacy colors are enabled (default: true)
     */
    public boolean isLegacyColorsEnabled() {
        return getBooleanSetting("legacyColors", true);
    }

    /**
     * Checks if debug mode is enabled.
     *
     * @return true if debug mode is enabled (default: false)
     */
    public boolean isDebugEnabled() {
        return getBooleanSetting("debug", false);
    }

    /**
     * Checks if translation caching is enabled.
     *
     * @return true if caching is enabled (default: true)
     */
    public boolean isCacheTranslationsEnabled() {
        return getBooleanSetting("cacheTranslations", true);
    }

    /**
     * Converts this section to an R18nConfiguration record.
     *
     * @return the R18nConfiguration instance
     */
    @NotNull
    public R18nConfiguration toConfiguration() {
        return new R18nConfiguration.Builder()
                .defaultLocale(getDefaultLanguage())
                .supportedLocales(getSupportedLanguages().toArray(new String[0]))
                .keyValidationEnabled(isValidateKeysEnabled())
                .placeholderAPIEnabled(isPlaceholderAPIEnabled())
                .legacyColorSupport(isLegacyColorsEnabled())
                .debugMode(isDebugEnabled())
                .build();
    }

    /**
     * Helper method to get a boolean setting with a default value.
     *
     * @param key          the setting key
     * @param defaultValue the default value if not found
     * @return the boolean value
     */
    private boolean getBooleanSetting(String key, boolean defaultValue) {
        if (settings == null) {
            return defaultValue;
        }
        Object value = settings.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }
}
