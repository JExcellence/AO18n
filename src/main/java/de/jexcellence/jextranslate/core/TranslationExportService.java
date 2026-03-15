package de.jexcellence.jextranslate.core;

import org.jetbrains.annotations.NotNull;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/**
 * Service for exporting translations to various formats.
 *
 * <p>Supports exporting translations to CSV, JSON, and YAML formats.
 * This is useful for sending translations to external translation services
 * or for backup purposes.</p>
 *
 * @author JExcellence
 * @version 4.0.0
 * @since 4.0.0
 */
public final class TranslationExportService {

    private static final Logger LOGGER = Logger.getLogger(TranslationExportService.class.getName());

    private final ObjectMapper jsonMapper;
    private final Yaml yamlWriter;

    /**
     * Supported export formats.
     */
    public enum ExportFormat {
        /** Comma-separated values with key, locale, value columns. */
        CSV,
        /** JSON format with flat key-value structure per locale. */
        JSON,
        /** YAML format. */
        YAML
    }

    /**
     * Creates a new TranslationExportService.
     */
    public TranslationExportService() {
        this.jsonMapper = new ObjectMapper();
        this.jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
        
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setPrettyFlow(true);
        dumperOptions.setIndent(2);
        this.yamlWriter = new Yaml(dumperOptions);
    }


    /**
     * Exports translations to the specified format.
     *
     * @param outputPath   the path to write the export file
     * @param format       the export format
     * @param translations the translations to export (Map&lt;key, Map&lt;locale, List&lt;String&gt;&gt;&gt;)
     * @throws IOException if an I/O error occurs during export
     */
    public void export(@NotNull Path outputPath, @NotNull ExportFormat format,
                       @NotNull Map<String, Map<String, List<String>>> translations) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        Objects.requireNonNull(format, "format must not be null");
        Objects.requireNonNull(translations, "translations must not be null");

        // Ensure parent directory exists
        Path parentDir = outputPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        switch (format) {
            case CSV -> exportCsv(outputPath, translations);
            case JSON -> exportJson(outputPath, translations);
            case YAML -> exportYaml(outputPath, translations);
        }

        LOGGER.info(String.format("Exported %d translation keys to %s in %s format",
                translations.size(), outputPath.getFileName(), format));
    }

    /**
     * Exports translations to CSV format.
     *
     * <p>The CSV file will have the following columns: key, locale, value.
     * Multi-line values are joined with \n.</p>
     *
     * @param outputPath   the path to write the CSV file
     * @param translations the translations to export
     * @throws IOException if an I/O error occurs
     */
    private void exportCsv(@NotNull Path outputPath, 
                           @NotNull Map<String, Map<String, List<String>>> translations) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write header
            writer.write("key,locale,value");
            writer.newLine();

            // Sort keys for consistent output
            List<String> sortedKeys = new ArrayList<>(translations.keySet());
            Collections.sort(sortedKeys);

            for (String key : sortedKeys) {
                Map<String, List<String>> localeMap = translations.get(key);
                if (localeMap == null) continue;

                // Sort locales for consistent output
                List<String> sortedLocales = new ArrayList<>(localeMap.keySet());
                Collections.sort(sortedLocales);

                for (String locale : sortedLocales) {
                    List<String> values = localeMap.get(locale);
                    if (values == null || values.isEmpty()) continue;

                    // Join multi-line values with \n
                    String value = String.join("\\n", values);
                    
                    // Write CSV row with proper escaping
                    writer.write(String.format("\"%s\",\"%s\",\"%s\"",
                            escapeCsv(key),
                            escapeCsv(locale),
                            escapeCsv(value)));
                    writer.newLine();
                }
            }
        }
    }

    /**
     * Escapes a string for CSV format.
     *
     * @param value the value to escape
     * @return the escaped value
     */
    @NotNull
    private String escapeCsv(@NotNull String value) {
        // Escape double quotes by doubling them
        return value.replace("\"", "\"\"");
    }


    /**
     * Exports translations to JSON format.
     *
     * <p>Creates a flat key-value structure per locale. The output structure is:
     * <pre>
     * {
     *   "en_US": {
     *     "key1": "value1",
     *     "key2": "value2"
     *   },
     *   "de_DE": {
     *     "key1": "wert1",
     *     "key2": "wert2"
     *   }
     * }
     * </pre>
     * </p>
     *
     * @param outputPath   the path to write the JSON file
     * @param translations the translations to export
     * @throws IOException if an I/O error occurs
     */
    private void exportJson(@NotNull Path outputPath,
                            @NotNull Map<String, Map<String, List<String>>> translations) throws IOException {
        // Reorganize: Map<key, Map<locale, value>> -> Map<locale, Map<key, value>>
        Map<String, Map<String, String>> byLocale = new TreeMap<>();

        for (Map.Entry<String, Map<String, List<String>>> keyEntry : translations.entrySet()) {
            String key = keyEntry.getKey();
            Map<String, List<String>> localeMap = keyEntry.getValue();

            for (Map.Entry<String, List<String>> localeEntry : localeMap.entrySet()) {
                String locale = localeEntry.getKey();
                List<String> values = localeEntry.getValue();

                if (values == null || values.isEmpty()) continue;

                // Join multi-line values with newline
                String value = String.join("\n", values);

                byLocale.computeIfAbsent(locale, k -> new TreeMap<>()).put(key, value);
            }
        }

        jsonMapper.writeValue(outputPath.toFile(), byLocale);
    }

    /**
     * Exports translations to YAML format.
     *
     * <p>Creates a structure similar to JSON export but in YAML format.</p>
     *
     * @param outputPath   the path to write the YAML file
     * @param translations the translations to export
     * @throws IOException if an I/O error occurs
     */
    private void exportYaml(@NotNull Path outputPath,
                            @NotNull Map<String, Map<String, List<String>>> translations) throws IOException {
        // Reorganize: Map<key, Map<locale, value>> -> Map<locale, Map<key, value>>
        Map<String, Map<String, Object>> byLocale = new TreeMap<>();

        for (Map.Entry<String, Map<String, List<String>>> keyEntry : translations.entrySet()) {
            String key = keyEntry.getKey();
            Map<String, List<String>> localeMap = keyEntry.getValue();

            for (Map.Entry<String, List<String>> localeEntry : localeMap.entrySet()) {
                String locale = localeEntry.getKey();
                List<String> values = localeEntry.getValue();

                if (values == null || values.isEmpty()) continue;

                // For YAML, keep multi-line as list if more than one line, otherwise as string
                Object value = values.size() == 1 ? values.get(0) : new ArrayList<>(values);

                byLocale.computeIfAbsent(locale, k -> new TreeMap<>()).put(key, value);
            }
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            yamlWriter.dump(byLocale, writer);
        }
    }

    /**
     * Gets the recommended file extension for the given export format.
     *
     * @param format the export format
     * @return the file extension (including the dot)
     */
    @NotNull
    public static String getFileExtension(@NotNull ExportFormat format) {
        return switch (format) {
            case CSV -> ".csv";
            case JSON -> ".json";
            case YAML -> ".yml";
        };
    }

    /**
     * Parses an export format from a string.
     *
     * @param formatString the format string (case-insensitive)
     * @return the parsed format, or empty if invalid
     */
    @NotNull
    public static Optional<ExportFormat> parseFormat(@NotNull String formatString) {
        try {
            return Optional.of(ExportFormat.valueOf(formatString.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
