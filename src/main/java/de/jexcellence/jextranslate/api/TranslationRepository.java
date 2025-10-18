package de.jexcellence.jextranslate.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface TranslationRepository {

    @NotNull
    Optional<String> getTranslation(@NotNull TranslationKey key, @NotNull Locale locale);

    @NotNull
    default CompletableFuture<Optional<String>> getTranslationAsync(@NotNull TranslationKey key, @NotNull Locale locale) {
        return CompletableFuture.supplyAsync(() -> getTranslation(key, locale));
    }

    default boolean hasTranslation(@NotNull TranslationKey key, @NotNull Locale locale) {
        return getTranslation(key, locale).isPresent();
    }

    @NotNull
    Set<Locale> getAvailableLocales();

    @NotNull
    Locale getDefaultLocale();

    void setDefaultLocale(@NotNull Locale locale);

    @NotNull
    Set<TranslationKey> getAvailableKeys(@NotNull Locale locale);

    @NotNull
    Set<TranslationKey> getAllAvailableKeys();

    @NotNull
    CompletableFuture<Void> reload();

    void addListener(@NotNull RepositoryListener listener);

    void removeListener(@NotNull RepositoryListener listener);

    @NotNull
    RepositoryMetadata getMetadata();

    interface RepositoryListener {
        default void onReload(@NotNull TranslationRepository repository) {}

        default void onTranslationLoaded(@NotNull TranslationRepository repository, @NotNull TranslationKey key, @NotNull Locale locale, @NotNull String translation) {}

        default void onError(@NotNull TranslationRepository repository, @NotNull Throwable error) {}
    }

    interface RepositoryMetadata {
        @NotNull
        String getType();

        @NotNull
        String getSource();

        long getLastModified();

        int getTotalTranslations();

        @Nullable
        String getProperty(@NotNull String key);
    }
}
