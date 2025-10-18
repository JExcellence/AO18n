package de.jexcellence.jextranslate.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Optional;

public interface LocaleResolver {

    @NotNull
    Optional<Locale> resolveLocale(@NotNull Player player);

    @NotNull
    Locale getDefaultLocale();

    void setDefaultLocale(@NotNull Locale locale);

    default boolean setPlayerLocale(@NotNull Player player, @NotNull Locale locale) {
        return false;
    }

    default boolean clearPlayerLocale(@NotNull Player player) {
        return false;
    }

    default boolean supportsLocaleStorage() {
        return false;
    }
}
