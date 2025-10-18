package de.jexcellence.jextranslate.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.regex.Pattern;

public record TranslationKey(@NotNull String key) {

    private static final String SEPARATOR = ".";
    private static final Pattern VALID_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    public TranslationKey {
        Objects.requireNonNull(key, "Key cannot be null");
        validateKey(key);
    }

    @NotNull
    public static TranslationKey of(@NotNull final String key) {
        return new TranslationKey(key);
    }

    @NotNull
    public static TranslationKey of(@NotNull final String... segments) {
        if (segments.length == 0) {
            throw new IllegalArgumentException("At least one segment is required");
        }
        return new TranslationKey(String.join(SEPARATOR, segments));
    }

    private static void validateKey(@NotNull final String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Key cannot be empty");
        }
        if (key.startsWith(SEPARATOR) || key.endsWith(SEPARATOR)) {
            throw new IllegalArgumentException("Key cannot start or end with separator: " + key);
        }
        if (key.contains("..")) {
            throw new IllegalArgumentException("Key cannot contain consecutive separators: " + key);
        }
        if (!VALID_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException(
                "Key contains invalid characters (only lowercase letters, numbers, dots, hyphens, underscores): " + key
            );
        }
    }

    @NotNull
    public TranslationKey child(@NotNull final String segment) {
        Objects.requireNonNull(segment, "Segment cannot be null");
        return new TranslationKey(this.key + SEPARATOR + segment);
    }

    @Nullable
    public TranslationKey parent() {
        final int lastDot = this.key.lastIndexOf(SEPARATOR);
        return lastDot == -1 ? null : new TranslationKey(this.key.substring(0, lastDot));
    }

    @NotNull
    public String lastSegment() {
        final int lastDot = this.key.lastIndexOf(SEPARATOR);
        return lastDot == -1 ? this.key : this.key.substring(lastDot + 1);
    }

    public boolean startsWith(@NotNull final String prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        return this.key.startsWith(prefix);
    }

    public boolean startsWith(@NotNull final TranslationKey prefix) {
        Objects.requireNonNull(prefix, "Prefix cannot be null");
        return startsWith(prefix.key());
    }

    public int depth() {
        return (int) this.key.chars().filter(ch -> ch == '.').count() + 1;
    }

    public boolean isRoot() {
        return !this.key.contains(SEPARATOR);
    }

    @Override
    public String toString() {
        return this.key;
    }
}
