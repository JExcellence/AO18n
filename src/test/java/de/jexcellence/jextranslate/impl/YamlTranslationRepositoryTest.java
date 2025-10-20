package de.jexcellence.jextranslate.impl;

import de.jexcellence.jextranslate.api.TranslationKey;
import de.jexcellence.jextranslate.api.TranslationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlTranslationRepositoryTest {

    private static final TranslationKey GREETING_KEY = TranslationKey.of("greeting.hello");
    private static final TranslationKey FAREWELL_KEY = TranslationKey.of("farewell.lines");
    private static final TranslationKey NESTED_KEY = TranslationKey.of("nested.deeper.value");
    private static final TranslationKey DEFAULT_ONLY_KEY = TranslationKey.of("fallback.only");

    @Test
    void constructorReloadProvidesFallbacksAndMetadata(@TempDir final Path tempDir) throws Exception {
        writeStandardTranslations(tempDir);

        final YamlTranslationRepository repository = new YamlTranslationRepository(tempDir, Locale.GERMAN);

        final AtomicInteger reloads = new AtomicInteger();
        final Set<String> loadedEvents = ConcurrentHashMap.newKeySet();
        final List<Throwable> errors = new ArrayList<>();

        repository.addListener(new TranslationRepository.RepositoryListener() {
            @Override
            public void onReload(final TranslationRepository repository) {
                reloads.incrementAndGet();
            }

            @Override
            public void onTranslationLoaded(final TranslationRepository repository, final TranslationKey key,
                                            final Locale locale, final String translation) {
                loadedEvents.add(locale.toString() + ":" + key.key());
            }

            @Override
            public void onError(final TranslationRepository repository, final Throwable error) {
                errors.add(error);
            }
        });

        repository.reload().join();

        assertEquals(1, reloads.get(), "Reload listener should be invoked once");
        assertTrue(errors.isEmpty(), "No errors should be emitted for valid translations");
        assertEquals(Set.of(
            "en:" + GREETING_KEY.key(),
            "en:" + FAREWELL_KEY.key(),
            "en:" + NESTED_KEY.key(),
            "en_US:" + GREETING_KEY.key(),
            "de:" + GREETING_KEY.key(),
            "de:" + DEFAULT_ONLY_KEY.key()
        ), loadedEvents, "Translation loaded events should be emitted for every locale entry");

        assertEquals(Optional.of("Howdy partner!"), repository.getTranslation(GREETING_KEY, Locale.US),
            "Exact locale lookup should match the locale file");
        assertEquals(Optional.of("Goodbye\nSee you soon"), repository.getTranslation(FAREWELL_KEY, Locale.US),
            "Language fallback should flatten list values into newline separated text");
        assertEquals(Optional.of("Standardwert"), repository.getTranslation(DEFAULT_ONLY_KEY, Locale.US),
            "Default locale should be used when specific locale data is unavailable");
        assertTrue(repository.getTranslation(TranslationKey.of("unknown.key"), Locale.US).isEmpty(),
            "Missing translations should return an empty optional");

        assertEquals(Locale.GERMAN, repository.getDefaultLocale(), "Initial default locale should match constructor");
        assertEquals(Set.of(Locale.GERMAN, Locale.ENGLISH, Locale.US), repository.getAvailableLocales(),
            "All locale files should be reported as available");

        assertEquals(Set.of(GREETING_KEY, FAREWELL_KEY, NESTED_KEY), repository.getAvailableKeys(Locale.ENGLISH),
            "Locale specific keys should include nested maps and list entries");
        assertEquals(Set.of(GREETING_KEY), repository.getAvailableKeys(Locale.US),
            "Locale override should only expose its own keys");
        assertEquals(Set.of(GREETING_KEY, FAREWELL_KEY, NESTED_KEY, DEFAULT_ONLY_KEY), repository.getAllAvailableKeys(),
            "All keys across locales should be unioned");

        final TranslationRepository.RepositoryMetadata metadata = repository.getMetadata();
        assertEquals("yaml", metadata.getType(), "Metadata type should describe the repository");
        assertEquals(tempDir.toString(), metadata.getSource(), "Metadata source should reflect the directory");
        assertTrue(metadata.getLastModified() > 0, "Last modified should be populated after reload");
        assertEquals(6, metadata.getTotalTranslations(), "Total translations should aggregate every locale entry");
        assertEquals(tempDir.toString(), metadata.getProperty("directory"),
            "Directory property should echo the source path");
        assertEquals("3", metadata.getProperty("locales"),
            "Locales property should report the number of loaded locales");

        final long initialLastModified = metadata.getLastModified();
        assertTrue(initialLastModified > 0, "Initial last modified timestamp should be set");
        Thread.sleep(20L);
        repository.reload().join();
        final long afterReloadLastModified = repository.getMetadata().getLastModified();
        assertTrue(afterReloadLastModified > initialLastModified,
            "Asynchronous reload should update the last modified timestamp");

        repository.setDefaultLocale(Locale.US);
        assertEquals(Locale.US, repository.getDefaultLocale(), "Default locale should update when available");
        assertThrows(IllegalArgumentException.class, () -> repository.setDefaultLocale(Locale.FRANCE),
            "Setting an unavailable locale must throw an exception");
    }

    @Test
    void factoryCreateLoadsTranslationsAndSupportsFallbacks(@TempDir final Path tempDir) throws IOException {
        writeStandardTranslations(tempDir);

        final YamlTranslationRepository repository = YamlTranslationRepository.create(tempDir, Locale.GERMAN);

        assertEquals(Optional.of("Howdy partner!"), repository.getTranslation(GREETING_KEY, Locale.US),
            "Factory create should eagerly load translations for direct lookups");
        assertEquals(Optional.of("Goodbye\nSee you soon"), repository.getTranslation(FAREWELL_KEY, Locale.US),
            "Language fallback should operate after factory load");
        assertEquals(Optional.of("Standardwert"), repository.getTranslation(DEFAULT_ONLY_KEY, Locale.US),
            "Default locale fallback should work with factory create instances");
    }

    @Test
    void reloadNotifiesErrorsForInvalidYaml(@TempDir final Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("de.yml"), "greeting:\n  hello: \"Hallo!\"\n");
        Files.writeString(tempDir.resolve("en.yml"), "greeting: [unterminated\n");

        final YamlTranslationRepository repository = new YamlTranslationRepository(tempDir, Locale.GERMAN);

        final AtomicInteger reloads = new AtomicInteger();
        final AtomicInteger loadedCount = new AtomicInteger();
        final List<Throwable> errors = new ArrayList<>();

        repository.addListener(new TranslationRepository.RepositoryListener() {
            @Override
            public void onReload(final TranslationRepository repository) {
                reloads.incrementAndGet();
            }

            @Override
            public void onTranslationLoaded(final TranslationRepository repository, final TranslationKey key,
                                            final Locale locale, final String translation) {
                loadedCount.incrementAndGet();
            }

            @Override
            public void onError(final TranslationRepository repository, final Throwable error) {
                errors.add(error);
            }
        });

        repository.reload().join();

        assertEquals(1, reloads.get(), "Reload listener should still fire for invalid YAML");
        assertTrue(loadedCount.get() > 0, "Valid files should still trigger translation loaded events");
        assertFalse(errors.isEmpty(), "Invalid YAML should be reported to listeners");
        assertTrue(errors.get(0) instanceof Exception, "Errors should include the thrown exception instance");
    }

    @Test
    void reloadCreatesMissingDirectoryAndLogsWarning(@TempDir final Path tempDir) {
        final Path translationsDir = tempDir.resolve("missing");
        final YamlTranslationRepository repository = new YamlTranslationRepository(translationsDir, Locale.ENGLISH);

        final Logger logger = Logger.getLogger(YamlTranslationRepository.class.getName());
        final List<LogRecord> records = new ArrayList<>();
        final Handler handler = new Handler() {
            @Override
            public void publish(final LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
                // no-op
            }

            @Override
            public void close() {
                // no-op
            }
        };

        logger.addHandler(handler);
        try {
            repository.reload().join();
        } finally {
            logger.removeHandler(handler);
        }

        assertTrue(Files.exists(translationsDir), "Reload should create missing directories");
        assertTrue(records.stream().anyMatch(record -> record.getLevel() == Level.WARNING
            && record.getMessage().contains("Translations directory did not exist")),
            "A warning should be logged when the directory is created");
        assertTrue(repository.getAvailableLocales().isEmpty(), "No locales should be reported when no files exist");

        final TranslationRepository.RepositoryMetadata metadata = repository.getMetadata();
        assertEquals(0, metadata.getTotalTranslations(), "No translations should be counted for a new directory");
        assertEquals(translationsDir.toString(), metadata.getSource(),
            "Metadata source should reflect the created directory");
        assertNotNull(metadata.getProperty("directory"), "Directory property should be exposed even without files");
    }

    private static void writeStandardTranslations(final Path directory) throws IOException {
        Files.writeString(directory.resolve("en.yml"), """
            greeting:
              hello: "Hello!"
            farewell:
              lines:
                - "Goodbye"
                - "See you soon"
            nested:
              deeper:
                value: "English depth"
            """);

        Files.writeString(directory.resolve("en_US.yml"), """
            greeting:
              hello: "Howdy partner!"
            """);

        Files.writeString(directory.resolve("de.yml"), """
            greeting:
              hello: "Hallo!"
            fallback:
              only: "Standardwert"
            """);
    }
}
