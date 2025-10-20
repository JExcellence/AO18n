package de.jexcellence.jextranslate.api;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

class TranslationRepositoryTest {

    private static final TranslationKey DUMMY_KEY = new TranslationKey("dummy.key");
    private static final Locale DUMMY_LOCALE = Locale.GERMAN;

    @Test
    void getTranslationAsyncDelegatesToSynchronousLookup() {
        TrackingRepository repository = new TrackingRepository(Optional.of("value"));

        Optional<String> result = repository.getTranslationAsync(DUMMY_KEY, DUMMY_LOCALE).join();

        assertSame(repository.translation, result, "Asynchronous result should reference the same Optional instance");
        assertEquals(1, repository.getTranslationInvocationCount,
                "getTranslation should be called exactly once by the async variant");
    }

    @Test
    void hasTranslationReflectsOptionalPresence() {
        TrackingRepository presentRepository = new TrackingRepository(Optional.of("present"));
        assertTrue(presentRepository.hasTranslation(DUMMY_KEY, DUMMY_LOCALE),
                "hasTranslation should report true when a translation is present");

        TrackingRepository emptyRepository = new TrackingRepository(Optional.empty());
        assertFalse(emptyRepository.hasTranslation(DUMMY_KEY, DUMMY_LOCALE),
                "hasTranslation should report false when no translation is present");
    }

    @Test
    void asyncTranslationErrorsSurfaceThroughCompletionException() {
        TrackingRepository failingRepository = new TrackingRepository(Optional.of("unused")) {
            @Override
            public Optional<String> getTranslation(TranslationKey key, Locale locale) {
                throw new IllegalStateException("boom");
            }
        };

        CompletionException exception = assertThrows(CompletionException.class,
                () -> failingRepository.getTranslationAsync(DUMMY_KEY, DUMMY_LOCALE).join(),
                "join() should wrap repository failures in a CompletionException");
        assertTrue(exception.getCause() instanceof IllegalStateException,
                "The original failure should be preserved as the CompletionException cause");
    }

    private static class TrackingRepository implements TranslationRepository {

        private final Optional<String> translation;
        private int getTranslationInvocationCount;

        private TrackingRepository(Optional<String> translation) {
            this.translation = translation;
        }

        @Override
        public Optional<String> getTranslation(TranslationKey key, Locale locale) {
            getTranslationInvocationCount++;
            return translation;
        }

        @Override
        public Set<Locale> getAvailableLocales() {
            return Collections.singleton(localeOrDefault());
        }

        @Override
        public Locale getDefaultLocale() {
            return localeOrDefault();
        }

        @Override
        public void setDefaultLocale(Locale locale) {
            // no-op for tests
        }

        @Override
        public Set<TranslationKey> getAvailableKeys(Locale locale) {
            return Collections.singleton(DUMMY_KEY);
        }

        @Override
        public Set<TranslationKey> getAllAvailableKeys() {
            return Collections.singleton(DUMMY_KEY);
        }

        @Override
        public CompletableFuture<Void> reload() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void addListener(RepositoryListener listener) {
            // no-op for tests
        }

        @Override
        public void removeListener(RepositoryListener listener) {
            // no-op for tests
        }

        @Override
        public RepositoryMetadata getMetadata() {
            return new RepositoryMetadata() {
                @Override
                public String getType() {
                    return "tracking";
                }

                @Override
                public String getSource() {
                    return "memory";
                }

                @Override
                public long getLastModified() {
                    return 0;
                }

                @Override
                public int getTotalTranslations() {
                    return 0;
                }

                @Override
                public String getProperty(String key) {
                    return null;
                }
            };
        }

        private Locale localeOrDefault() {
            return translation.map(value -> DUMMY_LOCALE).orElse(Locale.ENGLISH);
        }
    }
}
